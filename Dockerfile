# 第一阶段：使用 Maven 和 Java 11 编译项目
FROM maven:3.9-eclipse-temurin-11 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

# 第二阶段：使用 Java 11 运行 Spring Boot
FROM eclipse-temurin:11-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8124

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
