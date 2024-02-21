package org.example

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types._

import java.util.Properties

object Main {

  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("KafkaStreamingApp")
//      .master("k8s://http://192.168.64.2:30081")
      .master("local[*]")
      .config("spark.executor.memory", "1g")
      .config("spark.executor.instances", "5")
//      .config("spark.metrics.namespace", "default")
//      .config("spark.metrics.conf", "src/main/resources/metrics.properties")
      .getOrCreate()


    val props = new Properties()
    props.put("bootstrap.servers", "10.244.0.175:9092")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

    val kafkaServer = "10.244.0.175:9092"
    val kafkaTopic = "wikimedia_recentchange2"

    val maxConnectionAttempts = 10
    var connectionAttempt = 0

//    var producer: Option[KafkaProducer[String, String]] = None


    val producer = new KafkaProducer[String, String](props)

//    while (connectionAttempt < maxConnectionAttempts && producer.isEmpty) {
//      try {
//        // Attempt to create a Kafka consumer
//        producer = Some(new KafkaProducer[String, String](props))
//        println("Successfully connected to Kafka broker.")
//      } catch {
//        case e: Exception =>
//          // Handle the exception (e.g., log it)
//          println(s"Connection attempt $connectionAttempt failed. Retrying...")
//          connectionAttempt += 1
//
//          // Optional: Add a delay before the next attempt
//          Thread.sleep(5000) // 5 seconds delay
//      }
//    }

    val kafkaDF = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaServer)
      .option("subscribe", kafkaTopic)
      .option("startingOffsets", "latest")
      .load()

    val jsonSchema = new StructType()
      .add("$schema", StringType)
      .add("meta", new StructType()
        .add("uri", StringType)
        .add("request_id", StringType)
        .add("id", StringType)
        .add("dt", StringType)
        .add("domain", StringType)
        .add("stream", StringType)
        .add("topic", StringType)
        .add("partition", IntegerType)
        .add("offset", LongType)
      )
      .add("id", LongType)
      .add("type", StringType)
      .add("namespace", IntegerType)
      .add("title", StringType)
      .add("title_url", StringType)
      .add("comment", StringType)
      .add("timestamp", LongType)
      .add("user", StringType)
      .add("bot", BooleanType)
      .add("notify_url", StringType)
      .add("minor", BooleanType)
      .add("patrolled", BooleanType)
      .add("length", new StructType()
        .add("old", IntegerType)
        .add("new", IntegerType)
      )
      .add("revision", new StructType()
        .add("old", LongType)
        .add("new", LongType)
      )
      .add("server_url", StringType)
      .add("server_name", StringType)
      .add("server_script_path", StringType)
      .add("wiki", StringType)
      .add("parsedcomment", StringType)

    var parsedDF = kafkaDF
    try {
      parsedDF = kafkaDF.selectExpr("CAST(value AS STRING)")
        .select(from_json(col("value"), jsonSchema).as("json"))
        .select("json.*")
      val query = parsedDF.writeStream
        .trigger(Trigger.ProcessingTime("1 second"))
        .foreachBatch(processBatch _)
        .format("console")
        .start()
      query.awaitTermination()
    } catch {
      case e: Exception =>
        println("Ошибка " + e.printStackTrace())
        val record = new ProducerRecord[String, String](kafkaTopic, kafkaDF.toString())
        producer.send(record)
    }
    finally {}

    def processBatch(df: DataFrame, batchId: Long): Unit = {
      df.write
        .format("parquet")
        .mode("append")
        .save("hdfs://192.168.64.2:9000/newKafkaTemplate/")
    }

    val query = parsedDF.writeStream
      .trigger(Trigger.ProcessingTime("10 seconds"))
//      .foreachBatch(processBatch _)
      .format("console")
      .start()
  }
}