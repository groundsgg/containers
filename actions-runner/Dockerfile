# GitHub Actions runner image for the ARC ephemeral CI runners — the
# `grounds-runners` scale set on the ci-pve-1 cluster.
#
# Built on the upstream actions runner, plus Node.js 24 and pnpm so Node /
# Next.js pipelines (e.g. grounds-portal) run directly on the runner with
# no job-level `container:`. Every runner pod is ephemeral — one job, then
# discarded — so there is no workspace pollution to contain.
#
# TODO: pin the base to a specific runner release
# (https://github.com/actions/runner/releases) and keep it roughly in sync
# with the gha-runner-scale-set chart version used by ARC.
FROM ghcr.io/actions/actions-runner:latest

# Build arguments for metadata
ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION

# OCI labels for container metadata
LABEL org.opencontainers.image.title="actions-runner" \
      org.opencontainers.image.description="GitHub Actions runner for the ARC ephemeral CI runners — actions-runner base plus Node.js 24 and pnpm." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION"

# Node.js 24 (NodeSource) + pnpm + jq. The upstream image runs as the
# unprivileged `runner` user; apt needs root, so drop back afterwards.
USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends jq \
    && curl -fsSL https://deb.nodesource.com/setup_24.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install --global --no-fund --no-update-notifier pnpm@10.26.2 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

USER runner
