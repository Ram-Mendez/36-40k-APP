ARG BUILD_MODE=from-source

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime-base
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
EXPOSE 8086
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]

FROM runtime-base AS runtime-prebuilt
COPY target/business-operations-backend-1.0.0.jar app.jar

FROM runtime-base AS runtime-from-source
COPY --from=build /workspace/target/business-operations-backend-1.0.0.jar app.jar

FROM runtime-${BUILD_MODE}
USER app
