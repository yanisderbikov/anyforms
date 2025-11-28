FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests


# Стадия рантайма: JRE 17 (Eclipse Temurin)
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8090

# Ограничиваем JVM, чтобы комфортно жить в контейнере 512 МБ
# -Xmx384m  — максимум heap
# -Xms256m  — стартовый heap
# -XX:MaxMetaspaceSize=128m — ограничение metaspace
ENTRYPOINT ["java", "-Xms256m", "-Xmx384m", "-XX:MaxMetaspaceSize=128m", "-jar", "app.jar"]
