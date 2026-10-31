# Verified transfer admission

This build-time patch fixes Velocity's shared-IP login limiter rejecting an automatic
Grounds transfer before plugin-player can validate its handoff. It is **off by default**.
Normal LOGIN uses Velocity's unchanged limiter. TRANSFER can only reach online
authentication after a bounded cookie check and service-player reservation.

The input first passes the existing privacy patch. Both transformations validate the
pinned upstream checksum and exact method anchors. An upstream change fails the build;
there is no instrumentation agent, plugin-event exemption, or separate Velocity fork.

## Contract and limits

- Set `GROUNDS_TRANSFER_ADMISSION_ENABLED=true` only with the matching plugin-player
  bridge and the admission/abort API on every service-player replica. Required existing
  settings: `PLAYER_SERVICE_URL`, `PROXY_ID`, rotating `GROUNDS_TOKEN_FILE`.
- Cookie: `grounds:player-session-handoff`, maximum 4096 bytes, original 15-second ticket.
- A destination-created random connection UUID and proxy ID bind the early reservation.
  That UUID is also the final exact session ID. The authenticated profile must match.
- Cookie + HTTP verification: maximum 3 seconds, HTTP request 2 seconds. Maximum 32
  pending/admitted connections per process. Online authentication is never skipped.
- Service-player shares a 32-outstanding-ticket issuance budget across source proxies
  and service replicas; each destination also has a database admission budget of 32.
  plugin-agones retries a full budget before its drain deadline and keeps protecting
  running rounds. Minting happens at the actual send attempt, never on queue insertion.
- Disconnect/timeout closes the pending socket and sends a bounded best-effort abort.
  Abort and final consume lock the same source row. Abort fences a future consume or
  deletes only the already committed destination UUID. Lost consume responses also
  trigger this exact abort/cleanup in plugin-player. Failure never rebinds an existing
  reservation or extends its expiry. Expired tickets cannot authenticate.
- No player IDs, IPs or tokens are metric labels. The service publishes admission result
  counters and wait/duration timers. Do not log admission payloads or cookie contents.

## Rollout and rollback gate

1. Publish and roll service-player's additive schema/API first; verify **all** serving
   replicas implement admission and abort. Old unreserved consumes remain compatible;
   reserved tickets refuse legacy unbound consumption.
2. Publish matching plugin-player and plugin-proxy, and bake the updated plugin-agones
   into the Velocity image. The gate remains off during this preparation.
3. Test a separate Stage target with the gate enabled: valid handoff, shared NAT,
   concurrent source drains, two destination proxies, restart twice, replay, timeout,
   missing service, close during consume, and rollback. Keep Bedrock's flag off.
4. Require authenticated Java-player smoke evidence before activating the public Stage
   receiver group. Local tests stop at the real online encryption request; their HTTP
   authority is a loopback fixture, **not** evidence of a live Mojang-authenticated move.
5. Before rollback, stop new sends to the canary, finish or close outstanding transfers
   and wait out the original ticket lifetime. Roll the gate/runtime/plugin group back
   together; retain the admission API until no gated proxy/reservation remains.

No public ingress or PROXY framing is changed by this patch. The separate IP-preservation
phase is gated: current Cilium audit mode does not enforce a trusted-router boundary.
Never enable `haproxy-protocol` against untrusted direct connections or switch a global
mixed Java/Geyser router to PROXY output. `externalTrafficPolicy: Local` also requires
continuous router coverage on every published node, including DNS-TTL overlap on removal.

## Current route inventory

| Route | Existing receiver framing | Admission/IP treatment |
| --- | --- | --- |
| Java public NodePort → mc-router → Java Velocity | raw Minecraft TCP | Cluster/SNAT + router shared IP; verified gate handles NAT transfers |
| Same-region, cross-proxy and cross-region host transfers | normal client TRANSFER through regional entry | same authoritative service reservation; `/proxy` also uses session handoff |
| Geyser UDP entry → Geyser → Bedrock Velocity | raw Java/Floodgate connector | gate off; preserve current wait-only Bedrock drain |
| Velocity → Java backend | modern player-info forwarding | separate backend protocol, no ingress PROXY toggle |
| Internal direct proxy connections and TCP readiness | raw TCP | no new trusted-header path; ordinary limiter unchanged |
| HTTP metrics/readiness | HTTP on existing management port | unaffected by Minecraft framing |

Run `python3 scripts/test-transfer-admission-build.py` with JDK 25. It fetches the pinned
upstream, checks reproducibility/reapplication/checksum/anchor drift, then runs real LOGIN
wire tests with the flag on/off, two Velocity processes and a bounded loopback authority.
