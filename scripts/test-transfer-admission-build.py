#!/usr/bin/env python3
"""Upgrade gate: pinned artifact, exact patch anchors, real LOGIN packets, default off."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('privacy', ROOT / 'scripts/test-player-access-privacy.py')
privacy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(privacy)

with tempfile.TemporaryDirectory(prefix='grounds-admission-build-') as temporary:
    work = Path(temporary)
    original = work / 'original.jar'
    cache = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if cache:
        shutil.copyfile(cache, original)
    else:
        version = privacy.setting('velocity', 'VELOCITY_VERSION')
        build = int(privacy.setting('velocity', 'VELOCITY_BUILD'))
        request = urllib.request.Request(f'https://fill.papermc.io/v3/projects/velocity/versions/{version}/builds', headers={'User-Agent': privacy.USER_AGENT})
        with urllib.request.urlopen(request, timeout=30) as response:
            builds = json.load(response)
        selected = next(item for item in builds if item.get('id', item.get('number')) == build)
        privacy.fetch(selected['downloads']['server:default']['url'], original)
    expected = privacy.setting('velocity', 'VELOCITY_SHA256')
    private = work / 'private.jar'
    patched = work / 'admission.jar'
    privacy.run('sh', str(ROOT / 'scripts/player-access-privacy/build.sh'), 'velocity', str(original), str(private), expected, cwd=ROOT)
    patch = ['sh', str(ROOT / 'scripts/transfer-admission/build.sh')]
    privacy.run(*patch, str(private), str(patched), privacy.digest(private), cwd=ROOT)
    repeated = work / 'repeated.jar'
    privacy.run(*patch, str(private), str(repeated), privacy.digest(private), cwd=ROOT)
    assert privacy.digest(patched) == privacy.digest(repeated), 'patch must be reproducible'
    privacy.run(*patch, str(private), str(work / 'bad-hash.jar'), '0' * 64, cwd=ROOT, expected=1)
    privacy.run(*patch, str(patched), str(work / 'double.jar'), privacy.digest(patched), cwd=ROOT, expected=1)
    malformed = work / 'drift.jar'
    with zipfile.ZipFile(private) as source, zipfile.ZipFile(malformed, 'w') as out:
        for item in source.infolist():
            data = source.read(item)
            if item.filename.endswith('/HandshakeSessionHandler.class'):
                data = privacy.rename_utf8(data, b'handleLogin', b'handleL0gin')
            out.writestr(item, data)
    privacy.run(*patch, str(malformed), str(work / 'drift-output.jar'), privacy.digest(malformed), cwd=ROOT, expected=1)
    subprocess.run([sys.executable, str(ROOT / 'scripts/test-transfer-admission.py'), str(patched)], cwd=ROOT, check=True, timeout=60)
    subprocess.run([sys.executable, str(ROOT / 'scripts/test-transfer-admission.py'), str(patched)], env=dict(os.environ, GROUNDS_WIRE_GATE='false'), cwd=ROOT, check=True, timeout=60)
print('Transfer patch upgrade guards and LOGIN wire verified')
