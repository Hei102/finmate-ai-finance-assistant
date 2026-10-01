FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /workspace/target/java-ai-langchain4j-1.0-SNAPSHOT.jar /app/app.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
