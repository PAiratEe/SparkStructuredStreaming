package org.example

import org.apache.kafka.clients.producer.KafkaProducer
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
    props.put("bootstrap.servers", "my-cluster-kafka-bootstrap.default.svc:9092")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

    val kafkaServer = "my-cluster-kafka-bootstrap.default.svc:9092"
    val kafkaTopic = "test"

    val maxConnectionAttempts = 10
    var connectionAttempt = 0

    val producer = new KafkaProducer[String, String](props)
//    producer.initTransactions()

    val kafkaDF = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaServer)
      .option("subscribe", kafkaTopic)
      .option("startingOffsets", "latest")
      .load()
    //      .option("errors.tolerance", "all")
    //      .option("errors.deadletterqueue.topic.name", "reserve")
    //      .load()

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

//    Рабочий код без DLQ
//    var parsedDF = kafkaDF.selectExpr("CAST(value AS STRING)")
//      .select(from_json(col("value"), jsonSchema).as("json"))
//      .select("json.*").writeStream.trigger(Trigger.ProcessingTime("1 second"))
//
//    try {
//      parsedDF.foreachBatch(processBatch _)
//      ////        .format("console")
//      //        .start()
//
//    } catch {
//      case e: Exception =>
//        println("Ошибка " + e.printStackTrace())
//        val record = new ProducerRecord[String, String]("reserve", kafkaDF.toString())
//        producer.send(record)
//    }
//    finally {
//      parsedDF.start().awaitTermination()
//    }
//
//    def processBatch(df: DataFrame, batchId: Long): Unit = {
//      df.write
//        .format("parquet")
//        .mode("append")
//        .save("hdfs://hadoop-hadoop-hdfs-nn.default.svc:9000/") //nn
//    }
    val parsedDF = kafkaDF.selectExpr("CAST(value AS STRING)")
      .select(from_json(col("value"), jsonSchema).as("json"))
      .select("json.*")
      .writeStream
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
        processBatch(batchDF, batchId, producer, "reserve")
      }
      .trigger(Trigger.ProcessingTime("1 second"))
      .start()

    parsedDF.awaitTermination()

  }

  def processBatch(df: DataFrame, batchId: Long, producer: KafkaProducer[String, String], dlqTopic: String): Unit = {
    try {
      // if
      df.write
        .format("parquet")
        .mode("append")
        .save("hdfs://hadoop-hadoop-hdfs-nn.default.svc:9000/") //nn

      // Commit Kafka offsets only after successful write to HDFS
      // Note: This is important to ensure that you don't lose data
      df.selectExpr("CAST(topic AS STRING)", "CAST(partition AS STRING)", "CAST(offset AS STRING)")
        .write
        .format("kafka")
        .option("kafka.bootstrap.servers", "my-cluster-kafka-bootstrap.default.svc:9092")
        .option("topic", "offsetsTopic")
        .save()
    } catch {
      case e: Exception =>
        println(s"Error processing batch $batchId: ${e.getMessage}")
        //Send failed records to DLQ
        df.selectExpr("CAST(value AS STRING)")
          .write
          .format("kafka")
          .option("kafka.bootstrap.servers", "my-cluster-kafka-bootstrap.default.svc:9092")
          .option("topic", dlqTopic)
          .save()
    }
  }
}