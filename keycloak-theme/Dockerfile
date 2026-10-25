FROM quay.io/keycloak/keycloak:26.7.4 AS builder

COPY --chown=keycloak:keycloak --chmod=644 dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar /opt/keycloak/providers/finance-theme.jar
RUN touch -m --date=@1743465600 /opt/keycloak/providers/finance-theme.jar
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.7.4
COPY --from=builder /opt/keycloak/ /opt/keycloak/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
