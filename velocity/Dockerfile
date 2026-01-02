FROM eclipse-temurin:25-jre-alpine

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG VELOCITY_VERSION=3.4.0-SNAPSHOT
ARG VELOCITY_BUILD=558
ARG PLUGINS="plugin-server-discovery:plugin-server-discovery-velocity:0.1.0"

LABEL org.opencontainers.image.title="velocity" \
      org.opencontainers.image.description="A small Docker image for running a Velocity proxy." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION"

WORKDIR /app

COPY --chmod=755 scripts/download-plugins.sh /app/download-plugins.sh
COPY --chown=velocity:velocity velocity/config/plugins /app/plugins

RUN --mount=type=secret,id=github_token \
    set -eux; \
    apk add --no-cache curl netcat-openbsd; \
    if [ -z "$VELOCITY_BUILD" ]; then \
      echo "Velocity build must be set" >&2; \
      exit 1; \
    fi; \
    curl -fsSL -o /app/velocity.jar \
      "https://api.papermc.io/v2/projects/velocity/versions/${VELOCITY_VERSION}/builds/${VELOCITY_BUILD}/downloads/velocity-${VELOCITY_VERSION}-${VELOCITY_BUILD}.jar"; \
    mkdir -p /app/plugins; \
    if [ -n "$PLUGINS" ]; then \
      PLUGINS="$PLUGINS" GITHUB_TOKEN="$(cat /run/secrets/github_token)" /app/download-plugins.sh; \
    fi; \
    rm -f /app/download-plugins.sh; \
    apk del curl; \
    addgroup -S velocity; \
    adduser -S -G velocity -h /app velocity; \
    chown -R velocity:velocity /app

COPY --chown=velocity:velocity --chmod=755 velocity/scripts/start.sh /app/start.sh
COPY --chown=velocity:velocity velocity/config/velocity.toml /app/velocity.toml

USER velocity

EXPOSE 25565

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD nc -z localhost 25565 || exit 1

CMD ["sh", "/app/start.sh"]
