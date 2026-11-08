FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /build/target/carrier-1.0.0.jar app.jar
RUN groupadd --system carrier && useradd --system --gid carrier carrier
USER carrier
EXPOSE 9096
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
