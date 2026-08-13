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

ENV APP_ANDROID_APK_PATH=/app/downloads/travel-footprint-android.apk \
    SPRING_PROFILES_ACTIVE=prod \
    APP_DEMO_SEED_ENABLED=false \
    APP_UPLOAD_STORAGE_MODE=database \
    JAVA_TOOL_OPTIONS="-Xms40m -Xmx80m -Xss512k -XX:MaxMetaspaceSize=72m -XX:ReservedCodeCacheSize=20m -XX:MaxDirectMemorySize=8m -XX:ActiveProcessorCount=1 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
