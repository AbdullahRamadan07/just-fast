#!/usr/bin/env bash
# Ordered rollout:
#   1. platform resources (namespace, config, secrets, dev MySQL)
#   2. Flyway migration Job (must complete)
#   3. application (API, web, ingress)
#
# Usage: deploy/k8s/deploy.sh deploy/k8s/overlays/dev
set -euo pipefail

OVERLAY="${1:?usage: deploy.sh <overlay-dir>}"
NAMESPACE=just-fast

echo "==> Applying platform resources"
kubectl apply -k "$OVERLAY" -l app.kubernetes.io/component=platform
if kubectl -n "$NAMESPACE" get statefulset mysql >/dev/null 2>&1; then
  kubectl -n "$NAMESPACE" rollout status statefulset/mysql --timeout=5m
fi

echo "==> Running database migrations"
kubectl -n "$NAMESPACE" delete job just-fast-migrate --ignore-not-found --wait=true
kubectl apply -k "$OVERLAY" -l app.kubernetes.io/component=migration
if ! kubectl -n "$NAMESPACE" wait --for=condition=complete job/just-fast-migrate --timeout=10m; then
  echo "Migration failed; application rollout aborted." >&2
  kubectl -n "$NAMESPACE" logs job/just-fast-migrate --tail=100 >&2 || true
  exit 1
fi

echo "==> Rolling out application"
kubectl apply -k "$OVERLAY" -l app.kubernetes.io/component=app
kubectl -n "$NAMESPACE" rollout status deployment/backend --timeout=10m
kubectl -n "$NAMESPACE" rollout status deployment/frontend --timeout=5m
echo "==> Done"
