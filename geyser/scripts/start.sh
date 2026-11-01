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

if [ -n "${GROUNDS_GEYSER_IDENTITY_FILE:-}" ]; then
    # Geyser tightens PEM permissions and rejects symlinks. Copy the projected
    # read-only Secret into the writable data directory rather than mounting
    # it directly at the final path.
    test -s "$GROUNDS_GEYSER_IDENTITY_FILE"
    mkdir -p /opt/geyser/nethernet
    chmod 700 /opt/geyser/nethernet
    cp "$GROUNDS_GEYSER_IDENTITY_FILE" /opt/geyser/nethernet/identity.pem
    chmod 600 /opt/geyser/nethernet/identity.pem
fi

set -- java -XX:+AlwaysPreTouch -XX:+ParallelRefProcEnabled -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC
# No shell expansion of the address: this value comes from the Downward API.
if [ -n "${GROUNDS_GEYSER_ADVERTISE_ADDRESS:-}" ]; then
    set -- "$@" "-DgeyserAdvertiseAddresses=$GROUNDS_GEYSER_ADVERTISE_ADDRESS"
fi
exec "$@" -jar /opt/geyser/Geyser.jar
