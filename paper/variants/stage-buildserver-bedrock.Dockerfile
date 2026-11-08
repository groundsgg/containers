# Keep the accepted Stage editor, map restore and plugin artifacts unchanged.
FROM ghcr.io/groundsgg/npc-stage-poc:backend-policy-paper-2.21.0-61fb57caee88@sha256:cee0ebfea6587605870867b6d5ec11bc26e2eea47974a592f42a7beabd1a77da
COPY --chown=paper:paper --chmod=755 paper/scripts/start.sh /opt/grounds/paper-start.sh
