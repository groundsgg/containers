#!/bin/sh
set -eu

# Modern player-info forwarding needs the shared secret as a FILE (velocity.toml
# references forwarding-secret-file="forwarding.secret"). Materialize it from the
# injected env at boot — printf, no trailing newline, so it byte-matches the
# secret on the backend servers.
if [ -n "${VELOCITY_FORWARDING_SECRET:-}" ]; then
    printf '%s' "$VELOCITY_FORWARDING_SECRET" > /app/forwarding.secret
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

exec java -XX:+AlwaysPreTouch -XX:+ParallelRefProcEnabled -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=4M -XX:MaxInlineLevel=15 -jar /app/velocity.jar
