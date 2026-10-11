#!/usr/bin/env bash
# Smoke test do mechanical-hub-orders contra uma instancia ja no ar.
#
# Uso: ci/smoke-test.sh <base-url>     (ex.: http://localhost:8080)
#
# No pipeline roda via `kubectl port-forward` para o Service; localmente,
# contra o container subido com o docker-compose. Confere que a aplicacao
# subiu com banco e migrations (readiness), que o catalogo das seeds e servido,
# que as rotas do mecanico exigem a identidade do gateway e que as metricas
# do outbox estao expostas.
set -euo pipefail

BASE_URL="${1:?informe a URL base, ex.: http://localhost:8080}"
OIL_CHANGE_REPAIR_ID="03d94934-6201-449b-b8db-490546c35d34"
MECHANIC_ID="7a2e9b41-3c5d-4f60-8e1a-000000000002"

fail() {
  echo "FALHOU: $*" >&2
  exit 1
}

status_of() {
  curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$@"
}

echo "→ readiness"
readiness="$(curl -fsS --max-time 10 "$BASE_URL/actuator/health/readiness")" || fail "readiness nao respondeu"
grep -q '"status":"UP"' <<< "$readiness" || fail "readiness diferente de UP: $readiness"

echo "→ GET /services com a identidade do gateway"
services="$(curl -fsS --max-time 10 \
  -H "x-user-id: $MECHANIC_ID" -H "x-user-role: MECHANICAL" -H "x-user-name: Smoke Test" \
  "$BASE_URL/services")" || fail "GET /services nao retornou 2xx"
grep -q "$OIL_CHANGE_REPAIR_ID" <<< "$services" || fail "catalogo sem o reparo das seeds"

echo "→ GET /orders sem identidade"
[ "$(status_of "$BASE_URL/orders")" = "401" ] || fail "GET /orders sem headers do gateway deveria ser 401"

echo "→ GET /mechanical-hub/service-orders sem token"
[ "$(status_of "$BASE_URL/mechanical-hub/service-orders/OS-INEXISTENTE")" = "404" ] || fail "rota do cliente sem token deveria ser 404"

echo "→ /actuator/prometheus"
metrics="$(curl -fsS --max-time 10 "$BASE_URL/actuator/prometheus")" || fail "prometheus nao respondeu"
grep -q '^outbox_oldest_pending_age_seconds' <<< "$metrics" || fail "metrica outbox_oldest_pending_age_seconds ausente"

echo "Smoke test OK em $BASE_URL"
