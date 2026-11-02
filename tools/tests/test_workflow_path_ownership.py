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
    def test_android_validation_is_limited_to_android_owned_inputs(self):
        source = workflow("android-validation.yml")
        for event in ("push", "pull_request"):
            section = event_section(source, event)
            self.assertIn("'apps/android/**'", section)
            self.assertNotIn("'tools/**'", section)
            self.assertNotIn("'apps/web/**'", section)
            self.assertNotIn("'tools/verify-website.py'", section)
            self.assertNotIn("'tools/tests/test_website_security_contract.py'", section)
            for release_path in ("'tools/prepare-verified-downloads.py'", "'tools/publish-verified-downloads.py'", "'tools/tests/test_verified_downloads.py'", "'tools/tests/test_publish_verified_downloads.py'"):
                self.assertNotIn(release_path, section)

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
                # iOS archive and walkthrough runners are owned only by iOS inputs.
                self.assertNotIn("'apps/android/**'", section)
                self.assertNotIn("'tools/verify-android-companion-interface.py'", section)
                for release_path in ("'tools/prepare-verified-downloads.py'", "'tools/publish-verified-downloads.py'", "'tools/tests/test_verified_downloads.py'", "'tools/tests/test_publish_verified_downloads.py'"):
                    self.assertNotIn(release_path, section)

    def test_ios_runtime_workflows_are_strictly_ios_owned(self):
        for name in ("ios-runtime-artifacts.yml", "ios-visual-walkthrough.yml"):
            source = workflow(name)
            for event in ("push", "pull_request"):
                section = event_section(source, event)
                self.assertIn("'apps/ios/**'", section)
                self.assertNotIn("'apps/android/**'", section)
                self.assertNotIn("'tools/verify-android-companion-interface.py'", section)
        android = event_section(workflow("android-validation.yml"), "push")
        self.assertIn("'apps/android/**'", android)
        self.assertNotIn("'apps/ios/**'", android)

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

    def test_shared_browser_assets_do_not_allocate_native_ui_walkthroughs(self):
        for name in ("ios-visual-walkthrough.yml", "ios-smoke.yml"):
            source = workflow(name)
            pull_request = event_section(source, "pull_request")
            self.assertIn("'!apps/ios/Resources/CompanionRemote/**'", pull_request)
        walkthrough = event_section(workflow("ios-visual-walkthrough.yml"), "push")
        self.assertIn("'!apps/ios/Resources/CompanionRemote/**'", walkthrough)

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

    def test_native_validation_reruns_do_not_cancel_other_attempts(self):
        for name, prefix in (("android-validation.yml", "android-"),
                             ("ios-validation.yml", "ios-")):
            source = workflow(name)
            self.assertIn("group: " + prefix, source)
            self.assertIn("${{ github.run_attempt }}", source)
            self.assertIn("cancel-in-progress: true", source)

    def test_website_workflow_owns_one_lightweight_pr_documentation_gate(self):
        source = workflow("website-validation.yml")
        self.assertIn("  pull_request:\n    types:", source)
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

    def test_main_release_workflows_embed_docs_guard_in_existing_jobs(self):
        names = ("android-validation.yml", "ios-validation.yml",
                 "ios-runtime-artifacts.yml", "ios-visual-walkthrough.yml",
                 "website-validation.yml", "vercel-deploy.yml",
                 "verified-downloads.yml")
        for name in names:
            source = workflow(name)
            self.assertIn("Enforce documentation synchronization", source, name)
            self.assertIn("if: github.event_name == 'push'", source, name)
        walkthrough = workflow("ios-visual-walkthrough.yml")
        self.assertEqual(walkthrough.count("Enforce documentation synchronization"), 2)

    def test_platform_download_publisher_has_independent_completion_ownership(self):
        source = workflow("platform-download-publishing.yml")
        self.assertIn("KANTA Android validation", source)
        self.assertIn("KANTA iOS unsigned artifacts", source)
        self.assertIn("group: platform-download-publishing", source)
        self.assertIn("tools/prepare-platform-download.py", source)
        self.assertIn("tools/publish-platform-download.py", source)
        self.assertIn("ref: main", source)
        self.assertIn("git push origin HEAD:main", source)
        contracts = workflow("verified-downloads.yml")
        self.assertNotIn("workflow_run:", contracts)
        self.assertNotIn("  package:", contracts)

    def test_vercel_production_workflow_does_not_validate_pull_requests(self):
        source = workflow("vercel-deploy.yml")
        self.assertNotIn("\n  pull_request:", source)
        self.assertIn("workflow_dispatch:", source)
        self.assertIn("push:", source)


if __name__ == "__main__":
    unittest.main()
