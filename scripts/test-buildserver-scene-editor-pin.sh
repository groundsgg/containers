#!/bin/sh
set -eu

# Regression test for the image contract. It deliberately reads the values that
# human operators use in the README, then verifies the resulting image rather
# than treating Dockerfile text as the only evidence.
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
IMAGE=${1:-grounds-buildserver:scene-editor-test}

base_image='ghcr.io/groundsgg/buildsystem:sha-7c85294@sha256:e81e6f8a6f2e64d450ae9150c6a77fc8bab4ee5ac49345d2abbc7f2546947f23'
scene_editor_url='https://maven.pkg.github.com/groundsgg/plugin-scene-editor/gg/grounds/plugin-scene-editor-paper/0.1.0/plugin-scene-editor-paper-0.1.0.jar'
scene_editor_sha256='50be2507ddee8fa896abd1571c202cdc63e5aa3a8f905e2546da85576579ef17'

fail=0
require_arg() {
    file=$1
    name=$2
    expected=$3
    description=$4
    values=$(awk -v prefix="ARG $name=" 'index($0, prefix) == 1 { print substr($0, length(prefix) + 1) }' "$file")
    count=$(printf '%s\n' "$values" | sed '/^$/d' | wc -l | tr -d ' ')
    if [ "$count" -ne 1 ] || [ "$values" != "$expected" ]; then
        echo "$description must have exactly one operative ARG with value $expected in $file" >&2
        fail=1
    fi
}

require_readme_line() {
    file=$1
    text=$2
    description=$3
    matches=$(grep -Fxc "$text" "$file" || true)
    if [ "$matches" -ne 1 ]; then
        echo "expected one $description in $file, found $matches" >&2
        fail=1
    fi
}

require_arg "$ROOT_DIR/buildserver/Dockerfile" BUILD_SYSTEM_IMAGE "$base_image" 'immutable BuildSystem pin'
require_arg "$ROOT_DIR/buildserver/Dockerfile" SCENE_EDITOR_URL "$scene_editor_url" 'Scene Editor Maven URL pin'
require_arg "$ROOT_DIR/buildserver/Dockerfile" SCENE_EDITOR_SHA256 "$scene_editor_sha256" 'Scene Editor SHA-256 pin'
require_readme_line "$ROOT_DIR/buildserver/README.md" "\`FROM $base_image\`. That layer already has BuildSystem, GroundsMaps (including \`/map pull\`), and plugin-permissions." 'README immutable BuildSystem pin'
require_readme_line "$ROOT_DIR/buildserver/README.md" "Scene Editor Maven URL: \`$scene_editor_url\`." 'README Scene Editor Maven URL pin'
require_readme_line "$ROOT_DIR/buildserver/README.md" "Scene Editor SHA-256: \`$scene_editor_sha256\`." 'README Scene Editor SHA-256 pin'

plugin_listing=$(docker run --rm --entrypoint sh "$IMAGE" -c 'find /app/plugins -maxdepth 1 -type f -name "*.jar" -exec basename {} \; | sort')
plugin_count() {
    pattern=$1
    printf '%s\n' "$plugin_listing" | grep -Ec "$pattern" || true
}

assert_plugin_count() {
    pattern=$1
    expected=$2
    description=$3
    actual=$(plugin_count "$pattern")
    if [ "$actual" -ne "$expected" ]; then
        echo "expected $expected $description JAR(s), found $actual" >&2
        printf '%s\n' "$plugin_listing" >&2
        fail=1
    fi
}

assert_plugin_count '^GroundsSceneEditor[^/]*\.jar$' 1 'GroundsSceneEditor'
assert_plugin_count '^BuildSystem[^/]*\.jar$' 1 'BuildSystem'
assert_plugin_count '^GroundsMaps[^/]*\.jar$' 1 'GroundsMaps'

editor_sha=$(docker run --rm --entrypoint sh "$IMAGE" -c 'set -eu; editor=$(find /app/plugins -maxdepth 1 -type f -name "GroundsSceneEditor*.jar"); [ "$(printf "%s\\n" "$editor" | wc -l | tr -d " ")" = 1 ]; sha256sum "$editor" | awk "{print \$1}"' 2>/dev/null || true)
if [ "$editor_sha" != "$scene_editor_sha256" ]; then
    echo "Scene Editor JAR SHA-256 mismatch: expected $scene_editor_sha256, got ${editor_sha:-missing}" >&2
    fail=1
fi

[ "$fail" -eq 0 ]
