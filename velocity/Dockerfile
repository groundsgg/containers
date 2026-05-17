FROM eclipse-temurin:25-jre-alpine

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG VELOCITY_VERSION=3.4.0-SNAPSHOT
ARG VELOCITY_BUILD=558
ARG PLUGINS="plugin-agones:plugin-agones-velocity:0.5.0"
ARG GROUNDS_RUNTIME_PLUGIN_VERSION="0.1.0"

LABEL org.opencontainers.image.title="velocity" \
      org.opencontainers.image.description="A small Docker image for running a Velocity proxy." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION" \
      gg.grounds.runtime.manifest.path="/opt/grounds/runtime-manifest.json"

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
    mkdir -p /opt/grounds; \
    grounds_runtime_url="https://github.com/groundsgg/plugin-grounds-runtime/releases/download/v${GROUNDS_RUNTIME_PLUGIN_VERSION}/plugin-grounds-runtime-velocity-${GROUNDS_RUNTIME_PLUGIN_VERSION}.jar"; \
    curl -fsSL -o /app/plugins/plugin-grounds-runtime.jar "$grounds_runtime_url"; \
    printf '%s\n' \
      '{' \
      '  "schemaVersion": 1,' \
      '  "runtime": {' \
      '    "id": "plugin-grounds-runtime-velocity",' \
      '    "name": "plugin-grounds-runtime",' \
      "    \"version\": \"${GROUNDS_RUNTIME_PLUGIN_VERSION}\"," \
      '    "platform": "velocity"' \
      '  },' \
      '  "libraries": [' \
      '    {"group": "org.jetbrains.kotlin", "name": "kotlin-stdlib", "version": "2.3.0"},' \
      '    {"group": "org.jetbrains.kotlin", "name": "kotlin-stdlib-jdk8", "version": "2.3.0"},' \
      '    {"group": "org.jetbrains.kotlinx", "name": "kotlinx-coroutines-core", "version": "1.10.2"},' \
      '    {"group": "com.google.protobuf", "name": "protobuf-java", "version": "4.34.1"},' \
      '    {"group": "io.grpc", "name": "grpc-api", "version": "1.81.0"},' \
      '    {"group": "io.grpc", "name": "grpc-core", "version": "1.81.0"},' \
      '    {"group": "io.grpc", "name": "grpc-context", "version": "1.81.0"},' \
      '    {"group": "io.grpc", "name": "grpc-stub", "version": "1.81.0"},' \
      '    {"group": "io.grpc", "name": "grpc-protobuf", "version": "1.81.0"},' \
      '    {"group": "io.grpc", "name": "grpc-netty-shaded", "version": "1.81.0"}' \
      '  ]' \
      '}' > /opt/grounds/runtime-manifest.json; \
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
