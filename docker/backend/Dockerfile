# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests dependency:go-offline

COPY src src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

FROM mcr.microsoft.com/playwright/java:v1.53.0-noble

WORKDIR /app

ENV SERVER_PORT=8084 \
    SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Shanghai

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
