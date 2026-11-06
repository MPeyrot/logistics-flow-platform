#!/usr/bin/env bash
# Usage : PROJECT_ID=<projet> [REGION=europe-west1] scripts/deploy/infra.sh
#
# Crée dans le projet GCP tout ce qu'il faut avant un premier déploiement (workflow up) :
# API, dépôt Artifact Registry et sa règle de nettoyage, comptes de service et droits,
# conteneurs de secrets (sans valeur), Workload Identity Federation pour GitHub Actions,
# métrique de coût d'ai-service.
#
# Idempotent : crée ce qui manque, ne supprime rien, relançable sans erreur. Ne pose aucune
# valeur de secret (c'est l'assistant, setup-wizard.sh). Noms des ressources : names.sh.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source-path=SCRIPTDIR source=names.sh
source "$here/names.sh"

PROJECT_ID="${PROJECT_ID:?PROJECT_ID manquant : PROJECT_ID=<projet> scripts/deploy/infra.sh}"
export CLOUDSDK_CORE_PROJECT="$PROJECT_ID"   # toutes les commandes gcloud visent ce projet, sans toucher à la configuration
export CLOUDSDK_CORE_DISABLE_PROMPTS=1

APIS="run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com
iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com
cloudresourcemanager.googleapis.com logging.googleapis.com monitoring.googleapis.com"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

log()  { printf '\n▸ %s\n' "$*"; }
ok()   { printf '  ✓ %s\n' "$*"; }
made() { printf '  + %s\n' "$*"; }

# exists CMD… : vrai si la commande gcloud (un describe) réussit.
exists() { "$@" >/dev/null 2>&1; }

# retry CMD… : les droits IAM sur un compte de service tout juste créé échouent parfois
# quelques secondes (« does not exist »), le temps que la création se propage.
retry() {
  local n=1 err
  until err="$("$@" 2>&1 >/dev/null)"; do
    if (( n >= 6 )); then printf 'échec après %s tentatives : %s\n%s\n' "$n" "$*" "$err" >&2; return 1; fi
    sleep $(( n * 5 )); n=$(( n + 1 ))
  done
}

sa_email() { printf '%s@%s.iam.gserviceaccount.com' "$1" "$PROJECT_ID"; }

ensure_sa() {
  local name="$1" display="$2"
  if exists gcloud iam service-accounts describe "$(sa_email "$name")"; then
    ok "compte de service $name"
  else
    gcloud iam service-accounts create "$name" --display-name="$display" >/dev/null
    made "compte de service $name"
  fi
}

project_binding() {
  retry gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="$1" --role="$2" --condition=None --quiet
}

# ── Projet ────────────────────────────────────────────────────────────────
log "Projet $PROJECT_ID ($REGION)"
gcloud auth print-access-token >/dev/null 2>&1 || { echo "gcloud n'est pas connecté : gcloud auth login" >&2; exit 1; }
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
ok "numéro $PROJECT_NUMBER"

# ── API ───────────────────────────────────────────────────────────────────
log "API"
# shellcheck disable=SC2086 # découpage voulu de la liste
gcloud services enable $APIS --quiet
ok "API activées (liste APIS en tête du script)"

# ── Artifact Registry ─────────────────────────────────────────────────────
log "Artifact Registry"
if exists gcloud artifacts repositories describe "$AR_REPOSITORY" --location="$REGION"; then
  ok "dépôt $AR_REPOSITORY"
else
  gcloud artifacts repositories create "$AR_REPOSITORY" --repository-format=docker \
    --location="$REGION" --description="Images des services de la démo" >/dev/null
  made "dépôt $AR_REPOSITORY"
fi
# Chaque image de service est un paquet : on garde ses $AR_KEEP_IMAGES dernières versions, le reste
# est supprimé (règle « Keep » prioritaire sur « Delete »). Réappliquée à chaque passage, même contenu.
cat > "$tmp/cleanup.json" <<JSON
[
  {"name": "garder-les-dernieres", "action": {"type": "Keep"},
   "mostRecentVersions": {"keepCount": $AR_KEEP_IMAGES}},
  {"name": "supprimer-le-reste", "action": {"type": "Delete"},
   "condition": {"tagState": "any"}}
]
JSON
gcloud artifacts repositories set-cleanup-policies "$AR_REPOSITORY" --location="$REGION" \
  --policy="$tmp/cleanup.json" --no-dry-run >/dev/null
ok "règle de nettoyage : $AR_KEEP_IMAGES dernières images par service"

# ── Comptes d'exécution et secrets ────────────────────────────────────────
log "Comptes d'exécution et secrets"
for service in $SERVICES; do
  sa="$(runtime_sa_name "$service")"
  ensure_sa "$sa" "Exécution de $service (Cloud Run)"
  project_binding "serviceAccount:$(sa_email "$sa")" roles/logging.logWriter
  for secret in $(service_secrets "$service"); do
    if exists gcloud secrets describe "$secret"; then
      ok "secret $secret"
    else
      gcloud secrets create "$secret" --replication-policy=user-managed --locations="$REGION" \
        --labels="service=$service" >/dev/null
      made "secret $secret (sans valeur)"
    fi
    # droit d'accès au niveau du secret, pas du projet : chaque service ne lit que les siens
    retry gcloud secrets add-iam-policy-binding "$secret" \
      --member="serviceAccount:$(sa_email "$sa")" --role=roles/secretmanager.secretAccessor
  done
  ok "$sa : logging.logWriter, secretAccessor sur $(service_secrets "$service" | wc -w | tr -d ' ') secret(s)"
done

# ── Compte de déploiement ─────────────────────────────────────────────────
log "Compte de déploiement (GitHub Actions)"
ensure_sa "$DEPLOY_SA_NAME" "Déploiement depuis GitHub Actions"
deployer="serviceAccount:$(sa_email "$DEPLOY_SA_NAME")"
project_binding "$deployer" roles/run.admin
retry gcloud artifacts repositories add-iam-policy-binding "$AR_REPOSITORY" --location="$REGION" \
  --member="$deployer" --role=roles/artifactregistry.writer
for service in $SERVICES; do
  retry gcloud iam service-accounts add-iam-policy-binding "$(sa_email "$(runtime_sa_name "$service")")" \
    --member="$deployer" --role=roles/iam.serviceAccountUser
done
ok "$DEPLOY_SA_NAME : run.admin, artifactregistry.writer sur $AR_REPOSITORY, serviceAccountUser sur les 3 comptes d'exécution"

# ── Workload Identity Federation ──────────────────────────────────────────
log "Workload Identity Federation"
if exists gcloud iam workload-identity-pools describe "$WIF_POOL" --location=global; then
  ok "pool $WIF_POOL"
else
  gcloud iam workload-identity-pools create "$WIF_POOL" --location=global \
    --display-name="GitHub Actions" >/dev/null
  made "pool $WIF_POOL"
fi
# Seuls les jetons OIDC émis pour ce dépôt sont acceptés. Réappliqué s'il existe déjà, pour que la
# condition reste celle-ci.
provider_flags=(
  --location=global --workload-identity-pool="$WIF_POOL"
  --display-name="GitHub OIDC"
  --issuer-uri="https://token.actions.githubusercontent.com"
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref"
  --attribute-condition="assertion.repository=='$GITHUB_REPO'"
)
if exists gcloud iam workload-identity-pools providers describe "$WIF_PROVIDER" \
    --location=global --workload-identity-pool="$WIF_POOL"; then
  gcloud iam workload-identity-pools providers update-oidc "$WIF_PROVIDER" "${provider_flags[@]}" >/dev/null
  ok "fournisseur $WIF_PROVIDER (limité à $GITHUB_REPO)"
else
  gcloud iam workload-identity-pools providers create-oidc "$WIF_PROVIDER" "${provider_flags[@]}" >/dev/null
  made "fournisseur $WIF_PROVIDER (limité à $GITHUB_REPO)"
fi
pool_path="projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$WIF_POOL"
retry gcloud iam service-accounts add-iam-policy-binding "$(sa_email "$DEPLOY_SA_NAME")" \
  --member="principalSet://iam.googleapis.com/$pool_path/attribute.repository/$GITHUB_REPO" \
  --role=roles/iam.workloadIdentityUser
ok "$GITHUB_REPO peut agir comme $DEPLOY_SA_NAME"

# ── Métrique de coût (#58) ────────────────────────────────────────────────
log "Métrique basée sur les journaux"
# Lignes d'extraction facturée d'ai-service (profil gcp, #64) : costUsd, model et outcome en champs JSON.
# Seaux exponentiels de 0,0001 $ à ~52 $.
cat > "$tmp/metric.yaml" <<YAML
description: Coût en dollars de chaque appel facturé à l'API Claude par ai-service (extraction ou bon illisible)
filter: >-
  resource.type="cloud_run_revision"
  AND resource.labels.service_name="ai-service"
  AND jsonPayload.costUsd:*
valueExtractor: EXTRACT(jsonPayload.costUsd)
labelExtractors:
  model: EXTRACT(jsonPayload.model)
  outcome: EXTRACT(jsonPayload.outcome)
metricDescriptor:
  metricKind: DELTA
  valueType: DISTRIBUTION
  unit: "{USD}"
  labels:
    - key: model
      valueType: STRING
      description: Modèle Claude appelé
    - key: outcome
      valueType: STRING
      description: extracted ou unreadable
bucketOptions:
  exponentialBuckets:
    numFiniteBuckets: 20
    growthFactor: 2
    scale: 0.0001
YAML
if exists gcloud logging metrics describe "$COST_METRIC"; then
  ok "métrique $COST_METRIC"
else
  gcloud logging metrics create "$COST_METRIC" --config-from-file="$tmp/metric.yaml" >/dev/null
  made "métrique $COST_METRIC (logging.googleapis.com/user/$COST_METRIC)"
fi

# ── Résumé ────────────────────────────────────────────────────────────────
cat <<EOF

✓ Infrastructure prête. Variables du dépôt GitHub (posées par setup-wizard.sh) :
  GCP_PROJECT_ID=$PROJECT_ID
  GCP_PROJECT_NUMBER=$PROJECT_NUMBER
  GCP_REGION=$REGION
  GCP_AR_REPOSITORY=$AR_REPOSITORY
  GCP_WIF_PROVIDER=$pool_path/providers/$WIF_PROVIDER
  GCP_DEPLOY_SA=$(sa_email "$DEPLOY_SA_NAME")
EOF
