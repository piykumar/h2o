package hexlytics.rf;

import hexlytics.rf.Data.Row;
import hexlytics.rf.Statistic.Split;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

public class Data implements Iterable<Row> {

  
  public final class Row {  
    int index;    
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(index);
      sb.append(" ["+classOf()+"]:");
      for (int i = 0; i <data_.columns();i++) 
        sb.append(" "+getS(i));
      return sb.toString();
    }   
    public int numClasses() { return classes(); }
    public int classOf() { return  data_.classOf(index); }
    public double getD(int col) { return data_.getD(col,index); }
    public short getS(int col) { return data_.getS(index,col); }
    
    /** To support weights if we ever do in the future. */
    public final double weight() { return 1; }
    
    /** Support for binning information on the columns.  */
    public final int getColumnClass(int colIndex) {
      return data_.getColumnClass(index,colIndex);
    }
  }
 
  public class RowIter implements Iterator<Row> {
    final Row r = new Row();
    int pos = 0;
    public boolean hasNext() { return pos<rows(); }
    public Row next() { fillRow(r,pos);  pos++;  return r; }
    public void remove() { throw new Error("Unsported"); }
  }

  protected final DataAdapter data_;
  
  String name_;
  
  static final DecimalFormat df = new  DecimalFormat ("0.##");

  /** Returns new Data object that stores all adapter's rows unchanged.   */
  public static Data make(DataAdapter da) { return new Data(da); }
     
  protected Data(DataAdapter da) { data_ = da; name_=da.name(); }
        
  public Iterator<Row> iterator() { return new RowIter(); } 
  
  /** Fills the given row object with data stored from the index-th row in the
   * current view. Note that the index is indexed in current data object's view
   */
  protected final Row fillRow(Row r, int rowIndex) { r.index = permute(rowIndex); return r; }
  
  /** Returns the row with given index. */
  public Row getRow(int rowIndex) { return fillRow(new Row(),rowIndex); }  

  
  public int columnClasses(int colIndex) { return data_.columnClasses(colIndex); }

  /** Given a value in enum format, returns a value in the original range. */
  public float unmap(int col, float v){
    short idx = (short)v; // Convert split-point of the form X.5 to a (short)X    
    C c = data_.c_[col];
    if (v == idx) {  // this value isn't a split
      return c._v2o[idx+0];        
    } else {
      double dlo = c._v2o[idx+0]; // Convert to the original values
      double dhi = (idx < c.sz_) ? c._v2o[idx+1] : dlo+1.0;
      double dmid = (dlo+dhi)/2.0; // Compute an original split-value
      float fmid = (float)dmid;
      assert (float)dlo < fmid && fmid < (float)dhi; // Assert that the float will properly split
      return fmid;
    }
  }
    
  /** Returns the number of rows that is accessible by this Data object. */
  public int rows()        { return data_.rows(); }  
  public  int columns()    { return data_.columns() -1 ; } // -1 to remove class column
  public  int classes()    { return data_.classes(); } 
  public Random random()          { return data_.random_; }
  public String colName(int c)    { return data_.colName(c); } 
  public double colMin(int c)     { return data_.colMin(c); }
  public double colMax(int c)     { return data_.colMax(c); }
  public double colTot(int c)     { return data_.colTot(c); }
  public  String[] columnNames()  { return data_.columnNames(); }
  public  String classColumnName(){ return data_.classColumnName(); } 
  public String name() { return name_; }   
  public int last(int column) { return data_.c_[column].o2v_.size(); }
  
  private final boolean silent = true;
  
  public String toString() {
    String res = "Data "+ name()+"\n";
    if (columns()>0) { res+= rows()+" rows, "+ columns() + " cols, "+ classes() +" classes\n"; }
    if (silent) return res;
    String[][] s = new String[columns()][4];
    for(int i=0;i<columns();i++){
      s[i][0] = "col("+colName(i)+")";
      s[i][1] = "["+df.format(colMin(i)) +","+df.format(colMax(i))+"]" ;
      s[i][2] = " avg=" + df.format(colTot(i)/(double)rows());
      s[i][3] = "";//" precision=" + colPre(i); 
    }
    int[] l = new int[4];
    for(int j=0;j<4;j++)
      for(int k=0;k<columns();k++) 
        l[j] = Math.max(l[j], s[k][j].length());
    for(int k=0;k<columns();k++) {
      for(int j=0;j<4;j++) {
        res+= s[k][j]; int pad = l[j] - s[k][j].length();
        for(int m=0;m<=pad;m++) res+=" ";
      }
      res+="\n";
    }      
    
   /* res +="========\n";  res +="class histogram\n";
    int[] dist =null;// data_.c_[data_.classIdx_].distribution();
    int[] sorted = Arrays.copyOf(dist, dist.length);
    Arrays.sort(sorted);
    int max = sorted[sorted.length-1];
    int[] prop = new int[dist.length];
    for(int j=0;j<prop.length;j++)
      prop[j]= (int) (10.0 * ( (float)dist[j]/max )); 
    for(int m=10;m>=0;m--) {
      for(int j=0;j<prop.length;j++)
        if (prop[j]>= m) res += "**  "; else res+="    ";
      res+="\n";
    }
    
    res+="[";
    for(int j=0;j<dist.length;j++) res+=dist[j]+((j==dist.length-1)?"":",");     
    res+="]\n";
    */
    res+=head(5);
    
    return res;
  }
  
  public String head(int j) {
    String res ="";
    for(Row r : this) {
      res += "["; for(int i=0;i<data_.columns();i++) res += r.getS(i)+",";res += "]\n";
      if (j--==0) break;
    }
    return res;
  }
  
  // subsets -------------------------------------------------------------------
  
  public void filter(Split c, Data[] result, Statistic[] stats) {
    int l=0, r=0;
    int[] li = new int[rows()], ri=new int[rows()];
    int i = 0;
    for(Row row : this) {
      if (row.getS(c.column) < c.value) {     
        stats[0].add(row); li[l++] = permute(i);
      } else {
        stats[1].add(row); ri[r++] = permute(i);
      }
      i++;
    }
    result[0]= new Subset(this,li,l);
    result[1]= new Subset(this,ri,r);
  }
  
  public void filter(int column, int split, Data[] result, BaseStatistic left, BaseStatistic right) {
    int l=0, r=0;
    int[] li = new int[rows()], ri=new int[rows()];
    int i = 0;
    for(Row row : this) {
      if (row.getColumnClass(column) <= split) {
        left.add(row); li[l++] = permute(i);
      } else {
        right.add(row); ri[r++] = permute(i);
      }
      i++;
    }
    result[0]= new Subset(this,li,l);
    result[1]= new Subset(this,ri,r);
  }

  public void filterExclusion(int column, int split, Data[] result, GiniStatistic/*Statistic*/[] stats) {
    int l=0, r=0;
    int[] li = new int[rows()], ri=new int[rows()];
    int i = 0;
    for(Row row : this) {
      if (row.getColumnClass(column) == split) {
        stats[0].add(row); li[l++] = permute(i);
      } else {
        stats[1].add(row); ri[r++] = permute(i);
      }
      i++;
    }
    result[0]= new Subset(this,li,l);
    result[1]= new Subset(this,ri,r);
  }

  public Data sampleWithReplacement(double bagSizePct) {        
    int[] sample = new int[(int)(rows() * bagSizePct)];    
    Random r = new Random(data_.random_.nextLong());
    for (int i=0;i<sample.length;i++) 
      sample[i] = permute(r.nextInt(rows())); 
    Arrays.sort(sample); // make sure we access data in order
    return new Subset(this,sample,sample.length);
  }  
  
  public Data complement() { throw new Error("Only for subsets."); }
  
  
  /** Returns the original index of the row in the DataAdapter object for row
   * indexed in the given Data object. 
   */
  protected int permute(int idx) { return idx; }
  
}

class Subset extends Data {  
  final protected int[] permutation_;
  final int sz_;
  final Data parent_;
  
  /** Returns the original DataAdapter's row index of given row. */
  @Override protected int permute(int idx) { return permutation_[idx]; }
  
  /** Returns the number of rows. */
  @Override public int rows() { return sz_; }

  // Totals, mins & maxs are not recomputed on the subsets.
  @Override public double colMin(int c)     { return data_.colMin(c); }
  @Override public double colMax(int c)     { return data_.colMax(c); }
  @Override public double colTot(int c)     { return Double.NaN; }
 
  /** Creates new subset of the given data adapter. The permutation is an array
   * of original row indices of the DataAdapter object that will be used. 
   */
  public Subset(Data data, int[] permutation, int sz) {
    super(data.data_);
    sz_ = sz;
    parent_=data;
    permutation_ = permutation;
    name_ =data.name_+"->subset"; 
  }
  
  public Data complement() {
    Set<Integer> s = new HashSet<Integer>();
    for (Row r : parent_) s.add(r.index);
    for (Row r : this) s.remove(r.index);
    int[] p = new int[s.size()];
    int i=0;
    for (Integer v : s) p[i++] = v.intValue(); 
    return new Subset(parent_,p,p.length);
  }  
}