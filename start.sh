#!/usr/bin/env bash
# One command to build and run the whole platform.  Requires only Docker (with the compose plugin).
set -euo pipefail
cd "$(dirname "$0")"

echo "==> Building images and starting FinFlow (first run takes several minutes: Maven downloads dependencies)"
docker compose up --build -d

echo "==> Waiting for the gateway and Keycloak to become ready ..."
for i in $(seq 1 90); do
  gw=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health || true)
  kc=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8180/realms/finflow/.well-known/openid-configuration || true)
  ui=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:3000/ || true)
  if [ "$gw" = "200" ] && [ "$kc" = "200" ] && [ "$ui" = "200" ]; then
    break
  fi
  sleep 5
done

cat <<'MSG'

  FinFlow is up.

  Demo UI ............ http://localhost:3000        (log in with a demo user below)
  API gateway ........ http://localhost:8080
  Keycloak admin ..... http://localhost:8180        (admin / admin)
  Grafana ............ http://localhost:3001        (dashboard: FinFlow - Platform Overview)
  Prometheus ......... http://localhost:9090
  Kafka UI ........... http://localhost:8090

  Demo users:  alice / alice123   bob / bob123   merchant / merchant123   admin / admin123

  Stop:   ./stop.sh          Smoke test:   ./scripts/smoke-test.sh
MSG
