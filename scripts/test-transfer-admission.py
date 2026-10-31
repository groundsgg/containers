#!/usr/bin/env python3
"""Blackbox the actual Velocity LOGIN wire, with only a loopback admission authority."""
import http.server
import json
import os
from pathlib import Path
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import time
import unittest
import uuid

JAR = str(Path(sys.argv.pop(1)).resolve())
KEY = b'grounds:player-session-handoff'
ENABLED = os.environ.get('GROUNDS_WIRE_GATE', 'true') == 'true'

def vi(n):
    out = bytearray()
    while n > 127:
        out.append((n & 127) | 128)
        n >>= 7
    return bytes(out + bytes([n]))

def string(value):
    return vi(len(value)) + value

def frame(data):
    return vi(len(data)) + data

def exact(s, size):
    out = b''
    while len(out) < size:
        part = s.recv(size - len(out))
        if not part:
            raise EOFError('connection closed')
        out += part
    return out

def read_vi(s):
    n = 0
    for shift in range(0, 35, 7):
        b = exact(s, 1)[0]
        n |= (b & 127) << shift
        if b < 128:
            return n
    raise ValueError('oversized varint')

def packet(s):
    return exact(s, read_vi(s))

class Authority(http.server.BaseHTTPRequestHandler):
    requests = []
    lock = threading.Lock()
    reservations = {}
    delay = 0
    def log_message(self, *args):
        pass
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        if self.path.endswith('/abort'):
            self.send_response(204)
            self.send_header('Content-Length', '0')
            self.end_headers()
            return
        self.requests.append((self.path, self.headers.get('Authorization'), body))
        time.sleep(self.delay)
        with self.lock:
            profile = body.get('playerId')
            owner = self.reservations.get(profile)
            binding = (body.get('connectionId'), body.get('destinationProxyId'))
            status = 201 if body.get('handoffToken') == 'v' * 43 and owner in (None, binding) else 409
            if status == 201:
                self.reservations[profile] = binding
        payload = json.dumps(dict(body, expiresAt='2099-01-01T00:00:00Z')).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        try:
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError):
            pass

class AdmissionWireTest(unittest.TestCase):
    counter = 0
    @classmethod
    def setUpClass(cls):
        cls.private = Path(tempfile.mkdtemp(prefix='grounds-admission-wire-'))
        cls.authority = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Authority)
        threading.Thread(target=cls.authority.serve_forever, daemon=True).start()
        with socket.socket() as s:
            s.bind(('127.0.0.1', 0))
            cls.port = s.getsockname()[1]
        (cls.private / 'velocity.toml').write_text(f'''config-version = "2.8"
bind = "127.0.0.1:{cls.port}"
online-mode = true
force-key-authentication = false
player-info-forwarding-mode = "none"
forwarding-secret-file = "forwarding.secret"
[servers]
lobby = "127.0.0.1:1"
factions = "127.0.0.1:1"
minigames = "127.0.0.1:1"
try = ["lobby"]
[advanced]
login-ratelimit = 3000
accepts-transfers = true
[query]
enabled = false
''')
        token = cls.private / 'workload-token'
        token.write_text('isolated-test-workload')
        env = dict(os.environ, GROUNDS_TRANSFER_ADMISSION_ENABLED=str(ENABLED).lower(),
            PLAYER_SERVICE_URL=f'http://127.0.0.1:{cls.authority.server_port}',
            GROUNDS_TOKEN_FILE=str(token), PROXY_ID='wire-target')
        cls.env = env
        cls.log = (cls.private / 'proxy.log').open('w')
        cls.process = subprocess.Popen([os.environ.get('JAVA', 'java'), '-XX:ActiveProcessorCount=2', '-Xmx256m', '-Dterminal.jline=false', '-Dterminal.ansi=false', '--enable-native-access=ALL-UNNAMED', '-jar', JAR],
            cwd=cls.private, env=env, stdin=subprocess.DEVNULL, stdout=cls.log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 30
        while 'Done (' not in (cls.private / 'proxy.log').read_text():
            if cls.process.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError((cls.private / 'proxy.log').read_text())
            time.sleep(.1)

    @classmethod
    def tearDownClass(cls):
        cls.process.terminate()
        try:
            cls.process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            cls.process.kill()
            cls.process.wait()
        cls.log.close()
        cls.authority.shutdown()
        cls.authority.server_close()
        print((cls.private / 'proxy.log').read_text()[-1500:])
        shutil.rmtree(cls.private)

    def setUp(self):
        type(self).counter += 1
        self.ip = '127.0.0.' + str(10 + self.counter)
        self.sockets = []
        self.profile = uuid.uuid4()
        Authority.requests.clear()
        Authority.reservations.clear()
        Authority.delay = 0

    def tearDown(self):
        for s in self.sockets:
            s.close()

    def connect(self, transfer=True, start=True, port=None):
        s = socket.socket()
        s.settimeout(5)
        s.bind((self.ip, 0))
        s.connect(('127.0.0.1', port or self.port))
        self.sockets.append(s)
        s.sendall(frame(b'\0' + vi(769) + string(b'probe.invalid') + struct.pack('>H', self.port)
                        + vi(3 if transfer else 2)))
        if start:
            s.sendall(frame(b'\0' + string(b'AdmissionProbe') + self.profile.bytes))
        return s

    def cookie(self, s, token='v' * 43):
        p = packet(s)
        self.assertEqual(p, b'\x05' + string(KEY), 'transfer must request its handoff before authentication')
        data = json.dumps({'playerId': str(self.profile), 'token': token}).encode()
        s.sendall(frame(b'\x04' + string(KEY) + b'\x01' + string(data)))

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_normal_login_then_valid_transfer_same_ip_requires_authentication(self):
        self.connect(transfer=False, start=False)
        time.sleep(.15)
        s = self.connect()
        self.cookie(s)
        self.assertEqual(packet(s)[0], 1, 'valid handoff must continue to the encryption/authentication request')
        self.assertEqual(len(Authority.requests), 1)
        path, authorization, body = Authority.requests[0]
        self.assertEqual(path, '/v2/players/sessions/transfer/admission')
        self.assertEqual(authorization, 'Bearer isolated-test-workload')
        self.assertEqual(body['playerId'], str(self.profile))
        self.assertEqual(body['destinationProxyId'], 'wire-target')
        self.assertEqual(uuid.UUID(body['connectionId']).version, 4)

    def test_normal_logins_keep_the_existing_ip_limit(self):
        self.connect(transfer=False, start=False)
        time.sleep(.15)
        s = self.connect(transfer=False, start=False)
        self.assertIn(b'You are logging in too fast', packet(s))
        self.assertEqual(Authority.requests, [])

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_bare_transfer_flag_has_no_admission(self):
        s = self.connect()
        self.assertEqual(packet(s), b'\x05' + string(KEY))
        s.sendall(frame(b'\x04' + string(KEY) + b'\0'))
        self.assertEqual(packet(s)[0], 0)
        self.assertEqual(Authority.requests, [])

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_rejected_ticket_never_reaches_authentication(self):
        s = self.connect()
        self.cookie(s, token='r' * 43)
        self.assertEqual(packet(s)[0], 0)
        self.assertEqual(len(Authority.requests), 1)

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_cookie_wait_is_bounded(self):
        start = time.monotonic()
        s = self.connect()
        self.assertEqual(packet(s), b'\x05' + string(KEY))
        self.assertEqual(packet(s)[0], 0)
        self.assertLess(time.monotonic() - start, 4.5)

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_distinct_profiles_share_one_nat_without_colliding(self):
        a = self.connect()
        self.cookie(a)
        self.profile = uuid.uuid4()
        b = self.connect()
        self.cookie(b)
        self.assertEqual(packet(a)[0], 1)
        self.assertEqual(packet(b)[0], 1)
        self.assertEqual(len(Authority.requests), 2)

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_two_actual_proxies_cannot_admit_the_same_ticket_twice(self):
        with tempfile.TemporaryDirectory(prefix='grounds-second-admission-proxy-') as directory:
            work = Path(directory)
            with socket.socket() as reserved:
                reserved.bind(('127.0.0.1', 0))
                port = reserved.getsockname()[1]
            (work / 'velocity.toml').write_text((self.private / 'velocity.toml').read_text().replace(f'127.0.0.1:{self.port}', f'127.0.0.1:{port}'))
            with (work / 'proxy.log').open('w') as log:
                process = subprocess.Popen([os.environ.get('JAVA', 'java'), '-XX:ActiveProcessorCount=2', '-Xmx256m', '-Dterminal.jline=false', '-Dterminal.ansi=false', '--enable-native-access=ALL-UNNAMED', '-jar', JAR],
                    cwd=work, env=dict(self.env, PROXY_ID='wire-target-b'), stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
                try:
                    deadline = time.monotonic() + 20
                    while 'Done (' not in (work / 'proxy.log').read_text():
                        if process.poll() is not None or time.monotonic() > deadline:
                            raise RuntimeError((work / 'proxy.log').read_text()[-2000:])
                        time.sleep(.1)
                    a, b = self.connect(), self.connect(port=port)
                    self.cookie(a)
                    self.cookie(b)
                    self.assertEqual(sorted([packet(a)[0], packet(b)[0]]), [0, 1])
                    self.assertEqual({body['destinationProxyId'] for _, _, body in Authority.requests}, {'wire-target', 'wire-target-b'})
                finally:
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill(); process.wait()

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_z_fast_failures_have_a_global_attempt_rate_bound(self):
        admitted_to_cookie = 0
        start = time.monotonic()
        for _ in range(200):
            s = self.connect()
            response = packet(s)
            if response[0] == 5:
                admitted_to_cookie += 1
                s.sendall(frame(b'\x04' + string(KEY) + b'\0'))
                self.assertEqual(packet(s)[0], 0)
            else:
                self.assertEqual(response[0], 0)
            s.close()
        elapsed = time.monotonic() - start
        self.assertLessEqual(admitted_to_cookie, 64 + int(elapsed * 64) + 2,
            'fast invalid attempts must not replenish their own admission budget')

    @unittest.skipIf(ENABLED, 'gate enabled')
    def test_disabled_gate_preserves_transfer_ip_limit(self):
        self.connect(transfer=False, start=False)
        time.sleep(.15)
        self.assertIn(b'You are logging in too fast', packet(self.connect(start=False)))
        self.assertEqual(Authority.requests, [])

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_forged_profile_is_rejected_before_http_and_authentication(self):
        s = self.connect()
        self.assertEqual(packet(s), b'\x05' + string(KEY))
        data = json.dumps({'playerId': str(uuid.uuid4()), 'token': 'v' * 43}).encode()
        s.sendall(frame(b'\x04' + string(KEY) + b'\x01' + string(data)))
        self.assertEqual(packet(s)[0], 0)
        self.assertEqual(Authority.requests, [])

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_cookie_payload_is_limited(self):
        s = self.connect()
        self.assertEqual(packet(s), b'\x05' + string(KEY))
        s.sendall(frame(b'\x04' + string(KEY) + b'\x01' + string(b'x' * 4097)))
        self.assertEqual(packet(s)[0], 0)
        self.assertEqual(Authority.requests, [])

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_parallel_connections_cannot_reuse_an_admission(self):
        a, b = self.connect(), self.connect()
        self.cookie(a)
        self.cookie(b)
        self.assertEqual(sorted([packet(a)[0], packet(b)[0]]), [0, 1])

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_unanswered_transfer_burst_is_bounded_and_close_releases_budget(self):
        waiting = [self.connect() for _ in range(32)]
        for s in waiting:
            self.assertEqual(packet(s), b'\x05' + string(KEY))
        overflow = self.connect()
        self.assertEqual(packet(overflow)[0], 0)
        for s in waiting:
            s.close()
        time.sleep(.2)
        valid = self.connect()
        self.cookie(valid)
        self.assertEqual(packet(valid)[0], 1)

    @unittest.skipUnless(ENABLED, 'gate disabled')
    def test_slow_authority_never_opens_authentication_after_timeout(self):
        Authority.delay = 4
        s = self.connect()
        self.cookie(s)
        self.assertEqual(packet(s)[0], 0)
        self.assertEqual(s.recv(1), b'')

if __name__ == '__main__':
    unittest.main()
