FROM eclipse-temurin:25-jre-alpine

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG PAPER_VERSION=1.21.11
ARG PAPER_BUILD=54
ARG PLUGINS="plugin-server-discovery:plugin-server-discovery-paper:0.1.0"

LABEL org.opencontainers.image.title="paper" \
      org.opencontainers.image.description="A small Docker image for running a Paper Minecraft server." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION"

WORKDIR /app

COPY --chmod=755 scripts/download-plugins.sh /app/download-plugins.sh

RUN --mount=type=secret,id=github_token \
    set -eux; \
    apk add --no-cache curl netcat-openbsd; \
    [ -n "$PAPER_VERSION" ] || { echo "Paper version must be set" >&2; exit 1; }; \
    [ -n "$PAPER_BUILD" ] || { echo "Paper build must be set" >&2; exit 1; }; \
    curl -fsSL -o /app/paper.jar \
      "https://api.papermc.io/v2/projects/paper/versions/${PAPER_VERSION}/builds/${PAPER_BUILD}/downloads/paper-${PAPER_VERSION}-${PAPER_BUILD}.jar"; \
    mkdir -p /app/plugins; \
    if [ -n "$PLUGINS" ]; then \
      if [ -f /run/secrets/github_token ]; then \
        PLUGINS="$PLUGINS" GITHUB_TOKEN="$(cat /run/secrets/github_token)" /app/download-plugins.sh; \
      else \
        PLUGINS="$PLUGINS" /app/download-plugins.sh; \
      fi; \
    fi; \
    apk del curl; \
    addgroup -S paper; \
    adduser -S -G paper -h /app paper; \
    chown -R paper:paper /app

COPY --chown=paper:paper --chmod=755 scripts/start.sh /app/start.sh
COPY --chown=paper:paper config/paper-global.yml /app/config/paper-global.yml
COPY --chown=paper:paper config/server.properties /app/server.properties

USER paper

EXPOSE 25565

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD nc -z localhost 25565 || exit 1

CMD ["sh", "/app/start.sh"]
