#!/usr/bin/env bash
# End-to-end check against the running stack (uses the OAuth2 password grant for scripting only).
set -euo pipefail
API=http://localhost:8080
KC=http://localhost:8180/realms/finflow/protocol/openid-connect/token
ALICE=aaaaaaaa-0000-0000-0000-000000000001
BOB=aaaaaaaa-0000-0000-0000-000000000002

token() {
  curl -s -X POST "$KC" -d grant_type=password -d client_id=finflow-ui -d "username=$1" -d "password=$2" -d scope=openid \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}
step() { echo; echo "── $*"; }

step "1. Unauthenticated request is rejected (expect 401)"
curl -s -o /dev/null -w "HTTP %{http_code}\n" $API/api/v1/wallets/me

ALICE_T=$(token alice alice123); ADMIN_T=$(token admin admin123)
[ -n "$ALICE_T" ] && echo "got JWT for alice (${#ALICE_T} chars)"

step "2. Alice's profile + wallet (wallet is cached in Redis for 15s)"
curl -s -H "Authorization: Bearer $ALICE_T" $API/api/v1/users/me; echo
curl -s -H "Authorization: Bearer $ALICE_T" $API/api/v1/wallets/me; echo
curl -s -H "Authorization: Bearer $ALICE_T" $API/api/v1/wallets/me; echo

KEY=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)
step "3. Alice pays Bob 250 INR (Idempotency-Key $KEY)"
curl -s -i -X POST $API/api/v1/payments -H "Authorization: Bearer $ALICE_T" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" -H "X-Correlation-Id: smoke-test-1" -d "{\"toUserId\":\"$BOB\",\"amount\":250.00,\"note\":\"smoke test\"}" | grep -E "^HTTP|Idempotent-Replayed|\{"

step "4. Same request again with the same key: 200 + Idempotent-Replayed: true, balance is charged only once"
curl -s -i -X POST $API/api/v1/payments -H "Authorization: Bearer $ALICE_T" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" -d "{\"toUserId\":\"$BOB\",\"amount\":250.00,\"note\":\"smoke test\"}" | grep -E "^HTTP|Idempotent-Replayed"

step "5. Alice is not allowed to use admin endpoints (expect 403)"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST -H "Authorization: Bearer $ALICE_T" $API/api/v1/admin/wallets/$BOB/freeze

sleep 3
step "6. Admin reads the audit trail for correlation id smoke-test-1 (events produced by this one request)"
curl -s -H "Authorization: Bearer $ADMIN_T" $API/api/v1/audit/trace/smoke-test-1 | sed 's/},{/},\n{/g' | cut -c1-220

step "7. Alice's notifications (produced by the Kafka consumer)"
curl -s -H "Authorization: Bearer $ALICE_T" $API/api/v1/notifications/me | cut -c1-400; echo
echo; echo "Smoke test finished."
