package water;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.Notification;
import javax.management.NotificationEmitter;

/**
 * Manages memory assigned to key/value pairs. All byte arrays used in
 * keys/values should be allocated through this class - otherwise we risking
 * running out of java memory, and throw unexpected OutOfMemory errors. The
 * theory here is that *most* allocated bytes are allocated in large chunks by
 * allocating new Values - with large backing arrays. If we intercept these
 * allocation points, we cover most Java allocations. If such an allocation
 * might trigger an OOM error we first free up some other memory.
 * 
 * MemoryManager monitors memory used by the K/V store (by walking through the
 * store (see Cleaner) and overall heap usage by hooking into gc.
 * 
 * Memory is freed if either the cached memory is above the limit or if the
 * overall heap usage is too high (in which case we want to use less mem for
 * cache). There is also a lower limit on the amount of cache so that we never
 * delete all the cache and therefore some computation should always be able to
 * progress.
 * 
 * The amount of memory to be freed is determined as the max of cached mem above
 * the limit and heap usage above the limit.
 * 
 * @author tomas
 * @author cliffc
 */
public abstract class MemoryManager {

  // estimate of current memory held by values in K/V store
  static volatile long CACHED = 0;
  // max heap memory
  static final long MEM_MAX;

  static final long MEM_CRITICAL; // clear all above
  static final long MEM_HI; // clean half of above

  static final long CACHE_HI; // clean half of above
  static final long CACHE_LO; // don not clean anything if below

  // Number of threads blocked waiting for memory
  private static int NUM_BLOCKED = 0;

  
// amount of memory to be cleaned by the cleaner thread
  static volatile long mem2Free;

//block new allocations if false
  static boolean canAllocate = true;

  private static int _sCounter = 0; // contains count of successive "old" values
  private static int _uCounter = 0; // contains count of successive "young"
                                    // values

  // current amount of memory to be freed
  // (set by gc callback, cleaner method decreases it as it frees memory)

  // expected recent use time, values not used in this interval will be
  // removed when low memory
  static long _maxTime = 4000;
  private static long _previousT = _maxTime;

  // if overall heap memory goes over this limit,
  // stop allocating new value sand remove as much as
  // possible from the cache

  // if true non persisted old values will be persisted and freed
  static boolean _doPersist = true;

  public static boolean memCritical() {
    return !canAllocate;
  }

  public static void setCacheSz(long c) {
    CACHED = c;
    if (CACHED > CACHE_HI) {
      long free = (CACHED - CACHE_HI) >> 1;
      if (free > mem2Free) {
        // no need to wake up cleaner thread cause this method is expected to be
        // called from the cleaner
        mem2Free = free;
      }
    }
  }

  // test if value should be remove and update _maxTime estimate
  static boolean removeValue(long currentTime, Value v) {
    long deltaT = currentTime - v._lastAccessedTime;
    if (deltaT > _maxTime) {
      _uCounter = 0;
      // if we hit 10 old elements in a row, increase expected age
      if (++_sCounter == 10) {
        if (_previousT > _maxTime) {
          long x = _maxTime;
          _maxTime += ((_previousT - _maxTime) >> 1);
          _previousT = x;
        } else {
          _previousT = _maxTime;
          _maxTime = (_maxTime << 1) - (_maxTime >> 1);
        }
        _sCounter = 0;
      }
      if ((mem2Free > 0) && (CACHED > CACHE_LO)) {
        byte[] m = v._mem;
        if (m != null) {
          v.free_mem();
          mem2Free -= m.length;
          if (!canAllocate && (mem2Free < ((MEM_CRITICAL - MEM_HI) >> 1))) {
            System.out.println("MEMORY BELOW CRITICAL, ALLOWING ALLOCATIONS");
            synchronized (MemoryManager.class) {
              if (!canAllocate) {
                canAllocate = true;
                if (NUM_BLOCKED > 0) {
                  NUM_BLOCKED = 0;
                  MemoryManager.class.notifyAll();
                }
              }
            }
          }
        }
        return true;
      }
    } else {
      _sCounter = 0;
      // if we hit 10 young elements in a row, decrease expected age
      if (++_uCounter == 10) {
        if (_previousT < _maxTime) {
          long x = _maxTime;
          _maxTime -= ((_maxTime - _previousT) >> 1);
          _previousT = x;
        } else {
          _previousT = _maxTime;
          _maxTime = (_maxTime >> 1) + (_maxTime >> 2);
        }
        _uCounter = 0;
      }
    }
    return false;
  }

  /**
   * Monitors the heap usage after full gc run and tells Cleaner to free memory
   * if mem usage is too high. Stops new allocation if mem usage is critical.
   * 
   * @author tomas
   * 
   */
  private static class HeapUsageMonitor implements
      javax.management.NotificationListener {
    MemoryMXBean _allMemBean = ManagementFactory.getMemoryMXBean(); // general

    HeapUsageMonitor() {
      int c = 0;
      for (MemoryPoolMXBean m : ManagementFactory.getMemoryPoolMXBeans()) {
        if (m.getType() != MemoryType.HEAP) // only interested in HEAP
          continue;
        if (m.isCollectionUsageThresholdSupported()
            && m.isUsageThresholdSupported()) {
          // should be Old pool, get called when memory is critical
          m.setCollectionUsageThreshold((MEM_MAX >> 2));
          NotificationEmitter emitter = (NotificationEmitter) _allMemBean;
          emitter.addNotificationListener(this, null, m);
          ++c;
        }
      }
      assert c == 1;
    }

    /**
     * Callback routine called by JVM after full gc run. Has two functions: 1)
     * sets the amount of memory to be cleaned from the cache by the Cleaner 2)
     * sets the canAllocate flag to false if memory level is critical
     * 
     */
    public void handleNotification(Notification notification, Object handback) {
      String notifType = notification.getType();
      if (notifType
          .equals(MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED)) {
        // overall heap usage
        long heapUsage = _allMemBean.getHeapMemoryUsage().getUsed();
        long clean = 0;
        if (heapUsage > MEM_CRITICAL) { // memory level critical
          System.out.println("MEMORY LEVEL CRITICAL, stopping allocations");
          clean = (heapUsage - MEM_CRITICAL) + ((MEM_CRITICAL - MEM_HI) >> 1);
          canAllocate = false; // stop new allocations until we free enough mem
        } else if (heapUsage > MEM_HI) {
          clean = ((heapUsage - MEM_HI) >> 1);
        }
        if (clean > mem2Free) {
          mem2Free = clean;
          System.out.println("heap usage too high, free " + (mem2Free >> 20)
              + "M");
          synchronized (H2O.STORE) {
            H2O.STORE.notifyAll(); // make sure cleaner thread is running
          }
        }
      }
    }
  }

  static final HeapUsageMonitor _heapMonitor;

  static {
    MEM_MAX = Runtime.getRuntime().maxMemory();

    MEM_CRITICAL = MEM_MAX - (MEM_MAX >> 2);
    MEM_HI = MEM_CRITICAL - (MEM_CRITICAL >> 2);
    CACHE_HI = MEM_MAX >> 1; // start offloading to disk when cache above 1/2 of
                             // the heap
    CACHE_LO = MEM_MAX >> 3; // keep at least 1/8 of the heap for cache
    System.out.println("MAX MEM = " + (MEM_MAX >> 20) + "M, MEM_CRITICAL = "
        + (MEM_CRITICAL >> 20) + "M, MEM_HI = " + (MEM_HI >> 20) + "M");
    _heapMonitor = new HeapUsageMonitor();
  }

  // allocates memory, will block until there is enough available memory
  public static byte[] allocateMemory(int size) {
    if (size < 256)
      return new byte[size];
    while (!canAllocate) {
      // Failed: block until we think we can allocate
      synchronized (MemoryManager.class) {
        NUM_BLOCKED++;
        try {
          MemoryManager.class.wait(1000);
        } catch (InterruptedException ex) {
        }
        --NUM_BLOCKED;
      }
    }
    return new byte[size];
  }

}
