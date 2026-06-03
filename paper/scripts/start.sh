#!/bin/sh
set -eu

CUSTOM_SYSTEM_PROPERTIES="-Dcom.mojang.eula.agree=true"

# World type: flat by default → near-instant world-gen (fast spawn on the dev
# platform; the empty default world is fine for service/plugin dev). Override
# per-app via the LEVEL_TYPE env (e.g. LEVEL_TYPE=normal).
sed -i "s/^level-type=.*/level-type=${LEVEL_TYPE:-flat}/" /app/server.properties

exec java $CUSTOM_SYSTEM_PROPERTIES -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -jar /app/paper.jar nogui
