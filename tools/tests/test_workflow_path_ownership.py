"""Keep KANTA workflow triggers aligned with platform ownership."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"


def event_section(source: str, event: str) -> str:
    trigger_block = source.split("\njobs:", 1)[0]
    match = re.search(
        rf"(?ms)^  {re.escape(event)}:\n(.*?)(?=^  [A-Za-z0-9_-]+:\n|^permissions:|^concurrency:|\Z)",
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
        push = event_section(source, "push")
        for path in ("'apps/web/**'", "'tools/verify-website.py'",
                     "'apps/ios/PRIVACY.md'", "'apps/ios/PublicSite/**'",
                     "'apps/android/app/src/main/assets/privacy.txt'",
                     "'tools/tests/test_website_security_contract.py'"):
            self.assertIn(path, push)
        pull_request = event_section(source, "pull_request")
        self.assertNotIn("paths:", pull_request)

    def test_native_release_gates_ignore_website_only_tooling(self):
        for name in ("ios-runtime-artifacts.yml", "ios-visual-walkthrough.yml"):
            source = workflow(name)
            for event in ("push", "pull_request"):
                section = event_section(source, event)
                self.assertNotIn("'tools/verify-website.py'", section)
                self.assertNotIn("'tools/tests/test_website_security_contract.py'", section)
                # Android changes need same-SHA iOS release evidence after merge, not on PRs.
                if event == "push":
                    self.assertIn("'apps/android/**'", section)
                else:
                    self.assertNotIn("'apps/android/**'", section)

    def test_ios_documentation_changes_do_not_allocate_native_runners(self):
        for name in ("ios-validation.yml", "ios-runtime-artifacts.yml",
                     "ios-visual-walkthrough.yml", "ios-smoke.yml"):
            source = workflow(name)
            for event in ("push", "pull_request"):
                if event not in source.split("\njobs:", 1)[0]:
                    continue
                section = event_section(source, event)
                self.assertIn("'!apps/ios/PRIVACY.md'", section)
                self.assertIn("'!apps/ios/PublicSite/**'", section)
                self.assertIn("'!apps/ios/Docs/**'", section)

    def test_ios_feature_branches_use_pull_request_event_once(self):
        source = workflow("ios-validation.yml")
        push = event_section(source, "push")
        self.assertIn("branches: [main]", push)
        self.assertNotIn("['**']", push)
        pr = event_section(source, "pull_request")
        self.assertIn("'apps/android/app/src/main/java/ph/kanta/reborn/core/CompanionProtocol.java'", pr)

    def test_release_contract_workflow_checks_every_workflow_edit(self):
        source = workflow("verified-downloads.yml")
        for event in ("push", "pull_request"):
            section = event_section(source, event)
            self.assertIn("'.github/workflows/**'", section)
            self.assertIn("'tools/tests/test_workflow_path_ownership.py'", section)
        self.assertIn("if: github.event_name == 'push'", source)

    def test_website_workflow_owns_one_lightweight_pr_documentation_gate(self):
        source = workflow("website-validation.yml")
        self.assertIn("  pull_request:\\n    types:", source)
        self.assertNotIn("    paths:", source.split("  pull_request:", 1)[1].split("permissions:", 1)[0])
        self.assertIn("  documentation-sync:", source)
        self.assertIn("runs-on: ubuntu-latest", source)
        self.assertIn("timeout-minutes: 5", source)
        self.assertIn("website_changed:", source)
        self.assertIn("tools/check-documentation-sync.py", source)
        self.assertIn("needs: documentation-sync", source)
        self.assertIn("DOC_SYNC_BASE: ${{ github.event.pull_request.base.sha }}", source)
        self.assertIn("DOC_SYNC_HEAD: ${{ github.event.pull_request.head.sha }}", source)
        self.assertTrue((WORKFLOWS / "documentation-sync.yml").exists() is False)

    def test_vercel_production_workflow_does_not_validate_pull_requests(self):
        source = workflow("vercel-deploy.yml")
        self.assertNotIn("\n  pull_request:", source)
        self.assertIn("workflow_dispatch:", source)
        self.assertIn("push:", source)


if __name__ == "__main__":
    unittest.main()
