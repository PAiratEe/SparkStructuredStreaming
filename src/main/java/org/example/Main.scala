package org.example

import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, SparkSession}

import java.io.PrintWriter
import java.util.{Properties, Timer, TimerTask}

object Main {

  def main(args: Array[String]): Unit = {

    // Создание новой сессии Spark
    val spark = SparkSession.builder()
      .appName("KafkaStreamingApp")
      .master("k8s://https://192.168.49.2:8443")
      .config("spark.kubernetes.file.upload.path", "/opt/spark/")
      .config("spark.kubernetes.authenticate.driver.serviceAccountName", "spark")
      .config("spark.executor.memory", "1g")
      .config("spark.dynamicAllocation.enabled", "false")
      .config("spark.executor.instances", "2") // ставим в конфиге 2 executor'а

      .getOrCreate()

    // Создание producer для чтения из Kafka
    val propsProducer = new Properties()
    propsProducer.put("bootstrap.servers", "my-cluster-kafka-bootstrap.default.svc:9092")
    propsProducer.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    propsProducer.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

    val kafkaServer = "my-cluster-kafka-bootstrap.default.svc:9092"
    val kafkaTopic = "test"
    val dlqTopic = "reserve"

    // Создание потока для чтения и обработки из Kafka Topic 1
    val kafkaDF = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaServer)
      .option("subscribe", kafkaTopic)
      .option("startingOffsets", "latest")
      .option("errors.tolerance", "all")
      .option("errors.deadletterqueue.topic.name", dlqTopic)
      .load()

    // Создание потока для чтения и обработки некорректно обработанных сообщения из DLQ
    val dlqDF = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaServer)
      .option("subscribe", "reserve")
      .option("startingOffsets", "latest")
      .option("errors.tolerance", "all")
      .option("errors.deadletterqueue.topic.name", dlqTopic)
      .load()


    // Схема для парсинга сообщений
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


    // Непосредственная обработка сообщений из основного топика
    val parsedDF = kafkaDF.selectExpr("CAST(value AS STRING)")
      .select(from_json(col("value"), jsonSchema).as("json"))
      .select("json.*")
      .filter("bot = true")
      .writeStream
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
        processBatch(batchDF, batchId)
      }
      .option("checkpointLocation", "hdfs://hadoop-hadoop-hdfs-nn.default.svc:9000/offests") // отправляем оффсеты в hadoop
      .trigger(Trigger.ProcessingTime("1 second"))
      .start()

    // Непосредственная обработка сообщений из DLQ топика
    val query = dlqDF.selectExpr("CAST(value AS STRING)")
      .select(from_json(col("value"), jsonSchema).as("json"))
      .select("json.*")
      .filter("bot = true")
      .writeStream
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
        processBatch(batchDF, batchId)
      }
      .option("checkpointLocation", "hdfs://hadoop-hadoop-hdfs-nn.default.svc:9000/dlq_offests") // отправляем оффсеты в hadoop
      .start()

    def countAndSave(): Unit = {
      val messageCount = kafkaDF.count() + dlqDF.count()
      val outputFilePath =" hdfs://hadoop-hadoop-hdfs-nn.default.svc:9000/count/message_count.txt"

      // Запись количества сообщений в текстовый файл
      val writer = new PrintWriter(outputFilePath)
      writer.println(messageCount)
      writer.close()

      println(s"Message count saved to $outputFilePath")
    }

    // Запуск задачи каждые 30 минут
    val timer = new Timer()
    val task = new TimerTask {
      def run(): Unit = {
        countAndSave()
      }
    }
    val interval = 30 * 60 * 1000
    timer.schedule(task, 0, interval)

    parsedDF.awaitTermination()
    query.awaitTermination()

  }

  // Метод для сохранения обработанных сообщений в hadoop
  def processBatch(df: DataFrame, id: Long): Unit = {
    df.show()

    df.write
      .format("parquet")
      .mode("append")
      .save("hdfs://hadoop-hadoop-hdfs-nn.default.svc:9000/data") // прописываем адрес NameNode
  }
}