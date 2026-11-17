"""Private Songbook state and decorative-effect ownership contract."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "apps/web"


class SongbookUIContractTests(unittest.TestCase):
    def test_core_script_owns_unlock_lock_and_navigation(self):
        core = (WEB / "songbook.js").read_text(encoding="utf-8")
        page = (WEB / "songbook.html").read_text(encoding="utf-8")
        scripts = re.findall(r'<script[^>]+src=["\']([^"\']+)', page)
        self.assertIn('$("unlock-btn").addEventListener("click"', core)
        self.assertIn('$("lock").addEventListener("click"', core)
        self.assertIn('$("prev").addEventListener("click"', core)
        self.assertIn('$("next").addEventListener("click"', core)
        self.assertIn('function turn(dir)', core)
        self.assertLess(scripts.index("/songbook.js"), scripts.index("/songbook-fx.js"))

    def test_fx_script_cannot_override_private_reader_state(self):
        fx = (WEB / "songbook-fx.js").read_text(encoding="utf-8")
        self.assertNotIn("initLockState", fx)
        self.assertNotIn("initPageFlip", fx)
        self.assertNotIn("addEventListener(\"click\"", fx)
        self.assertNotIn("addEventListener(\"keydown\"", fx)
        self.assertNotIn("innerHTML", fx)
        self.assertNotIn("songs-left", fx)
        self.assertNotIn("songs-right", fx)

    def test_core_page_flip_animation_is_not_hidden_by_theme(self):
        theme = (WEB / "theme.css").read_text(encoding="utf-8")
        songbook = (WEB / "songbook.css").read_text(encoding="utf-8")
        self.assertNotIn(".flip-sheet { display: none", theme)
        self.assertIn(".physical-book.next .flip-sheet", songbook)
        self.assertIn(".physical-book.prev .flip-sheet", songbook)


if __name__ == "__main__":
    unittest.main()
