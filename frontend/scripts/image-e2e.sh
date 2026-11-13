#!/usr/bin/env bash
# Vérifie l'image du front : construction, `nginx -t`, puis e2e Playwright contre le conteneur.
# Les services sont remplacés par deux conteneurs qui renvoient leur nom, le chemin et l'en-tête Host reçus.
# Usage : depuis frontend/, `scripts/image-e2e.sh` ; port de l'image : IMAGE_PORT (défaut 8088).
set -euo pipefail
cd "$(dirname "$0")/.."

PORT="${IMAGE_PORT:-8088}"
SUFFIX="${IMAGE_E2E_SUFFIX:-$$}"
IMAGE="logistics-frontend:e2e-${SUFFIX}"
NETWORK="frontend-e2e-${SUFFIX}"
NODE_IMAGE="$(grep -m1 '^FROM node' Dockerfile | awk '{print $2}')"

cleanup() {
  docker rm -f "front-${SUFFIX}" "shipment-up-${SUFFIX}" "ingestion-up-${SUFFIX}" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

docker build -t "$IMAGE" .
docker network create "$NETWORK" >/dev/null

ECHO='require("http").createServer((q,r)=>{r.setHeader("content-type","application/json");r.end(JSON.stringify({upstream:process.env.NAME,path:q.url,host:q.headers.host}))}).listen(9000)'
for name in shipment ingestion; do
  docker run -d --name "${name}-up-${SUFFIX}" --network "$NETWORK" --network-alias "${name}-up" \
    -e NAME="$name" "$NODE_IMAGE" node -e "$ECHO" >/dev/null
done

ENVS=(-e SHIPMENT_SERVICE_URL=http://shipment-up:9000 -e INGESTION_SERVICE_URL=http://ingestion-up:9000)

echo "== nginx -t"
docker run --rm --network "$NETWORK" "${ENVS[@]}" "$IMAGE" nginx -t

docker run -d --name "front-${SUFFIX}" --network "$NETWORK" "${ENVS[@]}" -p "${PORT}:8080" "$IMAGE" >/dev/null
for _ in $(seq 1 30); do
  curl -fsS -o /dev/null "http://localhost:${PORT}/" && break
  sleep 1
done

echo "== e2e sur l'image"
IMAGE_URL="http://localhost:${PORT}" npx playwright test -c playwright.image.config.ts
