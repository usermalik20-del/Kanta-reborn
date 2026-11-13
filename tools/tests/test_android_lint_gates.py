"""Exercise lint ratchet and ELF gates, including negative controls."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'tools' / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

lint = load('verify-android-lint')
native = load('verify-android-native-alignment')

def elf(machine=183, align=16384, offset=0, address=0):
    data = bytearray(120)
    data[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<H', data, 18, machine)
    struct.pack_into('<Q', data, 32, 64)
    struct.pack_into('<HH', data, 54, 56, 1)
    struct.pack_into('<IIQQQQQQ', data, 64, 1, 5, offset, address, 0, 120, 120, align)
    return data

class LintGateTests(unittest.TestCase):
    def test_findings_are_location_stable_but_content_specific(self):
        self.assertEqual(lint.normalized_file('/runner/a/apps/android/app/src/Test.java'), 'app/src/Test.java')
        self.assertEqual(lint.normalized_file('/cache/hash/transformed/library/jni/arm64-v8a/a.so'), 'library/jni/arm64-v8a/a.so')

    def test_reappearance_new_findings_and_higher_counts_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'lint.xml'
            finding = '<issue id="SetWorldWritable" severity="Warning" message="unsafe" errorLine1="temp.setWritable(true, false);"><location file="/runner/apps/android/app/src/Test.java" line="1"/></issue>'
            report.write_text('<issues>' + finding + '</issues>')
            key = next(iter(lint.findings(report)))
            budget = {'findings': [{'identity': list(key), 'count': 1}]}
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertTrue(lint.check(report, budget))
                self.assertFalse(lint.check(report, {'findings': []}))
                report.write_text('<issues>' + finding * 2 + '</issues>')
                self.assertFalse(lint.check(report, budget))
                report.write_text('<issues>' + finding.replace('unsafe', 'new problem') + '</issues>')
                self.assertFalse(lint.check(report, budget))
                report.write_text('<issues/>')
                self.assertTrue(lint.check(report, budget))

    def test_errors_and_malformed_reports_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'lint.xml'
            for xml in ('<html/>', '<issues><issue id="Broken" severity="Error"/></issues>', '<issues><issue id="X" severity="Warning" message="x"/></issues>'):
                report.write_text(xml)
                with self.assertRaises(ValueError):
                    lint.findings(report)

    def test_fixed_security_findings_are_not_in_debt(self):
        budget = json.loads((ROOT / 'tools/android-lint-debt.json').read_text())
        ids = {entry['identity'][0] for entry in budget['findings']}
        for fixed in ('SetWorldWritable', 'UnsafeIntentLaunch', 'CustomX509TrustManager',
                      'SetJavaScriptEnabled', 'StaticFieldLeak', 'DataExtractionRules', 'Aligned16KB'):
            self.assertNotIn(fixed, ids)

class NativeGateTests(unittest.TestCase):
    def test_accepts_16kb_and_64kb_segments(self):
        native.verify_elf(elf(), 183)
        native.verify_elf(elf(align=65536), 183)

    def test_rejects_small_alignment_and_incongruent_offsets(self):
        for data in (elf(align=4096), elf(offset=4096), elf(align=24576), elf(machine=62), b'not ELF'):
            with self.assertRaises(ValueError):
                native.verify_elf(data, 183)

    def test_rejects_unaligned_uncompressed_apk_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'app.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('lib/arm64-v8a/a.so', elf())
                archive.writestr('lib/x86_64/a.so', elf(machine=62))
            with self.assertRaisesRegex(ValueError, 'ZIP-aligned'):
                native.verify_archive(apk)

    def test_checks_each_packaged_library_and_requires_both_abis(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'app.apk'
            with zipfile.ZipFile(apk, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr('lib/arm64-v8a/good.so', elf())
                archive.writestr('lib/x86_64/good.so', elf(machine=62))
            with contextlib.redirect_stdout(io.StringIO()):
                native.verify_archive(apk)
            with zipfile.ZipFile(apk, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr('lib/arm64-v8a/bad.so', elf(align=4096))
            with self.assertRaises(ValueError):
                native.verify_archive(apk)
            with zipfile.ZipFile(apk, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr('lib/arm64-v8a/good.so', elf())
            with self.assertRaises(ValueError):
                native.verify_archive(apk)

if __name__ == '__main__':
    unittest.main()
