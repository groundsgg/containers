FROM eclipse-temurin:25-jdk AS build

ARG GITHUB_USER
ARG MINESTOM_LOBBY_REF=main

WORKDIR /workspace

RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

RUN --mount=type=cache,target=/root/.gradle/caches,sharing=locked \
    --mount=type=cache,target=/root/.gradle/wrapper,sharing=locked \
    --mount=type=secret,id=github_token,required=true \
    set -eux; \
    : "${GITHUB_USER:?GITHUB_USER build arg is required}"; \
    token="$(cat /run/secrets/github_token)"; \
    git clone --depth 1 --branch "$MINESTOM_LOBBY_REF" \
      "https://${GITHUB_USER}:${token}@github.com/groundsgg/minestom-lobby.git" .; \
    rm -rf .git; \
    ./gradlew --no-daemon --stacktrace \
      -Pgithub.user="${GITHUB_USER}" \
      -Pgithub.token="${token}" \
      build -x test

FROM eclipse-temurin:25-jre-alpine

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION

LABEL org.opencontainers.image.title="minestom-lobby" \
      org.opencontainers.image.description="A small Docker image for running a Minestom lobby server." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION"

WORKDIR /app

RUN set -eux; \
    apk add --no-cache netcat-openbsd; \
    addgroup -S minestom; \
    adduser -S -G minestom -h /app minestom; \
    chown -R minestom:minestom /app

COPY --from=build --chown=minestom:minestom /workspace/build/libs/*.jar /app/minestom-lobby.jar

USER minestom

EXPOSE 25565

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD nc -z localhost 25565 || exit 1

ENTRYPOINT ["java", "-jar", "/app/minestom-lobby.jar", "--port", "25565"]
