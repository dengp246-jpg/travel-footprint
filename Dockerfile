FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN mkdir -p /app/uploads /app/downloads

COPY --from=build /app/target/travel-footprint-0.0.1-SNAPSHOT.jar /app/app.jar
COPY distribution/travel-footprint-android.apk /app/downloads/travel-footprint-android.apk

ENV APP_ANDROID_APK_PATH=/app/downloads/travel-footprint-android.apk

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
