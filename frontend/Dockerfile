# syntax=docker/dockerfile:1

FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npx ng build --configuration production

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist/just-fast-web/browser /usr/share/nginx/html
# API_UPSTREAM is the Spring Boot service the /api location proxies to.
ENV API_UPSTREAM=http://backend:8080
EXPOSE 8080
