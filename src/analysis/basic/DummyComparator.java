package analysis.basic;

import java.util.Comparator;
import scala.Tuple2;


public class DummyComparator implements java.io.Serializable, Comparator<Tuple2<Integer, String>>{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public int compare(Tuple2<Integer, String> tuple1, Tuple2<Integer, String> tuple2){
//        return tuple1._1 < tuple2._1 ? 0 : 1;
        return Integer.compare(tuple2._1, tuple1._1);
	}
//public class DummyComparator implements Comparator<Tuple2<Integer, String>>, java.io.Serializable {
//    /**
//	 * 
//	 */
//	private static final long serialVersionUID = 1L;
//
//	@Override
//    public int compare(Tuple2<Integer, String> tuple1, Tuple2<Integer, String> tuple2) {
//        return tuple1._1 < tuple2._1 ? 0 : 1;
//    }
//	@Override
//    public int compare(Tuple2<Integer, String> o1, Tuple2<Integer, String> o2) {
//        return Integer.compare(o1._1(), o2._1());
//    }
//}
}
