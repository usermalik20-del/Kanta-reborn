import importlib.util
import unittest
from unittest.mock import patch
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("ota",ROOT/"verify-ota-publication.py")
ota=importlib.util.module_from_spec(spec); spec.loader.exec_module(ota)
class OtaPublicationTests(unittest.TestCase):
 def test_aapt_metadata_is_strict(self):
  d=ota.parse_badging("package: name='ph.kanta.reborn' versionCode='12' versionName='0.12-preview' platformBuildVersionCode='36'\nsdkVersion:'26'\n")
  self.assertEqual(d,{"versionCode":12,"versionName":"0.12-preview","minSdk":26})
 def test_compile_sdk_is_not_used_as_min_sdk(self):
  with self.assertRaises(ota.OtaError):
   ota.parse_badging("package: name='ph.kanta.reborn' versionCode='12' versionName='x' platformBuildVersionCode='36'\n")
 def test_wrong_package_refused(self):
  with self.assertRaises(ota.OtaError): ota.parse_badging("package: name='other' versionCode='12' versionName='x' sdkVersion:'26'\n")
 def test_multiple_signers_refused(self):
  line="Signer #1 certificate SHA-256 digest: "+"a"*64+"\nSigner #2 certificate SHA-256 digest: "+"b"*64
  with self.assertRaises(ota.OtaError): ota.parse_signer(line)
 def test_invalid_signer_refused(self):
  with self.assertRaises(ota.OtaError): ota.parse_signer("Signer #1 certificate SHA-256 digest: nope")
 def test_missing_history_refuses_bootstrap(self):
  with self.assertRaises(ota.OtaError): ota.verify({"signerSha256": "a" * 64}, None, "aapt", "apksigner")
 def test_unapproved_signer_transition_still_refused(self):
  candidate={"signerSha256":"b"*64}
  old={"android":{}}
  with patch.object(ota,"previous_signer",return_value="a"*64):
   with self.assertRaises(ota.OtaError): ota.verify(candidate,old,"aapt","apksigner")
 def test_explicitly_pinned_new_signer_allows_only_that_transition(self):
  candidate={"signerSha256":ota.APPROVED_RELEASE_SIGNER_SHA256}
  old={"android":{}}
  with patch.object(ota,"previous_signer",return_value="a"*64):
   result=ota.verify(candidate,old,"aapt","apksigner",ota.APPROVED_RELEASE_SIGNER_SHA256)
  self.assertEqual(result["previousSignerSha256"],"a"*64)
  self.assertTrue(result["signerTransition"])
 def test_invalid_transition_pin_refused(self):
  with patch.object(ota,"previous_signer",return_value="a"*64):
   with self.assertRaises(ota.OtaError):
    ota.verify({"signerSha256":"a"*64},{"android":{}},"aapt","apksigner","not-a-fingerprint")
 def test_wrong_candidate_cannot_use_pinned_transition(self):
  old={"android":{}}
  with patch.object(ota,"previous_signer",return_value="a"*64):
   with self.assertRaises(ota.OtaError):
    ota.verify({"signerSha256":"b"*64},old,"aapt","apksigner",ota.APPROVED_RELEASE_SIGNER_SHA256)
 def test_matching_approved_signer_is_not_a_transition(self):
  old={"android":{}}
  with patch.object(ota,"previous_signer",return_value=ota.APPROVED_RELEASE_SIGNER_SHA256):
   result=ota.verify({"signerSha256":ota.APPROVED_RELEASE_SIGNER_SHA256},old,"aapt","apksigner",ota.APPROVED_RELEASE_SIGNER_SHA256)
  self.assertFalse(result["signerTransition"])
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

 def test_signing_secret_step_is_main_only_and_not_for_pull_requests(self):
  android=(ROOT.parent/".github/workflows/android-validation.yml").read_text()
  step=android[android.index("Prepare owner-controlled Android release signing"):android.index("Native SoundFont sanitizer regression")]
  self.assertIn("github.ref == 'refs/heads/main'",step)
  self.assertIn("github.event_name != 'pull_request'",step)
  self.assertIn("secrets.KANTA_ANDROID_SIGNING_KEYSTORE_BASE64",step)
  self.assertIn("secrets.KANTA_ANDROID_SIGNING_PASSWORD",step)
  self.assertIn("RUNNER_TEMP",step)
  build=(ROOT.parent/"apps/android/app/build.gradle").read_text()
  self.assertIn("KANTA_ANDROID_SIGNING_STORE_FILE",build)
  self.assertIn("KANTA_ANDROID_SIGNING_PASSWORD",build)
  self.assertIn("keyAlias 'kanta-release'",build)

if __name__=="__main__": unittest.main()
