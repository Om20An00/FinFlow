FROM quay.io/keycloak/keycloak:24.0.5 AS builder
ENV KC_DB=dev-file KC_CACHE=local
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:24.0.5
COPY --from=builder /opt/keycloak/ /opt/keycloak/
ENV KC_DB=dev-file KC_CACHE=local
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]