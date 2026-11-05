import importlib.util
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("ota",ROOT/"verify-ota-publication.py")
ota=importlib.util.module_from_spec(spec); spec.loader.exec_module(ota)
class OtaPublicationTests(unittest.TestCase):
 def test_aapt_metadata_accepts_real_separate_sdk_line(self):
  text="package: name='ph.kanta.reborn' versionCode='12' versionName='0.12-preview'\nsdkVersion:'26'\ntargetSdkVersion:'35'\n"
  self.assertEqual(ota.parse_badging(text),{"versionCode":12,"versionName":"0.12-preview","minSdk":26})
 def test_sdk_value_on_unrelated_line_is_not_accepted(self):
  with self.assertRaisesRegex(ota.OtaError,"missing numeric"): ota.parse_badging("package: name='ph.kanta.reborn' versionCode='12' versionName='x'\nuses-sdkVersion:'26'\n")
 def test_wrong_package_refused(self):
  with self.assertRaises(ota.OtaError): ota.parse_badging("package: name='other' versionCode='12' versionName='x'\nsdkVersion:'26'\n")
 def test_multiple_signers_refused(self):
  with self.assertRaises(ota.OtaError): ota.parse_signer("Signer #1 certificate SHA-256 digest: "+"a"*64+"\nSigner #2 certificate SHA-256 digest: "+"b"*64)
 def test_invalid_signer_refused(self):
  with self.assertRaises(ota.OtaError): ota.parse_signer("Signer #1 certificate SHA-256 digest: nope")
 def test_missing_history_refuses_bootstrap(self):
  with self.assertRaises(ota.OtaError): ota.verify({"signerSha256":"a"*64},None,"aapt","apksigner")
if __name__=="__main__": unittest.main()
