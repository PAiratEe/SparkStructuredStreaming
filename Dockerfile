FROM apache/spark:3.5.0

# Set the Spark application properties
ENV SPARK_HOME=/opt/spark
ENV PATH=$PATH:/opt/spark/bin

# Set the working directory
WORKDIR /opt/spark

# Copy your application JAR to the image
COPY src/main/resources/metrics.properties /opt/spark/
COPY target/SparkStructured-1.0-SNAPSHOT.jar /opt/spark/work-dir
