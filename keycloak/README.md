# keycloak

Custom Keycloak image with the [keycloak-minecraft-idp](https://github.com/groundsgg/keycloak-minecraft-idp) and [keycloak-permissions-event-listener](https://github.com/groundsgg/keycloak-permissions-event-listener) extensions pre-installed and optimized.

## Pull

```bash
docker pull ghcr.io/groundsgg/keycloak:latest
```

## Build

```bash
docker build -f keycloak/Dockerfile -t keycloak-custom .
```

Provider versions are independent build arguments:

| Provider | Build argument | Default version |
| --- | --- | --- |
| Minecraft identity provider | `KEYCLOAK_MINECRAFT_VERSION` | `1.3.1` |
| Permissions event listener | `KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION` | `0.2.2` |

The provider assets are pinned by `KEYCLOAK_MINECRAFT_SHA256` and `KEYCLOAK_PERMISSIONS_EVENT_LISTENER_SHA256`. Override each provider's version and checksum together when updating it.

To override either provider version:

```bash
docker build -f keycloak/Dockerfile \
  --build-arg KEYCLOAK_MINECRAFT_VERSION=1.3.1 \
  --build-arg KEYCLOAK_MINECRAFT_SHA256=23e65b41601d3cd4568f79a53c57e501f80a9b473a21a34799e1ef7e03aff4ab \
  --build-arg KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION=0.2.2 \
  --build-arg KEYCLOAK_PERMISSIONS_EVENT_LISTENER_SHA256=1ad0806552d8e975441aa355bbdcb4f737b4c3083f693fac358cad629e10fb51 \
  -t keycloak-custom .
```

Updating either provider requires a new Keycloak image release. It does not require a release of the other provider.

## Configuration

This image runs Keycloak in optimized mode (`start --optimized`). All Keycloak configuration should be provided via environment variables at runtime. See the [Keycloak Server Configuration](https://www.keycloak.org/server/all-config) documentation for available options.

Required configuration:

| Variable         | Description                       |
|------------------|-----------------------------------|
| `KC_DB`          | Database vendor (e.g. `postgres`) |
| `KC_DB_URL`      | JDBC database URL                 |
| `KC_DB_USERNAME` | Database username                 |
| `KC_DB_PASSWORD` | Database password                 |
| `KC_HOSTNAME`    | Hostname for the Keycloak server  |

The permissions event listener also requires:

| Variable                                                    | Required | Default                      | Description                            |
|-------------------------------------------------------------|----------|------------------------------|----------------------------------------|
| `KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_NATS_URL`        | Yes      | -                            | NATS server URL with JetStream enabled |
| `KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_REALM`           | Yes      | -                            | Comma-separated accepted realm IDs     |
| `KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_SUBJECT`         | No       | `minecraft-identity.changed` | NATS subject for invalidation events   |

Enable the `permissions-events` event listener in the target realm. The configured subject must be retained by a JetStream stream before Keycloak publishes identity invalidations.

### Permissions listener 0.2 migration

Version 0.2.2 still filters configured realms by their stable Keycloak realm IDs, while published identity-change events include both the realm ID and realm name. Before deploying this image, replace any realm names in `KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_REALM` with their corresponding realm IDs. Multiple realm IDs can be provided as a comma-separated list.

## Dependency Updates

The base Keycloak image (`quay.io/keycloak/keycloak`) is tracked by Dependabot. The `KEYCLOAK_MINECRAFT_VERSION` and `KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION` build arguments are **not** automatically tracked by Dependabot and must be updated manually when new release assets of [keycloak-minecraft-idp](https://github.com/groundsgg/keycloak-minecraft-idp) or [keycloak-permissions-event-listener](https://github.com/groundsgg/keycloak-permissions-event-listener) are available.
