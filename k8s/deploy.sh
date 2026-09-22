#!/usr/bin/env bash
# Deploy FinFlow to a local Kubernetes cluster (kind or minikube).
#   kind:      kind create cluster --name finflow
#   minikube:  minikube start --cpus 4 --memory 8192
# HPA needs metrics-server (minikube: `minikube addons enable metrics-server`).
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Building images"
docker compose build

IMAGES="gateway user-service wallet-service payment-service notification-service audit-service analytics-service ui"
if command -v kind >/dev/null && kind get clusters 2>/dev/null | grep -q .; then
  for i in $IMAGES; do kind load docker-image "finflow/$i:1.0.0" --name "$(kind get clusters | head -1)"; done
elif command -v minikube >/dev/null; then
  for i in $IMAGES; do minikube image load "finflow/$i:1.0.0"; done
fi

kubectl apply -f k8s/00-namespace.yaml
kubectl -n finflow create configmap postgres-init --from-file=init.sql=infra/postgres/init.sql --dry-run=client -o yaml | kubectl apply -f -
kubectl -n finflow create configmap keycloak-realm --from-file=finflow-realm.json=infra/keycloak/finflow-realm.json --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/05-secrets.yaml -f k8s/10-infra.yaml
echo "==> Waiting for infrastructure"
kubectl -n finflow rollout status deploy/postgres deploy/redis deploy/kafka deploy/keycloak --timeout=300s
kubectl apply -f k8s/20-services.yaml -f k8s/30-ui.yaml
kubectl -n finflow rollout status deploy/gateway deploy/wallet-service deploy/payment-service --timeout=400s

cat <<'MSG'

Deployed. Expose the pieces the browser needs (three terminals, or background them with &):

  kubectl -n finflow port-forward svc/ui 3000:80
  kubectl -n finflow port-forward svc/gateway 8080:8080
  kubectl -n finflow port-forward svc/keycloak 8180:8080

Then open http://localhost:3000. Try scaling:

  kubectl -n finflow scale deployment payment-service --replicas=4
  kubectl -n finflow get pods -w
MSG
