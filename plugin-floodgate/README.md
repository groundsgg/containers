# plugin-floodgate

Ships the [Floodgate](https://geysermc.org/wiki/floodgate/) Velocity JAR so a `grounds-velocity` proxy can fetch it at startup.

Nothing in this image is ever executed. The `plugin-velocity-jar` chart's init-container overrides the entrypoint and copies `/jar/plugin.jar` into a shared volume, which a tiny httpd container then serves at `/plugin.jar`; the proxy's own init-containers fetch it from there.

## Pull

```bash
docker pull ghcr.io/groundsgg/plugin-floodgate:latest
```

## Build

```bash
docker build -f plugin-floodgate/Dockerfile -t plugin-floodgate .
```

To use a specific Floodgate build:

```bash
docker build -f plugin-floodgate/Dockerfile \
  --build-arg FLOODGATE_VERSION=2.2.5 \
  --build-arg FLOODGATE_BUILD=140 \
  --build-arg FLOODGATE_SHA256=f5867ad79b90d38abcc72755a685428fbcf423b52c9830a39ffed5203de6936a \
  -t plugin-floodgate .
```

## What Floodgate is for

A Bedrock player has an Xbox Live account and no Mojang account, so an online-mode proxy has no Java login to verify. Geyser authenticates the player against Xbox Live and signs the resulting player data with a key shared with this plugin; Floodgate verifies that signature and admits the player.

The UUID it assigns is derived from the player's XUID and shaped `00000000-0000-0000-XXXX-XXXXXXXXXXXX`. The zeroed high bits are the point: the UUID can never collide with a real Java UUID, and any service in the network can recognise a Bedrock player from `uuid.mostSignificantBits == 0` without taking a dependency on Floodgate.

The alternative — an offline-mode proxy — derives UUIDs from the *username*, which means a Bedrock player and a Java player sharing a name share an identity.

## Two things it needs on the proxy

- `force-key-authentication = false`, because Bedrock players have no Mojang profile key and are kicked at login while it is enforced. The `velocity` image reads `VELOCITY_FORCE_KEY_AUTHENTICATION` at boot so only the Bedrock proxy relaxes it.
- The same key file Geyser uses, as the `floodgate-key` Secret. It is provisioned out-of-band, the way `velocity-forwarding-secret` is — a generated key would differ on each side and every login would fail its signature check.
