package analysis.basic;

import com.mongodb.hadoop.MongoInputFormat;

import com.mongodb.hadoop.MongoOutputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import scala.Tuple2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;



public class TopNoArrest implements java.io.Serializable {

	private static final long serialVersionUID = 1L;

	public void run() {
		
		JavaSparkContext sc = new JavaSparkContext(new SparkConf().setMaster("local[4]").setAppName("CrimesInChicago"));
		
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
		JavaPairRDD<Object, BSONObject> documents = sc.newAPIHadoopRDD(
				mongodbConfig,            // Configuration
				MongoInputFormat.class,   // InputFormat: read from a live cluster.
				Object.class,             // Key class
				BSONObject.class          // Value class
				);
		
		JavaPairRDD<String, Integer> arrest = documents.flatMapToPair(
				t-> {
					String key = ((String)t._2.get("Arrest")).trim(); 
					List<Tuple2<String,Integer>> crimes = new ArrayList<Tuple2<String,Integer>>();
					if (key != null && key.length() > 0) {
						crimes.add(new Tuple2<String,Integer>(key,1));
					}

					return crimes;
				});

		JavaPairRDD<String, Integer> count = arrest.reduceByKey(
				(a, b) -> a + b);
		
		//sort crimes by number of no arrest
				JavaPairRDD<Integer,String> order = count.flatMapToPair(
						t -> {
							List<Tuple2<Integer,String>> rows = new ArrayList<>();
							rows.add(new Tuple2<Integer,String>(t._2,t._1));
							Collections.sort(rows, (p1,p2) -> (int)(p2._1 - p1._1));
							return rows;
						});

		//create the BSON to store in MongoDB
		JavaPairRDD<Object, BSONObject> finalOrderedTopNoArrest = order.flatMapToPair(
				t -> {
					BSONObject bson = new BasicBSONObject();
					List<Tuple2<Object, BSONObject>> finalOrdered = new ArrayList<Tuple2<Object, BSONObject>>();
					bson.put("NumberOfCrimes", t._1);
					bson.put("Arrest", t._2);
					finalOrdered.add(new Tuple2<Object,BSONObject>(null,bson));
					return finalOrdered;
				});

		// Create a separate Configuration for saving data back to MongoDB.
		Configuration outputConfig = new Configuration();
		outputConfig.set("mongo.output.uri",
				"mongodb://localhost:27017/bigData.topNoArrest");

		// Save this RDD as a Hadoop "file".
		// The path argument is unused; all documents will go to 'mongo.output.uri'.
		finalOrderedTopNoArrest.saveAsNewAPIHadoopFile(
				"file:///this-is-completely-unused",
				Object.class,
				BSONObject.class,
				MongoOutputFormat.class,
				outputConfig
				);
	}

	public static void main(final String[] args) {
		long start = System.currentTimeMillis();
		new TopNoArrest().run();
		System.out.println("Completed in " 
				+(System.currentTimeMillis() - start)/1000.0 
				+" seconds");
	}
}
