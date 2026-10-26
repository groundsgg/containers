# Crafatar (https://github.com/crafatar/crafatar) at a pinned upstream commit,
# built on a supported Node LTS. Upstream's own image is node:12-alpine; its
# native dependencies do not build or run on any Node that still gets security
# fixes, so this image carries them over (see README):
#   - canvas 2.11.2 -> 3.2.3 (N-API), via patches/canvas-3.patch and the
#     package-lock.json next to this file, which also moves nan 2.18 -> 2.30
#   - lwip 1.3.1 patched for current V8 and zlib, via patches/lwip-node24.patch
ARG NODE_IMAGE=node:24.22.1-trixie-slim

FROM ${NODE_IMAGE} AS build

ARG CRAFATAR_REPOSITORY=https://github.com/crafatar/crafatar.git
# Full SHA: fetching by commit is self-verifying, unlike a tag or a branch.
ARG CRAFATAR_COMMIT=eff76b334c30e9e0266c4c46d53644ff77815d88

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      ca-certificates git patch python3 build-essential pkg-config \
      libcairo2-dev libpango1.0-dev libjpeg-dev libgif-dev; \
    rm -rf /var/lib/apt/lists/*

WORKDIR /src/crafatar

COPY crafatar/patches/ /src/patches/
COPY crafatar/package-lock.json /src/package-lock.json

# Install without scripts, patch lwip, then build both native modules from
# source against the distro libraries. Building canvas from source also keeps
# its prebuilt binary (downloaded outside the lockfile's integrity) out.
RUN set -eux; \
    git init -q .; \
    git fetch -q --depth 1 "$CRAFATAR_REPOSITORY" "$CRAFATAR_COMMIT"; \
    git checkout -q FETCH_HEAD; \
    [ "$(git rev-parse HEAD)" = "$CRAFATAR_COMMIT" ]; \
    git apply /src/patches/canvas-3.patch; \
    cp /src/package-lock.json package-lock.json; \
    npm ci --omit=dev --ignore-scripts --no-audit --no-fund; \
    patch -p1 -d node_modules/@randy.tarampi/lwip < /src/patches/lwip-node24.patch; \
    npm_config_build_from_source=true npm rebuild --no-audit --no-fund; \
    npm cache clean --force

FROM ${NODE_IMAGE}

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION
ARG CRAFATAR_COMMIT=eff76b334c30e9e0266c4c46d53644ff77815d88

LABEL org.opencontainers.image.title="crafatar" \
      org.opencontainers.image.description="Crafatar Minecraft avatar service, built from a pinned upstream commit on a supported Node LTS." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION" \
      org.opencontainers.image.licenses="MIT" \
      gg.grounds.crafatar.commit="$CRAFATAR_COMMIT"

# Runtime halves of the -dev packages canvas was compiled against.
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      libcairo2 libpango-1.0-0 libpangocairo-1.0-0 libjpeg62-turbo libgif7; \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /src/crafatar/node_modules/ node_modules/
COPY --from=build /src/crafatar/package.json /src/crafatar/www.js /src/crafatar/config.js ./
COPY --from=build /src/crafatar/lib/ lib/

# Fails the build if a native module cannot load its shared libraries, or if
# canvas' size setters do not reach the native object (the canvas 2 + Node 24
# failure: renders came out 0x0).
RUN node -e 'const c = require("canvas").createCanvas(); c.width = 2; c.height = 2; c.getContext("2d").getImageData(0, 0, 2, 2); require("@randy.tarampi/lwip")'

# Crafatar writes its image cache below ./images but never creates the
# directories itself; a volume mounted over them has to provide them too.
RUN mkdir -p images/faces images/helms images/skins images/renders images/capes \
    && chown -R 1000:1000 images

ENV NODE_ENV=production
# `node` (uid 1000) from the base image; numeric so runAsNonRoot can check it.
USER 1000:1000
EXPOSE 3000
# Not `npm start`: npm wants a writable $HOME, which a read-only root lacks.
CMD ["node", "www.js"]
