FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /src/target/*.jar /app/app.jar
USER appuser
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8088
ENTRYPOINT ["java","-jar","/app/app.jar"]
