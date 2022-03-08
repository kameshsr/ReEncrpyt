FROM openjdk:1.11.0
EXPOSE 8080
ARG JAR_FILE=target/ReEncrypt-1.0.0.jar
ADD ${JAR_FILE} ReEncrypt.jar
ENTRYPOINT ["java","-jar","/ReEncrypt.jar"]

