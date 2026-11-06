#!/usr/bin/env bash
# Usage : scripts/generate-jwt-key.sh [--force | --stdout]
# Génère la clé privée RSA locale qui signe les JWT de shipment-service, dans .secrets/ (ignoré par git).
# scripts/env.sh la charge ensuite dans JWT_PRIVATE_KEY. En ligne, la clé vient d'un secret, jamais de ce fichier.
# --stdout : écrit une clé neuve sur la sortie standard, sans fichier, pour la passer directement à
# Secret Manager (setup-wizard.sh) ; la clé en ligne reste ainsi distincte de la clé locale.
set -euo pipefail

if [[ "${1:-}" == "--stdout" ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null
  exit 0
fi

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
