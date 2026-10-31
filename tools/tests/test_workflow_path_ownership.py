"""Keep KANTA workflow triggers aligned with platform ownership."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"


def event_section(source: str, event: str) -> str:
    trigger_block = source.split("\njobs:", 1)[0]
    match = re.search(
        rf"(?ms)^  {re.escape(event)}:\n(.*?)(?=^  [A-Za-z0-9_-]+:\n|^permissions:|^concurrency:|\\Z)",
        trigger_block,
    )
    if match is None:
        raise AssertionError(f"Missing {event} trigger")
    return match.group(1)


def workflow(name: str) -> str:
    return (WORKFLOWS / name).read_text(encoding="utf-8")


class WorkflowOwnershipTests(unittest.TestCase):
    def test_android_validation_is_limited_to_android_and_release_inputs(self):
        source = workflow("android-validation.yml")
        for event in ("push", "pull_request"):
            section = event_section(source, event)
            self.assertIn("'apps/android/**'", section)
            self.assertNotIn("'tools/**'", section)
            self.assertNotIn("'apps/web/**'", section)
            self.assertNotIn("'tools/verify-website.py'", section)
            self.assertNotIn("'tools/tests/test_website_security_contract.py'", section)

    def test_website_validation_owns_website_contracts(self):
        source = workflow("website-validation.yml")
        for event in ("push", "pull_request"):
            section = event_section(source, event)
            self.assertIn("'apps/web/**'", section)
            self.assertIn("'tools/verify-website.py'", section)
            self.assertIn("'tools/tests/test_website_security_contract.py'", section)
            self.assertIn("'.github/workflows/**'", section)

    def test_native_release_gates_ignore_website_only_tooling(self):
        for name in ("ios-runtime-artifacts.yml", "ios-visual-walkthrough.yml"):
            source = workflow(name)
            for event in ("push", "pull_request"):
                section = event_section(source, event)
                self.assertNotIn("'tools/verify-website.py'", section)
                self.assertNotIn("'tools/tests/test_website_security_contract.py'", section)
                # Preserve same-SHA native release evidence for Android changes.
                self.assertIn("'apps/android/**'", section)

    def test_ios_feature_branches_use_pull_request_event_once(self):
        source = workflow("ios-validation.yml")
        push = event_section(source, "push")
        self.assertIn("branches: [main]", push)
        self.assertNotIn("['**']", push)

    def test_vercel_production_workflow_does_not_validate_pull_requests(self):
        source = workflow("vercel-deploy.yml")
        self.assertNotIn("\n  pull_request:", source)
        self.assertIn("workflow_dispatch:", source)
        self.assertIn("push:", source)


if __name__ == "__main__":
    unittest.main()
