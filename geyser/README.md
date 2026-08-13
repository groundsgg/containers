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
  --build-arg GEYSER_VERSION=2.11.1 \
  --build-arg GEYSER_BUILD=1219 \
  --build-arg GEYSER_SHA256=b7f1875bfff183a9f27f8bdfea0d289e030516c8d7b7c37678733a79883a9451 \
  -t geyser .
```

All three move together — the checksum is verified against the download, so a version bump without a matching digest fails the build rather than shipping something unverified.

## Paths this image guarantees

Geyser resolves everything relative to its working directory, so these are a contract with the chart, not an implementation detail:

| Path | What puts it there |
|---|---|
| `/opt/geyser/Geyser.jar` | this image |
| `/opt/geyser/config.yml` | `grounds-geyser` ConfigMap, mounted read-only via `subPath` |
| `/opt/geyser/floodgate/key.pem` | the `floodgate-key` Secret |

The config is mounted as a single file rather than a directory, because Geyser also writes caches and logs into this directory at runtime.

## Why it is pinned

Bedrock changes its protocol roughly every six weeks and Geyser follows. A floating tag would turn an unrelated pod restart into an unannounced protocol upgrade — and a Geyser one version behind rejects every client that has already auto-updated. Bump it deliberately.

## Why there is no healthcheck

Geyser speaks RakNet over UDP and exposes no HTTP endpoint. A UDP port probe cannot tell "listening" from "nothing there", so it would report healthy for a dead process. An honest check needs an unconnected-ping round trip; Kubernetes restarts the pod when the process exits, which covers the case that actually happens.
