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
#
# Deliberately BEFORE the offline-mode block: that one forces this key off too,
# and it has to be able to override an explicit `true` here. An offline player
# carries no Mojang-signed key either, so enforcing it there would reject them
# one step later, looking like an unrelated failure.
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

# Offline mode, for load testing only. Bot swarms (azalea, mineflayer) have no
# Mojang account, so an online-mode proxy rejects them during login and no load
# ever reaches the backends.
#
# Opt-in and exact-match on the literal `false`: a typo, an empty value or an
# unset variable all leave the baked velocity.toml untouched, so every existing
# deployment keeps Mojang authentication. The only way to turn this off is to
# mean it.
#
# force-key-authentication goes off together with it — offline players carry no
# Mojang-signed profile key, so leaving it on rejects them one step later, which
# looks like an unrelated failure.
if [ "${VELOCITY_ONLINE_MODE:-}" = "false" ]; then
    echo "WARNING: VELOCITY_ONLINE_MODE=false — Mojang authentication is OFF." >&2
    echo "WARNING: anyone can join as any username, including staff names." >&2
    echo "WARNING: this proxy must not be reachable from the public internet." >&2
    sed -i \
        -e 's/^online-mode = .*/online-mode = false/' \
        -e 's/^force-key-authentication = .*/force-key-authentication = false/' \
        /app/velocity.toml
    # Velocity would silently start in online mode if the keys ever move or get
    # renamed upstream, and the bots' rejection would look like a network fault.
    # Fail loudly instead.
    grep -q '^online-mode = false$' /app/velocity.toml || {
        echo "FATAL: could not disable online-mode in /app/velocity.toml" >&2
        exit 1
    }
fi

# Login rate limit, for load testing only. Velocity allows one login per source
# IP every `login-ratelimit` milliseconds (3000 by default). A bot fleet shares
# one pod IP per worker, so that ceiling is the ramp: one bot per 3 s per
# worker, and 750 bots over four workers takes eleven minutes before the test
# has even started.
#
# The failure mode when it bites is not an error. Bots connect, get kicked with
# "You are logging in too fast", the fleet's reconcile loop replaces them, and
# the whole thing churns at a constant few hundred looking like a server
# problem. That is worth an opt-in knob.
#
# Numeric and opt-in: a non-numeric value is refused rather than silently
# ignored, because a typo here would restore the 3 s default and re-create
# exactly the confusing symptom above.
if [ -n "${VELOCITY_LOGIN_RATELIMIT:-}" ]; then
    case "$VELOCITY_LOGIN_RATELIMIT" in
        ''|*[!0-9]*)
            echo "FATAL: VELOCITY_LOGIN_RATELIMIT must be milliseconds, got '$VELOCITY_LOGIN_RATELIMIT'" >&2
            exit 1
            ;;
    esac
    if [ "$VELOCITY_LOGIN_RATELIMIT" -lt 3000 ]; then
        echo "WARNING: VELOCITY_LOGIN_RATELIMIT=${VELOCITY_LOGIN_RATELIMIT}ms is below Velocity's" >&2
        echo "WARNING: 3000ms default — this proxy is easier to flood with logins." >&2
    fi
    sed -i "s/^login-ratelimit = .*/login-ratelimit = ${VELOCITY_LOGIN_RATELIMIT}/" /app/velocity.toml
    grep -q "^login-ratelimit = ${VELOCITY_LOGIN_RATELIMIT}$" /app/velocity.toml || {
        echo "FATAL: could not set login-ratelimit in /app/velocity.toml" >&2
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
