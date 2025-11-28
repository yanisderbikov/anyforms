FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Копируем pom.xml и загружаем зависимости (кэширование слоя)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Копируем исходный код
COPY src ./src

# Собираем приложение
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Копируем собранный JAR из стадии сборки
COPY --from=build /app/target/*.jar app.jar

# Открываем порт
EXPOSE 8090

# Запускаем приложение с адекватными лимитами памяти для JVM
# При лимите контейнера 512 МБ:
#   -Xmx384m  — heap до ~384 МБ
#   -Xms256m  — стартовый heap
#   -XX:MaxMetaspaceSize=128m — ограничение metaspace
ENTRYPOINT ["java", "-Xms256m", "-Xmx384m", "-XX:MaxMetaspaceSize=128m", "-jar", "app.jar"]
