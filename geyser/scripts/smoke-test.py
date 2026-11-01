#!/usr/bin/env python3
"""Real native NetherNet startup, config migration and stable operator identity."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid

DOCKER = [os.environ.get("DOCKER", "docker")]
if os.environ.get("DOCKER_HOST"):
    DOCKER += ["--host", os.environ["DOCKER_HOST"]]


def docker(*args):
    return subprocess.check_output(DOCKER + list(args), stderr=subprocess.STDOUT, text=True)


def main(image):
    with tempfile.TemporaryDirectory(prefix="grounds-geyser-smoke-") as tmp:
        root = Path(tmp)
        root.chmod(0o755)  # Matches a projected Kubernetes config directory.
        # Synthetic, throwaway P-384 identity; never a live credential.
        private = subprocess.check_output(["openssl", "genpkey", "-algorithm", "EC", "-pkeyopt", "ec_paramgen_curve:P-384"], stderr=subprocess.PIPE)
        public = subprocess.check_output(["openssl", "pkey", "-pubout"], input=private, stderr=subprocess.PIPE)
        identity = private + public
        (root / "identity.pem").write_bytes(identity)
        (root / "identity.pem").chmod(0o644)
        (root / "floodgate-key.pem").write_text("synthetic-key-16b")
        (root / "config.yml").write_text("""bedrock:
  address: 0.0.0.0
  port: 19132
  transport: nethernet
  signaling:
    mode: builtin
    port: 19132
    stun-discovery: false
  motd1: Grounds smoke
  motd2: stage
remote:
  address: 127.0.0.1
  port: 25565
  auth-type: floodgate
floodgate-key-file: /config/floodgate-key.pem
passthrough-motd: false
passthrough-player-counts: false
""")
        expected = hashlib.sha256(identity).hexdigest()
        for attempt in range(2):
            name = "grounds-geyser-smoke-" + uuid.uuid4().hex[:12]
            try:
                docker("create", "--name", name, "--memory=768m", "--cpus=1", "-p", "127.0.0.1::19132/tcp",
                       "-e", "JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 -Xmx256m",
                       "-e", "GROUNDS_GEYSER_ADVERTISE_ADDRESS=93.184.215.14",
                       "-e", "GROUNDS_GEYSER_IDENTITY_FILE=/config/identity.pem", image)
                docker("cp", str(root) + "/.", name + ":/config")
                docker("start", name)
                deadline = time.monotonic() + 10
                while True:
                    inspected = json.loads(docker("inspect", name))[0]
                    mappings = inspected["NetworkSettings"]["Ports"].get("19132/tcp")
                    if mappings:
                        port = mappings[0]["HostPort"]
                        break
                    if time.monotonic() > deadline:
                        raise AssertionError("local Docker port mapping unavailable: " + str(inspected["State"]))
                    time.sleep(.2)
                url = "http://127.0.0.1:" + port + "/v1/join"
                deadline = time.monotonic() + 100
                while True:
                    try:
                        with urllib.request.urlopen(url, timeout=2) as response:
                            data = json.load(response)
                        break
                    except (OSError, ValueError):
                        if time.monotonic() > deadline:
                            raise AssertionError("native NetherNet signaling did not become ready\n" + docker("logs", "--tail", "60", name))
                        time.sleep(1)
                assert data["version"] == "26.60" and data["protocol"] == 2225, data
                assert data["onlineAuth"] is True and data["selfSignedAuth"] is False, data
                actual = docker("exec", name, "sha256sum", "/opt/geyser/nethernet/identity.pem").split()[0]
                assert actual == expected, "operator identity changed"
                # A fresh pod gets the same durable identity, not a newly generated one.
                assert docker("exec", name, "stat", "-c", "%a", "/opt/geyser/nethernet/identity.pem").strip() == "600"
                request = urllib.request.Request(url + "/12345", data=b"invalid-sdp", headers={"Content-Type": "application/sdp"})
                try:
                    urllib.request.urlopen(request, timeout=5)
                    raise AssertionError("unauthenticated SDP was accepted")
                except urllib.error.HTTPError as error:
                    assert error.code in (400, 401, 403), error.code
                print("Fresh container", attempt + 1, "NetherNet ready; Bedrock", data["version"], "stable identity and auth rejection verified", flush=True)
            except Exception:
                print(docker("logs", "--tail", "60", name), flush=True)
                raise
            finally:
                subprocess.run(DOCKER + ["rm", "-f", name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    main(sys.argv[1])
