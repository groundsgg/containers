# Stage-only prototype. Keep the exact currently deployed Geyser runtime,
# NetherNet startup and nameplate pack. Add an independent resource pack.
FROM ghcr.io/groundsgg/geyser:sha-c8b6f55@sha256:1f7370fb6409756449596c0917b32005dfe99903a9507ed6353d241b45281f70 AS geyser-runtime

FROM eclipse-temurin:25-jdk-alpine AS sidebar-extension
COPY --from=geyser-runtime /opt/geyser/Geyser.jar /build/geyser.jar
COPY extension /build/extension
RUN sh /build/extension/build.sh /build/geyser.jar /build/out

FROM geyser-runtime
COPY --chown=geyser:geyser grounds-sidebar-poc.mcpack /opt/geyser/packs/grounds-sidebar-poc.mcpack
RUN mkdir -p /opt/geyser/extensions
COPY --from=sidebar-extension --chown=geyser:geyser /build/out/grounds-sidebar-head.jar /opt/geyser/extensions/grounds-sidebar-head.jar
