#!/usr/bin/env python3
"""Real pinned runtime regression test; all codes are local synthetic fixtures."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "scripts/player-access-privacy"
USER_AGENT = "grounds-containers/1.0 (https://github.com/groundsgg/containers)"


def run(*args, cwd, expected=0):
    result = subprocess.run(args, cwd=cwd, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=45, check=False)
    if result.returncode != expected:
        raise AssertionError(f"command failed ({result.returncode}): {args[0]}\n{result.stdout[-5000:]}")
    return result.stdout


def setting(runtime, name):
    source = (ROOT / runtime / "Dockerfile").read_text()
    found = re.search(rf"^ARG {name}=(\S+)$", source, re.MULTILINE)
    if not found:
        raise AssertionError(f"missing pinned {name}")
    return found.group(1)


def fetch(url, destination):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response, destination.open("wb") as target:
        shutil.copyfileobj(response, target)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rename_utf8(class_bytes, old, new):
    """Change only a same-length CONSTANT_Utf8 name in a malformed-shape fixture."""
    data = bytearray(class_bytes)
    count = struct.unpack_from(">H", data, 8)[0]
    offset, index, replacements = 10, 1, 0
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
             12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            if data[offset:offset + length] == old:
                assert len(old) == len(new)
                data[offset:offset + length] = new
                replacements += 1
            offset += length
        else:
            offset += sizes[tag]
            if tag in (5, 6):
                index += 1
        index += 1
    assert replacements == 1
    return data


def main():
    cache = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else None
    with tempfile.TemporaryDirectory(prefix="grounds-runtime-privacy-") as temporary:
        work = Path(temporary)
        classes = work / "classes"
        probe = work / "probe"
        originals = {}
        for runtime in ("velocity", "geyser"):
            version = setting(runtime, f"{runtime.upper()}_VERSION")
            build = setting(runtime, f"{runtime.upper()}_BUILD")
            expected = setting(runtime, f"{runtime.upper()}_SHA256")
            original = work / f"{runtime}-original.jar"
            cached = cache / f"{runtime}-current-original.jar" if cache else None
            if cached and cached.is_file():
                shutil.copyfile(cached, original)
            elif runtime == "velocity":
                request = urllib.request.Request(
                    f"https://fill.papermc.io/v3/projects/velocity/versions/{version}/builds",
                    headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(request, timeout=30) as response:
                    builds = json.load(response)
                selected = next(item for item in builds if item.get("id", item.get("number")) == int(build))
                fetch(selected["downloads"]["server:default"]["url"], original)
            else:
                fetch(f"https://download.geysermc.org/v2/projects/geyser/versions/{version}/builds/{build}/downloads/standalone", original)
            assert digest(original) == expected, f"{runtime} checksum differs from Docker pin"
            originals[runtime] = (original, expected)

        run("javac", "-cp", str(originals["velocity"][0]), "-d", str(probe),
            str(SOURCE / "RuntimePrivacyProbe.java"), cwd=work)

        for runtime, (original, expected) in originals.items():
            entry = "gg.grounds.build.privacy.RuntimePrivacyProbe"
            original_cp = os.pathsep.join([str(probe), str(original)])
            # Baseline proves actual logger and packet path run, not merely a missing-class RED.
            run("java", "--enable-native-access=ALL-UNNAMED", "-cp", original_cp,
                entry, runtime, "exposed", cwd=work)
            red = run("java", "--enable-native-access=ALL-UNNAMED", "-cp", original_cp,
                      entry, runtime, "private", cwd=work, expected=1)
            assert "credential log privacy mismatch" in red, "RED failed for an unrelated reason"

            patched = work / f"{runtime}-patched.jar"
            run("sh", str(SOURCE / "build.sh"), runtime, str(original), str(patched), expected, cwd=work)
            patched_cp = os.pathsep.join([str(probe), str(patched)])
            run("java", "--enable-native-access=ALL-UNNAMED", "-Xverify:all", "-cp", patched_cp,
                entry, runtime, "private", cwd=work)
            run("java", "--enable-native-access=ALL-UNNAMED", "-Xverify:all", "-cp", patched_cp,
                entry, runtime, "verify", cwd=work)
            with zipfile.ZipFile(patched) as jar:
                assert "gg/grounds/runtime/logging/PlayerAccessLogPrivacy.class" in jar.namelist()

            # Failed verification must not overwrite a previously good output.
            sentinel = work / "sentinel.jar"
            sentinel.write_bytes(b"leave-existing-output-unchanged")
            wrong = run("sh", str(SOURCE / "build.sh"), runtime, str(original), str(sentinel),
                        "0" * 64, cwd=work, expected=1)
            assert "checksum mismatch" in wrong
            assert sentinel.read_bytes() == b"leave-existing-output-unchanged"
            double = run("sh", str(SOURCE / "build.sh"), runtime, str(patched), str(sentinel),
                         digest(patched), cwd=work, expected=1)
            assert "already or partially patched" in double
            assert sentinel.read_bytes() == b"leave-existing-output-unchanged"

            missing = work / "missing-runtime.jar"
            with zipfile.ZipFile(missing, "w") as jar:
                jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
            # Compile the tool once against the real API to test malformed structure directly.
            run("javac", "-cp", str(original), "-d", str(classes), str(SOURCE / "PlayerAccessLogPrivacy.java"),
                str(SOURCE / "PatchRuntime.java"), cwd=work)
            absent = run("java", "-cp", str(classes), "gg.grounds.build.privacy.PatchRuntime",
                         runtime, str(missing), str(sentinel),
                         str(classes / "gg/grounds/runtime/logging/PlayerAccessLogPrivacy.class"),
                         digest(missing), cwd=work, expected=1)
            assert "runtime class is missing" in absent
            assert sentinel.read_bytes() == b"leave-existing-output-unchanged"

            target = ("com/velocitypowered/proxy/connection/client/ConnectedPlayer.class" if runtime == "velocity"
                      else "org/geysermc/geyser/platform/standalone/GeyserStandaloneLogger.class")
            with zipfile.ZipFile(original) as jar:
                bad_class = rename_utf8(jar.read(target), b"disconnect0" if runtime == "velocity" else b"info",
                                        b"disconnectX" if runtime == "velocity" else b"infx")
            bad_shape = work / f"{runtime}-wrong-shape.jar"
            with zipfile.ZipFile(bad_shape, "w") as jar:
                jar.writestr(target, bad_class)
            partial = work / f"{runtime}-partial.jar"
            with zipfile.ZipFile(patched) as jar:
                patched_class = jar.read(target)
            with zipfile.ZipFile(partial, "w") as jar:
                jar.writestr(target, patched_class)
            for malformed, reason in [(bad_shape, "Unexpected logging method/invocation shape"),
                                      (partial, "already or partially patched")]:
                failure = run("java", "-cp", os.pathsep.join([str(classes), str(original)]),
                              "gg.grounds.build.privacy.PatchRuntime", runtime, str(malformed), str(sentinel),
                              str(classes / "gg/grounds/runtime/logging/PlayerAccessLogPrivacy.class"),
                              digest(malformed), cwd=work, expected=1)
                assert reason in failure
                assert sentinel.read_bytes() == b"leave-existing-output-unchanged"
            print(f"PASS {runtime}: actual method/log output, packet preservation, ACCEPT/reconfiguration, checksum, partial patch and atomic-output guards")


if __name__ == "__main__":
    main()
