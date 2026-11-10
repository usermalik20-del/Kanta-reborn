from __future__ import annotations

import ast
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parent


class AndroidRuntimeRunnerContractTests(unittest.TestCase):
    def test_runtime_runner_parses_and_reaches_acceptance_steps(self) -> None:
        source_path = ROOT / "run-android-runtime.py"
        source = source_path.read_text(encoding="utf-8")
        module = ast.parse(source, filename=str(source_path))
        main = next(
            node for node in module.body
            if isinstance(node, ast.FunctionDef) and node.name == "main"
        )

        network_guard = next(
            node for node in ast.walk(main)
            if isinstance(node, ast.If)
            and isinstance(node.test, ast.UnaryOp)
            and isinstance(node.test.op, ast.Not)
            and isinstance(node.test.operand, ast.Name)
            and node.test.operand.id == "network_ok"
        )
        self.assertTrue(
            any(isinstance(node, ast.Raise) for node in network_guard.body),
            "missing private IPv4 must fail explicitly",
        )

        guarded_nodes = set()
        for statement in network_guard.body:
            guarded_nodes.update(ast.walk(statement))

        acceptance_calls = [
            node for node in ast.walk(main)
            if isinstance(node, ast.Call)
            and isinstance(node.func, ast.Name)
            and node.func.id == "run"
            and node.args
            and isinstance(node.args[0], ast.Name)
            and node.args[0].id == "adb"
            and len(node.args) > 1
            and isinstance(node.args[1], ast.Constant)
            and node.args[1].value == "install"
        ]
        self.assertEqual(len(acceptance_calls), 2, "both APKs must be installed")
        self.assertTrue(
            all(call not in guarded_nodes for call in acceptance_calls),
            "APK installation must run after the network guard, not inside its failure branch",
        )

        self.assertIn('screenshots = ROOT / "files"', source)
        self.assertIn("screenshot_path = screenshots / screenshot_name", source)
        self.assertIn("if screenshot_path.stat().st_size == 0:", source)
        self.assertNotIn("if screenshot_path.exists()", source)
        self.assertIn('"am", "instrument"', source)
        self.assertIn('[str(adb), "shell", "screencap", "-p"]', source)

    def test_first_launch_activates_the_newly_created_stage(self) -> None:
        source = (REPO_ROOT / "apps/android/app/src/main/java/ph/kanta/reborn/MainActivity.java").read_text()
        start = source.index("protected void onStart()")
        body = source[start:source.index("protected void onPause()", start)]
        self.assertLess(body.index("session.listen(this::render);"),
                        body.index("stagePresenter.setVisualActive("))

    def test_live_video_evidence_comes_from_texture_updates(self) -> None:
        base = REPO_ROOT / "apps/android/app/src/main/java/ph/kanta/reborn"
        background = (base / "stage/BackgroundStage.java").read_text()
        callback = background.split("void onSurfaceTextureUpdated(", 1)[1].split("});", 1)[0]
        self.assertIn("videoFrameListener.run();", callback)
        self.assertIn("visualActive", callback)
        self.assertIn("currentSource == Source.BGV", callback)
        presenter = (base / "stage/StagePresenterImpl.java").read_text()
        self.assertIn("backgroundStage.setVideoFrameListener(", presenter)
        ticker = presenter.split("frameTicker = new Runnable()", 1)[1].split("// Initially hide", 1)[0]
        self.assertNotIn("recordLiveFrame", ticker)
        fullscreen = (base / "StageSurface.java").read_text()
        refresh = fullscreen.split("void refresh()", 1)[1].split("void mountCamera", 1)[0]
        self.assertNotIn("recordLiveFrame", refresh)
        callback = fullscreen.split("void onSurfaceTextureUpdated(", 1)[1]
        self.assertIn("presence.recordLiveFrame();", callback)
        self.assertIn("active", callback)

    def test_runtime_failure_fails_ci_but_diagnostics_are_preserved(self) -> None:
        workflow = (REPO_ROOT / ".github/workflows/android-validation.yml").read_text()
        runtime = workflow.split("- name: Android emulator runtime checks", 1)[1].split("- name:", 1)[0]
        self.assertNotIn("continue-on-error: true", runtime)
        diagnostics = workflow.split("- name: Preserve Android runtime diagnostics", 1)[1]
        self.assertIn("if: always()", diagnostics)
        self.assertIn("name: android-reconstruction-checks", diagnostics)

    def test_standby_checks_run_after_the_stage_view_is_resolved(self) -> None:
        """The standby checks read persistentPrimaryStage, so it must already be assigned.

        Regression: stageStandbyChecks() was once called above the assignment, so its first
        assertion failed, the instrumentation process crashed before any capture() ran, and
        the emulator step still reported success because it is continue-on-error.
        """
        source_path = (
            REPO_ROOT / "apps/android/app/src/androidTest/java/ph/kanta/reborn/RuntimeChecks.java"
        )
        source = source_path.read_text(encoding="utf-8")
        start = source.index("public void onStart()")
        # onStart() is bounded by the javadoc of the first helper it calls.
        body = source[start:source.index("Proves the empty stage", start)]

        assignment = body.index("persistentPrimaryStage = activity.getWindow()")
        standby = body.index("stageStandbyChecks();")
        self.assertLess(
            assignment,
            standby,
            "stageStandbyChecks() runs before persistentPrimaryStage is assigned, so its "
            "first check throws before any screenshot is captured",
        )

        # The helpers behind the standby assertions must not silently tolerate a null stage.
        for helper in ("stageStandbyChecks", "standbyLiveFrame"):
            match = re.search(
                r"private [\w<>.\[\]]+ " + helper + r"\([^)]*\)\s*\{",
                source,
            )
            self.assertIsNotNone(match, helper + " helper disappeared; update this guard")
            self.assertIn(
                "persistentPrimaryStage",
                source[match.end():match.end() + 700],
                helper + " no longer reads persistentPrimaryStage; update this guard",
            )


if __name__ == "__main__":
    unittest.main()
