FROM node:26.1.0-alpine

# Build arguments for metadata
ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION

# OCI labels for container metadata
LABEL org.opencontainers.image.title="dev-container-node" \
      org.opencontainers.image.description="A Node.js container which is used for development in our DevSpace environment." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION"

# Install system dependencies and pnpm
RUN apk add --no-cache wget curl nano bash && \
    npm install --global --no-fund --no-update-notifier pnpm@10.26.2

# Set working directory
WORKDIR /app

# Copy and set permissions for startup script
COPY ./dev-container-node/devspace_start.sh ./
RUN chmod +x devspace_start.sh

# Default command for interactive development
CMD ["/bin/bash"]
