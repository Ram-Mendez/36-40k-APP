#!/usr/bin/env bash
# Run from Jenkins after login and customer creation, against the temporary Kind lab.
set -euo pipefail
set +x

: "${BASE_URL:?BASE_URL is required}"
: "${ACCESS_TOKEN:?ACCESS_TOKEN is required}"
: "${CUSTOMER_ID:?CUSTOMER_ID is required}"
namespace="${KUBE_NAMESPACE:-backend-lab}"
evidence_dir="${EVIDENCE_DIR:-$PWD/evidence}"
request_id="microservices-$(openssl rand -hex 8)"
scratch_dir="$(mktemp -d)"
restore_shipping=false
mkdir -p "$evidence_dir"

cleanup() {
  if [[ "$restore_shipping" == true ]]; then
    kubectl -n "$namespace" scale deployment/shipping-service --replicas=1 || true
    kubectl -n "$namespace" rollout status deployment/shipping-service --timeout=240s || true
  fi
  rm -rf -- "$scratch_dir"
}
trap cleanup EXIT

api() {
  local method="$1" path="$2"
  local args=(--silent --show-error --fail-with-body --connect-timeout 5 --max-time 15
    --request "$method" --header "Authorization: Bearer $ACCESS_TOKEN"
    --header "X-Request-ID: $request_id" --header 'Content-Type: application/json')
  if (( $# == 3 )); then args+=(--data "$3"); fi
  curl "${args[@]}" "$BASE_URL$path"
}

expect_status() {
  local expected="$1" method="$2" path="$3" status
  status="$(curl --silent --show-error --connect-timeout 5 --max-time 10 \
    --output "$scratch_dir/response.json" --write-out '%{http_code}' \
    --request "$method" --header "Authorization: Bearer $ACCESS_TOKEN" \
    --header "X-Request-ID: $request_id" "$BASE_URL$path")"
  if [[ "$status" != "$expected" ]]; then
    printf 'Expected HTTP %s for %s %s, received %s\n' "$expected" "$method" "$path" "$status" >&2
    cat "$scratch_dir/response.json" >&2
    return 1
  fi
}

create_order() {
  local quantity="$1" order
  order="$(api POST /api/v1/orders "$(jq -nc --argjson id "$CUSTOMER_ID" '{customerId:$id}')")"
  local id
  id="$(jq -er '.id' <<< "$order")"
  api PUT "/api/v1/orders/$id/lines/$product_id" \
    "$(jq -nc --argjson q "$quantity" '{quantity:$q}')" > /dev/null
  api POST "/api/v1/orders/$id/confirm" | jq -e '.status == "CONFIRMED"' > /dev/null
  printf '%s' "$id"
}

sku="MS-$(openssl rand -hex 8)"
product="$(api POST /api/v1/products \
  "$(jq -nc --arg sku "$sku" '{sku:$sku,name:"Microservices smoke product",unitPrice:100.00}')")"
product_id="$(jq -er '.id' <<< "$product")"
api POST "/api/v1/inventory/$product_id/adjustments" \
  '{"quantity":10,"reason":"Microservices smoke initial stock"}' > /dev/null

# Real DNS + HTTP contract between the two independently deployed applications.
order_id="$(create_order 1)"
for attempt in 1 2; do
  kubectl -n "$namespace" exec deployment/backend-app -- \
    curl --silent --show-error --fail --max-time 10 --header "X-Request-ID: $request_id" \
    "http://shipping-service:9096/quotes?orderId=$order_id&amount=100.00" \
    > "$scratch_dir/quote-$attempt.json"
done
jq -e '.cost == 7.50 and (.reference | length > 0)' "$scratch_dir/quote-1.json" > /dev/null
diff <(jq -S . "$scratch_dir/quote-1.json") <(jq -S . "$scratch_dir/quote-2.json")
api POST "/api/v1/orders/$order_id/complete" > "$evidence_dir/completed-order.json"
jq -e '.status == "COMPLETED" and .shippingCost == 7.50 and (.shippingReference | length > 0)' \
  "$evidence_dir/completed-order.json" > /dev/null
expect_status 409 POST "/api/v1/orders/$order_id/complete"
api GET "/api/v1/inventory?productId=$product_id" \
  | jq -e '.content[0] | .onHand == 9 and .reserved == 0 and .available == 9' > /dev/null

# A dependency outage must preserve confirmed state and stock until an explicit retry.
retry_order_id="$(create_order 2)"
restore_shipping=true
kubectl -n "$namespace" scale deployment/shipping-service --replicas=0
kubectl -n "$namespace" wait --for=delete pod -l app=shipping-service --timeout=90s
expect_status 503 POST "/api/v1/orders/$retry_order_id/complete"
jq -e '.code == "SHIPPING_UNAVAILABLE"' "$scratch_dir/response.json" > /dev/null
api GET "/api/v1/orders/$retry_order_id" | jq -e '.status == "CONFIRMED"' > /dev/null
api GET "/api/v1/inventory?productId=$product_id" \
  | jq -e '.content[0] | .onHand == 9 and .reserved == 2 and .available == 7' > /dev/null
kubectl -n "$namespace" scale deployment/shipping-service --replicas=1
kubectl -n "$namespace" rollout status deployment/shipping-service --timeout=240s
restore_shipping=false
api POST "/api/v1/orders/$retry_order_id/complete" > "$evidence_dir/recovered-order.json"
jq -e '.status == "COMPLETED" and .shippingCost == 0' "$evidence_dir/recovered-order.json" > /dev/null
expect_status 409 POST "/api/v1/orders/$retry_order_id/complete"
api GET "/api/v1/inventory?productId=$product_id" \
  | jq -e '.content[0] | .onHand == 7 and .reserved == 0 and .available == 7' > /dev/null

# Verify persisted business effects, not merely HTTP status codes.
ship_count="$(kubectl -n "$namespace" exec deployment/postgres -- \
  psql -U business -d business_operations -tAc \
  "SELECT count(*) FROM stock_movement WHERE product_id=$product_id AND kind='SHIP'")"
test "$ship_count" -eq 2
printf 'result=PASS\nrequest_id=%s\nservice_dns=PASS\nquote_repeat=PASS\norder_complete=PASS\nshipping_outage_503=PASS\nreservation_preserved=PASS\nretry_complete=PASS\nfree_shipping_threshold=PASS\nrepeat_complete_409=PASS\nship_movements=%s\n' \
  "$request_id" "$ship_count" | tee "$evidence_dir/microservices-smoke.txt"
