FROM eclipse-temurin:21-jdk-jammy
WORKDIR /mock
COPY CarrierSimulator.java .
RUN javac CarrierSimulator.java && groupadd --system carrier && useradd --system --gid carrier carrier
USER carrier
EXPOSE 9096
CMD ["java", "CarrierSimulator"]
