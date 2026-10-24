FROM jenkins/jenkins:lts-jdk21

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl docker.io git jq openssl \
    && rm -rf /var/lib/apt/lists/*

USER jenkins
RUN jenkins-plugin-cli --plugins workflow-aggregator

COPY --chown=jenkins:jenkins init.groovy /usr/share/jenkins/ref/init.groovy.d/backend-lab.groovy
