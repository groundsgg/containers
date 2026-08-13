#!/bin/sh
set -eu

# Modern player-info forwarding needs the shared secret as a FILE (velocity.toml
# references forwarding-secret-file="forwarding.secret"). Materialize it from the
# injected env at boot — printf, no trailing newline, so it byte-matches the
# secret on the backend servers.
if [ -n "${VELOCITY_FORWARDING_SECRET:-}" ]; then
    printf '%s' "$VELOCITY_FORWARDING_SECRET" > /app/forwarding.secret
fi

# velocity.toml is baked into the image, so a release that needs a different
# value for one key would otherwise need its own image. This is the one key that
# genuinely differs per proxy: Floodgate requires force-key-authentication off,
# because a Bedrock player has no Mojang profile key and is kicked at login while
# it is enforced.
#
# Only the Bedrock proxy sets this. Leaving it unset keeps the image default, so
# the Java proxies keep enforcing chat signatures — which is the whole reason
# Bedrock gets a proxy of its own rather than this being relaxed everywhere.
if [ -n "${VELOCITY_FORCE_KEY_AUTHENTICATION:-}" ]; then
    case "$VELOCITY_FORCE_KEY_AUTHENTICATION" in
        true|false) ;;
        *) echo "VELOCITY_FORCE_KEY_AUTHENTICATION must be 'true' or 'false', got '${VELOCITY_FORCE_KEY_AUTHENTICATION}'" >&2; exit 1 ;;
    esac
    sed -i "s/^force-key-authentication = .*/force-key-authentication = ${VELOCITY_FORCE_KEY_AUTHENTICATION}/" /app/velocity.toml
    grep -q "^force-key-authentication = ${VELOCITY_FORCE_KEY_AUTHENTICATION}$" /app/velocity.toml || {
        echo "Failed to set force-key-authentication in /app/velocity.toml" >&2
        exit 1
    }
fi

# Floodgate reads its key from its own plugin data directory, and it also
# CREATES a config.yml there on first start. Mounting the Secret straight onto
# plugins/floodgate would make that directory read-only and the plugin would
# fail to initialise — the same trap Geyser has with its config.
#
# So the Secret is mounted somewhere neutral and copied in, leaving the
# directory writable. Geyser and Floodgate have to see byte-identical keys:
# the key is what Geyser signs Bedrock player data with, and Floodgate rejects
# every login whose signature it cannot verify.
if [ -n "${FLOODGATE_KEY_FILE:-}" ]; then
    if [ ! -f "$FLOODGATE_KEY_FILE" ]; then
        echo "FLOODGATE_KEY_FILE is set to '${FLOODGATE_KEY_FILE}' but no such file exists" >&2
        exit 1
    fi
    mkdir -p /app/plugins/floodgate
    cp "$FLOODGATE_KEY_FILE" /app/plugins/floodgate/key.pem
fi

exec java -XX:+AlwaysPreTouch -XX:+ParallelRefProcEnabled -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=4M -XX:MaxInlineLevel=15 -jar /app/velocity.jar
