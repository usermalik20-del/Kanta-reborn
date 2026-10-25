"""The smoke gate must reject missing native cases and earlier failed XCTest attempts."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[4]
SPEC = importlib.util.spec_from_file_location("ios_smoke_results", ROOT / "tools/verify-ios-smoke-results.py")
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class IOSSmokeResultsTests(unittest.TestCase):
    def verify(self, text):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "tests.log"
            path.write_text(text)
            return GATE.verify(path)

    def passing_log(self, cases):
        return "\n".join(f"Test Case '-[{case}]' passed (0.01 seconds)." for case in cases)

    def test_requires_all_native_cases_and_navigation(self):
        self.assertEqual(self.verify(self.passing_log(GATE.EXPECTED)), set(GATE.EXPECTED))

    def test_navigation_only_cannot_hide_missing_native_execution(self):
        cases = [case for case in GATE.EXPECTED if case.endswith(" testFocusedNavigationSmoke")]
        with self.assertRaisesRegex(ValueError, "Missing XCTest passes"):
            self.verify(self.passing_log(cases))

    def test_previous_smoke_cases_cannot_hide_missing_online_screen_execution(self):
        previous_cases = [case for case in GATE.EXPECTED if " testOnlinePreview" not in case]
        with self.assertRaisesRegex(ValueError, "Missing XCTest passes"):
            self.verify(self.passing_log(previous_cases))

    def test_zero_test_restart_does_not_erase_prior_failure(self):
        text = ("Restarting after unexpected exit\nExecuted 0 tests, with 0 failures\n"
                + self.passing_log(GATE.EXPECTED))
        with self.assertRaisesRegex(ValueError, "Restarting after unexpected exit"):
            self.verify(text)

    def test_earlier_source_assertion_failure_cannot_be_overridden(self):
        text = "Tests/Native.swift:42: error: failed assertion\n" + self.passing_log(GATE.EXPECTED)
        with self.assertRaisesRegex(ValueError, "failed assertion"):
            self.verify(text)
