FROM node:20-bookworm-slim AS theme-builder

RUN apt-get update \
    && apt-get install --yes --no-install-recommends maven openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /theme
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build-keycloak-theme

FROM quay.io/keycloak/keycloak:26.7.4 AS builder

# Build-time options khớp với lúc chạy prod ⇒ prod start --optimized, khỏi build lại Quarkus mỗi lần khởi động
# (tốn ~10s CPU và vài trăm MB RAM — đáng kể trên máy 1 GB). start-dev ở môi trường dev không bị ảnh hưởng.
ENV KC_DB=postgres KC_HEALTH_ENABLED=true
COPY --from=theme-builder --chown=keycloak:keycloak --chmod=644 /theme/dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar /opt/keycloak/providers/finance-theme.jar
RUN touch -m --date=@1743465600 /opt/keycloak/providers/finance-theme.jar
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.7.4
COPY --from=builder /opt/keycloak/ /opt/keycloak/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
