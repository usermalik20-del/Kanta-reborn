from __future__ import annotations

import ast
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


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


if __name__ == "__main__":
    unittest.main()
