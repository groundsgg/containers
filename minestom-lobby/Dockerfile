FROM eclipse-temurin:25-jre-alpine

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG MINESTOM_LOBBY_VERSION=0.1.0

LABEL org.opencontainers.image.title="minestom-lobby" \
      org.opencontainers.image.description="A small Docker image for running a Minestom lobby server." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION"

WORKDIR /app

RUN --mount=type=secret,id=github_token \
    set -eux; \
    apk add --no-cache curl netcat-openbsd; \
    curl -fsSL \
      -H "Authorization: Bearer $(cat /run/secrets/github_token)" \
      -o /app/minestom-lobby.jar \
      "https://maven.pkg.github.com/groundsgg/minestom-lobby/gg/grounds/minestom-lobby/${MINESTOM_LOBBY_VERSION}/minestom-lobby-${MINESTOM_LOBBY_VERSION}.jar"; \
    apk del curl; \
    addgroup -S minestom; \
    adduser -S -G minestom -h /app minestom; \
    chown -R minestom:minestom /app

USER minestom

EXPOSE 25565

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD nc -z localhost 25565 || exit 1

ENTRYPOINT ["java", "-jar", "/app/minestom-lobby.jar", "--port", "25565"]
