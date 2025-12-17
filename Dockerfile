FROM eclipse-temurin:17-jre-alpine
COPY target/memo-0.0.1-SNAPSHOT.jar memo-server.jar
ENTRYPOINT ["java","-jar","/memo-server.jar"]
EXPOSE 8080