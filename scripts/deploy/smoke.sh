#!/usr/bin/env bash
# Parcours de fumée d'un déploiement : dit si la plateforme marche de bout en bout, sans appel à Claude.
#
# Variables d'environnement (obligatoires) :
#   SHIPMENT_URL, INGESTION_URL, AI_URL   URL de base des trois services (sans « / » final)
#   ADMIN_USERNAME, ADMIN_PASSWORD        ADMIN amorcé de shipment-service
#   FRONTEND_URL                          URL du front (nginx), sans « / » final : dernière étape, à travers lui
# Facultatives : SMOKE_TIMEOUT (secondes d'attente du scan, 90 par défaut), SMOKE_POLL (intervalle, 3).
# Besoin : curl, jq. Les données créées restent dans la base (accepté). Ni jeton ni mot de passe affichés.
set -euo pipefail

for v in SHIPMENT_URL INGESTION_URL AI_URL FRONTEND_URL ADMIN_USERNAME ADMIN_PASSWORD; do
  if [ -z "${!v:-}" ]; then echo "Variable manquante : $v" >&2; exit 2; fi
done
SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-90}"
SMOKE_POLL="${SMOKE_POLL:-3}"
SHIPMENT_URL="${SHIPMENT_URL%/}"; INGESTION_URL="${INGESTION_URL%/}"; AI_URL="${AI_URL%/}"
FRONTEND_URL="${FRONTEND_URL%/}"

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT
STATUS=""

step() { printf '\n== %s\n' "$*"; }
fail() { echo "ECHEC : $*" >&2; exit 1; }

# call METHOD URL [TOKEN [JSON]] : renseigne STATUS et BODY_FILE. Jeton et corps passent par des descripteurs
# (printf est interne au shell), jamais par la ligne de commande, visible dans ps.
call() {
  local method="$1" url="$2" token="${3:-}" json="${4:-}"
  local opts=(-sS --max-time 60 -o "$BODY_FILE" -w '%{http_code}' -X "$method")
  # Les substitutions de processus sont écrites dans la commande curl elle-même : rangées dans un
  # tableau, leur descripteur est déjà fermé quand curl tourne (bash 3.2, pipeline).
  if [ -n "$token" ] && [ -n "$json" ]; then
    STATUS="$(curl "${opts[@]}" -H @<(printf 'Authorization: Bearer %s\n' "$token") \
      -H 'Content-Type: application/json' --data @<(printf '%s' "$json") "$url")" \
      || fail "$method $url injoignable"
  elif [ -n "$json" ]; then
    STATUS="$(curl "${opts[@]}" -H 'Content-Type: application/json' --data @<(printf '%s' "$json") "$url")" \
      || fail "$method $url injoignable"
  elif [ -n "$token" ]; then
    STATUS="$(curl "${opts[@]}" -H @<(printf 'Authorization: Bearer %s\n' "$token") "$url")" \
      || fail "$method $url injoignable"
  else
    STATUS="$(curl "${opts[@]}" "$url")" || fail "$method $url injoignable"
  fi
}

# call_upload URL TOKEN FICHIER : envoie FICHIER en multipart (partie « file »), comme call.
call_upload() {
  STATUS="$(curl -sS --max-time 60 -o "$BODY_FILE" -w '%{http_code}' -X POST \
    -H @<(printf 'Authorization: Bearer %s\n' "$2") -F "file=@$3;type=text/csv" "$1")" \
    || fail "POST $1 injoignable"
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
  json="$(U="$1" P="$2" jq -nc '{username:env.U,password:env.P}')"
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
  "$(U="$SCANNER_USER" P="$ACCOUNT_PASSWORD" S="$SITE" jq -nc '{username:env.U,password:env.P,role:"SCANNER",site:env.S}')"
expect 201 "Compte SCANNER $SCANNER_USER créé (site $SITE)"
call POST "$SHIPMENT_URL/api/users" "$ADMIN_TOKEN" \
  "$(U="$OPERATOR_USER" P="$ACCOUNT_PASSWORD" jq -nc '{username:env.U,password:env.P,role:"OPERATOR"}')"
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

step "6. Import CSV de 2 lignes par ingestion-service (SCANNER) : une valide, une invalide"
IMPORT_FILE="$(mktemp)"
printf 'shipmentReference,type,site,occurredAt\n%s,ARRIVED_AT_SITE,%s,%s\n%s,NOT_A_TYPE,%s,%s\n' \
  "$REFERENCE" "$SITE" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$REFERENCE" "$SITE" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >"$IMPORT_FILE"
call_upload "$INGESTION_URL/api/scans/imports" "$SCANNER_TOKEN" "$IMPORT_FILE"
rm -f "$IMPORT_FILE"
expect 200 "Import CSV accepté"
[ "$(jq -r '.published' "$BODY_FILE")" = "1" ] || fail_response "published = 1 attendu"
[ "$(jq -r '.rejected' "$BODY_FILE")" = "1" ] || fail_response "rejected = 1 attendu"
echo "OK  Import : 1 ligne publiée, 1 rejetée"

step "7. Attente du passage en IN_TRANSIT (jusqu'à ${SMOKE_TIMEOUT} s, par Kafka)"
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

step "8. ai-service : santé et refus sans jeton (aucune extraction réelle)"
call GET "$AI_URL/actuator/health"
expect 200 "ai-service /actuator/health répond"
[ "$(jq -r '.status' "$BODY_FILE")" = "UP" ] || fail_response "ai-service n'est pas UP"
echo "OK  ai-service est UP"
call POST "$AI_URL/api/delivery-notes/extract"
expect 401 "POST /api/delivery-notes/extract sans jeton est refusé"

step "9. Front : pages, en-tête de sécurité, connexion et refus sans jeton, à travers nginx"
HEADERS_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE" "$HEADERS_FILE"' EXIT
call_front_page() { # call_front_page CHEMIN : comme call GET, et garde les en-têtes de réponse
  STATUS="$(curl -sS --max-time 60 -D "$HEADERS_FILE" -o "$BODY_FILE" -w '%{http_code}' "$FRONTEND_URL$1")" \
    || fail "GET $FRONTEND_URL$1 injoignable"
}
for path in / /shipments /projet; do
  call_front_page "$path"
  expect 200 "Front GET $path"
  grep -qi '^content-type:.*text/html' "$HEADERS_FILE" || fail_response "Front GET $path : Content-Type text/html attendu"
  grep -qi '<html' "$BODY_FILE" || fail_response "Front GET $path : le corps n'est pas du HTML"
done
grep -qi '^content-security-policy:' "$HEADERS_FILE" || fail_response "Front : en-tête Content-Security-Policy absent"
echo "OK  Content-Security-Policy présent"
call_front_page /project-stats.json
expect 200 "Front GET /project-stats.json (chiffres générés au déploiement)"
jq -e '.commit | strings | length > 0' "$BODY_FILE" >/dev/null || fail_response "Front : project-stats.json sans champ commit"
call POST "$FRONTEND_URL/api/auth/token" "" "$(U="$ADMIN_USERNAME" P="$ADMIN_PASSWORD" jq -nc '{username:env.U,password:env.P}')"
expect 200 "Connexion de l'ADMIN par le front (/api/auth/token)"
jq -e '.accessToken' "$BODY_FILE" >/dev/null || fail_response "Front : pas de jeton dans la réponse"
call POST "$FRONTEND_URL/api/scans"
expect 401 "POST /api/scans sans jeton par le front (ingestion-service) est refusé"

printf '\nFumée réussie : la plateforme marche de bout en bout.\n'
