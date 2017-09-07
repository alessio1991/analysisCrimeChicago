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
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;



public class TopCrimes4Arrest implements java.io.Serializable {

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
		
		//Arrange the keys for the count --> every key is made from district+typeOfCrime so that it is possible to do the count with these fields together
		JavaPairRDD<String, Integer> arrestAndCrime = documents.flatMapToPair(
				t-> {
					String arrest = "";
					String typeOfCrime = "";
					String key = "";
					List<Tuple2<String,Integer>> crimesWithArrest = new ArrayList<Tuple2<String,Integer>>();
					
					arrest = ((String)t._2.get("Arrest")).trim(); 
					typeOfCrime = ((String)t._2.get("Primary Type")).trim();
					

					if (arrest != null && arrest.length() > 0  && typeOfCrime != null && typeOfCrime.length() > 0) {	
						key = arrest+"|"+typeOfCrime;
						crimesWithArrest.add(new Tuple2<String, Integer>(key,1));
					}
					
					return crimesWithArrest;
				});
		
		//count the type of crime for every district
		JavaPairRDD<String, Integer> count = arrestAndCrime.reduceByKey(
				(a, b) -> a + b);
		
		//organize the bson to permit the sorting
		JavaPairRDD<String, Iterable<BSONObject>> bsonDisordered = count.flatMapToPair(
				t-> {
					BSONObject bson = new BasicBSONObject();
					List<Tuple2<String,BSONObject>> listOfBsons = new ArrayList<Tuple2<String,BSONObject>>();
					StringTokenizer tokenizerFile = new StringTokenizer(t._1,"|");
					String key = tokenizerFile.nextToken();
					bson.put("TypeOfCrime",tokenizerFile.nextToken());
					bson.put("NumberOfEvents", t._2);
					listOfBsons.add(new Tuple2<String,BSONObject>(key,bson));
					return listOfBsons;
				}).groupByKey();

		//sort the "bsons" by numberOfEvents (count) 
		JavaPairRDD<String, Iterable<BSONObject>> bsonOrdered = bsonDisordered.flatMapToPair(
				t -> {	
					List<Tuple2<String,Iterable<BSONObject>>> district2countTypeOfCrime = new ArrayList<Tuple2<String,Iterable<BSONObject>>>();
					List<BSONObject> listOfBson = new ArrayList<BSONObject>();
					
					t._2.forEach(b->listOfBson.add(b));
					
					Collections.sort(listOfBson, new Comparator<BSONObject>(){
						
						@Override
						public int compare(BSONObject bo1, BSONObject bo2) {
							return (int)bo2.get("NumberOfEvents") - (int)bo1.get("NumberOfEvents");
						}
					});
					Iterable<BSONObject> newIterable = listOfBson;
					district2countTypeOfCrime.add(new Tuple2<String,Iterable<BSONObject>>(t._1,newIterable));
					return district2countTypeOfCrime;
				});
		
		//create the BSON to store in MongoDB
	    JavaPairRDD<Object, BSONObject> finalOrderedTopCrimes = bsonOrdered.flatMapToPair(
				t -> {
					BSONObject bson = new BasicBSONObject();
					List<Tuple2<Object, BSONObject>> finalOrdered = new ArrayList<Tuple2<Object, BSONObject>>();
					bson.put("Arrest", t._1);
					bson.put("ListOfCrimes", t._2);
					finalOrdered.add(new Tuple2<Object,BSONObject>(null,bson));
					return finalOrdered;
				});

		// Create a separate Configuration for saving data back to MongoDB.
		Configuration outputConfig = new Configuration();
		outputConfig.set("mongo.output.uri",
				"mongodb://localhost:27017/bigData.topCrimes4Arrest");

		// Save this RDD as a Hadoop "file".
		// The path argument is unused; all documents will go to 'mongo.output.uri'.
		finalOrderedTopCrimes.saveAsNewAPIHadoopFile(
				"file:///this-is-completely-unused",
				Object.class,
				BSONObject.class,
				MongoOutputFormat.class,
				outputConfig
				);
	}

	public static void main(final String[] args) {
		long start = System.currentTimeMillis();
		new TopCrimes4Arrest().run();
		System.out.println("Completed in " 
				+(System.currentTimeMillis() - start)/1000.0 
				+" seconds");
	}
}
