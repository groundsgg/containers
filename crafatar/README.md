# crafatar

[Crafatar](https://github.com/crafatar/crafatar) — Minecraft avatars, skins, capes and 3D renders by UUID, fetched from Mojang's session server. Serves `avatars.grounds.gg` from the core cluster (grounds-pulumi `core/src/platform/avatars.ts`); Mojang's session server answers from there, but not from Cloudflare Workers.

Built from a pinned upstream commit (`CRAFATAR_COMMIT`, a full SHA) on Node 24 LTS. amd64 only, like the core nodes.

## Pull

```bash
docker pull ghcr.io/groundsgg/crafatar:edge
```

Every push to `main` also tags `sha-<commit>`; pin that, by digest.

## Build

```bash
docker build -f crafatar/Dockerfile -t crafatar .
```

## Run

Crafatar needs a Redis (it stores which skin hash a UUID had and when it was last checked), and writes the rendered images below `/app/images/{faces,helms,skins,renders,capes}`. Both are caches; losing them only costs Mojang lookups.

```bash
docker run --rm -p 3000:3000 -e REDIS_URL=redis://redis:6379 ghcr.io/groundsgg/crafatar:edge
curl -o head.png 'http://localhost:3000/avatars/069a79f4-44e9-4726-a5be-fca90e38aaf5?size=128&overlay'
```

UUIDs work with or without dashes (no redirect). An unknown UUID gets Steve or Alex, chosen from the UUID. `overlay` adds the hat layer; legacy 64×32 skins whose hat area is fully opaque keep their plain face, as in the game.

The root filesystem can be read-only: mount writable volumes over the five image directories. The image creates them, but an empty volume mounted over `/app/images` hides them, and Crafatar does not create them itself.

### Environment

Read by upstream's `config.js`. The ones that matter here:

| Variable | Default | |
|---|---|---|
| `REDIS_URL` | `redis://localhost:6379` | |
| `PORT` / `BIND` | `3000` / `0.0.0.0` | |
| `CACHE_LOCAL` | `1200` | seconds before a UUID's skin is checked again |
| `CACHE_BROWSER` | `3600` | `Cache-Control: max-age` on successful responses |
| `EPHEMERAL_STORAGE` | `false` | `true` flushes Redis on start; set it when the image volume does not outlive Redis |
| `AVATAR_MAX` | `512` | largest `size` accepted |
| `EXTERNAL_HTTP_TIMEOUT` | `2000` | ms per Mojang request |
| `SESSIONS_RATE_LIMIT` | unset | outgoing session-server requests per second |

## Patches

Upstream pins Node 12. Its native dependencies do not build, or build but break, on any Node that still gets security fixes, so this image changes these and nothing in Crafatar's own code:

| | Why |
|---|---|
| `patches/canvas-3.patch` + `package-lock.json`: canvas 2.11.2 → 3.2.3 | canvas 2 has no Node 24 build and does not compile against Node 26. Compiled for Node 24 against a newer nan, it loads but its `width`/`height` setters no longer reach the native object, so every 3D render fails with a 0×0 canvas. canvas 3 is N-API; Crafatar's render code runs on it unchanged. The lockfile is upstream's with only canvas' own dependencies and nan (2.18 → 2.30, which lwip needs for current V8) changed. canvas is compiled from source against Debian's cairo/pango instead of downloading its prebuilt binary. |
| `patches/lwip-node24.patch`, six lines in `src/image/image.cpp` | `Object::CreationContext` and context-less `Object::Set` were removed from V8; replaced by their Nan equivalents. |
| `patches/lwip-node24.patch`, `PNG_ZLIB_VERNUM` → 0 | lwip's bundled libpng was configured against zlib 1.2.11 and refuses to compile against another `zlib.h`; Node's headers ship zlib 1.3. 0 is libpng's own "prebuilt config" value, which skips that compile-time check. |
| `patches/lwip-node24.patch`, `#include <unistd.h>` in the bundled zlib's `gzguts.h` | GCC 14 (Debian trixie) makes implicit function declarations an error; `lseek`/`read`/`write`/`close` were undeclared. Same fix as zlib 1.2.12. |

Upstream's test suite (`npm test`, needs Redis and Mojang) on this build: 91 passing, 16 failing. All 16 compare a CRC32 of the PNG bytes; the pixels are identical (checked against the bundled Steve images), only the zlib output differs. Upstream's Dockerfile runs the tests during the build; this one does not, since they need the network and a live Mojang. The build does check that both native modules load and that canvas can size a canvas.

Bumping `CRAFATAR_COMMIT`: if upstream changes `package.json` or its lockfile, regenerate `package-lock.json` (`npm install --package-lock-only` after applying `canvas-3.patch`) and check the patches still apply.
