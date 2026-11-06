#!/usr/bin/env bash
# Parcours de fumée d'un déploiement : dit si la plateforme marche de bout en bout, sans appel à Claude.
#
# Variables d'environnement (obligatoires) :
#   SHIPMENT_URL, INGESTION_URL, AI_URL   URL de base des trois services (sans « / » final)
#   ADMIN_USERNAME, ADMIN_PASSWORD        ADMIN amorcé de shipment-service
# Facultatives : SMOKE_TIMEOUT (secondes d'attente du scan, 90 par défaut), SMOKE_POLL (intervalle, 3).
# Besoin : curl, jq. Les données créées restent dans la base (accepté). Ni jeton ni mot de passe affichés.
set -euo pipefail

for v in SHIPMENT_URL INGESTION_URL AI_URL ADMIN_USERNAME ADMIN_PASSWORD; do
  if [ -z "${!v:-}" ]; then echo "Variable manquante : $v" >&2; exit 2; fi
done
SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-90}"
SMOKE_POLL="${SMOKE_POLL:-3}"
SHIPMENT_URL="${SHIPMENT_URL%/}"; INGESTION_URL="${INGESTION_URL%/}"; AI_URL="${AI_URL%/}"

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT
STATUS=""

step() { printf '\n== %s\n' "$*"; }
fail() { echo "ECHEC : $*" >&2; exit 1; }

# call METHOD URL [TOKEN [JSON]] : renseigne STATUS et BODY_FILE. Le jeton passe par stdin, pas par la ligne de commande.
call() {
  local method="$1" url="$2" token="${3:-}" json="${4:-}" args=()
  args=(-sS --max-time 60 -o "$BODY_FILE" -w '%{http_code}' -X "$method")
  if [ -n "$json" ]; then args+=(-H 'Content-Type: application/json' --data "$json"); fi
  if [ -n "$token" ]; then
    STATUS="$(printf 'Authorization: Bearer %s\n' "$token" | curl "${args[@]}" -H @- "$url")" \
      || fail "$method $url injoignable"
  else
    STATUS="$(curl "${args[@]}" "$url")" || fail "$method $url injoignable"
  fi
}

# Affiche statut et corps reçus, sans jamais laisser passer un jeton.
fail_response() {
  echo "ECHEC : $1" >&2
  echo "Statut reçu : $STATUS" >&2
  echo "Corps reçu : $(head -c 1000 "$BODY_FILE" | sed -E 's/"(accessToken|password)"[[:space:]]*:[[:space:]]*"[^"]*"/"\1":"***"/g')" >&2
  exit 1
}

expect() { # expect STATUT_ATTENDU MESSAGE
  [ "$STATUS" = "$1" ] || fail_response "$2 (attendu $1)"
  echo "OK  $2 ($STATUS)"
}

token_for() { # token_for USER PASSWORD : écrit le jeton sur stdout
  local json
  json="$(jq -nc --arg u "$1" --arg p "$2" '{username:$u,password:$p}')"
  call POST "$SHIPMENT_URL/api/auth/token" "" "$json"
  expect 200 "Jeton obtenu pour $1" >&2
  jq -er '.accessToken' "$BODY_FILE"
}

# Date dans 3 jours en ISO-8601 UTC, portable GNU (date -d) et BSD (date -v).
in_three_days() {
  date -u -d '+3 days' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+3d +%Y-%m-%dT%H:%M:%SZ
}

SUFFIX="$(date -u +%Y%m%d%H%M%S)-$RANDOM$RANDOM"
SCANNER_USER="smoke-scanner-$SUFFIX"
OPERATOR_USER="smoke-operator-$SUFFIX"
SITE="SMOKE-$SUFFIX"
REFERENCE="SMOKE-$SUFFIX"
# Mot de passe jetable des comptes de fumée, jamais affiché.
ACCOUNT_PASSWORD="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24 || true)"

step "1. Jeton ADMIN"
ADMIN_TOKEN="$(token_for "$ADMIN_USERNAME" "$ADMIN_PASSWORD")"

step "2. Comptes SCANNER (avec site) et OPERATOR"
call POST "$SHIPMENT_URL/api/users" "$ADMIN_TOKEN" \
  "$(jq -nc --arg u "$SCANNER_USER" --arg p "$ACCOUNT_PASSWORD" --arg s "$SITE" '{username:$u,password:$p,role:"SCANNER",site:$s}')"
expect 201 "Compte SCANNER $SCANNER_USER créé (site $SITE)"
call POST "$SHIPMENT_URL/api/users" "$ADMIN_TOKEN" \
  "$(jq -nc --arg u "$OPERATOR_USER" --arg p "$ACCOUNT_PASSWORD" '{username:$u,password:$p,role:"OPERATOR"}')"
expect 201 "Compte OPERATOR $OPERATOR_USER créé"

step "3. Jetons SCANNER et OPERATOR"
SCANNER_TOKEN="$(token_for "$SCANNER_USER" "$ACCOUNT_PASSWORD")"
OPERATOR_TOKEN="$(token_for "$OPERATOR_USER" "$ACCOUNT_PASSWORD")"

step "4. Création de l'expédition $REFERENCE (OPERATOR)"
call POST "$SHIPMENT_URL/api/shipments" "$OPERATOR_TOKEN" \
  "$(jq -nc --arg r "$REFERENCE" --arg d "$(in_three_days)" '{reference:$r,origin:"Smoke origin",destination:"Smoke destination",expectedDeliveryAt:$d}')"
expect 201 "Expédition $REFERENCE créée"
[ "$(jq -r '.status' "$BODY_FILE")" = "CREATED" ] || fail_response "Statut initial différent de CREATED"

step "5. Scan PICKED_UP par ingestion-service (SCANNER)"
call POST "$INGESTION_URL/api/scans" "$SCANNER_TOKEN" \
  "$(jq -nc --arg r "$REFERENCE" --arg s "$SITE" --arg t "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{shipmentReference:$r,type:"PICKED_UP",site:$s,occurredAt:$t}')"
case "$STATUS" in 2??) echo "OK  Scan accepté ($STATUS)" ;; *) fail_response "Scan refusé par ingestion-service" ;; esac

step "6. Attente du passage en IN_TRANSIT (jusqu'à ${SMOKE_TIMEOUT} s, par Kafka)"
deadline=$((SECONDS + SMOKE_TIMEOUT))
current=""
while :; do
  call GET "$SHIPMENT_URL/api/shipments" "$OPERATOR_TOKEN"
  [ "$STATUS" = "200" ] || fail_response "Liste des expéditions illisible"
  current="$(jq -r --arg r "$REFERENCE" '.[] | select(.reference==$r) | .status' "$BODY_FILE")"
  if [ "$current" = "IN_TRANSIT" ]; then echo "OK  $REFERENCE est IN_TRANSIT"; break; fi
  if [ "$SECONDS" -ge "$deadline" ]; then
    fail "$REFERENCE est « ${current:-introuvable} » après ${SMOKE_TIMEOUT} s, IN_TRANSIT attendu (Kafka ou consommateur en panne ?)"
  fi
  sleep "$SMOKE_POLL"
done

step "7. ai-service : santé et refus sans jeton (aucune extraction réelle)"
call GET "$AI_URL/actuator/health"
expect 200 "ai-service /actuator/health répond"
[ "$(jq -r '.status' "$BODY_FILE")" = "UP" ] || fail_response "ai-service n'est pas UP"
echo "OK  ai-service est UP"
call POST "$AI_URL/api/delivery-notes/extract"
expect 401 "POST /api/delivery-notes/extract sans jeton est refusé"

printf '\nFumée réussie : la plateforme marche de bout en bout.\n'
