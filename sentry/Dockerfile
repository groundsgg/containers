# Custom Sentry image with the `sentry-auth-oidc` SSO plugin baked in,
# so self-hosted Sentry can authenticate against the grounds Keycloak
# realm (same "bake the extension into the image" pattern as the
# keycloak image with keycloak-minecraft-idp).
#
# Only the Sentry web/worker image needs the plugin — relay, snuba,
# symbolicator and vroom stay on their upstream images (configured via
# the chart's `images.*` keys; only `images.sentry` points here).
#
# IMPORTANT: SENTRY_AUTH_OIDC_VERSION must be pinned to a release that
# is compatible with the Sentry app version of the FROM tag below
# (Sentry's auth-provider API changed across majors; the community
# forks — Infomaniak / mavriq — track it). Verify on PyPI before
# bumping either the base tag or the plugin version.
ARG SENTRY_VERSION=26.5.0

# Sentry publishes the self-hosted images to ghcr (not Docker Hub) —
# same registry the sentry-kubernetes chart pulls relay/snuba/etc. from.
FROM ghcr.io/getsentry/sentry:${SENTRY_VERSION}

# Pinned default so the containers CI builds without custom build-args
# (mirrors the keycloak image's KEYCLOAK_MINECRAFT_VERSION default).
# 9.1.1 is the latest sentry-auth-oidc (Python >=3.11, matches the
# Sentry 26.5.0 base). Verify on PyPI before bumping either version.
ARG SENTRY_AUTH_OIDC_VERSION=9.1.1
ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION

LABEL org.opencontainers.image.title="sentry" \
      org.opencontainers.image.description="Custom Sentry image with the sentry-auth-oidc SSO plugin." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION"

# pip install must run as root; the base image drops back to its
# runtime user via its own ENTRYPOINT, so we restore root only for the
# install layer.
USER root

RUN set -eux; \
    [ -n "$SENTRY_AUTH_OIDC_VERSION" ] || { echo "SENTRY_AUTH_OIDC_VERSION must be set" >&2; exit 1; }; \
    pip install --no-cache-dir "sentry-auth-oidc==${SENTRY_AUTH_OIDC_VERSION}"; \
    # The package's import module name differs from the dist name and it
    # registers with Sentry via setuptools entry points, not a fixed
    # module import — so verify the install by dist name, not by import.
    pip show sentry-auth-oidc >/dev/null

# Hand control back to the upstream image's default user + entrypoint
# (the base image's USER directive; getsentry/sentry runs as a
# non-root `sentry`-equivalent at runtime).
USER 1000
