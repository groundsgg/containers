FROM quay.io/keycloak/keycloak:26.1 AS builder

ARG KEYCLOAK_MINECRAFT_VERSION=1.0.0

WORKDIR /opt/keycloak

ADD --chmod=644 "https://github.com/groundsgg/keycloak-minecraft/releases/download/v${KEYCLOAK_MINECRAFT_VERSION}/keycloak-minecraft.jar" /opt/keycloak/providers/keycloak-minecraft.jar

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.1

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION

LABEL org.opencontainers.image.title="keycloak" \
      org.opencontainers.image.description="Custom Keycloak image with keycloak-minecraft extension." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION"

COPY --from=builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
