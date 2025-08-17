FROM gradle:8.8-jdk21-alpine AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon clean shadowJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
