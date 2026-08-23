#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

run_case() {
    component=$1
    secret_name=$2
    expected_online_mode=$3

    test_dir=$(mktemp -d)
    trap 'rm -rf "$test_dir"' EXIT HUP INT TERM

    mkdir -p "$test_dir/config" "$test_dir/test-bin"
    cp "$ROOT_DIR/$component/config/server.properties" "$test_dir/server.properties"
    cp "$ROOT_DIR/$component/config/paper-global.yml" "$test_dir/config/paper-global.yml"
    cp "$ROOT_DIR/$component/scripts/start.sh" "$test_dir/start.sh"

    cat > "$test_dir/test-bin/java" <<'EOF'
#!/bin/sh
set -eu

grep -Fx 'level-type=flat' /app/server.properties >/dev/null

expected_online_mode=$(cat /app/.expected-online-mode)
grep -Fx "online-mode=$expected_online_mode" /app/server.properties >/dev/null

if [ "$expected_online_mode" = false ]; then
    grep -Fx '    enabled: true' /app/config/paper-global.yml >/dev/null
    grep -Fxf /app/.expected-yaml-secret /app/config/paper-global.yml >/dev/null
else
    grep -Fx '    enabled: false' /app/config/paper-global.yml >/dev/null
    grep -Fx "    secret: ''" /app/config/paper-global.yml >/dev/null
fi
EOF
    printf '%s' "$expected_online_mode" > "$test_dir/.expected-online-mode"
    printf '%s\n' "    secret: 'stage''forwarding\secret'" > "$test_dir/.expected-yaml-secret"
    chmod -R a+rwX "$test_dir"
    chmod a+x "$test_dir/start.sh" "$test_dir/test-bin/java"

    docker_args="--rm --entrypoint sh -e PATH=/app/test-bin:/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin -v $test_dir:/app"
    if [ -n "$secret_name" ]; then
        docker_args="$docker_args -e $secret_name=stage'forwarding\secret"
    fi

    # shellcheck disable=SC2086 # Docker arguments are intentionally assembled as words.
    docker run $docker_args alpine:3.24 /app/start.sh

    rm -rf "$test_dir"
    trap - EXIT HUP INT TERM
}

for component in paper paper-gamemode; do
    run_case "$component" PAPER_VELOCITY_SECRET false
    run_case "$component" VELOCITY_FORWARDING_SECRET false
    run_case "$component" '' true
done
