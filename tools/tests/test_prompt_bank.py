"""Contract checks for the external prompt-bank helper.

The helper records upstream metadata only. These checks keep the upstream
licence requirement, the cache boundary and the curated map honest, and they
fail if upstream prompt text is ever copied into this repository.
"""
import contextlib
import importlib.util
import io
import pathlib
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
HELPER = ROOT / "tools/llm-prompt-bank.py"
DOC = ROOT / "docs/AI_PROMPT_BANK.md"

spec = importlib.util.spec_from_file_location("llm_prompt_bank", HELPER)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

# Distinctive upstream phrases. None of them may appear anywhere in this
# repository: the upstream project publishes no licence, so its text is used
# at runtime and never vendored.
UPSTREAM_PHRASES = (
    "Assistant Operating Specification",
    "Developer Content Policy",
    "Hard Boundaries (Non-Overridable)",
    "VANTA",
    "policy override",
)


class PromptBankMetadataTests(unittest.TestCase):
    def test_pins_upstream_url_and_commit(self):
        self.assertEqual(
            module.UPSTREAM_URL, "https://github.com/togg53192-cmd/jailbreaks"
        )
        self.assertRegex(module.UPSTREAM_COMMIT, r"^[0-9a-f]{40}$")

    def test_curated_map_only_lists_prompt_payloads(self):
        self.assertTrue(module.PROMPT_FILES)
        for name, models in module.PROMPT_FILES.items():
            self.assertTrue(name.endswith(".md") or name.endswith(".txt"), name)
            self.assertTrue(models, name)

    def test_map_covers_the_measured_entries(self):
        for name in ("nemotron-3-ultra.md", "gpt-oss-20b.md", "gemma4.md", "Unified.md"):
            self.assertIn(name, module.PROMPT_FILES)

    def test_modelfiles_are_declared(self):
        self.assertEqual(module.MODELFILES, {"gpt-oss-20b.md", "gemma4.md"})
        for name in module.MODELFILES:
            self.assertIn(name, module.PROMPT_FILES)

    def test_records_missing_upstream_licence(self):
        self.assertIn("no licence", module.UPSTREAM_LICENCE)


class PromptBankBoundaryTests(unittest.TestCase):
    def test_cache_directory_inside_the_repository_is_rejected(self):
        with self.assertRaises(ValueError):
            module.validate_cache_dir(ROOT / "tools" / "cache")
        with self.assertRaises(ValueError):
            module.validate_cache_dir(ROOT)

    def test_cache_directory_outside_the_repository_is_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(
                module.validate_cache_dir(directory), pathlib.Path(directory).resolve()
            )

    def test_system_block_is_extracted_from_a_modelfile(self):
        block = module.extract_system_block('FROM x\n\nSYSTEM """\nhello\n"""\n')
        self.assertEqual(block, "hello")
        self.assertIsNone(module.extract_system_block("plain markdown"))

    def test_unknown_prompt_file_is_not_resolved(self):
        with tempfile.TemporaryDirectory() as directory:
            checkout = pathlib.Path(directory)
            with self.assertRaises(FileNotFoundError):
                module.system_prompt_path(checkout, "nemotron-3-ultra.md")

    def test_command_carries_the_system_prompt(self):
        command = module.build_command("model", "/tmp/system.txt", "", "/tmp/task.txt", 900)
        self.assertIn("--system-file", command)
        self.assertIn("--prompt-file", command)
        self.assertEqual(command[command.index("--max-tokens") + 1], "900")

    def test_runner_is_required_for_a_run(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(module.main(["--run", "not-in-map.md", "--model", "m"]), 2)
            self.assertEqual(module.main(["--run", "Unified.md"]), 2)

    def test_cache_inside_the_repository_fails_cleanly(self):
        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            code = module.main(["--verify-cache", "--cache-dir", str(ROOT / "cache")])
        self.assertEqual(code, 1)
        self.assertIn("FAIL", buffer.getvalue())

    def test_list_reports_the_pinned_commit(self):
        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            self.assertEqual(module.main(["--list"]), 0)
        self.assertIn(module.UPSTREAM_COMMIT, buffer.getvalue())


class PromptBankRepositoryTests(unittest.TestCase):
    def test_upstream_text_is_not_vendored(self):
        scanned = 0
        this_file = pathlib.Path(__file__).resolve()
        for directory in ("tools", "docs"):
            for path in (ROOT / directory).rglob("*"):
                if not path.is_file() or path.suffix not in (".py", ".md", ".txt", ".json"):
                    continue
                if path.resolve() == this_file:
                    # This check necessarily spells the phrases it looks for.
                    continue
                body = path.read_text(encoding="utf-8", errors="replace")
                for phrase in UPSTREAM_PHRASES:
                    self.assertNotIn(
                        phrase, body, "upstream prompt text vendored in %s" % path
                    )
                scanned += 1
        self.assertGreater(scanned, 0)

    def test_document_records_the_pin_and_the_licence_requirement(self):
        self.assertTrue(DOC.is_file())
        body = DOC.read_text(encoding="utf-8")
        self.assertIn(module.UPSTREAM_COMMIT, body)
        self.assertIn(module.UPSTREAM_URL, body)
        self.assertRegex(body, r"(?i)no licence file|licence file is absent|no upstream licence")

    def test_helper_is_runnable(self):
        completed = subprocess.run(
            [sys.executable, str(HELPER), "--help"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        self.assertEqual(completed.returncode, 0)
        self.assertIn("--run", completed.stdout)


if __name__ == "__main__":
    unittest.main()
