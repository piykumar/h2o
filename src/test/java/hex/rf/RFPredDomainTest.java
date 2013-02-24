package hex.rf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import hex.rf.Tree.StatType;

import org.junit.Test;

import water.*;
import water.util.TestUtil;

public class RFPredDomainTest extends TestUtil {

  public static final long[] a(long ...p) { return p; }

  protected void runIrisRF(final String trainDS, final String testDS, double expTestErr, long[][] expCM, String[] expDomain) throws Exception {
    String trainKeyName = "iris_train.hex";
    Key trainKey        = loadAndParseKey(trainKeyName, trainDS);
    ValueArray trainData = ValueArray.value(trainKey);

    int  trees    = 10;
    int  depth    = 50;
    StatType  statType = StatType.ENTROPY;
    long seed     = 0xae44a87f9edf1cbL;
    int[] cols    = new int[]{0,1,2,3,4};

    // Start the distributed Random Forest
    String modelName   = "model";
    final Key modelKey = Key.make(modelName);
    DRF drf = hex.rf.DRF.webMain(modelKey,cols,trainData,trees,depth,1.0f,(short)1024,statType,seed,false, null, -1, false, null, 0, 0);
    // Block
    drf.get();
    RFModel model = UKV.get(modelKey, new RFModel());

    String testKeyName  = "iris_test.hex";
    Key testKey         = loadAndParseKey(testKeyName, testDS);
    ValueArray testData = ValueArray.value(testKey);

    Confusion confusion = Confusion.make(model, testData._key, model._features-1, null, false);
    confusion.report();
    assertEquals("Error rate", expTestErr, confusion.classError(), 0.001);
    assertEquals("CF dimension", expCM.length, confusion._matrix.length);
    for (int i = 0; i<expCM.length; i++) {
      assertArrayEquals(expCM[i], confusion._matrix[i]);
    }
    assertArrayEquals("Confusion matrix", expCM, confusion._matrix);
    assertArrayEquals("CM domain", expDomain, confusion.domain());

    model.deleteKeys();
    UKV.remove(modelKey);
    UKV.remove(testKey);
    UKV.remove(trainKey);
    UKV.remove(confusion.keyFor());
  }

  /**
   * Scenario:
   *   model: A B C
   *   data : A B C D E
   *   CM   : A B C D E
   */
  @Test
  public void irisExtra() throws Exception {
    long[][] cm = new long[][] {
        a(0, 0,  0, 0,  1),
        a(0, 20, 0, 0,  0),
        a(0, 0,  0, 1,  0),
        a(0, 0,  0, 16, 0),
        a(0, 0,  0, 1,  13)
    };
    String[] cmDomain = new String[] {"Iris-borovicka", "Iris-setosa", "Iris-slivovicka", "Iris-versicolor", "Iris-virginica" };

    runIrisRF("smalldata/test/classifier/iris_train.csv",
              "smalldata/test/classifier/iris_test_extra.csv",
              0.057f, cm, cmDomain);
  }

  /**
   * Scenario:
   *   model: A B C
   *   data :   B
   *   CM   : A B C
   */
  @Test
  public void irisMissing() throws Exception {
    long[][] cm = new long[][] {
        a(0, 0,  0),
        a(0, 16, 0),
        a(0, 0,  0),
    };
    String[] cmDomain = new String[] {"Iris-setosa", "Iris-versicolor", "Iris-virginica" };

    runIrisRF("smalldata/test/classifier/iris_train.csv",
              "smalldata/test/classifier/iris_test_missing.csv",
              0.0f, cm, cmDomain);
  }

  /**
   * Scenario:
   *   model: A B C
   *   data :   B   D E
   *   CM   : A B C D E
   */
  @Test
  public void irisMissingAndExtra() throws Exception {
    long[][] cm = new long[][] {
        a(0, 0,  0, 0, 1),
        a(0, 0,  0, 0, 0),
        a(0, 0,  0, 1, 0),
        a(0, 0,  0,16, 0),
        a(0, 0,  0, 0, 0),
    };
    String[] cmDomain = new String[] {"Iris-borovicka", "Iris-setosa", "Iris-slivovicka", "Iris-versicolor", "Iris-virginica" };

    runIrisRF("smalldata/test/classifier/iris_train.csv",
              "smalldata/test/classifier/iris_test_missing_extra.csv",
              0.111f, cm, cmDomain);
  }


  /**
   * Scenario:
   *   model: [0,2]
   *   data : [0,4]
   *   CM   : [0,4]
   */
  @Test
  public void irisNumericExtra() throws Exception {
    long[][] cm = new long[][] {
        a(20, 0,  0,  0, 0),
        a(0,  16, 0,  0, 0),
        a(0,  1,  13, 0, 0),
        a(0,  0,  1,  0, 0),
        a(0,  1,  0,  0, 0),
    };
    String[] cmDomain = new String[] {"0", "1", "2", "3", "4" };

    runIrisRF("smalldata/test/classifier/iris_train_numeric.csv",
              "smalldata/test/classifier/iris_test_numeric_extra.csv",
              0.057f, cm, cmDomain);
  }

  /**
   * Scenario:
   *   model: [0,2]
   *   data : [2,2]
   *   CM   : [0,2]
   */
  @Test
  public void irisNumericMissing() throws Exception {
    long[][] cm = new long[][] {
        a(0, 0, 0),
        a(0, 16,  0),
        a(0, 0,  0),
    };
    String[] cmDomain = new String[] {"0", "1", "2" };

    runIrisRF("smalldata/test/classifier/iris_train_numeric.csv",
              "smalldata/test/classifier/iris_test_numeric_missing.csv",
              0.0f, cm, cmDomain);
  }

  /**
   * Scenario:
   *   model: [0,2]
   *   data : [0,4]\{0}\{2}
   *   CM   : [0,4]
   */
  @Test
  public void irisNumericMissingAndExtra() throws Exception {
    long[][] cm = new long[][] {
        a(0, 0,  0, 0, 0),
        a(0, 16, 0, 0, 0),
        a(0, 0,  0, 0, 0),
        a(0, 0,  1, 0, 0),
        a(0, 1,  0, 0, 0),
    };
    String[] cmDomain = new String[] {"0", "1", "2", "3", "4"};
    runIrisRF("smalldata/test/classifier/iris_train_numeric.csv",
              "smalldata/test/classifier/iris_test_numeric_missing_extra.csv",
              0.111f, cm, cmDomain);
  }

  /**
   * Scenario:
   *   model: [0,2]
   *   data : [-1,4]
   *   CM   : [-1,4]
   */
  @Test
  public void irisNumericExtra2() throws Exception {
    long[][] cm = new long[][] {
        a(0, 0,  0, 1, 0, 0),
        a(0, 20, 0, 0, 0, 0),
        a(0, 0, 16, 0, 0, 0),
        a(0, 0,  1,13, 0, 0),
        a(0, 0,  0, 0, 0, 0),
        a(0, 0,  1, 0, 0, 0),
    };
    String[] cmDomain = new String[] {"-1", "0", "1", "2", "3", "4"};
    runIrisRF("smalldata/test/classifier/iris_train_numeric.csv",
              "smalldata/test/classifier/iris_test_numeric_extra2.csv",
              0.057f, cm, cmDomain);
  }
}