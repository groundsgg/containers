# buildserver

Runnable Stage/prod buildserver image: `ghcr.io/groundsgg/buildsystem` plus pinned builder tools.

## Base

`FROM ghcr.io/groundsgg/buildsystem:sha-e564bc1@sha256:7ed86449527f1df4255af327c50936ed1627268705cdc9a69dc06315a2676080`. That layer already has BuildSystem, GroundsMaps (including `/map pull` and `/map import`), and plugin-permissions.

Scene Editor Maven URL: `https://maven.pkg.github.com/groundsgg/plugin-scene-editor/gg/grounds/plugin-scene-editor-paper/0.3.0/plugin-scene-editor-paper-0.3.0.jar`.

Scene Editor SHA-256: `5e5bafbfc9358db5b69d920d6c2520d6026c20225f4a51daf8e0d5326ad3574f`.
This checksum identifies the published Maven artifact consumed by the image build.

## Paper

Paper **26.3 build 159** (BETA channel; the only 26.3 builds so far) replaces the server JAR inherited from BuildSystem.
The official download URL and SHA-256 are pinned in this image's Dockerfile,
so updating the buildserver does not require rebuilding the shared Paper base.

## Tool plugins (build-time pins)

| Plugin | Version | Notes |
|---|---|---|
| FastAsyncWorldEdit | 2.16.0 | Modrinth |
| FastAsyncVoxelSniper | 3.2.5 | Requires FAWE. No 26.3 release listed; boots and enables on 26.3. |
| Axiom Paper | 6.0.1+26.3 | Client mod separate; multiplayer needs Axiom commercial license / whitelist (account-side, not a K8s secret). Grant `axiom.default`. |
| goPaintAdvanced | 1.8.2 | No 26.3 release listed (its update check warns); boots and enables on 26.3. |
| CreativeUtilities | 1.5.0 | No 26.3 release listed (its update check warns); boots and enables on 26.3. |
| EasyArmorStands | 3.3.2 | |
| Grounds Scene Editor | 0.3.0 | Downloaded from the pinned GitHub Maven URL above using a BuildKit secret. |
| goBrushAdvanced | — | Deferred (no Paper 26.2/26.3 build) |
| HeadDatabase | — | Pending Spigot vendor jar |

SHA256 pins live as `ARG`s in the Dockerfile.

Moved to Minecraft 26.3 on 2026-10-07 (the network runs 26.3 since 2026-10-06, and
Velocity 4 cannot hand a 26.3 client to a 26.2 backend). A local boot of the image
enabled every plugin and reached `Done`; the `No key layers in MapLike[{}]` line at
world creation appears on the 26.2 image too. Before that, all tool versions were
checked against stable Minecraft 26.2 releases on 2026-09-02. FAWE, FAVS, goPaintAdvanced, CreativeUtilities, and EasyArmorStands
were already current. The inherited GroundsPlatform 0.6.1, GroundsPluginRuntime
0.1.1, and GroundsPermissions 0.11.0 are the latest published releases.

## Persistence

Mount a PVC at `/data` and set `BUILDSERVER_DATA_ROOT=/data` (optional; default is `/data`). The start wrapper symlinks durable world/plugin-data dirs from `/app` onto the PVC. Plugin JARs stay on the image.

## Seed a world

In game: `/map login`, then `/map import <https-url> <world> [sha256=<hex>]` fetches a `.zip` or `.tar.zst` and creates a new build world. Only map files are kept (datapacks are dropped), private addresses are refused and size limits apply; see GROUNDS.md in `groundsgg/buildsystem`. Without a network source: copy a world folder onto the PVC, then `/worlds import <name>`.

## Build locally

`buildsystem` is currently amd64-only, so CI builds `buildserver` for `linux/amd64` only until the base is multi-arch.

```bash
gh auth token | docker buildx build \
  --platform linux/amd64 \
  --secret id=github_token,src=/dev/stdin \
  -f buildserver/Dockerfile \
  -t ghcr.io/groundsgg/buildserver:local \
  --load .
docker run --rm --entrypoint sh ghcr.io/groundsgg/buildserver:local -c 'ls /app/plugins | sort'
```

`gh auth token` streams the authenticated GitHub CLI token directly to the
BuildKit secret mount; it is neither printed nor written to a file.
