FROM apache/spark:3.5.0

# Set the Spark application properties
ENV SPARK_HOME=/opt/spark
ENV PATH=$PATH:/opt/spark/bin

# Set the working directory
WORKDIR /opt/spark/work-dir

# Copy your application JAR to the image
COPY target/SparkStructured-1.0-SNAPSHOT.jar /opt/spark/work-dir

#COPY entrypoint.sh /opt/entrypoint.sh
#USER root
## Make the script executable
#RUN chmod +x /opt/entrypoint.sh
#USER spark
# Set the entry point
#ENTRYPOINT ["/opt/entrypoint.sh"]
#RUN /opt/spark/bin/spark-submit \
#      --master local[*] \
#      --deploy-mode client \
#      --class org.example.Main \
#      --packages org.apache.kafka:kafka-clients:3.5.0 \
#      --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0 \
#      --conf spark.driver.extraJavaOptions="-Divy.cache.dir=/tmp -Divy.home=/tmp" \
#      --conf spark.executor.instances=5 \
#      --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark \
#      --conf spark.kubernetes.container.image=pairate\spark-app:0.0.1 \
#      --conf spark.kafka.bootstrap.servers=10.101.221.105:9092 \
#      /opt/spark/work-dir/SparkStructured-1.0-SNAPSHOT.jar



#      --conf spark.kubernetes.driverEnv.KAFKA_BOOTSTRAP_SERVERS=kafka-broker:9092 \
## Start the Spark application
#CMD ["spark-submit", \
#     "--master", "local[*]", \
#     "--deploy-mode", "client", \
#     "--class", "org.example.Main", \
#     "--conf", "spark.executor.instances=5", \
#     "--conf", "spark.kubernetes.authenticate.driver.serviceAccountName=spark", \
#     "--conf", "spark.kubernetes.container.image=pairate\spark-app:0.0.1", \
#     "--conf", "spark.kubernetes.driverEnv.KAFKA_BOOTSTRAP_SERVERS=192.168.0.4:9092", \
#     "/opt/spark/work-dir/SparkStructured-1.0-SNAPSHOT.jar"]

# Use Maven to build the JAR
#FROM maven:3.8.1-openjdk-11 AS builder
#
## Set the working directory in the container
#WORKDIR /app
#
## Copy the Maven POM file and download dependencies
#COPY pom.xml .
#RUN mvn dependency:go-offline
#
## Copy the source code
#COPY src ./src
#
## Build the JAR file
#RUN mvn clean package
#
## Use a smaller runtime image
#FROM openjdk:11-jre-slim
#
## Set the working directory in the container
#WORKDIR /app
#
## Copy the JAR file from the builder stage
#COPY --from=builder /app/target/SparkStructured-1.0-SNAPSHOT.jar .
#
## Specify the command to run on container start
#CMD ["java", "-jar", "SparkStructured-1.0-SNAPSHOT.jar"]
