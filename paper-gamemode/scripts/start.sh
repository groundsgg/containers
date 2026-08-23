#!/bin/sh
set -eu

CUSTOM_SYSTEM_PROPERTIES="-Dcom.mojang.eula.agree=true"

# World type: flat by default → near-instant world-gen (fast spawn on the dev
# platform; the empty default world is fine for gamemode dev). Override per-app
# via the LEVEL_TYPE env (e.g. LEVEL_TYPE=normal). Mirrors paper/scripts/start.sh.
sed -i "s/^level-type=.*/level-type=${LEVEL_TYPE:-flat}/" /app/server.properties

# Paper charts inject PAPER_VELOCITY_SECRET. Keep the older
# VELOCITY_FORWARDING_SECRET name as a compatibility alias for existing
# workloads that use this image directly.
velocity_secret=${PAPER_VELOCITY_SECRET:-${VELOCITY_FORWARDING_SECRET:-}}
if [ -n "$velocity_secret" ]; then
    sed -i 's/^online-mode=.*/online-mode=false/' /app/server.properties
    awk -v sec="$velocity_secret" '
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
