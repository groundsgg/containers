FROM eclipse-temurin:25-jdk-alpine AS privacy-builder

# Minecraft 26.3 (protocol 777) needs Velocity 4; it also accepts 26.2 clients.
ARG VELOCITY_VERSION=4.2.0
ARG VELOCITY_BUILD=30
ARG VELOCITY_SHA256=35a5596a5468a035d8a32c8de5ebb0dc6b8d8f0cc3ff5169d514aca762af8aa8
ARG VELOCITY_DOWNLOAD_USER_AGENT="grounds-containers/1.0 (https://github.com/groundsgg/containers)"
COPY scripts/player-access-privacy /privacy
RUN set -eu; \
    apk add --no-cache curl jq; \
    builds_api_url="https://fill.papermc.io/v3/projects/velocity/versions/${VELOCITY_VERSION}/builds"; \
    curl -fsSL -H "User-Agent: ${VELOCITY_DOWNLOAD_USER_AGENT}" "$builds_api_url" -o /tmp/builds.json; \
    download_url="$(jq -r --argjson build "${VELOCITY_BUILD}" 'first(.[] | select(.id == $build or .number == $build) | .downloads["server:default"].url) // empty' /tmp/builds.json)"; \
    [ -n "$download_url" ] || { echo "Pinned Velocity build was not found" >&2; exit 1; }; \
    curl -fsSL -H "User-Agent: ${VELOCITY_DOWNLOAD_USER_AGENT}" "$download_url" -o /tmp/velocity-original.jar; \
    sh /privacy/build.sh velocity /tmp/velocity-original.jar /out/velocity.jar "$VELOCITY_SHA256"

FROM eclipse-temurin:25-jre-alpine

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
# plugin-agones 3.3.0 can drain wait-only (GROUNDS_DRAIN_TRANSFERS=false, the
# Bedrock proxy) and moves a player on once their round ends after the
# deadline, so a proxy empties as rounds end and never cuts one short.
#
# plugin-agones 3.2.1 drains through plugin-player's session handoff, so the
# proxy that takes a draining player over finds the handoff cookie instead of
# refusing with "This transfer could not be verified".
#
# plugin-agones 3.2.0 also treats a lobby that retired itself (GameServer label
# agones.dev/sdk-grounds-retired=true, set when its map is re-pinned) as
# outdated: no new joins, and its players are moved to a current lobby.
#
# plugin-agones 3.0.0 fills lobbies fullest-first below a per-lobby cap
# (GROUNDS_LOBBY_PLAYER_CAP, default 100) instead of spreading joins evenly,
# with per-player reservations so a burst of joins cannot overfill one. It also
# exposes the placement to plugin-match, so returns from a match go home while
# the home lobby has room and use the same rule otherwise.
#
# plugin-agones 2.2.0 paces the proxy drain: transfers go out 20 a second
# (GROUNDS_DRAIN_TRANSFERS_PER_SECOND) instead of all in one burst, so a full
# proxy does not land on its replacement as hundreds of simultaneous logins.
#
# plugin-agones 2.1.0 drains lobbies: players on a lobby whose GameServerSet was
# rolled away are moved to a current one, and a kicked lobby player is sent to
# another lobby instead of being disconnected. It also fixes the proxy drain,
# which looked its region up by datacentre (REGION) in a catalogue keyed by
# continent and so never found a target to transfer to.
#
# plugin-agones 1.2.0 publishes ServerDisplayQuery so the proxy tab list can
# show kind plus replica id (Lobby s9fwt) instead of the Agones pod name.
#
# plugin-agones 0.7.2 is what makes /agones report the *network* player count.
# Before it, the command read Velocity's playersConnected — the players on the
# proxy running the command — so with two proxies in front of one lobby each of
# them confidently reported half of it.
# plugin-permissions is deliberately NOT baked. The chart fetches it per
# deployment, and two jars with the same plugin id made Velocity log
# "Refusing to load plugin ... since we already loaded a plugin with the same
# ID" on every single boot. Which of the two won came down to the filename
# sorting ahead of the other, which is not a thing to leave a permission
# system resting on.
ARG PLUGINS="plugin-agones:plugin-agones-velocity:3.3.0"
ARG GROUNDS_RUNTIME_PLUGIN_VERSION="0.3.0"
# grounds-platform velocity build: proxy-side whitelist gate, MOTD, and
# platform command execution. Gated on the arg so it can be disabled.
ARG GROUNDS_PLATFORM_PLUGIN_VERSION="0.6.1"

LABEL org.opencontainers.image.title="velocity" \
      org.opencontainers.image.description="A small Docker image for running a Velocity proxy." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION" \
      gg.grounds.runtime.manifest.path="/opt/grounds/runtime-manifest.json" \
      gg.grounds.player-access-log-privacy="1"

WORKDIR /app

COPY --chmod=755 scripts/download-plugins.sh /app/download-plugins.sh
COPY --chown=velocity:velocity velocity/config/plugins /app/plugins
COPY --from=privacy-builder /out/velocity.jar /app/velocity.jar

RUN --mount=type=secret,id=github_token \
    set -eu; \
    apk add --no-cache curl jq netcat-openbsd; \
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
