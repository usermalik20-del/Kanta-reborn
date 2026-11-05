"""Regression tests for the local preflight guard selector."""
import unittest
from pathlib import Path
from runpy import run_path

REPO_ROOT = Path(__file__).resolve().parents[2]
PREFLIGHT = run_path(str(REPO_ROOT / "tools/preflight.py"))
classify = PREFLIGHT["classify"]
plan = PREFLIGHT["plan"]
GUARDS = PREFLIGHT["GUARDS"]
CI_ONLY = PREFLIGHT["CI_ONLY"]


class PreflightTests(unittest.TestCase):
    def test_android_change_selects_java_structure_guard(self):
        classes = classify({"apps/android/app/src/main/java/example/Player.java"})
        self.assertIn("android", classes)
        commands = [" ".join(c) for c in plan(classes, "base", "head")]
        self.assertIn("tools/check-java-structure.py", commands[1])
        self.assertIn("tools/check-android-workspace-contract.py", commands[2])

    def test_ios_change_selects_presentation_contract(self):
        classes = classify({"apps/ios/Sources/Kanta/StageView.swift"})
        self.assertIn("ios", classes)
        self.assertTrue(any("check-ios-presentation-contract.py" in " ".join(c)
                            for c in plan(classes, "b", "h")))

    def test_web_change_selects_website_verifier(self):
        classes = classify({"apps/web/features.html"})
        self.assertIn("web", classes)
        self.assertTrue(any("verify-website.py" in " ".join(c) for c in plan(classes, "b", "h")))

    def test_workflow_change_selects_ownership_and_doc_sync(self):
        classes = classify({".github/workflows/website-validation.yml"})
        self.assertIn("workflows", classes)
        commands = [" ".join(c) for c in plan(classes, "base", "head")]
        self.assertTrue(any("test_workflow_path_ownership" in c for c in commands))
        self.assertTrue(any("check-documentation-sync.py base head" in c for c in commands))

    def test_tooling_change_runs_the_whole_python_suite(self):
        classes = classify({"tools/verify-website.py"})
        self.assertIn("tooling", classes)
        self.assertTrue(any("discover -s tools/tests" in " ".join(c) for c in plan(classes, "b", "h")))

    def test_documentation_only_change_runs_no_implementation_guard(self):
        classes = classify({"docs/TESTING.md", "README.md"})
        self.assertEqual(classes, {"any"})
        commands = [" ".join(c) for c in plan(classes, "b", "h")]
        self.assertEqual(len(commands), 2)
        self.assertIn("git diff --check", commands[0])
        self.assertIn("check-documentation-sync.py", commands[1])

    def test_documentation_only_paths_do_not_trigger_platform_classes(self):
        classes = classify({"apps/android/README.md", "apps/ios/AGENTS.md", "apps/web/README.md"})
        self.assertEqual(classes, {"any", "branding"})
        self.assertNotIn("android", classes)
        self.assertNotIn("ios", classes)
        self.assertNotIn("web", classes)

    def test_every_local_guard_script_exists(self):
        for name, commands in GUARDS.items():
            for command in commands:
                for token in command:
                    if token.startswith("tools/") and token.endswith(".py"):
                        self.assertTrue((REPO_ROOT / token).is_file(), f"{name}: missing {token}")

    def test_cloud_only_gates_are_disclosed(self):
        self.assertGreaterEqual(len(CI_ONLY), 4)
        joined = " ".join(CI_ONLY).lower()
        for expected in ("gradle", "ios", "node", "release"):
            self.assertIn(expected, joined)

    def test_plans_never_claim_a_local_build(self):
        source = (REPO_ROOT / "tools/preflight.py").read_text()
        self.assertIn("not a build, packaging, simulator or release result", source)


if __name__ == "__main__":
    unittest.main()
