FROM eclipse-temurin:25-jre-alpine

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG PAPER_VERSION=26.1.2
ARG PAPER_BUILD=63
ARG PAPER_DOWNLOAD_USER_AGENT="grounds-containers/1.0 (https://github.com/groundsgg/containers)"
# Empty by default. The paper baseImage is used exclusively by
# `type: plugin-paper` workloads, which forge renders as plain
# Deployments — never as Agones GameServers. The Agones SDK sidecar
# is only injected on GameServer-managed pods, so plugin-agones-paper
# in this image would be permanently inert (no sidecar to talk to,
# ever) and just adds boot-time noise on every pod. If a future
# `type: gamemode` baseImage based on Paper lands, plugin-agones-paper
# belongs in that image — not this one.
ARG PLUGINS=""
ARG GROUNDS_RUNTIME_PLUGIN_VERSION="0.1.1"
# Project-aware MOTD + whitelist sync against grounds-forge. Distributed
# as a public GitHub Release asset rather than GitHub Packages Maven, so
# we fetch it directly with curl below. When the plugin starts publishing
# to GHCR Maven this argument can move into the PLUGINS list and the
# extra curl step can disappear.
ARG GROUNDS_PLATFORM_PLUGIN_VERSION="0.3.0"

LABEL org.opencontainers.image.title="paper" \
      org.opencontainers.image.description="A small Docker image for running a Paper Minecraft server." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION" \
      gg.grounds.runtime.manifest.path="/opt/grounds/runtime-manifest.json"

WORKDIR /app

COPY --chmod=755 scripts/download-plugins.sh /app/download-plugins.sh

RUN --mount=type=secret,id=github_token \
    set -eux; \
    apk add --no-cache curl jq netcat-openbsd; \
    [ -n "$PAPER_VERSION" ] || { echo "Paper version must be set" >&2; exit 1; }; \
    [ -n "$PAPER_BUILD" ] || { echo "Paper build must be set" >&2; exit 1; }; \
    builds_api_url="https://fill.papermc.io/v3/projects/paper/versions/${PAPER_VERSION}/builds"; \
    builds_response="$(curl -fsSL -H "User-Agent: ${PAPER_DOWNLOAD_USER_AGENT}" "$builds_api_url")"; \
    if echo "$builds_response" | jq -e '.ok == false' > /dev/null 2>&1; then \
      error_message="$(echo "$builds_response" | jq -r '.message // "Unknown error"')"; \
      echo "Failed to fetch Paper builds (version=${PAPER_VERSION}, reason=${error_message})" >&2; \
      exit 1; \
    fi; \
    download_url="$(echo "$builds_response" | jq -r --argjson build "${PAPER_BUILD}" 'first(.[] | select(.channel == "STABLE" and (.id == $build or .number == $build)) | .downloads["server:default"].url) // empty')"; \
    [ -n "$download_url" ] || { echo "Failed to resolve Paper download URL (version=${PAPER_VERSION}, build=${PAPER_BUILD})" >&2; exit 1; }; \
    curl -fsSL -o /app/paper.jar \
      -H "User-Agent: ${PAPER_DOWNLOAD_USER_AGENT}" \
      "$download_url"; \
    mkdir -p /app/plugins; \
    mkdir -p /opt/grounds; \
    github_token="$(cat /run/secrets/github_token)"; \
    grounds_runtime_repository_url="https://maven.pkg.github.com/groundsgg/plugin-grounds-runtime/gg/grounds"; \
    grounds_runtime_artifact_id="plugin-grounds-runtime-paper"; \
    grounds_runtime_url="${grounds_runtime_repository_url}/${grounds_runtime_artifact_id}/${GROUNDS_RUNTIME_PLUGIN_VERSION}/${grounds_runtime_artifact_id}-${GROUNDS_RUNTIME_PLUGIN_VERSION}.jar"; \
    grounds_runtime_catalog_url="${grounds_runtime_repository_url}/grounds-runtime-catalog/${GROUNDS_RUNTIME_PLUGIN_VERSION}/grounds-runtime-catalog-${GROUNDS_RUNTIME_PLUGIN_VERSION}.json"; \
    curl -fsSL -H "Authorization: Bearer ${github_token}" -o /app/plugins/plugin-grounds-runtime.jar "$grounds_runtime_url"; \
    curl -fsSL -H "Authorization: Bearer ${github_token}" -o /tmp/grounds-runtime-libraries.json "$grounds_runtime_catalog_url"; \
    jq \
      --arg version "$GROUNDS_RUNTIME_PLUGIN_VERSION" \
      'if (.libraries | type) == "array" then {schemaVersion: 1, runtime: {id: "plugin-grounds-runtime-paper", name: "plugin-grounds-runtime", version: $version, platform: "paper"}, libraries: .libraries} else error("runtime catalog libraries must be an array") end' \
      /tmp/grounds-runtime-libraries.json > /opt/grounds/runtime-manifest.json; \
    if [ -n "$PLUGINS" ]; then \
      PLUGINS="$PLUGINS" GITHUB_TOKEN="$github_token" /app/download-plugins.sh; \
    fi; \
    if [ -n "$GROUNDS_PLATFORM_PLUGIN_VERSION" ]; then \
      grounds_platform_url="https://github.com/groundsgg/plugin-grounds-platform/releases/download/v${GROUNDS_PLATFORM_PLUGIN_VERSION}/plugin-grounds-platform-${GROUNDS_PLATFORM_PLUGIN_VERSION}-all.jar"; \
      echo "Downloading grounds-platform plugin (${GROUNDS_PLATFORM_PLUGIN_VERSION})..."; \
      curl -fsSL -o /app/plugins/plugin-grounds-platform.jar "$grounds_platform_url"; \
    fi; \
    rm -f /app/download-plugins.sh /tmp/grounds-runtime-libraries.json; \
    apk del curl jq; \
    addgroup -S paper; \
    adduser -S -G paper -h /app paper; \
    chown -R paper:paper /app

COPY --chown=paper:paper --chmod=755 paper/scripts/start.sh /app/start.sh
COPY --chown=paper:paper paper/config/paper-global.yml /app/config/paper-global.yml
COPY --chown=paper:paper paper/config/server.properties /app/server.properties

USER paper

EXPOSE 25565

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD nc -z localhost 25565 || exit 1

CMD ["sh", "/app/start.sh"]
