#!/bin/sh
set -eu

CUSTOM_SYSTEM_PROPERTIES="-Dcom.mojang.eula.agree=true"

# Behind a Velocity proxy (modern player-info forwarding): when the shared
# secret is injected, enable Velocity forwarding + write the secret into
# paper-global.yml, and hand player auth to the proxy (offline backend). The
# same secret is on the Velocity side via VELOCITY_FORWARDING_SECRET; a
# gamemode GameServer is only reachable through the proxy, never directly.
if [ -n "${VELOCITY_FORWARDING_SECRET:-}" ]; then
    sed -i 's/^online-mode=.*/online-mode=false/' /app/server.properties
    awk -v sec="$VELOCITY_FORWARDING_SECRET" '
        BEGIN { q = "\047" }
        /^  velocity:/ { invel = 1; print; next }
        invel && /^[a-zA-Z]/ { invel = 0 }
        invel && /^    enabled:/ { print "    enabled: true"; next }
        invel && /^    secret:/ { print "    secret: " q sec q; next }
        { print }
    ' /app/config/paper-global.yml > /app/config/paper-global.yml.tmp \
        && mv /app/config/paper-global.yml.tmp /app/config/paper-global.yml
fi

exec java $CUSTOM_SYSTEM_PROPERTIES -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -jar /app/paper.jar nogui
