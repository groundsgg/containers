# syntax=docker/dockerfile:1.7

# Grounds buildserver: BuildSystem stack + pinned builder tools.
# Tool JARs are fetched at image build with SHA256 checks — never at pod start.

ARG BUILD_SYSTEM_IMAGE=ghcr.io/groundsgg/buildsystem:sha-46d2937@sha256:2498777e6d8543d44953c88d29e4715a87040c4f1ca1f88bb63cdaee4ef50a1f

FROM alpine:3.24 AS plugins
RUN apk add --no-cache curl jq

# Update Paper independently of the BuildSystem image's inherited server JAR.
ARG PAPER_URL=https://fill-data.papermc.io/v1/objects/2224a0b2b6b096ff4c429ad926e97977213e4f633e90cb3a49b5eeb82f94bab0/paper-26.3-159.jar
ARG PAPER_SHA256=2224a0b2b6b096ff4c429ad926e97977213e4f633e90cb3a49b5eeb82f94bab0

ARG FAWE_URL=https://cdn.modrinth.com/data/z4HZZnLr/versions/U2dcKa9T/FastAsyncWorldEdit-Paper-2.16.0.jar
ARG FAWE_SHA256=1a3cf8178f22d97fe78db100c7d50ebe57acdb59f3df1a972c29761ebba6f0cd
ARG FAVS_URL=https://cdn.modrinth.com/data/D7XBSI1y/versions/AnYq3r1h/fastasyncvoxelsniper-3.2.5.jar
ARG FAVS_SHA256=485e8b5694df0dbda96038049cb9a73bee4e37d9ec8c7f589af5e8a590fca2e9
ARG AXIOM_URL=https://cdn.modrinth.com/data/evkiwA7V/versions/V2yAy4up/AxiomPaper-6.0.1-for-MC26.3.jar
ARG AXIOM_SHA256=8f3c478c4c7d5e3e9864cb0c5cc848a6663c06864ea5761edad246eba4f40752
ARG GOPAINT_URL=https://cdn.modrinth.com/data/a2wQ6jIv/versions/ICvdclhi/goPaintAdvanced-1.8.2-all.jar
ARG GOPAINT_SHA256=d9de8159a19e1dfb89e9193c8a3ace89844c7cac8380ae6c063b114d779c8085
ARG CREATIVE_URL=https://cdn.modrinth.com/data/zAcZq5oV/versions/K9wWSuIr/creative-utilities-1.5.0-all.jar
ARG CREATIVE_SHA256=0cd7c566e63ddd2ac7c0741ca0bcb3abfbafba7fddf0838d77ed742cbd1831e9
ARG EAS_URL=https://cdn.modrinth.com/data/8DDI76SG/versions/YIdwIece/EasyArmorStands-3.3.2.jar
ARG EAS_SHA256=e830257512d3b95557fd3bf2a787015c18e52ff87314478b014ac1c45dff57c3
ARG SCENE_EDITOR_URL=https://maven.pkg.github.com/groundsgg/plugin-scene-editor/gg/grounds/plugin-scene-editor-paper/0.7.0/plugin-scene-editor-paper-0.7.0.jar
ARG SCENE_EDITOR_SHA256=d59dc4beeb2a58ad451e6dbe4233596db0fb3baedc3e3fa68ff60273446be7db
ARG GROUNDS_RUNTIME_PLUGIN_VERSION=0.3.0
ARG GROUNDS_RUNTIME_PAPER_SHA256=89f973e0388604d37c6bce48e72b3a267041b7c5ba733fd2391f4e456228542b
ARG GROUNDS_RUNTIME_CATALOG_SHA256=9edfaf236238b41d72d221cf4f7cc7141fc2f13af29485d39736dfeb2cdcef48

WORKDIR /jars
RUN --mount=type=secret,id=github_token,required=true \
    set -eu; \
    github_token="$(cat /run/secrets/github_token)"; \
    download() { \
      url="$1"; sha="$2"; out="$3"; \
      curl -fsSL -A "grounds-containers/buildserver" -o "$out" "$url"; \
      echo "$sha  $out" | sha256sum -c -; \
    }; \
    download_private() { \
      url="$1"; sha="$2"; out="$3"; \
      curl -fsSL -A "grounds-containers/buildserver" \
        -H "Authorization: Bearer ${github_token}" -o "$out" "$url"; \
      echo "$sha  $out" | sha256sum -c -; \
    }; \
    download "$PAPER_URL" "$PAPER_SHA256" /paper.jar; \
    download "$FAWE_URL" "$FAWE_SHA256" FastAsyncWorldEdit.jar; \
    download "$FAVS_URL" "$FAVS_SHA256" FastAsyncVoxelSniper.jar; \
    download "$AXIOM_URL" "$AXIOM_SHA256" AxiomPaper.jar; \
    download "$GOPAINT_URL" "$GOPAINT_SHA256" goPaintAdvanced.jar; \
    download "$CREATIVE_URL" "$CREATIVE_SHA256" CreativeUtilities.jar; \
    download "$EAS_URL" "$EAS_SHA256" EasyArmorStands.jar; \
    download_private "$SCENE_EDITOR_URL" "$SCENE_EDITOR_SHA256" GroundsSceneEditor.jar; \
    runtime_repository_url="https://maven.pkg.github.com/groundsgg/plugin-grounds-runtime/gg/grounds"; \
    runtime_artifact_id="plugin-grounds-runtime-paper"; \
    download_private "${runtime_repository_url}/${runtime_artifact_id}/${GROUNDS_RUNTIME_PLUGIN_VERSION}/${runtime_artifact_id}-${GROUNDS_RUNTIME_PLUGIN_VERSION}.jar" \
      "$GROUNDS_RUNTIME_PAPER_SHA256" plugin-grounds-runtime.jar; \
    download_private "${runtime_repository_url}/grounds-runtime-catalog/${GROUNDS_RUNTIME_PLUGIN_VERSION}/grounds-runtime-catalog-${GROUNDS_RUNTIME_PLUGIN_VERSION}.json" \
      "$GROUNDS_RUNTIME_CATALOG_SHA256" /tmp/grounds-runtime-libraries.json; \
    jq --arg version "$GROUNDS_RUNTIME_PLUGIN_VERSION" \
      'if (.libraries | type) == "array" then {schemaVersion: 1, runtime: {id: "plugin-grounds-runtime-paper", name: "plugin-grounds-runtime", version: $version, platform: "paper"}, libraries: .libraries} else error("runtime catalog libraries must be an array") end' \
      /tmp/grounds-runtime-libraries.json > /runtime-manifest.json

# The runtime is a JRE, which cannot launch a .java source file (no jdk.compiler), so the
# working-copy restore is compiled here.
FROM eclipse-temurin:25-jdk-alpine AS restore
WORKDIR /src
COPY buildserver/restore/WorkspaceRestore.java .
RUN javac -d classes WorkspaceRestore.java && \
    jar --create --file /workspace-restore.jar --main-class WorkspaceRestore -C classes .

FROM ${BUILD_SYSTEM_IMAGE}

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG BUILD_SYSTEM_IMAGE

USER root
RUN mkdir -p /opt/grounds
COPY --from=plugins --chown=paper:paper /paper.jar /app/paper.jar
COPY --from=plugins /jars/*.jar /app/plugins/
COPY --from=plugins /runtime-manifest.json /opt/grounds/runtime-manifest.json
COPY --from=restore /workspace-restore.jar /opt/grounds/workspace-restore.jar
# Preserve the Paper start script from the base image, then install our wrapper.
RUN cp /app/start.sh /opt/grounds/paper-start.sh
COPY --chmod=755 buildserver/scripts/start.sh /app/start.sh
RUN chown -R paper:paper /app/plugins /opt/grounds

LABEL org.opencontainers.image.title="buildserver" \
      org.opencontainers.image.description="Grounds buildserver: BuildSystem stack plus builder tools" \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION" \
      gg.grounds.buildsystem.base="$BUILD_SYSTEM_IMAGE"

USER paper

EXPOSE 25565
CMD ["sh", "/app/start.sh"]
