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
if __name__=="__main__": unittest.main()
