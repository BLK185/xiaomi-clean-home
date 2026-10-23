import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from installer import main as m


class FakeDevice:
    address = '192.0.2.10:5555'
    def __init__(self):
        self.calls = []
        self.states = {'com.example.one': 0, 'com.example.two': 1, 'com.example.off': 3}
        self.current_home = 'com.example.launcher/.Home'
        self.fail = None
    def identity(self):
        return dict(model='MiTV-AYFR0', device='soul', manufacturer='Xiaomi', sdk=30, serial='TEST-ONLY')
    def packages(self, disabled=False):
        return {p for p, s in self.states.items() if not disabled or s in (2, 3, 4)}
    def package_state(self, name):
        return self.states[name], 'User 0: enabled=' + str(self.states[name])
    def home(self): return self.current_home
    def setting(self, namespace, name): return 'null'
    def put_setting(self, *args): self.calls.append(('setting', *args))
    def run(self, *args, **kwargs): self.calls.append(args); return 'Success'
    def shell(self, *args, **kwargs):
        self.calls.append(args)
        if args == ('pm', 'list', 'features'): return 'feature:android.software.leanback'
        if args[:3] == ('cmd', 'package', 'set-home-activity'): self.current_home = args[-1]
        if args[0] == 'pm' and args[1] in ('disable-user', 'enable', 'default-state'):
            self.states[args[-1]] = {'disable-user': 3, 'enable': 1, 'default-state': 0}[args[1]]
            if args[-1] == self.fail and args[1] == 'disable-user':
                raise m.InstallationError('Transport failed AFTER device mutation')
        return ''


class InstallerTests(unittest.TestCase):
    def plan(self, device):
        return m.prepare(device, device.identity(), dict(id='test', disable=list(device.states), keep=[]))[0]
    def test_core_protection(self):
        for name in m.PROTECTED | {'com.android.providers.media', 'com.android.networkstack', 'bad;reboot'}:
            with self.subTest(name=name), self.assertRaises(m.InstallationError): m.check_package(name)
    def test_unknown_firmware_stops(self):
        identity = FakeDevice().identity(); identity['sdk'] = 34
        with self.assertRaises(m.InstallationError): m.select_profile(identity)
        self.assertEqual([], m.select_profile(identity, True)['disable'])
    def test_profile_matches_tested_device(self):
        self.assertEqual(20, len(m.select_profile(FakeDevice().identity())['disable']))
    def test_plan_read_only(self):
        d = FakeDevice(); self.plan(d)
        self.assertFalse(any(c[0] in ('setting', 'install') or 'disable-user' in c for c in d.calls))
    def test_active_vpn_kept(self):
        d = FakeDevice(); d.setting = lambda ns, key: 'com.example.one' if key == 'always_on_vpn_app' else 'null'
        self.assertNotIn('com.example.one', self.plan(d)['package_states'])
    def test_home_guard_blocks_disable(self):
        d = FakeDevice(); state = self.plan(d)
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)/'state.json'; m.save_json(p, state)
            with patch.object(m, 'ensure_home', side_effect=m.InstallationError('Not active')):
                with self.assertRaises(m.InstallationError): m.apply(d, state, p, 'app.apk')
        self.assertFalse(any('disable-user' in c for c in d.calls))
        self.assertEqual('com.example.launcher/.Home', d.home())
    def test_transport_failure_rolls_back_exact_states(self):
        d = FakeDevice(); original = dict(d.states); state = self.plan(d); d.fail = 'com.example.two'
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)/'state.json'; m.save_json(p, state)
            with patch.object(m, 'ensure_home'):
                with self.assertRaises(m.InstallationError): m.apply(d, state, p, 'app.apk')
            self.assertTrue(json.loads(p.read_text())['restored'])
        self.assertEqual(original, d.states)
    def test_success_and_restore(self):
        d = FakeDevice(); original = dict(d.states); state = self.plan(d)
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)/'state.json'; m.save_json(p, state)
            with patch.object(m, 'ensure_home'): m.apply(d, state, p, 'app.apk')
            self.assertTrue(state['complete']); self.assertNotIn('com.example.off', state['attempted_packages'])
            m.restore(d, p)
        self.assertEqual(original, d.states)
    def test_restore_other_device_rejected(self):
        d = FakeDevice(); state = self.plan(d); state['device']['serial'] = 'OTHER'
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)/'state.json'; m.save_json(p, state)
            with self.assertRaises(m.InstallationError): m.restore(d, p)
    def test_checksum(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)/'app.apk'; p.write_bytes(b'apk fixture')
            m.verify_apk(p, hashlib.sha256(p.read_bytes()).hexdigest())
            with self.assertRaises(m.InstallationError): m.verify_apk(p, '0'*64)
    def test_zip_traversal(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)/'bad.zip'
            with zipfile.ZipFile(p, 'w') as z: z.writestr('../escape', 'bad')
            with self.assertRaises(m.InstallationError): m.safe_extract(p, Path(tmp)/'out')
            self.assertFalse((Path(tmp)/'escape').exists())
    def test_endpoint_injection_and_public_ip(self):
        self.assertEqual('192.0.2.10:5555', m.endpoint('192.0.2.10'))
        for address in ('8.8.8.8', '127.0.0.1', '192.0.2.10;reboot', '192.0.2.10:22'):
            with self.subTest(address=address), self.assertRaises(m.InstallationError): m.endpoint(address)


if __name__ == '__main__': unittest.main()
