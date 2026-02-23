# keycloak

Custom Keycloak image with the [keycloak-minecraft](https://github.com/groundsgg/keycloak-minecraft) extension pre-installed and optimized.

## Build

```bash
docker build -f keycloak/Dockerfile -t keycloak-custom .
```

To use a specific extension version:

```bash
docker build -f keycloak/Dockerfile --build-arg KEYCLOAK_MINECRAFT_VERSION=1.0.0 -t keycloak-custom .
```

## Configuration

This image runs Keycloak in optimized mode (`start --optimized`). All Keycloak configuration should be provided via environment variables at runtime. See the [Keycloak Server Configuration](https://www.keycloak.org/server/all-config) documentation for available options.

Common configuration:

| Variable | Description |
|---|---|
| `KC_DB` | Database vendor (e.g. `postgres`) |
| `KC_DB_URL` | JDBC database URL |
| `KC_DB_USERNAME` | Database username |
| `KC_DB_PASSWORD` | Database password |
| `KC_HOSTNAME` | Hostname for the Keycloak server |
