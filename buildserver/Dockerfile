# syntax=docker/dockerfile:1.7

# Grounds buildserver: BuildSystem stack + pinned builder tools.
# Tool JARs are fetched at image build with SHA256 checks — never at pod start.

ARG BUILD_SYSTEM_IMAGE=ghcr.io/groundsgg/buildsystem:sha-7c85294@sha256:e81e6f8a6f2e64d450ae9150c6a77fc8bab4ee5ac49345d2abbc7f2546947f23

FROM alpine:3.24 AS plugins
RUN apk add --no-cache curl

# Update Paper independently of the BuildSystem image's inherited server JAR.
ARG PAPER_URL=https://fill-data.papermc.io/v1/objects/0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54/paper-26.2-121.jar
ARG PAPER_SHA256=0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54

ARG FAWE_URL=https://cdn.modrinth.com/data/z4HZZnLr/versions/5TOYHuQr/FastAsyncWorldEdit-Paper-2.15.4.jar
ARG FAWE_SHA256=bbb1875e823ca01591bd23427e86f490e17cd82fe769dcb7e82a955dd8371acc
ARG FAVS_URL=https://cdn.modrinth.com/data/D7XBSI1y/versions/AnYq3r1h/fastasyncvoxelsniper-3.2.5.jar
ARG FAVS_SHA256=485e8b5694df0dbda96038049cb9a73bee4e37d9ec8c7f589af5e8a590fca2e9
ARG AXIOM_URL=https://cdn.modrinth.com/data/evkiwA7V/versions/BbhoGfI8/AxiomPaperPlugin-6.0.0-for-MC26.2.jar
ARG AXIOM_SHA256=3ee96af0353866898884673b03dffe12fdcd51b6ddbfc86cf6074bdcba5ee8e1
ARG GOPAINT_URL=https://cdn.modrinth.com/data/a2wQ6jIv/versions/ICvdclhi/goPaintAdvanced-1.8.2-all.jar
ARG GOPAINT_SHA256=d9de8159a19e1dfb89e9193c8a3ace89844c7cac8380ae6c063b114d779c8085
ARG CREATIVE_URL=https://cdn.modrinth.com/data/zAcZq5oV/versions/K9wWSuIr/creative-utilities-1.5.0-all.jar
ARG CREATIVE_SHA256=0cd7c566e63ddd2ac7c0741ca0bcb3abfbafba7fddf0838d77ed742cbd1831e9
ARG EAS_URL=https://cdn.modrinth.com/data/8DDI76SG/versions/CdGnJlay/EasyArmorStands-3.3.0.jar
ARG EAS_SHA256=1a6f428876e2a00cb4ed4feaa10646b5101bd5c25e103c7b6c3f0054dbdda39c
ARG SCENE_EDITOR_URL=https://maven.pkg.github.com/groundsgg/plugin-scene-editor/gg/grounds/plugin-scene-editor-paper/0.1.0/plugin-scene-editor-paper-0.1.0.jar
ARG SCENE_EDITOR_SHA256=50be2507ddee8fa896abd1571c202cdc63e5aa3a8f905e2546da85576579ef17

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
