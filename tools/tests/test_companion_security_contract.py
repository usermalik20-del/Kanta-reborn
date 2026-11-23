"""Keep Kanta-Connect pairing credentials out of Android Logcat."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
HOST_SOURCE = (
    ROOT / "apps/android/app/src/main/java/ph/kanta/reborn/core/companion/KantaConnectHost.java"
).read_text(encoding="utf-8")
MAIN_SOURCE = (
    ROOT / "apps/android/app/src/main/java/ph/kanta/reborn/MainActivity.java"
).read_text(encoding="utf-8")


class CompanionSecurityContractTests(unittest.TestCase):
    def test_pairing_code_is_never_written_to_logcat(self):
        log_calls = re.findall(
            r"\bLog\.(?:v|d|i|w|e)\s*\(.*?;",
            HOST_SOURCE,
            flags=re.DOTALL,
        )
        self.assertTrue(log_calls, "The host should retain operational lifecycle logging")
        joined = "\n".join(log_calls)
        self.assertNotIn("pairingCode", joined)
        self.assertNotIn("code=", joined.lower())
        self.assertIn('notifyStatus("Kanta-Connect hosting");', HOST_SOURCE)

    def test_android_controls_no_longer_expose_the_smart_link(self):
        self.assertIn("public String pairingCode()", HOST_SOURCE)
        self.assertNotIn("session.kantaConnectHost.pairingCode()", MAIN_SOURCE)
        self.assertNotIn("session.startKantaConnectHost()", MAIN_SOURCE)
        self.assertNotIn("showKantaConnectQR(", MAIN_SOURCE)

    def test_browser_remote_scan_keeps_bootstrap_out_of_urls_storage_and_logs(self):
        java = ROOT / "apps/android/app/src/main/java/ph/kanta/reborn"
        dialog = (java / "BrowserCompanionDialog.java").read_text(encoding="utf-8")
        scanner = (java / "BrowserPairingScanner.java").read_text(encoding="utf-8")
        transport = (java / "PinnedBrowserTransport.java").read_text(encoding="utf-8")
        qr = (java / "core/BrowserPairingQr.java").read_text(encoding="utf-8")
        host = (java / "core/BrowserRemoteServer.java").read_text(encoding="utf-8")
        main = ROOT / "apps/android/app/src/main"
        script = (main / "assets/companion/remote.js").read_text(encoding="utf-8")
        strings = (main / "res/values/remote_controls_strings.xml").read_text(encoding="utf-8")
        manifest = (main / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('android:name=".BrowserPairingScanner" android:exported="false"', manifest)
        self.assertIn("QrEncoder.encode(payload)", dialog)
        self.assertIn("addressQr.setSymbol(null);", dialog)
        self.assertIn("Do not bypass certificate warnings", strings)
        self.assertIn('web.loadUrl(origin+"/")', dialog)
        self.assertIn("setDomStorageEnabled(false)", dialog)
        self.assertIn("BrowserPairingQr.parse(text)", scanner)
        self.assertIn("frame.close()", scanner)
        self.assertNotIn("putExtra(", scanner + dialog)
        self.assertNotRegex(qr, r"https?://")
        self.assertIn('bootstrapCode.getAndSet("")', transport)
        self.assertIn("new URL(origin+path).openConnection(Proxy.NO_PROXY)", transport)
        self.assertIn("output.write(body)", transport)
        self.assertIn("Referrer-Policy: no-referrer", host)
        self.assertIn('referrerPolicy: "no-referrer"', script)
        self.assertNotRegex(script, r"localStorage|sessionStorage|location\.(?:hash|search)|console\.")
        self.assertNotRegex(dialog + scanner + transport + host, r"Log\.[vdiew]\(|printStackTrace\(")

if __name__ == "__main__":
    unittest.main()
