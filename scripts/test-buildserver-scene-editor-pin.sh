#!/bin/sh
set -eu

# Regression test for the image contract. It deliberately reads the values that
# human operators use in the README, then verifies the resulting image rather
# than treating Dockerfile text as the only evidence.
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
IMAGE=${1:-grounds-buildserver:scene-editor-test}

base_image='ghcr.io/groundsgg/buildsystem:sha-bb586c8@sha256:2b2622c1290c48fef3a676ec2efb6474c739d4bc62545a0e3231f5aabf4c831b'
scene_editor_url='https://maven.pkg.github.com/groundsgg/plugin-scene-editor/gg/grounds/plugin-scene-editor-paper/0.7.0/plugin-scene-editor-paper-0.7.0.jar'
scene_editor_sha256='d59dc4beeb2a58ad451e6dbe4233596db0fb3baedc3e3fa68ff60273446be7db'
runtime_sha256='89f973e0388604d37c6bce48e72b3a267041b7c5ba733fd2391f4e456228542b'

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
require_arg "$ROOT_DIR/buildserver/Dockerfile" GROUNDS_RUNTIME_PLUGIN_VERSION '0.3.0' 'Paper runtime version'
require_arg "$ROOT_DIR/buildserver/Dockerfile" GROUNDS_RUNTIME_PAPER_SHA256 "$runtime_sha256" 'Paper runtime SHA-256 pin'
require_readme_line "$ROOT_DIR/buildserver/README.md" "\`FROM $base_image\`. That layer already has BuildSystem, GroundsMaps (including \`/map pull\` and \`/map import\` with generated derivation metadata excluded from authoring roundtrips), and plugin-permissions." 'README immutable BuildSystem pin'
require_readme_line "$ROOT_DIR/buildserver/README.md" "Scene Editor Maven URL: \`$scene_editor_url\`." 'README Scene Editor Maven URL pin'
require_readme_line "$ROOT_DIR/buildserver/README.md" "Scene Editor SHA-256: \`$scene_editor_sha256\`." 'README Scene Editor SHA-256 pin'
require_readme_line "$ROOT_DIR/buildserver/README.md" '| Grounds Scene Editor | 0.7.0 | Downloaded from the pinned GitHub Maven URL above using a BuildKit secret. |' 'README Scene Editor version'

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
assert_plugin_count '^plugin-grounds-runtime\.jar$' 1 'GroundsPluginRuntime'

editor_sha=$(docker run --rm --entrypoint sh "$IMAGE" -c 'set -eu; editor=$(find /app/plugins -maxdepth 1 -type f -name "GroundsSceneEditor*.jar"); [ "$(printf "%s\\n" "$editor" | wc -l | tr -d " ")" = 1 ]; sha256sum "$editor" | awk "{print \$1}"' 2>/dev/null || true)
if [ "$editor_sha" != "$scene_editor_sha256" ]; then
    echo "Scene Editor JAR SHA-256 mismatch: expected $scene_editor_sha256, got ${editor_sha:-missing}" >&2
    fail=1
fi

runtime_sha=$(docker run --rm --entrypoint sh "$IMAGE" -c 'sha256sum /app/plugins/plugin-grounds-runtime.jar | cut -d " " -f 1')
if [ "$runtime_sha" != "$runtime_sha256" ]; then
    echo "Paper runtime JAR SHA-256 mismatch: expected $runtime_sha256, got $runtime_sha" >&2
    fail=1
fi

if ! docker run --rm --entrypoint sh "$IMAGE" -c 'unzip -l /app/plugins/plugin-grounds-runtime.jar | grep -q "gg/grounds/i18n/SystemMessageColors.class"'; then
    echo 'Paper runtime does not contain the shared chat palette' >&2
    fail=1
fi

if ! docker run --rm --entrypoint cat "$IMAGE" /opt/grounds/runtime-manifest.json | python3 -c 'import json, sys; m = json.load(sys.stdin); sys.exit(not (m.get("schemaVersion") == 1 and m.get("runtime", {}).get("id") == "plugin-grounds-runtime-paper" and m["runtime"].get("version") == "0.3.0" and m["runtime"].get("platform") == "paper" and isinstance(m.get("libraries"), list)))'; then
    echo 'Paper runtime manifest does not describe the selected runtime' >&2
    fail=1
fi

[ "$fail" -eq 0 ]
