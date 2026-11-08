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

## Grounds login theme

The image includes the native `grounds` login theme under
`/opt/keycloak/themes/grounds/login`. It extends `keycloak.v2`, retaining
Keycloak's forms, translations, scripts, session handling and MFA behavior.
Only sign-in pages are themed, including OTP, errors, expired authentication
and logout confirmation. Account management, administration and emails retain
their existing themes.

The visual tokens follow the Grounds Portal's Kumo neutral surfaces and blue
actions, with system light/dark preference and self-hosted Geist. The logo and
favicon are copied unchanged from `grounds-portal/public/brand/`. The Latin and
Latin Extended Geist variable fonts are the same assets bundled by Next.js
16.2.12 for the Portal; their SIL Open Font License is included next to them.
There are no external font, script or image dependencies. The token bridge is
`themes/grounds/login/resources/css/grounds.css`; update it alongside future
Portal design-system changes.

### Verification

Docker and Python 3 are required. From the repository root:

```bash
docker build -f keycloak/Dockerfile -t grounds-keycloak:theme-test .
bash keycloak/scripts/smoke-test.sh grounds-keycloak:theme-test
```

The existing CI smoke test checks both providers and the packaged theme using
isolated Postgres, NATS and Keycloak containers. It verifies local asset
delivery, invalid credentials, OTP challenge and rejection, a successful OTP
login, logout confirmation and completion, recovery from a missing
authentication session, the invalid-client error page and German translations.
The Keycloak HTTP port is assigned dynamically and bound only to loopback;
containers and their temporary data are removed on exit. The fixture users
and OTP secret in `scripts/theme-smoke-realm.json` are test-only and are never
copied into the image.

For an interactive preview with the exact upstream Keycloak version, run from
the repository root:

```bash
docker run --rm --name grounds-login-preview \
  -p 127.0.0.1:18782:8080 \
  -v "$PWD/keycloak/themes:/opt/keycloak/themes:ro" \
  -v "$PWD/keycloak/scripts/theme-smoke-realm.json:/opt/keycloak/data/import/grounds.json:ro" \
  quay.io/keycloak/keycloak:26.8.1-1 start-dev --import-realm \
  --spi-theme--cache-themes=false --spi-theme--cache-templates=false \
  --spi-theme--static-max-age=-1
```

Open the following URL; add `&ui_locales=de` for German:

```text
http://localhost:18782/realms/grounds/protocol/openid-connect/auth?client_id=theme-smoke&redirect_uri=http%3A%2F%2Flocalhost%2Fcallback&response_type=code&scope=openid
```

Use `theme-user` or `otp-user` with password `theme-password`; the latter
exercises the MFA prompt. The callback deliberately points to an absent local
application. This upstream-only preview has no custom providers or external
identity provider; the full image smoke test above covers provider coexistence.

### Production activation

Image packaging alone does not switch the live realm's theme. Roll out in order:

1. Build and publish the image, then pin and deploy its immutable digest through
   `grounds-pulumi/core/src/platform/keycloak.ts` (`KEYCLOAK_IMAGE`). Verify every
   Keycloak replica contains the theme before selecting it.
2. Add `loginTheme: "grounds"` to the existing `realmSettings` owner in
   `grounds-pulumi/core/src/platform/portal-keycloak.ts`, preview the realm update
   and apply only the intended change. An existing realm is not updated by
   editing its bootstrap `KeycloakRealmImport` alone.
3. Check the real Minecraft/Microsoft redirect, Portal/Hub SSO reuse and Control
   Center MFA. Native username/password accounts exist only in the smoke fixture;
   this theme does not add that login method to Grounds.

For rollback, restore the previous login-theme selection before rolling back
to an image without the `grounds` theme. Preserve the existing realm policies,
client settings, providers and credentials throughout.

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
