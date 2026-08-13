FROM alpine:3.24 AS downloader

# Pinned, not "latest": Geyser follows Bedrock's protocol, which changes roughly
# every six weeks, and a floating build would turn any unrelated rebuild into an
# unannounced protocol upgrade. Worse, it would do so silently — a Geyser one
# version behind rejects every client that already auto-updated.
ARG GEYSER_VERSION=2.11.1
ARG GEYSER_BUILD=1219
ARG GEYSER_SHA256=b7f1875bfff183a9f27f8bdfea0d289e030516c8d7b7c37678733a79883a9451

RUN set -eux; \
    apk add --no-cache curl; \
    [ -n "$GEYSER_VERSION" ] || { echo "Geyser version must be set" >&2; exit 1; }; \
    [ -n "$GEYSER_BUILD" ] || { echo "Geyser build must be set" >&2; exit 1; }; \
    download_url="https://download.geysermc.org/v2/projects/geyser/versions/${GEYSER_VERSION}/builds/${GEYSER_BUILD}/downloads/standalone"; \
    curl -fsSL -o /tmp/geyser.jar "$download_url"; \
    [ -s /tmp/geyser.jar ] || { echo "Downloaded Geyser jar is empty (version=${GEYSER_VERSION}, build=${GEYSER_BUILD})" >&2; exit 1; }; \
    head -c 4 /tmp/geyser.jar | grep -q '^PK' || { echo "Downloaded Geyser jar is not a valid JAR (version=${GEYSER_VERSION}, build=${GEYSER_BUILD})" >&2; exit 1; }; \
    echo "${GEYSER_SHA256}  /tmp/geyser.jar" | sha256sum -c -

FROM eclipse-temurin:25-jre-alpine

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG GEYSER_VERSION=2.11.1
ARG GEYSER_BUILD=1219

LABEL org.opencontainers.image.title="geyser" \
      org.opencontainers.image.description="Geyser Standalone — Bedrock UDP entry point that translates to the Java-protocol Velocity proxy." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION" \
      gg.grounds.geyser.version="$GEYSER_VERSION" \
      gg.grounds.geyser.build="$GEYSER_BUILD"

# Geyser resolves config.yml, its locale cache and its pack directory relative to
# the working directory, so this path is part of the contract with the chart.
#
# The rendered config is mounted at /config, NOT here: Geyser rewrites config.yml
# on startup and cannot do that against a read-only mount. start.sh copies it in.
WORKDIR /opt/geyser

COPY --from=downloader /tmp/geyser.jar /opt/geyser/Geyser.jar
COPY --chmod=755 geyser/scripts/start.sh /opt/geyser/start.sh

RUN set -eux; \
    addgroup -S geyser; \
    adduser -S -G geyser -h /opt/geyser geyser; \
    chown -R geyser:geyser /opt/geyser

USER geyser

EXPOSE 19132/udp

# No HEALTHCHECK. Geyser speaks RakNet over UDP and exposes no HTTP endpoint, and
# a UDP port probe cannot distinguish "listening" from "nothing there" — it would
# report healthy for a dead process. An honest check needs an unconnected-ping
# round trip, which is more machinery than it is worth here; Kubernetes restarts
# the pod when the process exits either way.

CMD ["sh", "/opt/geyser/start.sh"]
