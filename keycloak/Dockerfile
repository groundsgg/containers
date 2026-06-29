FROM alpine:3.24 AS downloader

ARG KEYCLOAK_MINECRAFT_VERSION=1.1.0

RUN set -eux; \
    apk add --no-cache curl; \
    [ -n "$KEYCLOAK_MINECRAFT_VERSION" ] || { echo "Keycloak Minecraft IDP version must be set" >&2; exit 1; }; \
    artifact_url="https://github.com/groundsgg/keycloak-minecraft-idp/releases/download/v${KEYCLOAK_MINECRAFT_VERSION}/keycloak-minecraft-idp-${KEYCLOAK_MINECRAFT_VERSION}.jar"; \
    output_file="/tmp/provider.jar"; \
    curl -fsSL -o "$output_file" "$artifact_url"; \
    [ -s "$output_file" ] || { echo "Downloaded Keycloak Minecraft IDP provider is empty (version=${KEYCLOAK_MINECRAFT_VERSION})" >&2; exit 1; }; \
    head -c 4 "$output_file" | grep -q '^PK' || { echo "Downloaded Keycloak Minecraft IDP provider is not a valid JAR (version=${KEYCLOAK_MINECRAFT_VERSION})" >&2; exit 1; }

FROM quay.io/keycloak/keycloak:26.6.4 AS builder

WORKDIR /opt/keycloak

COPY --from=downloader --chmod=644 /tmp/provider.jar /opt/keycloak/providers/keycloak-minecraft-idp.jar

RUN /opt/keycloak/bin/kc.sh build \
      --db=postgres \
      --health-enabled=true \
      --metrics-enabled=true \
      --features=user-event-metrics \
      --event-metrics-user-enabled=true \
      --event-metrics-user-tags=realm,idp,clientId

FROM quay.io/keycloak/keycloak:26.6.4

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION

LABEL org.opencontainers.image.title="keycloak" \
      org.opencontainers.image.description="Custom Keycloak image with keycloak-minecraft-idp extension." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION"

COPY --from=builder /opt/keycloak/ /opt/keycloak/

EXPOSE 8080 8443

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/localhost/8080 && echo -e "GET /health/ready HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 && grep -q "UP" <&3' || exit 1

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
