#!/usr/bin/env bash
# Prueba end-to-end del flujo autenticacion -> produccion -> consumo contra servicios en ejecucion.
# Requiere: curl, jq, openssl. Lee credenciales desde .env (no versionado) o desde el entorno.
#
#   ./scripts/e2e-test.sh
#   AUTH_URL=http://<ec2-auth>:8080 PRODUCER_URL=... CONSUMER_URL=... ./scripts/e2e-test.sh
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then set -a; source "$ROOT/.env"; set +a; fi

AUTH_URL="${AUTH_URL:-http://localhost:${AUTH_PORT:-8080}}"
PRODUCER_URL="${PRODUCER_URL:-http://localhost:${PRODUCER_PORT:-8081}}"
CONSUMER_URL="${CONSUMER_URL:-http://localhost:${CONSUMER_PORT:-8082}}"
LISTENER="${KAFKA_LISTENER_ID:-notificacionListener}"
: "${AUTH_USERNAME:?Defina AUTH_USERNAME}" "${AUTH_PASSWORD:?Defina AUTH_PASSWORD}"

PASS=0; FAIL=0
check() { # check <descripcion> <esperado> <obtenido>
  if [[ "$2" == "$3" ]]; then echo "  [OK]   $1 ($3)"; PASS=$((PASS+1)); else echo "  [FAIL] $1: esperado $2, obtenido $3"; FAIL=$((FAIL+1)); fi
}
code() { curl -s -o /tmp/e2e_body.$$ -w '%{http_code}' "$@"; }
body() { cat /tmp/e2e_body.$$; }
trap 'rm -f /tmp/e2e_body.$$' EXIT

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
# Firma un JWT HS256 con el secreto compartido (solo para probar tokens expirados)
jwt_hs256() {
  local h p s
  h=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
  p=$(printf '%s' "$1" | b64url)
  s=$(printf '%s.%s' "$h" "$p" | openssl dgst -sha256 -hmac "$JWT_SECRET" -binary | b64url)
  printf '%s.%s.%s' "$h" "$p" "$s"
}

echo "== 1. Autenticacion ($AUTH_URL)"
check "Credenciales invalidas -> 401" 401 "$(code -X POST "$AUTH_URL/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$AUTH_USERNAME\",\"password\":\"incorrecta\"}")"
check "Cuerpo incompleto -> 400" 400 "$(code -X POST "$AUTH_URL/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$AUTH_USERNAME\"}")"
check "Login valido -> 200" 200 "$(code -X POST "$AUTH_URL/auth/login" -H 'Content-Type: application/json' -d "$(jq -nc --arg u "$AUTH_USERNAME" --arg p "$AUTH_PASSWORD" '{username:$u,password:$p}')")"
TOKEN=$(body | jq -r .access_token)
check "token_type Bearer" Bearer "$(body | jq -r .token_type)"
echo "  claims: $(cut -d. -f2 <<<"$TOKEN" | tr '_-' '/+' | base64 -d 2>/dev/null | jq -c '{sub,roles,iss,aud,iat,exp}' 2>/dev/null)"

echo "== 2. Productor ($PRODUCER_URL)"
check "Sin token -> 401" 401 "$(code -X POST "$PRODUCER_URL/notificaciones" -H 'Content-Type: application/json' -d '{"usuario":"ana"}')"
check "Token invalido -> 401" 401 "$(code -X POST "$PRODUCER_URL/notificaciones" -H 'Authorization: Bearer abc.def.ghi' -H 'Content-Type: application/json' -d '{"usuario":"ana"}')"
if [[ -n "${JWT_SECRET:-}" ]]; then
  NOW=$(date +%s)
  EXPIRED=$(jwt_hs256 "{\"sub\":\"$AUTH_USERNAME\",\"roles\":[\"USER\",\"ADMIN\"],\"iss\":\"${JWT_ISSUER:-sumativa3-auth-service}\",\"aud\":[\"${JWT_AUDIENCE:-notificaciones-api}\"],\"iat\":$((NOW-7200)),\"exp\":$((NOW-3600))}")
  check "Token expirado -> 401" 401 "$(code -X POST "$PRODUCER_URL/notificaciones" -H "Authorization: Bearer $EXPIRED" -H 'Content-Type: application/json' -d '{"usuario":"ana"}')"
fi
check "JSON incompleto -> 400" 400 "$(code -X POST "$PRODUCER_URL/notificaciones" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}')"

IDS=()
for u in ana bruno carla; do
  check "Publicar notificacion de $u -> 201" 201 "$(code -X POST "$PRODUCER_URL/notificaciones" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"usuario\":\"$u\"}")"
  echo "         $(body | jq -c '{id:.notificacion.id,particion,offset}')"
  IDS+=("$(body | jq -r .notificacion.id)")
done

echo "== 3. Consumidor ($CONSUMER_URL)"
check "Estado sin token -> 401" 401 "$(code "$CONSUMER_URL/kafka-listener/$LISTENER/estado")"
consumidos() { curl -s -H "Authorization: Bearer $TOKEN" "$CONSUMER_URL/notificaciones/consumidas?limite=200" | jq -r '.mensajes[].notificacion.id'; }
esperar_ids() { # espera hasta 30 s a que todos los ids aparezcan como consumidos
  for _ in $(seq 1 30); do
    local faltan=0 lista; lista=$(consumidos)
    for id in "$@"; do grep -qx "$id" <<<"$lista" || faltan=1; done
    [[ $faltan == 0 ]] && echo yes && return; sleep 1
  done; echo no
}
check "Los 3 mensajes fueron consumidos" yes "$(esperar_ids "${IDS[@]}")"
check "Estado listener -> 200" 200 "$(code -H "Authorization: Bearer $TOKEN" "$CONSUMER_URL/kafka-listener/$LISTENER/estado")"
echo "         $(body | jq -c '{estado,groupId,particionesAsignadas}')"

check "Pausar listener -> 200" 200 "$(code -X POST -H "Authorization: Bearer $TOKEN" "$CONSUMER_URL/kafka-listener/$LISTENER/pausar")"
sleep 3
check "Listener en estado PAUSADO" PAUSADO "$(curl -s -H "Authorization: Bearer $TOKEN" "$CONSUMER_URL/kafka-listener/$LISTENER/estado" | jq -r .estado)"
code -X POST "$PRODUCER_URL/notificaciones" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"usuario":"durante-pausa"}' >/dev/null
PAUSA_ID=$(body | jq -r .notificacion.id)
sleep 4
check "Mensaje publicado en pausa NO consumido aun" no "$(consumidos | grep -qx "$PAUSA_ID" && echo yes || echo no)"
check "Reanudar listener -> 200" 200 "$(code -X POST -H "Authorization: Bearer $TOKEN" "$CONSUMER_URL/kafka-listener/$LISTENER/reanudar")"
check "Mensaje pendiente consumido tras reanudar" yes "$(esperar_ids "$PAUSA_ID")"

echo
echo "Resultado: $PASS OK, $FAIL fallidas"
[[ $FAIL == 0 ]]
