FROM node:25.1.0-alpine

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

# Disable download prompt for pnpm
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0

# Install system dependencies and enable corepack for pnpm
RUN apk add --no-cache wget curl nano bash && \
    corepack enable && \
    corepack prepare pnpm@10.20 --activate

# Set working directory
WORKDIR /app

# Copy and set permissions for startup script
COPY devspace_start.sh ./
RUN chmod +x devspace_start.sh

# Default command for interactive development
CMD ["/bin/bash"]
