FROM eclipse-temurin:25-jre-alpine

ARG VCS_REVISION
ARG BUILD_DATE
ARG IMAGE_VERSION

LABEL org.opencontainers.image.title="velocity" \
      org.opencontainers.image.description="A small Docker image for running a Velocity proxy." \
      org.opencontainers.image.vendor="grounds.gg" \
      org.opencontainers.image.authors="grounds.gg <hi@grounds.gg>" \
      org.opencontainers.image.source="https://github.com/groundsgg/containers" \
      org.opencontainers.image.revision="$VCS_REVISION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.version="$IMAGE_VERSION"

ARG VELOCITY_VERSION=3.4.0-SNAPSHOT
ARG VELOCITY_BUILD=558

WORKDIR /app

RUN set -eux; \
    apk add --no-cache curl; \
    if [ -z "$VELOCITY_BUILD" ]; then \
      echo "Velocity build must be set" >&2; \
      exit 1; \
    fi; \
    curl -fsSL -o /app/velocity.jar \
      "https://api.papermc.io/v2/projects/velocity/versions/${VELOCITY_VERSION}/builds/${VELOCITY_BUILD}/downloads/velocity-${VELOCITY_VERSION}-${VELOCITY_BUILD}.jar"; \
    addgroup -S velocity; \
    adduser -S -G velocity -h /app velocity; \
    chown -R velocity:velocity /app

COPY --chown=velocity:velocity config/velocity.toml /app/velocity.toml
COPY --chown=velocity:velocity config/plugins /app/plugins
COPY --chown=velocity:velocity config/forwarding.secret.example /app/forwarding.secret
COPY --chown=velocity:velocity scripts/start.sh /app/start.sh
RUN chmod +x /app/start.sh

USER velocity

EXPOSE 25565

CMD ["sh", "/app/start.sh"]
