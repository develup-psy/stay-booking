FROM gradle:8.10-jdk21 AS build

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew clean bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy

RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

RUN chown -R spring:spring /app

USER spring

EXPOSE 8080

ENTRYPOINT ["java", \
    "-Duser.timezone=Asia/Seoul", \
    "-Dfile.encoding=UTF-8", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", \
    "app.jar"]
