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

| Provider                    | Build argument                                | Default version |
| --------------------------- | --------------------------------------------- | --------------- |
| Minecraft identity provider | `KEYCLOAK_MINECRAFT_VERSION`                  | `1.3.1`         |
| Permissions event listener  | `KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION` | `0.2.2`         |

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

The design reference is **Grounds Hub**, `groundsgg/grounds-hub@889f6d8`,
including its repo-internal component library and focused sign-in page:

- `src/styles/design-tokens.css` → `resources/css/hub-tokens.css`: unchanged
  Mini-CD v0.19 values and semantic roles, with Tailwind `@theme` emitted as
  ordinary `:root` CSS for native Keycloak.
- `src/components/design-system/button.tsx`, `select.tsx` and their shared
  `src/app/globals.css` rules, plus the text-button variant from
  `src/styles/design-system.css` → `resources/css/hub-components.css`:
  `.btn`, `.btn-gold`, `.btn-ghost` and `.field` presentation. Theme properties
  attach these classes to Keycloak's existing controls. The restart text action
  sits below the primary form and before the legal footer, using Keycloak's
  server-generated restart URL and the same visibility condition as its native
  identity control. Keyboard order follows this visual order.
- `src/app/[locale]/login/login.css` → the layout adapter `grounds.css`:
  Stone Deep/Surface, gold primary actions, Energy focus, 6px control/10px panel
  radii, a dark sign-in panel and the same Clockwork background. The Hub is
  intentionally dark regardless of the operating-system preference.
- Bricolage Grotesque variable font, logo, favicon and Clockwork images are
  copied unchanged from that Hub checkout. Latin and Latin Extended subsets
  are self-hosted; the SIL Open Font License is included next to the fonts.
  Footer copy follows the Hub in de/en/es/fr/tr.

Keycloak uses FreeMarker, so the Hub's React components cannot be imported
at runtime. The versioned token/component CSS snapshots form the native
adapter; no React runtime or second authentication implementation is added.
When the Hub library changes, refresh these snapshots and their recorded
revision together, then verify the Keycloak integration. There are no remote
font, script or image dependencies.

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

## Keycloakify candidate (`grounds-login`)

The Hub workspace now owns `@groundsgg/ui` and `@groundsgg/keycloak-theme`.
The new React theme consumes shared primitives, tokens, CSS and self-hosted
fonts. The native `grounds` theme above remains available for rollback.
The default image stays native until a released, reviewed artifact digest is
pinned; no placeholder digest or unreviewed `latest` is used.

Build a candidate with an immutable artifact reference and the JAR checksum
from its manifest:

```bash
docker build -f keycloak/Dockerfile \
  --build-arg "GROUNDS_LOGIN_THEME_IMAGE=$THEME_IMAGE" \
  --build-arg "GROUNDS_LOGIN_THEME_SHA256=$JAR_SHA256" \
  -t grounds-keycloak:theme-candidate .
npm ci --prefix keycloak/tests
npm run install:browsers --prefix keycloak/tests
node keycloak/tests/run.mjs grounds-keycloak:theme-candidate
```

`THEME_IMAGE` must be the released
`ghcr.io/groundsgg/grounds-login-theme@sha256:…` reference. The Dockerfile checks
the JAR checksum and rejects any other theme descriptor before adding it to
`providers` and running `kc.sh build`. The existing two providers and the
native theme are unchanged.

`keycloak-theme-candidate.yml` manually validates a released artifact on main,
builds AMD64 and ARM64, and runs the browser suite. It publishes no image and
changes no realm. Grant its repository read access to the Hub artifact package.

The smoke script automatically requires browser verification when the image
contains the candidate JAR. The explicit Node runner rejects an image without
that JAR. It creates one ephemeral callback server, imports isolated test realms,
and cleans up the callback server, Postgres, NATS and Keycloak. Tests cover
invalid/valid login and OTP, restart and tab order, SSO in both directions,
LoA 2 step-up, logout, an actual timed-out login and recovery, detached errors,
JavaScript/bundle failure, language switching during MFA and 320/360/1440px
layouts. Fixture LoA flows preserve the production Cookie/alternative-level
structure; local password authentication replaces the external Minecraft
redirect solely in fixtures. These tests do not prove live Microsoft/Xbox login.
The legacy Python missing-tab test remains separate from the real timeout test.

JavaScript is required by the candidate. A static loading/reload surface and
readable noscript hint remain if the bundle cannot execute. Confirm this policy
before live activation; no CSP relaxation is part of this change.

### Release and live activation order

1. Merge the reviewed Hub UI version/changelog PR. Publish private `@groundsgg/ui`
   through its manual main-only workflow. Verify private visibility and registry
   read access for downstream CI and Cloudflare builds.
2. Build the theme through the separate artifact workflow. It verifies that
   published UI bytes match the workspace, registers only `grounds-login`, and
   records source SHA, UI integrity, Keycloak target and JAR checksum. Capture
   the actual OCI digest; never substitute a tag or fabricated checksum.
3. Run the candidate workflow above. After acceptance, pin that exact artifact
   and checksum in a separate reviewed containers change and publish the new
   Keycloak image. Read and record the live Keycloak image and realm/client
   theme selections before changing them; checkout state is not live evidence.
4. Pin the new Keycloak image through the existing
   `grounds-pulumi/core/src/platform/keycloak.ts` owner and image-only preview/
   saved-plan workflow. Confirm every replica contains both theme names before
   selecting `grounds-login` anywhere.
5. Prepare a dedicated, strictly checked theme-selection preview/saved-plan
   workflow. Use the existing `hub-demo-client` owner for a temporary Hub beta
   canary; leave redirects, PKCE, LoA and flow bindings unchanged. Realm selection
   belongs to `grounds-realm-settings` in `portal-keycloak.ts`, never the
   create-only `KeycloakRealmImport`. A realm settings hash change also replaces
   `keycloak-realm-events-reconcile`; allow only the proven hash annotation change
   and unchanged job specification, never an arbitrary dependent mutation.
6. Verify live Hub/Portal SSO, Minecraft/Microsoft redirects, fresh and existing
   sessions, provider cancellation/failure, Control Center OTP and logout.
   Realm-wide selection requires the separate activation decision; restore the
   prior client override when the realm becomes the owner of the selection.

Rollback restores the captured realm/client selections first, then the prior
image if necessary. Never remove an image while its theme is selected. Native
snapshot cleanup is a later PR after acceptance and a secured rollback path.
The existing image-only guard must continue rejecting realm/client mutations.

## Configuration

This image runs Keycloak in optimized mode (`start --optimized`). All Keycloak configuration should be provided via environment variables at runtime. See the [Keycloak Server Configuration](https://www.keycloak.org/server/all-config) documentation for available options.

Required configuration:

| Variable         | Description                       |
| ---------------- | --------------------------------- |
| `KC_DB`          | Database vendor (e.g. `postgres`) |
| `KC_DB_URL`      | JDBC database URL                 |
| `KC_DB_USERNAME` | Database username                 |
| `KC_DB_PASSWORD` | Database password                 |
| `KC_HOSTNAME`    | Hostname for the Keycloak server  |

The permissions event listener also requires:

| Variable                                             | Required | Default                      | Description                            |
| ---------------------------------------------------- | -------- | ---------------------------- | -------------------------------------- |
| `KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_NATS_URL` | Yes      | -                            | NATS server URL with JetStream enabled |
| `KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_REALM`    | Yes      | -                            | Comma-separated accepted realm IDs     |
| `KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_SUBJECT`  | No       | `minecraft-identity.changed` | NATS subject for invalidation events   |

Enable the `permissions-events` event listener in the target realm. The configured subject must be retained by a JetStream stream before Keycloak publishes identity invalidations.

### Permissions listener 0.2 migration

Version 0.2.2 still filters configured realms by their stable Keycloak realm IDs, while published identity-change events include both the realm ID and realm name. Before deploying this image, replace any realm names in `KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_REALM` with their corresponding realm IDs. Multiple realm IDs can be provided as a comma-separated list.

## Dependency Updates

The base Keycloak image (`quay.io/keycloak/keycloak`) is tracked by Dependabot. The `KEYCLOAK_MINECRAFT_VERSION` and `KEYCLOAK_PERMISSIONS_EVENT_LISTENER_VERSION` build arguments are **not** automatically tracked by Dependabot and must be updated manually when new release assets of [keycloak-minecraft-idp](https://github.com/groundsgg/keycloak-minecraft-idp) or [keycloak-permissions-event-listener](https://github.com/groundsgg/keycloak-permissions-event-listener) are available.
