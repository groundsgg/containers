#!/bin/sh
set -eu

# Contract test for the buildserver's working-copy restore (buildserver/restore). Runs the real
# program against a local registry fixture: a fresh server, a full restore, a corrupt blob and a
# path that escapes the root. Needs a JDK 25 and python3, no Docker.
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
WORK=$(mktemp -d)
trap 'kill "$fixture_pid" 2>/dev/null || true; rm -rf "$WORK"' EXIT

javac -d "$WORK/classes" "$ROOT_DIR/buildserver/restore/WorkspaceRestore.java"

cat > "$WORK/fixture.py" <<'PY'
import hashlib, http.server, json, os, sys

work = sys.argv[1]
blobs = {"good": b"region bytes\n", "yml": b"worlds: {}\n"}

def entry(path, blob, sha=None, size=None):
    data = blobs[blob]
    return {"path": path, "sha256": sha or hashlib.sha256(data).hexdigest(),
            "size": len(data) if size is None else size,
            "url": f"http://127.0.0.1:{port}/blob/{blob}"}

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, body=b"", content_type="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        form = self.rfile.read(length).decode()
        ok = self.path == "/token" and "grant_type=client_credentials" in form and "client_secret=s3cret" in form
        self.reply(200 if ok else 401, json.dumps({"access_token": "tok"}).encode() if ok else b"{}")

    def do_GET(self):
        if self.path.startswith("/blob/"):
            if self.headers.get("Authorization"):
                return self.reply(400, b"bearer leaked to the bucket")
            return self.reply(200, blobs[self.path[len("/blob/"):]], "application/octet-stream")
        if self.headers.get("Authorization") != "Bearer tok":
            return self.reply(401)
        scenario = open(os.path.join(work, "scenario")).read().strip()
        if scenario == "fresh":
            return self.reply(404, b"{}")
        files = {
            "full": [entry("world/region/r.0.0.mca", "good"),
                     entry("world/dimensions/minecraft/lobby/region/r.0.0.mca", "good"),
                     entry("plugins/BuildSystem/worlds.yml", "yml")],
            "corrupt": [entry("world/region/r.0.0.mca", "good", sha="0" * 64)],
            "escape": [entry("world/../../etc/passwd", "good")],
        }[scenario]
        self.reply(200, json.dumps({"snapshot": 42, "files": files}).encode())

server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
port = server.server_address[1]
open(os.path.join(work, "port"), "w").write(str(port))
server.serve_forever()
PY

python3 "$WORK/fixture.py" "$WORK" &
fixture_pid=$!
for _ in $(seq 50); do [ -s "$WORK/port" ] && break; sleep 0.1; done
port=$(cat "$WORK/port")

fail=0
run_restore() {
    scenario=$1
    root=$2
    printf '%s\n' "$scenario" > "$WORK/scenario"
    mkdir -p "$root"
    GROUNDS_WORKSPACE_SERVER=stage-buildserver \
    GROUNDS_WORKSPACE_ROOT="$root" \
    GROUNDS_MAPS_CLIENT_SECRET=s3cret \
    GROUNDS_MAPS_TOKEN_URL="http://127.0.0.1:$port/token" \
    GROUNDS_MAPS_REGISTRY_URL="http://127.0.0.1:$port" \
        java -cp "$WORK/classes" WorkspaceRestore > "$WORK/$scenario.log" 2>&1
}

expect() {
    description=$1
    shift
    if ! "$@"; then
        echo "FAIL: $description" >&2
        fail=1
    fi
}

expect_failure() {
    description=$1
    shift
    if "$@"; then
        echo "FAIL: $description" >&2
        fail=1
    fi
}

# Off unless the server id is set: no marker, success.
mkdir -p "$WORK/off"
expect 'unset server id exits 0' env -u GROUNDS_WORKSPACE_SERVER GROUNDS_WORKSPACE_ROOT="$WORK/off" \
    java -cp "$WORK/classes" WorkspaceRestore
expect 'unset server id writes no marker' test ! -e "$WORK/off/.workspace-head"

expect 'fresh server succeeds' run_restore fresh "$WORK/fresh"
expect 'fresh server marker is none' test "$(cat "$WORK/fresh/.workspace-head")" = none

mkdir -p "$WORK/full/world/region"
printf 'stale\n' > "$WORK/full/world/region/r.0.0.mca"
expect 'full restore succeeds' run_restore full "$WORK/full"
expect 'full restore marker is the snapshot' test "$(cat "$WORK/full/.workspace-head")" = 42
expect 'region replaced' test "$(cat "$WORK/full/world/region/r.0.0.mca")" = 'region bytes'
expect 'nested dimension restored' test -f "$WORK/full/world/dimensions/minecraft/lobby/region/r.0.0.mca"
expect 'plugin data restored' test "$(cat "$WORK/full/plugins/BuildSystem/worlds.yml")" = 'worlds: {}'
expect 'staging removed' test ! -e "$WORK/full/.workspace-restore"

mkdir -p "$WORK/corrupt"
printf '7\n' > "$WORK/corrupt/.workspace-head"
expect_failure 'sha mismatch fails' run_restore corrupt "$WORK/corrupt"
expect 'sha mismatch removes the old marker' test ! -e "$WORK/corrupt/.workspace-head"
expect 'sha mismatch leaves the world untouched' test ! -e "$WORK/corrupt/world"

expect_failure 'path escape fails' run_restore escape "$WORK/escape"
expect 'path escape is refused by validation' grep -q 'not a workspace path' "$WORK/escape.log"
expect 'path escape writes no marker' test ! -e "$WORK/escape/.workspace-head"

if [ "$fail" -ne 0 ]; then
    for log in "$WORK"/*.log; do echo "== $log" >&2; cat "$log" >&2; done
fi
[ "$fail" -eq 0 ]
