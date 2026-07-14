#!/usr/bin/env bash

set -euo pipefail

image="${1:?usage: $0 <image>}"
providers_dir="/opt/keycloak/providers"
minecraft_provider="${providers_dir}/keycloak-minecraft-idp.jar"
permissions_provider="${providers_dir}/keycloak-permissions-event-listener.jar"
tmp_dir="$(mktemp -d)"
container_id="$(docker create "$image")"
network="keycloak-provider-smoke-${RANDOM}-${RANDOM}"
postgres_container=""
nats_container=""
keycloak_container=""

cleanup() {
  for running_container in "$keycloak_container" "$nats_container" "$postgres_container"; do
    if [[ -n "$running_container" ]]; then
      docker rm -f "$running_container" >/dev/null 2>&1 || true
    fi
  done
  docker rm "$container_id" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

docker cp "${container_id}:${minecraft_provider}" "${tmp_dir}/keycloak-minecraft-idp.jar"
docker cp "${container_id}:${permissions_provider}" "${tmp_dir}/keycloak-permissions-event-listener.jar"

test -s "${tmp_dir}/keycloak-minecraft-idp.jar"
test -s "${tmp_dir}/keycloak-permissions-event-listener.jar"
if cmp -s \
  "${tmp_dir}/keycloak-minecraft-idp.jar" \
  "${tmp_dir}/keycloak-permissions-event-listener.jar"; then
  echo "Keycloak provider verification failed (reason=provider_artifacts_are_identical)" >&2
  exit 1
fi

config="$(docker run --rm \
  -e KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_NATS_URL="nats://nats:4222" \
  -e KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_REALM="grounds" \
  -e KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_SUBJECT="minecraft-identity.changed" \
  "$image" show-config)"

grep -Fq "kc.provider.file.keycloak-permissions-event-listener.jar.last-modified" <<<"$config"
grep -Fq "kc.spi-events-listener-permissions-events-nats-url =  nats://nats:4222" <<<"$config"
grep -Fq "kc.spi-events-listener-permissions-events-realm =  grounds" <<<"$config"
grep -Fq "kc.spi-events-listener-permissions-events-subject =  minecraft-identity.changed" <<<"$config"

cat >"${tmp_dir}/grounds-realm.json" <<'EOF'
{
  "realm": "grounds",
  "enabled": true,
  "eventsListeners": ["jboss-logging", "permissions-events"]
}
EOF

docker network create "$network" >/dev/null
postgres_container="$(docker run -d \
  --memory 256m \
  --network "$network" \
  --network-alias postgres \
  -e POSTGRES_DB=keycloak \
  -e POSTGRES_USER=keycloak \
  -e POSTGRES_PASSWORD=keycloak \
  postgres:17-alpine)"
nats_container="$(docker run -d \
  --memory 128m \
  --network "$network" \
  --network-alias nats \
  nats:2.11-alpine -js)"

for _ in {1..30}; do
  if docker logs "$nats_container" 2>&1 | grep -Fq "Server is ready"; then
    break
  fi
  sleep 1
done
if ! docker logs "$nats_container" 2>&1 | grep -Fq "Server is ready"; then
  docker logs "$nats_container" >&2 || true
  container_state="$(docker inspect -f 'running={{.State.Running}}, exitCode={{.State.ExitCode}}, oomKilled={{.State.OOMKilled}}, error={{.State.Error}}' "$nats_container" 2>/dev/null || echo 'unavailable')"
  echo "Keycloak provider runtime verification failed (reason=nats_not_ready, ${container_state})" >&2
  exit 1
fi

for _ in {1..30}; do
  if docker exec "$postgres_container" pg_isready -U keycloak -d keycloak >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
if ! docker exec "$postgres_container" pg_isready -U keycloak -d keycloak >/dev/null 2>&1; then
  docker logs "$postgres_container" >&2 || true
  container_state="$(docker inspect -f 'running={{.State.Running}}, exitCode={{.State.ExitCode}}, oomKilled={{.State.OOMKilled}}, error={{.State.Error}}' "$postgres_container" 2>/dev/null || echo 'unavailable')"
  echo "Keycloak provider runtime verification failed (reason=postgres_not_ready, ${container_state})" >&2
  exit 1
fi

keycloak_container="$(docker run -d \
  --memory 768m \
  --network "$network" \
  -v "${tmp_dir}/grounds-realm.json:/opt/keycloak/data/import/grounds-realm.json:ro" \
  -e KC_DB=postgres \
  -e KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak \
  -e KC_DB_USERNAME=keycloak \
  -e KC_DB_PASSWORD=keycloak \
  -e KC_HOSTNAME_STRICT=false \
  -e KC_HTTP_ENABLED=true \
  -e KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_NATS_URL=nats://nats:4222 \
  -e KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_REALM=grounds \
  -e KC_SPI_EVENTS_LISTENER_PERMISSIONS_EVENTS_SUBJECT=minecraft-identity.changed \
  "$image" start --optimized --import-realm)"

for _ in {1..90}; do
  if ! docker inspect -f '{{.State.Running}}' "$keycloak_container" 2>/dev/null | grep -Fxq true; then
    docker logs "$keycloak_container" >&2 || true
    container_state="$(docker inspect -f 'exitCode={{.State.ExitCode}}, oomKilled={{.State.OOMKilled}}, error={{.State.Error}}' "$keycloak_container" 2>/dev/null || echo 'unavailable')"
    echo "Keycloak provider runtime verification failed (reason=keycloak_exited, ${container_state})" >&2
    exit 1
  fi
  if docker exec "$keycloak_container" bash -c \
    'exec 3<>/dev/tcp/localhost/8080 && printf "GET /realms/grounds/.well-known/openid-configuration HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 && grep -q "HTTP/1.1 200" <&3' \
    >/dev/null 2>&1; then
    exit 0
  fi
  sleep 2
done

docker logs "$keycloak_container" >&2 || true
echo "Keycloak provider runtime verification failed (reason=realm_not_ready)" >&2
exit 1
