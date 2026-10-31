"""Regression tests for the implementation-to-documentation guard."""
import unittest
from pathlib import Path
from runpy import run_path

SYNC = run_path(str(Path(__file__).resolve().parents[2] / "tools/check-documentation-sync.py"))
is_website_change = SYNC["is_website_change"]
required_docs = SYNC["required_docs"]
validate = SYNC["validate"]


class DocumentationSyncTests(unittest.TestCase):
    def test_website_changes_require_root_and_website_docs(self):
        required = required_docs({"apps/web/site-consent.js"})
        self.assertEqual(required, {
            "README.md", "apps/web/README.md",
            "docs/ARCHITECTURE.md", "docs/TESTING.md",
        })

    def test_android_changes_require_android_release_docs(self):
        required = required_docs({"apps/android/app/src/main/java/example/Player.java"})
        self.assertEqual(required, {
            "README.md", "apps/android/README.md",
            "docs/ARCHITECTURE.md", "docs/TESTING.md", "docs/RECONSTRUCTION.md",
        })

    def test_ios_changes_require_ios_owner_and_release_docs(self):
        required = required_docs({"apps/ios/Sources/App/App.swift"})
        self.assertEqual(required, {
            "README.md", "apps/ios/AGENTS.md",
            "docs/ARCHITECTURE.md", "docs/TESTING.md", "docs/RECONSTRUCTION.md",
        })

    def test_workflow_changes_require_engineering_policy_docs(self):
        self.assertEqual(required_docs({".github/workflows/android-validation.yml"}), {
            "README.md", "AGENTS.md", "docs/ARCHITECTURE.md", "docs/TESTING.md",
        })

    def test_tools_changes_require_testing_docs(self):
        self.assertEqual(required_docs({"tools/verify-website.py"}), {"docs/TESTING.md"})

    def test_privacy_changes_require_platform_disclosure(self):
        required = required_docs({"apps/web/privacy.html"})
        self.assertEqual(required, {
            "apps/web/privacy.html", "apps/android/app/src/main/assets/privacy.txt",
            "apps/ios/PRIVACY.md", "apps/ios/PublicSite/privacy.html",
        })

    def test_privacy_copy_edits_do_not_allocate_native_documentation_sets(self):
        required = required_docs({"apps/ios/PRIVACY.md", "apps/ios/PublicSite/privacy.html",
                                 "apps/android/app/src/main/assets/privacy.txt"})
        self.assertNotIn("apps/ios/AGENTS.md", required)
        self.assertNotIn("docs/RECONSTRUCTION.md", required)

    def test_application_pages_cannot_masquerade_as_documentation_updates(self):
        changed = {"apps/web/site-consent.js", "apps/web/index.html"}
        _, missing = validate(changed)
        self.assertEqual(missing, {
            "README.md", "apps/web/README.md",
            "docs/ARCHITECTURE.md", "docs/TESTING.md",
        })

    def test_website_job_is_selected_only_for_website_and_disclosure_paths(self):
        self.assertTrue(is_website_change("apps/web/site-consent.js"))
        self.assertTrue(is_website_change("apps/ios/PublicSite/privacy.html"))
        self.assertTrue(is_website_change("apps/android/app/src/main/assets/privacy.txt"))
        self.assertFalse(is_website_change("apps/android/app/src/main/java/example/Player.java"))
        self.assertFalse(is_website_change("apps/ios/Sources/App/App.swift"))

    def test_missing_docs_fail_and_complete_docs_pass(self):
        changed = {"apps/web/site-consent.js", "apps/web/README.md", "docs/ARCHITECTURE.md"}
        _, missing = validate(changed)
        self.assertEqual(missing, {"README.md", "docs/TESTING.md"})
        complete = changed | {"README.md", "docs/TESTING.md"}
        _, missing = validate(complete)
        self.assertEqual(missing, set())


if __name__ == "__main__":
    unittest.main()
