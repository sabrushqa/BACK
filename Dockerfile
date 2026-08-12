FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN useradd --create-home --shell /bin/bash spring \
  && mkdir -p /app/uploads \
  && chown -R spring:spring /app

COPY --from=build /workspace/target/*.jar /app/app.jar

USER spring

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
