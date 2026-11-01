FROM eclipse-temurin:25-jdk-alpine AS downloader

# Pinned, not "latest": Geyser follows Bedrock's protocol, which changes roughly
# every six weeks, and a floating build would turn any unrelated rebuild into an
# unannounced protocol upgrade. Worse, it would do so silently — a Geyser one
# version behind rejects every client that already auto-updated.
ARG GEYSER_VERSION=2.12.1
ARG GEYSER_BUILD=1280
ARG GEYSER_SHA256=fa447f0c1343adeae248d95506b372ffe6b6633a51e88cfb83cc6978c48d3f10

RUN set -eux; \
    apk add --no-cache curl; \
    [ -n "$GEYSER_VERSION" ] || { echo "Geyser version must be set" >&2; exit 1; }; \
    [ -n "$GEYSER_BUILD" ] || { echo "Geyser build must be set" >&2; exit 1; }; \
    download_url="https://download.geysermc.org/v2/projects/geyser/versions/${GEYSER_VERSION}/builds/${GEYSER_BUILD}/downloads/standalone"; \
    curl -fsSL -o /tmp/geyser.jar "$download_url"; \
    [ -s /tmp/geyser.jar ] || { echo "Downloaded Geyser jar is empty (version=${GEYSER_VERSION}, build=${GEYSER_BUILD})" >&2; exit 1; }; \
    head -c 4 /tmp/geyser.jar | grep -q '^PK' || { echo "Downloaded Geyser jar is not a valid JAR (version=${GEYSER_VERSION}, build=${GEYSER_BUILD})" >&2; exit 1; }; \
    echo "${GEYSER_SHA256}  /tmp/geyser.jar" | sha256sum -c -

COPY scripts/player-access-privacy /privacy
RUN sh /privacy/build.sh geyser /tmp/geyser.jar /out/geyser.jar "$GEYSER_SHA256"

FROM eclipse-temurin:25-jdk-alpine AS bedrock-pack

WORKDIR /build
COPY geyser/pack-src /build/src
RUN java -Djava.awt.headless=true /build/src/BuildPack.java /build/src/bedrock.png /build/grounds-bedrock.mcpack

FROM eclipse-temurin:25-jre-alpine

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG GEYSER_VERSION=2.12.1
ARG GEYSER_BUILD=1280

LABEL org.opencontainers.image.title="geyser" \
      org.opencontainers.image.description="Geyser Standalone — Bedrock UDP entry point that translates to the Java-protocol Velocity proxy." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION" \
      gg.grounds.geyser.version="$GEYSER_VERSION" \
      gg.grounds.geyser.build="$GEYSER_BUILD" \
      gg.grounds.player-access-log-privacy="1"

# Geyser resolves config.yml, its locale cache and its pack directory relative to
# the working directory, so this path is part of the contract with the chart.
#
# The rendered config is mounted at /config, NOT here: Geyser rewrites config.yml
# on startup and cannot do that against a read-only mount. start.sh copies it in.
WORKDIR /opt/geyser

COPY --from=downloader /out/geyser.jar /opt/geyser/Geyser.jar
COPY --from=bedrock-pack /build/grounds-bedrock.mcpack /opt/geyser/packs/grounds-bedrock.mcpack
COPY --chmod=755 geyser/scripts/start.sh /opt/geyser/start.sh

RUN set -eux; \
    addgroup -S geyser; \
    adduser -S -G geyser -h /opt/geyser geyser; \
    chown -R geyser:geyser /opt/geyser

USER geyser

EXPOSE 19132/udp 19132/tcp

# No HEALTHCHECK. Geyser speaks RakNet over UDP and exposes no HTTP endpoint, and
# a UDP port probe cannot distinguish "listening" from "nothing there" — it would
# report healthy for a dead process. An honest check needs an unconnected-ping
# round trip, which is more machinery than it is worth here; Kubernetes restarts
# the pod when the process exits either way.

CMD ["sh", "/opt/geyser/start.sh"]
