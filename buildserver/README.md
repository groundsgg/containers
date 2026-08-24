# buildserver

Runnable Stage/prod buildserver image: `ghcr.io/groundsgg/buildsystem` plus pinned builder tools.

## Base

`FROM ghcr.io/groundsgg/buildsystem:<immutable>` (currently `sha-2eb0086`). That layer already has BuildSystem, GroundsMaps (including `/map pull`), and plugin-permissions.

## Tool plugins (build-time pins)

| Plugin | Version | Notes |
|---|---|---|
| FastAsyncWorldEdit | 2.15.4 | Modrinth |
| FastAsyncVoxelSniper | 3.2.5 | Requires FAWE |
| Axiom Paper | 5.0.4+26.2 | Client mod separate; multiplayer needs Axiom commercial license / whitelist (account-side, not a K8s secret). Grant `axiom.default`. |
| goPaintAdvanced | 1.8.2 | |
| CreativeUtilities | 1.5.0 | |
| EasyArmorStands | 3.3.0 | |
| goBrushAdvanced | — | Deferred (no Paper 26.2 build) |
| HeadDatabase | — | Pending Spigot vendor jar |

SHA256 pins live as `ARG`s in the Dockerfile.

## Persistence

Mount a PVC at `/data` and set `BUILDSERVER_DATA_ROOT=/data` (optional; default is `/data`). The start wrapper symlinks durable world/plugin-data dirs from `/app` onto the PVC. Plugin JARs stay on the image.

## Seed a world

Copy a world folder onto the PVC (e.g. under `/data/` then into the Paper layout), then in-game `/worlds import <name>`. There is no client upload path.

## Build locally

`buildsystem` is currently amd64-only, so CI builds `buildserver` for `linux/amd64` only until the base is multi-arch.

```bash
docker buildx build -f buildserver/Dockerfile -t ghcr.io/groundsgg/buildserver:local --load .
docker run --rm --entrypoint sh ghcr.io/groundsgg/buildserver:local -c 'ls /app/plugins | sort'
```
