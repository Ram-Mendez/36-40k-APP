ARG BUILD_MODE=from-source

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime-base
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --create-home --shell /usr/sbin/nologin app
WORKDIR /app
EXPOSE 8086
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java","-jar","/app/app.jar"]

FROM runtime-base AS runtime-prebuilt
COPY target/business-operations-backend-1.0.0.jar app.jar

FROM runtime-base AS runtime-from-source
COPY --from=build /workspace/target/business-operations-backend-1.0.0.jar app.jar

FROM runtime-${BUILD_MODE}
USER 10001:10001
