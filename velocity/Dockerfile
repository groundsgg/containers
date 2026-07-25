FROM eclipse-temurin:25-jre-alpine

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG VELOCITY_VERSION=3.5.1
ARG VELOCITY_BUILD=615
ARG VELOCITY_DOWNLOAD_USER_AGENT="grounds-containers/1.0 (https://github.com/groundsgg/containers)"
# plugin-agones 0.7.2 is what makes /agones report the *network* player count.
# Before it, the command read Velocity's playersConnected — the players on the
# proxy running the command — so with two proxies in front of one lobby each of
# them confidently reported half of it.
ARG PLUGINS="plugin-agones:plugin-agones-velocity:0.7.2,plugin-permissions:plugin-permissions-velocity:0.6.0"
ARG GROUNDS_RUNTIME_PLUGIN_VERSION="0.1.1"
# grounds-platform velocity build: proxy-side whitelist gate, MOTD, and
# platform command execution. Gated on the arg so it can be disabled.
ARG GROUNDS_PLATFORM_PLUGIN_VERSION="0.6.0"

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
    set -eu; \
    apk add --no-cache curl jq netcat-openbsd; \
    if [ -z "$VELOCITY_BUILD" ]; then \
      echo "Velocity build must be set" >&2; \
      exit 1; \
    fi; \
    builds_api_url="https://fill.papermc.io/v3/projects/velocity/versions/${VELOCITY_VERSION}/builds"; \
    builds_response="$(curl -fsSL -H "User-Agent: ${VELOCITY_DOWNLOAD_USER_AGENT}" "$builds_api_url")"; \
    download_url="$(echo "$builds_response" | jq -r --argjson build "${VELOCITY_BUILD}" 'first(.[] | select(.id == $build or .number == $build) | .downloads["server:default"].url) // empty')"; \
    [ -n "$download_url" ] || { echo "Failed to resolve Velocity download URL (version=${VELOCITY_VERSION}, build=${VELOCITY_BUILD})" >&2; exit 1; }; \
    curl -fsSL -o /app/velocity.jar \
      -H "User-Agent: ${VELOCITY_DOWNLOAD_USER_AGENT}" \
      "$download_url"; \
    mkdir -p /app/plugins; \
    mkdir -p /opt/grounds; \
    github_token="$(cat /run/secrets/github_token)"; \
    grounds_runtime_repository_url="https://maven.pkg.github.com/groundsgg/plugin-grounds-runtime/gg/grounds"; \
    grounds_runtime_artifact_id="plugin-grounds-runtime-velocity"; \
    grounds_runtime_url="${grounds_runtime_repository_url}/${grounds_runtime_artifact_id}/${GROUNDS_RUNTIME_PLUGIN_VERSION}/${grounds_runtime_artifact_id}-${GROUNDS_RUNTIME_PLUGIN_VERSION}.jar"; \
    grounds_runtime_catalog_url="${grounds_runtime_repository_url}/grounds-runtime-catalog/${GROUNDS_RUNTIME_PLUGIN_VERSION}/grounds-runtime-catalog-${GROUNDS_RUNTIME_PLUGIN_VERSION}.json"; \
    curl -fsSL -H "Authorization: Bearer ${github_token}" -o /app/plugins/plugin-grounds-runtime.jar "$grounds_runtime_url"; \
    curl -fsSL -H "Authorization: Bearer ${github_token}" -o /tmp/grounds-runtime-libraries.json "$grounds_runtime_catalog_url"; \
    jq \
      --arg version "$GROUNDS_RUNTIME_PLUGIN_VERSION" \
      'if (.libraries | type) == "array" then {schemaVersion: 1, runtime: {id: "plugin-grounds-runtime-velocity", name: "plugin-grounds-runtime", version: $version, platform: "velocity"}, libraries: .libraries} else error("runtime catalog libraries must be an array") end' \
      /tmp/grounds-runtime-libraries.json > /opt/grounds/runtime-manifest.json; \
    if [ -n "$PLUGINS" ]; then \
      PLUGINS="$PLUGINS" GITHUB_TOKEN="$github_token" /app/download-plugins.sh; \
    fi; \
    if [ -n "$GROUNDS_PLATFORM_PLUGIN_VERSION" ]; then \
      grounds_platform_url="https://github.com/groundsgg/plugin-grounds-platform/releases/download/v${GROUNDS_PLATFORM_PLUGIN_VERSION}/plugin-grounds-platform-velocity-${GROUNDS_PLATFORM_PLUGIN_VERSION}-all.jar"; \
      echo "Downloading grounds-platform velocity plugin (${GROUNDS_PLATFORM_PLUGIN_VERSION})..."; \
      curl -fsSL -o /app/plugins/plugin-grounds-platform.jar "$grounds_platform_url"; \
    fi; \
    rm -f /app/download-plugins.sh /tmp/grounds-runtime-libraries.json; \
    apk del curl jq; \
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
