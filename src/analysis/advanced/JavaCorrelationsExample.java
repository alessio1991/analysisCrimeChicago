package analysis.advanced;

import org.apache.hadoop.conf.Configuration;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;

import java.util.ArrayList;
import java.util.List;

import org.apache.spark.api.java.JavaDoubleRDD;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.mllib.stat.Statistics;
import org.bson.BSONObject;

import com.mongodb.hadoop.MongoInputFormat;

public class JavaCorrelationsExample {
	
	public static long min(long x, long y) {
		if (x<=y)
			return x;
		return y;
	}
	
	public static double parseDouble(String strNumber) {
		   if (strNumber != null && strNumber.length() > 0) {
		       try {
		          return Double.parseDouble(strNumber);
		       } catch(Exception e) {
		          return -1;
		       }
		   }
		   else return -1;
		}
	
	public static JavaDoubleRDD getDistricts(JavaPairRDD<Object, BSONObject> documents, int year) {
		JavaDoubleRDD districtsYear = documents.flatMapToDouble(
				t-> {
					int key = ((Integer)t._2.get("Year"));
					String districtTmp = (String.valueOf((t._2.get("District"))));
					double district = parseDouble(districtTmp);									
					List<Double> districts = new ArrayList<Double>();
					if (key == year && district >= 0) {
						districts.add(district);
					}
					return districts;
				});
		return districtsYear;
	}
	
/*	public static List<Double> rddToList(JavaDoubleRDD rdd) {
		long count = rdd.count();
		List<Double> temp = new ArrayList<Double>();
		for(int i=0;i<count;i++)
			temp = rdd.take(i);
		return temp;
	}*/
	
	public static JavaDoubleRDD getDistrictsCut(JavaPairRDD<Object, BSONObject> documents, int year, long length) {
		JavaDoubleRDD districtsYear = documents.flatMapToDouble(
				t-> {
					int key = ((Integer)t._2.get("Year"));
					String districtTmp = (String.valueOf((t._2.get("District"))));
					double district = parseDouble(districtTmp);
					List<Double> districts = new ArrayList<Double>();
					if (key == year && district >= 0 && districts.size() < length) {
						districts.add(district);
					}

					return districts;
				});
		return districtsYear;
	}
	
  public  void run() {

	  JavaSparkContext jsc = new JavaSparkContext(new SparkConf().setMaster("local[4]").setAppName("Correlation"));
		
		// Set configuration options for the MongoDB Hadoop Connector.
		Configuration mongodbConfig = new Configuration();
		
		// MongoInputFormat allows us to read from a live MongoDB instance.
		// We could also use BSONFileInputFormat to read BSON snapshots.
		mongodbConfig.set("mongo.job.input.format", "com.mongodb.hadoop.MongoInputFormat");

		// MongoDB connection string naming a collection to use.
		// If using BSON, use "mapred.input.dir" to configure the directory
		// where BSON files are located instead.
		mongodbConfig.set("mongo.input.uri",
				"mongodb://localhost:27017/bigData.crimes");

		// Create an RDD backed by the MongoDB collection.
		JavaPairRDD<Object, BSONObject> documents = jsc.newAPIHadoopRDD(
				mongodbConfig,            // Configuration
				MongoInputFormat.class,   // InputFormat: read from a live cluster.
				Object.class,             // Key class
				BSONObject.class          // Value class
				);
	
	JavaDoubleRDD districts2015tmp = getDistricts(documents, 2015);
	JavaDoubleRDD districts2016tmp = getDistricts(documents, 2016);
		
    long length2015 = districts2015tmp.count();
    long length2016 = districts2016tmp.count();
    long min = min(length2015, length2016);
    
    JavaDoubleRDD districts2015 = null;
    JavaDoubleRDD districts2016 = null;
    
    if (length2015 == min) {
    	districts2016 = getDistrictsCut(documents,2016,min);
    	districts2015 = districts2015tmp;
    } else {
    	districts2015 = getDistrictsCut(documents,2015,min);
    	districts2016 = districts2016tmp;
    }
   /* 
    List<Double> tmp15 = new ArrayList<Double>();
    tmp15 = rddToList(districts2015);
    List<Double> tmp16 = new ArrayList<Double>();
    tmp16 = rddToList(districts2016);
    districts2015 = jsc.parallelizeDoubles(tmp15);
    districts2016 = jsc.parallelizeDoubles(tmp16);
    */
	// compute the correlation using Pearson's method. Enter "spearman" for Spearman's method.
    // If a method is not specified, Pearson's method will be used by default.
    Double correlation = Statistics.corr(districts2015.srdd(), districts2016.srdd(), "pearson");
    System.out.println("Correlation is: " + correlation);
  }
  
  public static void main(final String[] args) {
		long start = System.currentTimeMillis();

		new JavaCorrelationsExample().run();
		
		System.out.println("Completed in " 
				+(System.currentTimeMillis() - start)/1000.0 
				+" seconds");
	}
}
