#!/bin/sh
set -eu

# Geyser does not just read config.yml, it rewrites it: on startup it loads the
# file, fills in every key it has a default for, and saves the result back
# atomically — temp file, then rename over the original.
#
# Mounting the ConfigMap directly at /opt/geyser/config.yml therefore does not
# work. The rename fails with "Resource busy" against the read-only bind mount
# and Geyser refuses to start, which reads as a config error rather than as a
# mount problem.
#
# So the mount lands at /config and is copied into the working directory, which
# is an ordinary writable layer. Geyser's rewrite goes to the copy and is
# discarded when the pod restarts — the ConfigMap stays the only source of
# truth, which is what we want anyway.
if [ -f /config/config.yml ]; then
    cp /config/config.yml /opt/geyser/config.yml
fi

exec java -XX:+AlwaysPreTouch -XX:+ParallelRefProcEnabled -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -jar /opt/geyser/Geyser.jar
