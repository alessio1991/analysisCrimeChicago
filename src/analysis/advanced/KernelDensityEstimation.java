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
import org.apache.spark.mllib.stat.KernelDensity;
import org.apache.spark.rdd.RDD;
import org.bson.BSONObject;

import com.mongodb.hadoop.MongoInputFormat;

public class KernelDensityEstimation {

	//convert string into double
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

	//get all district for a specific year
	@SuppressWarnings("static-access")
	public static RDD<Object> getDistricts(JavaPairRDD<Object, BSONObject> documents, int year) {
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
		return districtsYear.toRDD(districtsYear);
	}


	public void run()  {
		@SuppressWarnings("resource")
		JavaSparkContext jsc = new JavaSparkContext(new SparkConf().setMaster("local[4]").setAppName("KernelDensity"));

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

		//get all district of year 2017
		RDD<Object> districts2017 = getDistricts(documents, 2017);
		// Construct the density estimator with the sample data
		KernelDensity kd = new KernelDensity().setSample(districts2017);

		// Find density estimates for the given values
		double[] densities = kd.estimate(new double[]{1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0,11.0,
				12.0,13.0,14.0,15.0,16.0,17.0,18.0,19.0,20.0,21.0,22.0,23.0,24.0,25.0});

		File f = new File(System.getProperty("user.home"), "outputKernelDensity.txt");
	    try (PrintWriter pw = new PrintWriter(f)) {
	    	int i = 1;
	        for (Double d : densities) {
	        	pw.print("Distretto n." +i+ ": " );
	            pw.println(d);
	            i++;
	        }
	    } catch (FileNotFoundException e) {
	        e.printStackTrace();
	    }
		
		jsc.stop();

	}

	public static void main(String[] args) {

		long start = System.currentTimeMillis();

		new KernelDensityEstimation().run();

		System.out.println("Completed in " 
				+(System.currentTimeMillis() - start)/1000.0 
				+" seconds");
	}
}