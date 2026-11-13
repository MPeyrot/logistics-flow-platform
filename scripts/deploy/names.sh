# Noms des ressources GCP de la démo, lus par infra.sh et setup-wizard.sh (à sourcer, pas à lancer).
# Les workflows up / down (#67) reprennent les mêmes noms par les variables du dépôt GitHub
# posées par l'assistant. Changer un nom ici après un premier infra.sh crée une nouvelle ressource
# sans supprimer l'ancienne.
# shellcheck shell=bash disable=SC2034

REGION="${REGION:-europe-west1}"
GITHUB_REPO="MPeyrot/logistics-flow-platform"

# Services Cloud Run (même nom que le dossier services/<nom>)
SERVICES="shipment-service ingestion-service ai-service"
# Front (#90) : nginx qui sert l'interface et renvoie /api vers les services ci-dessus. Image par Dockerfile
# (frontend/Dockerfile), sans secret : il n'a donc pas de ligne dans service_secrets.
FRONTEND_SERVICE="frontend"

# Artifact Registry : europe-west1-docker.pkg.dev/<projet>/logistics/<service>:<sha> (le front aussi : .../frontend:<sha>)
AR_REPOSITORY="logistics"
AR_KEEP_IMAGES=2

# Comptes de service : un d'exécution par service, un de déploiement pour GitHub Actions
runtime_sa_name() { printf '%s-run' "$1"; }   # shipment-service-run, ingestion-service-run, ai-service-run, frontend-run
DEPLOY_SA_NAME="github-deployer"

# Workload Identity Federation (GitHub OIDC)
WIF_POOL="github"
WIF_PROVIDER="github-oidc"

# Secrets de chaque service. Le nom du secret n'est pas toujours celui de la variable d'environnement :
# chaque service lit sa paire Confluent sous KAFKA_API_KEY / KAFKA_API_SECRET (mise en correspondance
# par le workflow up, --set-secrets KAFKA_API_KEY=SHIPMENT_KAFKA_API_KEY:latest).
service_secrets() {
  case "$1" in
    shipment-service)  echo "JWT_PRIVATE_KEY BOOTSTRAP_ADMIN_PASSWORD DB_URL DB_USER DB_PASSWORD SHIPMENT_KAFKA_API_KEY SHIPMENT_KAFKA_API_SECRET" ;;
    ingestion-service) echo "INGESTION_KAFKA_API_KEY INGESTION_KAFKA_API_SECRET" ;;
    ai-service)        echo "ANTHROPIC_API_KEY" ;;
    *) echo "service inconnu : $1" >&2; return 1 ;;
  esac
}

# Métrique basée sur les journaux : coût des extractions d'ai-service (#58)
COST_METRIC="ai-claude-cost"

# Variables du dépôt GitHub (non secrètes) posées par setup-wizard.sh, lues par les workflows :
#   GCP_PROJECT_ID, GCP_PROJECT_NUMBER, GCP_REGION, GCP_AR_REPOSITORY,
#   GCP_WIF_PROVIDER, GCP_DEPLOY_SA, KAFKA_BOOTSTRAP_SERVERS, BOOTSTRAP_ADMIN_USERNAME
# Secret du dépôt GitHub : BOOTSTRAP_ADMIN_PASSWORD (le parcours de fumée se connecte en ADMIN).
