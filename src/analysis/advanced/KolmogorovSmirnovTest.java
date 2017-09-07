package analysis.advanced;

import org.apache.hadoop.conf.Configuration;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.spark.api.java.JavaDoubleRDD;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.mllib.stat.Statistics;
import org.apache.spark.mllib.stat.test.KolmogorovSmirnovTestResult;
import org.bson.BSONObject;

import com.mongodb.hadoop.MongoInputFormat;

public class KolmogorovSmirnovTest {

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

	public void run() {
		@SuppressWarnings("resource")
		JavaSparkContext jsc = new JavaSparkContext(new SparkConf().setMaster("local[4]").setAppName("TestingKolmogorov"));

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

		JavaDoubleRDD data = getDistricts(documents,2017);
		KolmogorovSmirnovTestResult testResult =
				Statistics.kolmogorovSmirnovTest(data, "norm", 0.0, 1.0);
		// summary of the test including the p-value, test statistic, and null hypothesis
		// if our p-value indicates significance, we can reject the null hypothesis
		File f = new File(System.getProperty("user.home"), "outputKolmogorovSmirnov.txt");
		try(PrintWriter out = new PrintWriter(f)){
		    out.println(testResult);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		jsc.stop();
	}


	public static void main(String[] args) {

		long start = System.currentTimeMillis();

		new KolmogorovSmirnovTest().run();

		System.out.println("Completed in " 
				+(System.currentTimeMillis() - start)/1000.0 
				+" seconds");

	}
}