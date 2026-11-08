ARG BUILD_MODE=from-source

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw ./
COPY services/shipping-service/pom.xml services/shipping-service/pom.xml
RUN chmod +x mvnw && ./mvnw -f services/shipping-service/pom.xml -B -ntp dependency:go-offline
COPY services/shipping-service/src services/shipping-service/src
RUN ./mvnw -f services/shipping-service/pom.xml -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime-base
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --create-home --shell /usr/sbin/nologin app
WORKDIR /app
EXPOSE 9096
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java","-jar","/app/app.jar"]

FROM runtime-base AS runtime-prebuilt
COPY services/shipping-service/target/shipping-service-1.0.0.jar app.jar

FROM runtime-base AS runtime-from-source
COPY --from=build /workspace/services/shipping-service/target/shipping-service-1.0.0.jar app.jar

FROM runtime-${BUILD_MODE}
USER 10001:10001
