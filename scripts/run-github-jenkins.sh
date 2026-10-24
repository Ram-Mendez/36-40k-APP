#!/usr/bin/env bash
set -euo pipefail

container_name=backend-lab-jenkins
image_tag="backend-lab-jenkins:${GITHUB_RUN_ID:-local}"
jenkins_home="${RUNNER_TEMP:-/tmp}/backend-lab-jenkins-home"
kubeconfig="${RUNNER_TEMP:-/tmp}/backend-lab-kubeconfig"
evidence_dir="${GITHUB_WORKSPACE:-$PWD}/evidence"

mkdir -p "$jenkins_home" "$evidence_dir"
kind export kubeconfig --name backend-lab --kubeconfig "$kubeconfig"

docker build --file infra/jenkins/Dockerfile --tag "$image_tag" infra/jenkins
docker run --detach \
  --name "$container_name" \
  --network host \
  --user "$(id -u):$(id -g)" \
  --group-add "$(stat -c '%g' /var/run/docker.sock)" \
  --volume /var/run/docker.sock:/var/run/docker.sock \
  --volume "$(command -v kind):/usr/local/bin/kind:ro" \
  --volume "$(command -v kubectl):/usr/local/bin/kubectl:ro" \
  --volume "$GITHUB_WORKSPACE:/workspace" \
  --volume "$jenkins_home:/var/jenkins_home" \
  --volume "$HOME/.m2:/maven-cache" \
  --volume "$kubeconfig:/run/kubeconfig:ro" \
  --env JENKINS_HOME=/var/jenkins_home \
  --env JAVA_OPTS='-Djenkins.install.runSetupWizard=false -Xms256m -Xmx768m' \
  --env JENKINS_OPTS='--httpPort=8080 --httpListenAddress=127.0.0.1' \
  --env KUBECONFIG=/run/kubeconfig \
  --env MAVEN_USER_HOME=/maven-cache \
  --env MAVEN_OPTS=-Dmaven.repo.local=/maven-cache/repository \
  --env GITHUB_SHA="${GITHUB_SHA:-}" \
  --env GITHUB_REF_NAME="${GITHUB_REF_NAME:-local}" \
  --env GITHUB_RUN_ID="${GITHUB_RUN_ID:-local}" \
  --env LAB_FAILURE_SIMULATION="${LAB_FAILURE_SIMULATION:-none}" \
  "$image_tag" > "$evidence_dir/jenkins-container-id.txt"

ready=false
for attempt in $(seq 1 120); do
  if curl --silent --fail http://127.0.0.1:8080/api/json > /dev/null; then
    ready=true
    break
  fi
  sleep 3
done
if [[ "$ready" != true ]]; then
  docker logs "$container_name" > "$evidence_dir/jenkins-startup.log" 2>&1 || true
  echo 'Temporary Jenkins did not become ready within six minutes' >&2
  exit 1
fi

docker exec "$container_name" curl --fail --silent http://127.0.0.1:8080/jnlpJars/jenkins-cli.jar --output /tmp/jenkins-cli.jar
docker exec "$container_name" java -jar /tmp/jenkins-cli.jar -s http://127.0.0.1:8080/ build backend-java-lab -s -v 2>&1 \
  | tee "$evidence_dir/jenkins-console.log"

for stage in Checkout Test Package 'Docker Build' 'Kubernetes Deploy' Verify; do
  if ! grep -F "($stage)" "$evidence_dir/jenkins-console.log" > /dev/null; then
    echo "Jenkins console evidence does not contain stage: $stage" >&2
    exit 1
  fi
done
