# Image du front : build Angular puis nginx non root sur 8080.
# Écart assumé à Paketo (qui vaut pour les services Spring) : un front statique n'a pas de buildpack.
# Les images sont épinglées par digest ; pour les mettre à jour :
#   docker buildx imagetools inspect node:24-alpine
#   docker buildx imagetools inspect nginxinc/nginx-unprivileged:stable-alpine
# Contexte de build : ce dossier (`docker build -t logistics-frontend frontend`).

FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
# Le gabarit est rendu au démarrage par l'entrypoint de l'image (envsubst, limité aux URL des services et au résolveur) ;
# NGINX_LOCAL_RESOLVERS (adresses lues dans /etc/resolv.conf) alimente la directive `resolver`.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1 \
    NGINX_ENVSUBST_FILTER="_SERVICE_URL$|^NGINX_LOCAL_RESOLVERS$"
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 8080
