FROM alpine:3.24 AS downloader

# Floodgate is what lets a single online-mode Velocity accept Bedrock players.
# Geyser authenticates them against Xbox Live and signs the player data with a
# shared key; Floodgate verifies that signature and admits the player with a
# UUID derived from their XUID, shaped 00000000-0000-0000-XXXX-XXXXXXXXXXXX so
# it can never collide with a real Java UUID.
#
# Pinned for the same reason Geyser is: the two speak a shared key format and
# player-data schema, so they move together.
ARG FLOODGATE_VERSION=2.2.5
ARG FLOODGATE_BUILD=140
ARG FLOODGATE_SHA256=f5867ad79b90d38abcc72755a685428fbcf423b52c9830a39ffed5203de6936a

RUN set -eux; \
    apk add --no-cache curl; \
    [ -n "$FLOODGATE_VERSION" ] || { echo "Floodgate version must be set" >&2; exit 1; }; \
    [ -n "$FLOODGATE_BUILD" ] || { echo "Floodgate build must be set" >&2; exit 1; }; \
    download_url="https://download.geysermc.org/v2/projects/floodgate/versions/${FLOODGATE_VERSION}/builds/${FLOODGATE_BUILD}/downloads/velocity"; \
    curl -fsSL -o /tmp/plugin.jar "$download_url"; \
    [ -s /tmp/plugin.jar ] || { echo "Downloaded Floodgate jar is empty (version=${FLOODGATE_VERSION}, build=${FLOODGATE_BUILD})" >&2; exit 1; }; \
    head -c 4 /tmp/plugin.jar | grep -q '^PK' || { echo "Downloaded Floodgate jar is not a valid JAR (version=${FLOODGATE_VERSION}, build=${FLOODGATE_BUILD})" >&2; exit 1; }; \
    echo "${FLOODGATE_SHA256}  /tmp/plugin.jar" | sha256sum -c -

FROM alpine:3.24

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG FLOODGATE_VERSION=2.2.5
ARG FLOODGATE_BUILD=140

LABEL org.opencontainers.image.title="plugin-floodgate" \
      org.opencontainers.image.description="Hosts the Floodgate Velocity JAR so the grounds-velocity proxy can fetch it at startup." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION" \
      gg.grounds.floodgate.version="$FLOODGATE_VERSION" \
      gg.grounds.floodgate.build="$FLOODGATE_BUILD"

# /jar/plugin.jar is the plugin-velocity-jar chart's contract: its init-container
# overrides the entrypoint and copies this path into the shared volume the httpd
# container serves. Nothing in this image is ever executed as written.
COPY --from=downloader --chmod=644 /tmp/plugin.jar /jar/plugin.jar
