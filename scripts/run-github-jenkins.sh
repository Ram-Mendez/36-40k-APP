#!/usr/bin/env bash
set -euo pipefail
# Keep secrets out of shell tracing, including when a caller uses bash -x.
set +x
umask 077

container_name=backend-lab-jenkins
image_tag="backend-lab-jenkins:${GITHUB_RUN_ID:-local}"
jenkins_home="${RUNNER_TEMP:-/tmp}/backend-lab-jenkins-home"
kubeconfig="${RUNNER_TEMP:-/tmp}/backend-lab-kubeconfig"
workspace_dir="${GITHUB_WORKSPACE:-$PWD}"
evidence_dir="$workspace_dir/evidence"
bootstrap_dir="$(mktemp -d "${RUNNER_TEMP:-/tmp}/backend-lab-jenkins-auth.XXXXXX")"
trap 'rm -rf -- "$bootstrap_dir"' EXIT

password_source=repository-secret
if [[ -z "${JENKINS_ADMIN_PASSWORD:-}" ]]; then
  JENKINS_ADMIN_PASSWORD="$(openssl rand -hex 32)"
  password_source=ephemeral
fi
if (( ${#JENKINS_ADMIN_PASSWORD} < 16 )); then
  echo 'JENKINS_ADMIN_PASSWORD must contain at least 16 characters' >&2
  exit 1
fi
printf '%s' "$JENKINS_ADMIN_PASSWORD" > "$bootstrap_dir/admin-password"
unset JENKINS_ADMIN_PASSWORD

mkdir -p "$jenkins_home" "$evidence_dir" "$HOME/.m2"
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
  --volume "$workspace_dir:/workspace" \
  --volume "$jenkins_home:/var/jenkins_home" \
  --volume "$bootstrap_dir:/run/jenkins-bootstrap" \
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
  if [[ -s "$bootstrap_dir/cli-auth" ]] && \
    curl --silent --fail --connect-timeout 2 --max-time 5 \
      --netrc-file "$bootstrap_dir/curl.netrc" \
      http://127.0.0.1:8080/job/backend-java-lab/api/json > /dev/null; then
    ready=true
    break
  fi
  sleep 3
done
if [[ "$ready" != true ]]; then
  docker logs "$container_name" > "$evidence_dir/jenkins-startup.log" 2>&1 || true
  echo 'Authenticated temporary Jenkins did not become ready before the startup deadline' >&2
  exit 1
fi

anonymous_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --connect-timeout 2 --max-time 10 http://127.0.0.1:8080/api/json)"
if [[ "$anonymous_status" != 401 && "$anonymous_status" != 403 ]]; then
  echo "Jenkins must deny anonymous API access; received HTTP $anonymous_status" >&2
  exit 1
fi
curl --fail --silent --show-error --netrc-file "$bootstrap_dir/curl.netrc" \
  http://127.0.0.1:8080/whoAmI/api/json > "$evidence_dir/jenkins-identity.json"
if ! jq --exit-status '.authenticated == true and .name == "ram-jenkins"' \
  "$evidence_dir/jenkins-identity.json" > /dev/null; then
  echo 'Jenkins did not authenticate as the expected ram-jenkins user' >&2
  exit 1
fi
curl --fail --silent --show-error --netrc-file "$bootstrap_dir/curl.netrc" \
  http://127.0.0.1:8080/crumbIssuer/api/json > /dev/null
printf 'username=ram-jenkins\npassword_source=%s\nanonymous_api_status=%s\ncsrf_enabled=true\n' \
  "$password_source" "$anonymous_status" > "$evidence_dir/jenkins-access.txt"

docker exec "$container_name" curl --fail --silent \
  --netrc-file /run/jenkins-bootstrap/curl.netrc \
  http://127.0.0.1:8080/jnlpJars/jenkins-cli.jar --output /tmp/jenkins-cli.jar
docker exec "$container_name" docker --version
docker exec "$container_name" java -jar /tmp/jenkins-cli.jar -s http://127.0.0.1:8080/ \
  -auth @/run/jenkins-bootstrap/cli-auth build backend-java-lab -s -v 2>&1 \
  | tee "$evidence_dir/jenkins-console.log"

for stage in Checkout Test Package 'Docker Build' 'Kubernetes Deploy' Verify; do
  if ! grep -F "($stage)" "$evidence_dir/jenkins-console.log" > /dev/null; then
    echo "Jenkins console evidence does not contain stage: $stage" >&2
    exit 1
  fi
done
