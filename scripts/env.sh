# Usage : source scripts/env.sh
# Fixe JAVA_HOME sur openjdk@21 pour ce shell (le JDK par défaut de la machine est plus récent).
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# Clé de signature des JWT de shipment-service, générée par scripts/generate-jwt-key.sh (fichier ignoré par git).
_jwt_key="$(git rev-parse --show-toplevel 2>/dev/null || pwd)/.secrets/jwt-private-key.pem"
if [ -f "$_jwt_key" ]; then
  export JWT_PRIVATE_KEY="$(cat "$_jwt_key")"
else
  echo "env.sh : pas de clé JWT locale, shipment-service ne démarrera pas. Lancer scripts/generate-jwt-key.sh." >&2
fi
unset _jwt_key
