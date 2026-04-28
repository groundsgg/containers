# keycloak

Custom Keycloak image with the [keycloak-minecraft-idp](https://github.com/groundsgg/keycloak-minecraft-idp) extension pre-installed and optimized.

## Pull

```bash
docker pull ghcr.io/groundsgg/keycloak:latest
```

## Build

```bash
docker build -f keycloak/Dockerfile -t keycloak-custom .
```

To use a specific extension version:

```bash
docker build -f keycloak/Dockerfile \
  --build-arg KEYCLOAK_MINECRAFT_VERSION=1.0.3 \
  -t keycloak-custom .
```

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

## Dependency Updates

The base Keycloak image (`quay.io/keycloak/keycloak`) is tracked by Dependabot. The `KEYCLOAK_MINECRAFT_VERSION` build argument is **not** automatically tracked by Dependabot and must be updated manually when a new release asset of [keycloak-minecraft-idp](https://github.com/groundsgg/keycloak-minecraft-idp) is available.
