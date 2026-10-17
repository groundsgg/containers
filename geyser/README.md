# geyser

[Geyser](https://geysermc.org/) Standalone — the Bedrock entry point. It terminates the RakNet/UDP session a Bedrock client opens, translates it to the Java protocol, and connects to a Velocity proxy as an ordinary Java client. Nothing downstream of the proxy learns the player came from Bedrock.

Deployed by the `grounds-geyser` Helm chart, which mounts the rendered `config.yml` and the Floodgate key into this image.

## Pull

```bash
docker pull ghcr.io/groundsgg/geyser:latest
```

## Build

```bash
docker build -f geyser/Dockerfile -t geyser .
```

To use a specific Geyser build:

```bash
docker build -f geyser/Dockerfile \
  --build-arg GEYSER_VERSION=2.12.0 \
  --build-arg GEYSER_BUILD=1264 \
  --build-arg GEYSER_SHA256=b4ecc3bec2206781013b58350955aedf63ca29e70637d476c92835a3214ff2fd \
  -t geyser .
```

All three move together — the checksum is verified against the download, so a version bump without a matching digest fails the build rather than shipping something unverified.

## Paths this image guarantees

Geyser resolves everything relative to its working directory, so these are a contract with the chart, not an implementation detail:

| Path | What puts it there |
|---|---|
| `/opt/geyser/Geyser.jar` | this image |
| `/opt/geyser/packs/grounds-bedrock.mcpack` | reproducible build from `pack-src/`; sent by Geyser's existing mandatory pack policy |
| `/opt/geyser/config.yml` | `grounds-geyser` ConfigMap, mounted read-only via `subPath` |
| `/opt/geyser/floodgate/key.pem` | the `floodgate-key` Secret |

The config is mounted as a single file rather than a directory, because Geyser also writes caches and logs into this directory at runtime.

## Mandatory Bedrock nameplate pack

The bundled Bedrock pack supplies the Bedrock block icon at U+E500
(`font/glyph_E5.png`, first 16×16 cell) and full-colour rank badges on page E6
(`font/glyph_E6.png`, 64×64 cells). Both native nameplates and the Bedrock tab list
select the same badge codepoint by resolved rank label and actual role RGB.
Badge backgrounds use the same slices and compact white letter pixels as Java's tab list.
They are sent with a white tint so the white lettering stays white. Native E0
controller icons and the E5 edition icon stay intact. Java's pack and its release
channels are independent.

`pack-src/rank-badges.properties` is exported display metadata from central Stage
Permissions roles, not another source of role colours. Keep the identical catalog at
`gg/grounds/display/bedrock-rank-badges.properties` in plugin-permissions/minestom
and plugin-proxy/velocity. After changing a role label or colour, export its actual
stored value, regenerate the badge atlas, increase the manifest version, and deploy
pack plus consumers together. Clients reconnect to receive the new mandatory pack.
Until a new variant is available, a changed/custom role remains readable with its
actual label and colour; Java glyphs are never sent as a Bedrock substitute.

The committed 16×16 block texture comes from
[Mojang's Bedrock samples](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/textures/blocks/bedrock.png)
(SHA-256 `20ced86ba8cb89e29e2115f76c758278893e145575a73ca311bcc4305c140d04`).
Texture copyright belongs to Mojang; see the samples repository's license.
Build the tiny pack locally with JDK 25:

```sh
java -Djava.awt.headless=true geyser/pack-src/BuildPack.java geyser/pack-src/bedrock.png /tmp/grounds-bedrock.mcpack
```

## Why it is pinned

Bedrock changes its protocol roughly every six weeks and Geyser follows. A floating tag would turn an unrelated pod restart into an unannounced protocol upgrade — and a Geyser one version behind rejects every client that has already auto-updated. Bump it deliberately.

## Why there is no healthcheck

Geyser speaks RakNet over UDP and exposes no HTTP endpoint. A UDP port probe cannot tell "listening" from "nothing there", so it would report healthy for a dead process. An honest check needs an unconnected-ping round trip; Kubernetes restarts the pod when the process exits, which covers the case that actually happens.
