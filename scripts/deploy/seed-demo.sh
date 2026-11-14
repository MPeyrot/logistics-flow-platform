#!/usr/bin/env bash
# Amorçage de données plausibles pour la démo en ligne : ~20 expéditions LFP-<AAMMJJ>-<nn> entre villes
# françaises, à tous les stades du cycle, avec des scans datés des jours précédents (envoyés par l'ADMIN).
#
# Variables d'environnement (obligatoires) :
#   SHIPMENT_URL, INGESTION_URL           URL de base des services (sans « / » final)
#   ADMIN_USERNAME, ADMIN_PASSWORD        ADMIN amorcé de shipment-service
# Besoin : curl, jq. Ni jeton ni mot de passe affichés. Aucun appel à Claude.
# Rejouable : sur une référence déjà créée (409, même jour), seuls les scans absents du détail de l'expédition
# sont envoyés (un premier passage interrompu se répare). Chaque scan a un eventId déterministe (référence et rang) :
# un scan parti mais pas encore consommé est renvoyé sans doublon.
# Pas de STALLED amorçable : il se compte depuis la réception du dernier scan, donc depuis l'amorçage.
# LATE : 07, 08 et 15 sont créées avec une échéance dans 5 min (l'API refuse une échéance passée) ; LATE s'ouvre
# à la première passe de détection qui suit (15 min après le démarrage de shipment-service, puis toutes les 15 min).
set -euo pipefail

for v in SHIPMENT_URL INGESTION_URL ADMIN_USERNAME ADMIN_PASSWORD; do
  if [ -z "${!v:-}" ]; then echo "Variable manquante : $v" >&2; exit 2; fi
done
SHIPMENT_URL="${SHIPMENT_URL%/}"; INGESTION_URL="${INGESTION_URL%/}"

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT
STATUS=""

fail() { echo "ECHEC : $*" >&2; exit 1; }

# call METHOD URL [TOKEN [JSON]] : renseigne STATUS et BODY_FILE. Jeton et corps passent par des descripteurs
# (printf est interne au shell), jamais par la ligne de commande, visible dans ps.
call() {
  local method="$1" url="$2" token="${3:-}" json="${4:-}"
  local opts=(-sS --max-time 60 -o "$BODY_FILE" -w '%{http_code}' -X "$method")
  if [ -n "$token" ] && [ -n "$json" ]; then
    STATUS="$(curl "${opts[@]}" -H @<(printf 'Authorization: Bearer %s\n' "$token") \
      -H 'Content-Type: application/json' --data @<(printf '%s' "$json") "$url")" \
      || fail "$method $url injoignable"
  elif [ -n "$json" ]; then
    STATUS="$(curl "${opts[@]}" -H 'Content-Type: application/json' --data @<(printf '%s' "$json") "$url")" \
      || fail "$method $url injoignable"
  else
    STATUS="$(curl "${opts[@]}" -H @<(printf 'Authorization: Bearer %s\n' "$token") "$url")" \
      || fail "$method $url injoignable"
  fi
}

# Affiche statut et corps reçus, sans jamais laisser passer un jeton.
fail_response() {
  echo "ECHEC : $1" >&2
  echo "Statut reçu : $STATUS" >&2
  echo "Corps reçu : $(head -c 1000 "$BODY_FILE" | sed -E 's/"(accessToken|password)"[[:space:]]*:[[:space:]]*"[^"]*"/"\1":"***"/g')" >&2
  exit 1
}

# Date à H heures d'ici (H négatif : dans le passé), ISO-8601 UTC, portable GNU et BSD.
hours_from_now() {
  local h="$1" gnu bsd
  if [ "$h" -ge 0 ]; then gnu="+$h hours"; bsd="+${h}H"; else gnu="$h hours"; bsd="${h}H"; fi
  date -u -d "$gnu" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v"$bsd" +%Y-%m-%dT%H:%M:%SZ
}

# Échéance d'une expédition. L'API la refuse dans le passé (400) : « late » = dans 5 min, dépassée à la
# première passe de détection (LATE) ; sinon H heures d'ici (H > 0).
due_at() {
  if [ "$1" = late ]; then
    date -u -d "+5 minutes" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+5M +%Y-%m-%dT%H:%M:%SZ
  else
    hours_from_now "$1"
  fi
}

# Une expédition par ligne : n | origine | étape | destination | parcours | échéance (heures d'ici, ou « late »).
# Parcours (heures dans le passé de chaque scan) : created, transit, out, delivered, failed, retry.
# Le site d'un scan est une ville : départ = origine, passage = étape, tournée et livraison = destination.
SHIPMENTS='01|Paris|Lyon|Marseille|delivered|12
02|Lille|Paris|Nantes|delivered|6
03|Strasbourg|Dijon|Lyon|delivered|20
04|Bordeaux|Toulouse|Montpellier|delivered|2
05|Nantes|Rennes|Brest|transit|40
06|Paris|Orléans|Tours|transit|60
07|Marseille|Lyon|Grenoble|transit|late
08|Lille|Reims|Strasbourg|transit|late
09|Toulouse|Bordeaux|Nantes|transit|30
10|Lyon|Dijon|Besançon|out|8
11|Paris|Rouen|Caen|out|6
12|Nice|Marseille|Montpellier|out|20
13|Bordeaux|Limoges|Clermont-Ferrand|failed|40
14|Rennes|Le Mans|Angers|failed|30
15|Strasbourg|Metz|Nancy|failed|late
16|Lyon|Saint-Étienne|Clermont-Ferrand|retry|36
17|Paris|Lille|Rouen|created|72
18|Nantes|Tours|Toulouse|created|96
19|Marseille|Nice|Lyon|created|48
20|Lille|Amiens|Paris|transit|50'

# UUID déterministe (32 premiers caractères du MD5 de $1, mis en forme 8-4-4-4-12), portable GNU et BSD.
uuid_of() {
  local h
  h="$(printf '%s' "$1" | { md5sum 2>/dev/null || md5 -r; } | cut -c1-32)"
  printf '%s-%s-%s-%s-%s' "${h:0:8}" "${h:8:4}" "${h:12:4}" "${h:16:4}" "${h:20:12}"
}

# Les scans d'une expédition partent dans l'ordre ; SCAN_RANK compte les scans du parcours, SCANS_PRESENT ceux
# que shipment-service a déjà enregistrés (les premiers : l'ordre est gardé par expédition).
SCAN_RANK=0
SCANS_PRESENT=0

# scan REF TYPE SITE HEURES [MOTIF] : envoie un scan daté de HEURES (négatif) par ingestion-service,
# sauf s'il figure déjà dans l'expédition.
scan() {
  local json
  SCAN_RANK=$((SCAN_RANK + 1))
  if [ "$SCAN_RANK" -le "$SCANS_PRESENT" ]; then return; fi
  json="$(jq -nc --arg i "$(uuid_of "$1|$SCAN_RANK")" --arg r "$1" --arg t "$2" --arg s "$3" --arg o "$(hours_from_now "$4")" --arg m "${5:-}" \
    '{eventId:$i,shipmentReference:$r,type:$t,site:$s,occurredAt:$o} + (if $m == "" then {} else {reason:$m} end)')"
  call POST "$INGESTION_URL/api/scans" "$ADMIN_TOKEN" "$json"
  case "$STATUS" in 2??) ;; *) fail_response "Scan $2 de $1 refusé par ingestion-service" ;; esac
}

echo "== Jeton ADMIN"
call POST "$SHIPMENT_URL/api/auth/token" "" \
  "$(U="$ADMIN_USERNAME" P="$ADMIN_PASSWORD" jq -nc '{username:env.U,password:env.P}')"
[ "$STATUS" = "200" ] || fail_response "Connexion de l'ADMIN refusée"
ADMIN_TOKEN="$(jq -er '.accessToken' "$BODY_FILE")"
echo "OK  Jeton obtenu pour $ADMIN_USERNAME"

DAY="$(date -u +%y%m%d)"
created=0
skipped=0
echo "== Expéditions LFP-$DAY-nn"
while IFS='|' read -r n origin hub dest course due; do
  ref="LFP-$DAY-$n"
  call POST "$SHIPMENT_URL/api/shipments" "$ADMIN_TOKEN" \
    "$(jq -nc --arg r "$ref" --arg o "$origin" --arg d "$dest" --arg e "$(due_at "$due")" \
      '{reference:$r,origin:$o,destination:$d,expectedDeliveryAt:$e}')"
  SCAN_RANK=0
  SCANS_PRESENT=0
  if [ "$STATUS" = "409" ]; then
    call GET "$SHIPMENT_URL/api/shipments/$ref" "$ADMIN_TOKEN"
    [ "$STATUS" = "200" ] || fail_response "Lecture de $ref refusée"
    SCANS_PRESENT="$(jq -er '.scans | length' "$BODY_FILE")" || fail "Détail de $ref illisible"
    echo "--  $ref existe déjà ($SCANS_PRESENT scan(s) enregistré(s)) : scans manquants renvoyés"
    skipped=$((skipped + 1))
  else
    [ "$STATUS" = "201" ] || fail_response "Création de $ref refusée"
    created=$((created + 1))
  fi
  case "$course" in
    created) ;;
    transit)
      scan "$ref" PICKED_UP "$origin" -72
      scan "$ref" ARRIVED_AT_SITE "$hub" -40 ;;
    out)
      scan "$ref" PICKED_UP "$origin" -72
      scan "$ref" ARRIVED_AT_SITE "$hub" -48
      scan "$ref" OUT_FOR_DELIVERY "$dest" -5 ;;
    delivered)
      scan "$ref" PICKED_UP "$origin" -120
      scan "$ref" ARRIVED_AT_SITE "$hub" -96
      scan "$ref" OUT_FOR_DELIVERY "$dest" -80
      scan "$ref" DELIVERED "$dest" -76 ;;
    failed)
      scan "$ref" PICKED_UP "$origin" -96
      scan "$ref" ARRIVED_AT_SITE "$hub" -72
      scan "$ref" OUT_FOR_DELIVERY "$dest" -30
      # Deux motifs en alternance : « Destinataire absent » (n impair), « Adresse incomplète » (n pair).
      if [ $((10#$n % 2)) -eq 1 ]; then reason="Destinataire absent"; else reason="Adresse incomplète"; fi
      scan "$ref" DELIVERY_FAILED "$dest" -28 "$reason" ;;
    retry)
      scan "$ref" PICKED_UP "$origin" -120
      scan "$ref" ARRIVED_AT_SITE "$hub" -96
      scan "$ref" OUT_FOR_DELIVERY "$dest" -50
      scan "$ref" DELIVERY_FAILED "$dest" -48 "Destinataire absent"
      scan "$ref" ARRIVED_AT_SITE "$dest" -24 ;;
    *) fail "Parcours inconnu : $course" ;;
  esac
  echo "OK  $ref $origin -> $dest ($course)"
done <<<"$SHIPMENTS"

printf '\nAmorçage terminé : %d créée(s), %d déjà existante(s).\n' "$created" "$skipped"
