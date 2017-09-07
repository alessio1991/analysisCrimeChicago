package analysis.basic;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.flink.api.java.tuple.Tuple;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import scala.Tuple2;

public class DayOfWeek {

	public static JavaRDD<Date> loadData() {
		SparkConf conf = new SparkConf().setAppName("DayOfWeek").setMaster("local[4]");
		JavaSparkContext sc = new JavaSparkContext(conf);
		JavaRDD<Date> tupla = sc.textFile("/home/alessio/Scrivania/"
				+ "Seattle_Police_Department_911_Incident_Response.csv")
				.map(line -> { String[] fields = line.split(",");
				DateFormat df = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a"); 
				Date date;
				date = df.parse(fields[7]);
				return date;
				});		
		return tupla;
	}

	public static JavaPairRDD<Date,Integer> getDay(){
		JavaRDD<Date> input = loadData();
		JavaPairRDD<Date, Integer> tupla = input.mapToPair(
				t -> { Calendar c = Calendar.getInstance();
				c.setTime(t);
				int day = c.get(Calendar.DAY_OF_WEEK);
				return new Tuple2<Date, Integer>(t, day);
				});
		return tupla;
	}
	
	public static void printOutput(JavaPairRDD<Date, Integer> input){
		JavaRDD<String> print = 
				input.map(t -> {String s = "";
								s = s+t._1();
								return s;
				});
		print.saveAsTextFile("/home/alessio/Scrivania/output9");
	}
	
	
	public static void main(String[] args) {
		JavaPairRDD<Date, Integer> input = getDay();
		printOutput(input);
	}
}
