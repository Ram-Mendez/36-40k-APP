pipeline {
    agent any

    options {
        skipDefaultCheckout()
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        SOURCE_DIR = '/workspace'
        EVIDENCE_DIR = '/workspace/evidence'
        KIND_CLUSTER_NAME = 'backend-lab'
        KUBE_NAMESPACE = 'backend-lab'
        APP_IMAGE = "business-operations-backend:jenkins-${GITHUB_RUN_ID}"
    }

    stages {
        stage('Checkout') {
            steps {
                dir('/workspace') {
                    sh '''#!/usr/bin/env bash
                        set -euo pipefail
                        mkdir -p "$EVIDENCE_DIR"
                        commit="$(git rev-parse HEAD)"
                        if [[ -n "${GITHUB_SHA:-}" && "$commit" != "$GITHUB_SHA" ]]; then
                          echo "Jenkins source commit $commit differs from Actions commit $GITHUB_SHA" >&2
                          exit 1
                        fi
                        git checkout --detach "$commit"
                        printf 'commit=%s\nref=%s\n' "$commit" "${GITHUB_REF_NAME:-local}" > "$EVIDENCE_DIR/checkout.txt"
                        echo "Checked out $commit"
                    '''
                }
            }
        }

        stage('Test') {
            steps {
                dir('/workspace') {
                    sh 'bash ./mvnw -B -ntp clean verify'
                }
            }
        }

        stage('Package') {
            steps {
                dir('/workspace') {
                    sh '''#!/usr/bin/env bash
                        set -euo pipefail
                        jar=target/business-operations-backend-1.0.0.jar
                        test -s "$jar"
                        sha256sum "$jar" | tee "$EVIDENCE_DIR/package.sha256"
                    '''
                }
            }
        }

        stage('Docker Build') {
            steps {
                dir('/workspace') {
                    sh '''#!/usr/bin/env bash
                        set -euo pipefail
                        docker build --build-arg BUILD_MODE=prebuilt --tag "$APP_IMAGE" .
                        docker image inspect "$APP_IMAGE" --format '{{.Id}}' | tee "$EVIDENCE_DIR/docker-image.txt"
                    '''
                }
            }
        }

        stage('Kubernetes Deploy') {
            steps {
                script {
                    env.CI_DB_PASSWORD = sh(script: 'openssl rand -hex 24', returnStdout: true).trim()
                    env.CI_JWT_SECRET = sh(script: 'openssl rand -hex 32', returnStdout: true).trim()
                    env.CI_ADMIN_USERNAME = "lab-admin-${env.BUILD_NUMBER}"
                    env.CI_ADMIN_PASSWORD = sh(script: 'openssl rand -hex 24', returnStdout: true).trim()
                }
                dir('/workspace') {
                    sh '''#!/usr/bin/env bash
                        set -euo pipefail
                        set +x
                        kubectl apply -f infra/kubernetes/namespace.yaml
                        kubectl -n "$KUBE_NAMESPACE" create secret generic backend-secrets \\
                          --from-literal=postgres-password="$CI_DB_PASSWORD" \\
                          --from-literal=jwt-secret="$CI_JWT_SECRET" \\
                          --from-literal=admin-username="$CI_ADMIN_USERNAME" \\
                          --from-literal=admin-password="$CI_ADMIN_PASSWORD" \\
                          --dry-run=client -o yaml | kubectl apply -f -
                        kubectl apply -f infra/kubernetes/configmap.yaml
                        kubectl apply -f infra/kubernetes/postgres.yaml
                        kubectl -n "$KUBE_NAMESPACE" rollout status deployment/postgres --timeout=180s
                        kind load docker-image "$APP_IMAGE" --name "$KIND_CLUSTER_NAME"
                        image_for_deploy="$APP_IMAGE"
                        if [[ "${LAB_FAILURE_SIMULATION:-none}" == image-pull ]]; then
                          image_for_deploy="${APP_IMAGE}-missing"
                          echo 'Controlled failure enabled: deploying an image that is not loaded in Kind'
                        fi
                        sed "s|business-operations-backend:ci|$image_for_deploy|" infra/kubernetes/backend.yaml | kubectl apply -f -
                        kubectl -n "$KUBE_NAMESPACE" rollout status deployment/backend-app --timeout=240s
                    '''
                }
            }
        }

        stage('Verify') {
            steps {
                dir('/workspace') {
                    sh '''#!/usr/bin/env bash
                        set -euo pipefail
                        mkdir -p "$EVIDENCE_DIR"
                        kubectl -n "$KUBE_NAMESPACE" get pods,deployments,services,pvc -o wide > "$EVIDENCE_DIR/kubernetes-resources.txt"
                        kubectl -n "$KUBE_NAMESPACE" get events --sort-by=.lastTimestamp > "$EVIDENCE_DIR/kubernetes-events.txt"
                        kubectl -n "$KUBE_NAMESPACE" exec deployment/postgres -- pg_isready -U business -d business_operations

                        kubectl -n "$KUBE_NAMESPACE" port-forward service/backend-app 18086:8086 > "$EVIDENCE_DIR/port-forward.log" 2>&1 &
                        port_forward_pid=$!
                        trap 'kill "$port_forward_pid" 2>/dev/null || true' EXIT
                        for attempt in $(seq 1 60); do
                          if curl --silent --fail http://127.0.0.1:18086/actuator/health/readiness > /tmp/backend-readiness.json; then
                            break
                          fi
                          sleep 2
                        done
                        jq -e '.status == "UP"' /tmp/backend-readiness.json > /dev/null
                        cp /tmp/backend-readiness.json "$EVIDENCE_DIR/readiness.json"
                        unauthorized_status="$(curl --silent --output /dev/null --write-out '%{http_code}' http://127.0.0.1:18086/api/v1/customers)"
                        test "$unauthorized_status" = 401

                        login_payload="$(jq -n --arg username "$CI_ADMIN_USERNAME" --arg password "$CI_ADMIN_PASSWORD" '{username:$username,password:$password}')"
                        login_response="$(curl --silent --show-error --fail --header 'Content-Type: application/json' --data "$login_payload" http://127.0.0.1:18086/api/v1/auth/login)"
                        access_token="$(jq -er '.accessToken' <<< "$login_response")"
                        tax_id="LAB$(openssl rand -hex 10)"
                        customer_payload="$(jq -n --arg taxId "$tax_id" '{name:"Jenkins Kind smoke customer",taxId:$taxId,email:"jenkins-kind@example.test",address:"Madrid"}')"
                        customer_response="$(curl --silent --show-error --fail --header 'Content-Type: application/json' --header "Authorization: Bearer $access_token" --data "$customer_payload" http://127.0.0.1:18086/api/v1/customers)"
                        customer_id="$(jq -er '.id' <<< "$customer_response")"
                        test -n "$customer_id"

                        kubectl -n "$KUBE_NAMESPACE" rollout restart deployment/backend-app
                        kubectl -n "$KUBE_NAMESPACE" rollout status deployment/backend-app --timeout=240s
                        kill "$port_forward_pid" 2>/dev/null || true
                        wait "$port_forward_pid" 2>/dev/null || true
                        kubectl -n "$KUBE_NAMESPACE" port-forward service/backend-app 18086:8086 >> "$EVIDENCE_DIR/port-forward.log" 2>&1 &
                        port_forward_pid=$!
                        rm -f /tmp/backend-readiness.json
                        for attempt in $(seq 1 30); do
                          if curl --silent --fail http://127.0.0.1:18086/actuator/health/readiness > /tmp/backend-readiness.json; then
                            break
                          fi
                          sleep 2
                        done
                        jq -e '.status == "UP"' /tmp/backend-readiness.json > /dev/null
                        kubectl -n "$KUBE_NAMESPACE" rollout restart deployment/postgres
                        kubectl -n "$KUBE_NAMESPACE" rollout status deployment/postgres --timeout=180s
                        kubectl -n "$KUBE_NAMESPACE" exec deployment/postgres -- pg_isready -U business -d business_operations
                        rm -f /tmp/backend-readiness.json
                        for attempt in $(seq 1 60); do
                          if curl --silent --fail http://127.0.0.1:18086/actuator/health/readiness > /tmp/backend-readiness.json; then
                            break
                          fi
                          sleep 2
                        done
                        jq -e '.status == "UP"' /tmp/backend-readiness.json > /dev/null
                        persisted_count="$(kubectl -n "$KUBE_NAMESPACE" exec deployment/postgres -- psql -U business -d business_operations -tAc "SELECT count(*) FROM customer WHERE tax_id='$tax_id'")"
                        test "$persisted_count" -eq 1
                        kubectl -n "$KUBE_NAMESPACE" exec deployment/postgres -- psql -U business -d business_operations -tAc "SELECT 'postgres=' || current_setting('server_version') || ', persisted_customer=' || count(*) FROM customer WHERE tax_id='$tax_id'" > "$EVIDENCE_DIR/postgres-persistence.txt"
                        printf 'readiness=UP\nunauthorized_http=%s\nadmin_login=PASS\ncustomer_create=PASS\ncustomer_id=%s\npostgres_persisted_after_app_and_postgres_restart=%s\n' \\
                          "$unauthorized_status" "$customer_id" "$persisted_count" | tee "$EVIDENCE_DIR/smoke-results.txt"
                    '''
                }
            }
        }
    }

    post {
        always {
            dir('/workspace') {
                sh '''#!/usr/bin/env bash
                    set +e
                    mkdir -p "$EVIDENCE_DIR"
                    kubectl -n "$KUBE_NAMESPACE" get all,pvc -o wide > "$EVIDENCE_DIR/final-kubernetes-resources.txt" 2>&1
                    kubectl -n "$KUBE_NAMESPACE" logs deployment/backend-app --all-containers=true --tail=500 > "$EVIDENCE_DIR/backend-app.log" 2>&1
                    kubectl -n "$KUBE_NAMESPACE" logs deployment/postgres --all-containers=true --tail=300 > "$EVIDENCE_DIR/postgres.log" 2>&1
                    kubectl -n "$KUBE_NAMESPACE" get events --sort-by=.lastTimestamp > "$EVIDENCE_DIR/final-kubernetes-events.txt" 2>&1
                '''
                archiveArtifacts artifacts: 'target/surefire-reports/**,target/failsafe-reports/**,target/site/jacoco/**,evidence/**', allowEmptyArchive: true, fingerprint: true
            }
        }
    }
}
