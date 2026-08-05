FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests



FROM eclipse-temurin:21-jre

WORKDIR /app

COPY certs/russian_trusted_root_ca.crt /tmp/russian_trusted_root_ca.crt
RUN keytool -importcert -cacerts -storepass changeit -noprompt -alias russian-trusted-root-ca -file /tmp/russian_trusted_root_ca.crt && \
    rm /tmp/russian_trusted_root_ca.crt

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-Xms256m", "-Xmx384m", "-XX:MetaspaceSize=128m", "-XX:MaxMetaspaceSize=256m", "-XX:+ExitOnOutOfMemoryError", "-Xlog:gc+metaspace=info", "-jar", "app.jar"]
