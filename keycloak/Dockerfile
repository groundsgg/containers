FROM alpine:3.24 AS downloader

ARG KEYCLOAK_MINECRAFT_VERSION=1.1.4
ARG KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION=0.2.2
ARG KEYCLOAK_PERMISSIONS_EVENT_LISTENER_SHA256=955b34541541562e77e81dcacb60f12b681bd22204c4f3cf4cde8ae8248cd034

RUN set -eux; \
    apk add --no-cache curl unzip; \
    [ -n "$KEYCLOAK_MINECRAFT_VERSION" ] || { echo "Keycloak Minecraft IDP version must be set" >&2; exit 1; }; \
    minecraft_artifact_url="https://github.com/groundsgg/keycloak-minecraft-idp/releases/download/v${KEYCLOAK_MINECRAFT_VERSION}/keycloak-minecraft-idp-${KEYCLOAK_MINECRAFT_VERSION}.jar"; \
    minecraft_output_file="/tmp/keycloak-minecraft-idp.jar"; \
    curl -fsSL -o "$minecraft_output_file" "$minecraft_artifact_url"; \
    [ -s "$minecraft_output_file" ] || { echo "Downloaded Keycloak Minecraft IDP provider is empty (version=${KEYCLOAK_MINECRAFT_VERSION})" >&2; exit 1; }; \
    head -c 4 "$minecraft_output_file" | grep -q '^PK' || { echo "Downloaded Keycloak Minecraft IDP provider is not a valid JAR (version=${KEYCLOAK_MINECRAFT_VERSION})" >&2; exit 1; }; \
    [ -n "$KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION" ] || { echo "Keycloak permissions event listener validation failed (reason=version_not_set)" >&2; exit 1; }; \
    [ -n "$KEYCLOAK_PERMISSIONS_EVENT_LISTENER_SHA256" ] || { echo "Keycloak permissions event listener validation failed (reason=checksum_not_set)" >&2; exit 1; }; \
    permissions_event_listener_output_file="/tmp/keycloak-permissions-event-listener.jar"; \
    permissions_event_listener_artifact_url="https://github.com/groundsgg/keycloak-permissions-event-listener/releases/download/v${KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION}/keycloak-permissions-event-listener-${KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION}.jar"; \
    curl -fsSL -o "$permissions_event_listener_output_file" "$permissions_event_listener_artifact_url"; \
    [ -s "$permissions_event_listener_output_file" ] || { echo "Keycloak permissions event listener download failed (version=${KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION}, reason=empty_artifact)" >&2; exit 1; }; \
    echo "$KEYCLOAK_PERMISSIONS_EVENT_LISTENER_SHA256  $permissions_event_listener_output_file" | sha256sum -c -; \
    head -c 4 "$permissions_event_listener_output_file" | grep -q '^PK' || { echo "Keycloak permissions event listener validation failed (version=${KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION}, reason=invalid_jar)" >&2; exit 1; }; \
    unzip -p "$permissions_event_listener_output_file" META-INF/services/org.keycloak.events.EventListenerProviderFactory \
      | grep -Fxq 'gg.grounds.keycloak.permissions.events.PermissionsEventListenerProviderFactory' \
      || { echo "Keycloak permissions event listener validation failed (version=${KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION}, reason=provider_factory_not_registered)" >&2; exit 1; }

FROM quay.io/keycloak/keycloak:26.7.1 AS builder

WORKDIR /opt/keycloak

COPY --from=downloader --chmod=644 /tmp/keycloak-minecraft-idp.jar /opt/keycloak/providers/keycloak-minecraft-idp.jar
COPY --from=downloader --chmod=644 /tmp/keycloak-permissions-event-listener.jar /opt/keycloak/providers/keycloak-permissions-event-listener.jar

RUN /opt/keycloak/bin/kc.sh build \
      --db=postgres \
      --health-enabled=true \
      --metrics-enabled=true \
      --features=user-event-metrics \
      --event-metrics-user-enabled=true \
      --event-metrics-user-tags=realm,idp,clientId

FROM quay.io/keycloak/keycloak:26.7.1

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG KEYCLOAK_MINECRAFT_VERSION=1.1.2
ARG KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION=0.2.1

LABEL org.opencontainers.image.title="keycloak" \
      org.opencontainers.image.description="Custom Keycloak image with keycloak-minecraft-idp and keycloak-permissions-event-listener extensions." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION" \
      gg.grounds.keycloak-minecraft-idp.version="$KEYCLOAK_MINECRAFT_VERSION" \
      gg.grounds.keycloak-permissions-event-listener.version="$KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION"

COPY --from=builder /opt/keycloak/ /opt/keycloak/

EXPOSE 8080 8443

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/localhost/8080 && echo -e "GET /health/ready HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 && grep -q "UP" <&3' || exit 1

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
