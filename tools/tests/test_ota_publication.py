import importlib.util
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("ota",ROOT/"verify-ota-publication.py")
ota=importlib.util.module_from_spec(spec); spec.loader.exec_module(ota)
class OtaPublicationTests(unittest.TestCase):
 def test_aapt_metadata_is_strict(self):
  d=ota.parse_badging("package: name='ph.kanta.reborn' versionCode='12' versionName='0.12-preview' sdkVersion:'26'\n")
  self.assertEqual(d,{"versionCode":12,"versionName":"0.12-preview","minSdk":26})
 def test_wrong_package_refused(self):
  with self.assertRaises(ota.OtaError): ota.parse_badging("package: name='other' versionCode='12' versionName='x' sdkVersion:'26'\n")
 def test_multiple_signers_refused(self):
  line="Signer #1 certificate SHA-256 digest: "+"a"*64+"\nSigner #2 certificate SHA-256 digest: "+"b"*64
  with self.assertRaises(ota.OtaError): ota.parse_signer(line)
 def test_invalid_signer_refused(self):
  with self.assertRaises(ota.OtaError): ota.parse_signer("Signer #1 certificate SHA-256 digest: nope")
 def test_missing_history_refuses_bootstrap(self):
  with self.assertRaises(ota.OtaError): ota.verify({"signerSha256": "a" * 64}, None, "aapt", "apksigner")
class PublisherToolchainContractTests(unittest.TestCase):
 """The publisher verifies the APK with aapt and apksigner, which are not on a bare runner's PATH."""

 WORKFLOW = ROOT.parent / ".github/workflows/platform-download-publishing.yml"

 def setUp(self):
  self.workflow = self.WORKFLOW.read_text()

 def test_build_tools_are_exposed_before_the_apk_verification(self):
  expose = self.workflow.index("GITHUB_PATH")
  verify = self.workflow.index("Verify APK metadata and signer continuity")
  self.assertLess(expose, verify, "build-tools must be on PATH before verify-ota-publication.py runs")
  self.assertIn("build-tools", self.workflow)

 def test_missing_build_tools_fail_loudly(self):
  segment = self.workflow[self.workflow.index("Put the Android build tools on PATH"):
                         self.workflow.index("Verify APK metadata and signer continuity")]
  self.assertIn("::error::", segment)
  self.assertIn("exit 1", segment)
  for binary in ("aapt", "apksigner"):
   self.assertIn(binary, segment)

 def test_the_verification_step_stays_android_only(self):
  verify = self.workflow.index("Verify APK metadata and signer continuity")
  segment = self.workflow[verify:verify + 500]
  self.assertIn("if: env.PLATFORM == 'android'", segment)
  self.assertIn("tools/verify-ota-publication.py", segment)

if __name__=="__main__": unittest.main()
