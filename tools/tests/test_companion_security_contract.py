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
            r"\\bLog\\.(?:v|d|i|w|e)\\s*\\(.*?;",
            HOST_SOURCE,
            flags=re.DOTALL,
        )
        self.assertTrue(log_calls, "The host should retain operational lifecycle logging")
        joined = "\\n".join(log_calls)
        self.assertNotIn("pairingCode", joined)
        self.assertNotIn("code=", joined.lower())
        self.assertIn('notifyStatus("Kanta-Connect hosting");', HOST_SOURCE)

    def test_owner_facing_pairing_flow_still_receives_the_code(self):
        self.assertIn("public String pairingCode()", HOST_SOURCE)
        self.assertIn("session.kantaConnectHost.pairingCode()", MAIN_SOURCE)
        self.assertIn("KantaConnectInfo.of(host, code)", MAIN_SOURCE)


if __name__ == "__main__":
    unittest.main()
