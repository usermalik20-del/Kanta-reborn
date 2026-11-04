"""Regression tests for the Java nested-method structure check."""
import tempfile
import unittest
from pathlib import Path
from runpy import run_path

CHECK = run_path(str(Path(__file__).resolve().parents[2] / "tools/check-java-structure.py"))
JavaStructure = CHECK["JavaStructure"]


def scan(source: str) -> list:
    java = JavaStructure("/nonexistent/Placeholder.java")
    # Monkey-patch the path source: run the checker over a temp file instead.
    with tempfile.NamedTemporaryFile("w", suffix=".java", delete=False) as f:
        f.write(source)
        name = f.name
    try:
        java.path = name
        return java.run()
    finally:
        Path(name).unlink()


class JavaStructureTests(unittest.TestCase):
    def test_nested_method_is_flagged(self):
        source = (
            "class A {\n"
            "  void outer() {\n"
            "    int x = 1;\n"
            "  }\n"
            "  void inner() {\n"
            "    void nested() {}\n"
            "  }\n"
            "}\n"
        )
        findings = scan(source)
        self.assertTrue(any("method declared inside a method body" in f for f in findings))
        self.assertTrue(any("inner" in f for f in findings))

    def test_constructor_chain_is_clean(self):
        source = (
            "class B {\n"
            "  B() { this(null); }\n"
            "  B(Object o) { super(o); }\n"
            "  void run() { helper(); }\n"
            "  private void helper() {}\n"
            "}\n"
        )
        self.assertEqual(scan(source), [])

    def test_inner_class_members_are_clean(self):
        source = (
            "class C {\n"
            "  static final class Inner {\n"
            "    void member() {}\n"
            "    int value() { return 1; }\n"
            "  }\n"
            "  void use() { Inner i = new Inner(); i.member(); }\n"
            "}\n"
        )
        self.assertEqual(scan(source), [])

    def test_anonymous_class_overrides_are_clean(self):
        source = (
            "class D {\n"
            "  void go() {\n"
            "    Runnable r = new Runnable() {\n"
            "      @Override public void run() {}\n"
            "    };\n"
            "  }\n"
            "}\n"
        )
        self.assertEqual(scan(source), [])

    def test_strings_and_comments_do_not_confuse_braces(self):
        source = (
            "class E {\n"
            "  String s = \"{ }\";\n"
            "  char c = '{';\n"
            "  // void fake() {\n"
            "  void real() {}\n"
            "}\n"
        )
        self.assertEqual(scan(source), [])

    def test_enum_and_record_bodies_are_clean(self):
        source = (
            "class F {\n"
            "  enum Kind { A, B }\n"
            "  record Pair(int x, int y) {}\n"
            "  Kind pick() { return Kind.A; }\n"
            "}\n"
        )
        self.assertEqual(scan(source), [])


if __name__ == "__main__":
    unittest.main()