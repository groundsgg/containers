#!/bin/sh
set -eu

# Modern player-info forwarding needs the shared secret as a FILE (velocity.toml
# references forwarding-secret-file="forwarding.secret"). Materialize it from the
# injected env at boot — printf, no trailing newline, so it byte-matches the
# secret on the backend servers.
if [ -n "${VELOCITY_FORWARDING_SECRET:-}" ]; then
    printf '%s' "$VELOCITY_FORWARDING_SECRET" > /app/forwarding.secret
fi

exec java -XX:+AlwaysPreTouch -XX:+ParallelRefProcEnabled -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=4M -XX:MaxInlineLevel=15 -jar /app/velocity.jar
