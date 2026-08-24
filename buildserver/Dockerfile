# syntax=docker/dockerfile:1.7

# Grounds buildserver: BuildSystem stack + pinned builder tools.
# Tool JARs are fetched at image build with SHA256 checks — never at pod start.

ARG BUILD_SYSTEM_IMAGE=ghcr.io/groundsgg/buildsystem:sha-9e29bb9

FROM alpine:3.24 AS plugins
RUN apk add --no-cache curl

ARG FAWE_URL=https://cdn.modrinth.com/data/z4HZZnLr/versions/5TOYHuQr/FastAsyncWorldEdit-Paper-2.15.4.jar
ARG FAWE_SHA256=bbb1875e823ca01591bd23427e86f490e17cd82fe769dcb7e82a955dd8371acc
ARG FAVS_URL=https://cdn.modrinth.com/data/D7XBSI1y/versions/AnYq3r1h/fastasyncvoxelsniper-3.2.5.jar
ARG FAVS_SHA256=485e8b5694df0dbda96038049cb9a73bee4e37d9ec8c7f589af5e8a590fca2e9
ARG AXIOM_URL=https://cdn.modrinth.com/data/evkiwA7V/versions/Ow8CJ6pP/AxiomPaper-5.0.4-for-MC26.2.jar
ARG AXIOM_SHA256=3d60342dbd37834337ecc4dc43931ec70092cf1b86b04b7b9788df188479e422
ARG GOPAINT_URL=https://cdn.modrinth.com/data/a2wQ6jIv/versions/ICvdclhi/goPaintAdvanced-1.8.2-all.jar
ARG GOPAINT_SHA256=d9de8159a19e1dfb89e9193c8a3ace89844c7cac8380ae6c063b114d779c8085
ARG CREATIVE_URL=https://cdn.modrinth.com/data/zAcZq5oV/versions/K9wWSuIr/creative-utilities-1.5.0-all.jar
ARG CREATIVE_SHA256=0cd7c566e63ddd2ac7c0741ca0bcb3abfbafba7fddf0838d77ed742cbd1831e9
ARG EAS_URL=https://cdn.modrinth.com/data/8DDI76SG/versions/CdGnJlay/EasyArmorStands-3.3.0.jar
ARG EAS_SHA256=1a6f428876e2a00cb4ed4feaa10646b5101bd5c25e103c7b6c3f0054dbdda39c

WORKDIR /jars
RUN set -eux; \
    download() { \
      url="$1"; sha="$2"; out="$3"; \
      curl -fsSL -A "grounds-containers/buildserver" -o "$out" "$url"; \
      echo "$sha  $out" | sha256sum -c -; \
    }; \
    download "$FAWE_URL" "$FAWE_SHA256" FastAsyncWorldEdit.jar; \
    download "$FAVS_URL" "$FAVS_SHA256" FastAsyncVoxelSniper.jar; \
    download "$AXIOM_URL" "$AXIOM_SHA256" AxiomPaper.jar; \
    download "$GOPAINT_URL" "$GOPAINT_SHA256" goPaintAdvanced.jar; \
    download "$CREATIVE_URL" "$CREATIVE_SHA256" CreativeUtilities.jar; \
    download "$EAS_URL" "$EAS_SHA256" EasyArmorStands.jar

FROM ${BUILD_SYSTEM_IMAGE}

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG BUILD_SYSTEM_IMAGE

USER root
RUN mkdir -p /opt/grounds
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
