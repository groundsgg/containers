# syntax=docker/dockerfile:1.7

# Grounds buildserver: BuildSystem stack + pinned builder tools.
# Tool JARs are fetched at image build with SHA256 checks — never at pod start.

ARG BUILD_SYSTEM_IMAGE=ghcr.io/groundsgg/buildsystem:sha-e564bc1@sha256:7ed86449527f1df4255af327c50936ed1627268705cdc9a69dc06315a2676080

FROM alpine:3.24 AS plugins
RUN apk add --no-cache curl

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
ARG SCENE_EDITOR_URL=https://maven.pkg.github.com/groundsgg/plugin-scene-editor/gg/grounds/plugin-scene-editor-paper/0.3.0/plugin-scene-editor-paper-0.3.0.jar
ARG SCENE_EDITOR_SHA256=5e5bafbfc9358db5b69d920d6c2520d6026c20225f4a51daf8e0d5326ad3574f

WORKDIR /jars
RUN --mount=type=secret,id=github_token \
    set -eu; \
    download() { \
      url="$1"; sha="$2"; out="$3"; \
      curl -fsSL -A "grounds-containers/buildserver" -o "$out" "$url"; \
      echo "$sha  $out" | sha256sum -c -; \
    }; \
    download "$PAPER_URL" "$PAPER_SHA256" /paper.jar; \
    download "$FAWE_URL" "$FAWE_SHA256" FastAsyncWorldEdit.jar; \
    download "$FAVS_URL" "$FAVS_SHA256" FastAsyncVoxelSniper.jar; \
    download "$AXIOM_URL" "$AXIOM_SHA256" AxiomPaper.jar; \
    download "$GOPAINT_URL" "$GOPAINT_SHA256" goPaintAdvanced.jar; \
    download "$CREATIVE_URL" "$CREATIVE_SHA256" CreativeUtilities.jar; \
    download "$EAS_URL" "$EAS_SHA256" EasyArmorStands.jar; \
    curl -fsSL -A "grounds-containers/buildserver" \
      -H "Authorization: Bearer $(cat /run/secrets/github_token)" \
      -o GroundsSceneEditor.jar "$SCENE_EDITOR_URL"; \
    echo "$SCENE_EDITOR_SHA256  GroundsSceneEditor.jar" | sha256sum -c -

FROM ${BUILD_SYSTEM_IMAGE}

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG BUILD_SYSTEM_IMAGE

USER root
RUN mkdir -p /opt/grounds
COPY --from=plugins --chown=paper:paper /paper.jar /app/paper.jar
COPY --from=plugins /jars/*.jar /app/plugins/
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
