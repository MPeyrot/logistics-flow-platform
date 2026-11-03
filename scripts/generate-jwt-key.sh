#!/usr/bin/env bash
# Usage : scripts/generate-jwt-key.sh [--force]
# Génère la clé privée RSA locale qui signe les JWT de shipment-service, dans .secrets/ (ignoré par git).
# scripts/env.sh la charge ensuite dans JWT_PRIVATE_KEY. En ligne, la clé vient d'un secret, jamais de ce fichier.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
file="$root/.secrets/jwt-private-key.pem"

if [[ -f "$file" && "${1:-}" != "--force" ]]; then
  echo "Clé déjà présente : $file (--force pour la remplacer ; les jetons déjà émis deviendront invalides)."
  exit 0
fi

mkdir -p "$root/.secrets"
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$file" 2>/dev/null
echo "Clé écrite dans $file. Lancer : source scripts/env.sh"
