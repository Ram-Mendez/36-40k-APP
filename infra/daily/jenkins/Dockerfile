FROM jenkins/jenkins:lts-jdk21
USER root
RUN apt-get update && apt-get install -y --no-install-recommends python3 postgresql-client && rm -rf /var/lib/apt/lists/*
USER jenkins
RUN jenkins-plugin-cli --plugins matrix-auth
COPY --chown=jenkins:jenkins init.groovy /usr/share/jenkins/ref/init.groovy.d/parte.groovy
