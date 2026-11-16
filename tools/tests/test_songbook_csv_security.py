"""Private Songbook CSV export and parser bounds contracts."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT / "apps/web/songbook.js").read_text(encoding="utf-8")


class SongbookCSVSecurityTests(unittest.TestCase):
    def test_export_serializes_every_untrusted_field_and_neutralizes_formulas(self):
        self.assertIn("function csvCell(value)", SOURCE)
        self.assertIn(r"/^[\s\u0000-\u001f]*[=+\-@]/", SOURCE)
        self.assertIn(r"""cell.replace(/"/g, '""')""", SOURCE)
        self.assertIn("row.map(csvCell)", SOURCE)
        self.assertIn("songs.map(song=>[song.code,song.title,song.artist,song.availability])", SOURCE)
        self.assertIn("new Blob([data]", SOURCE)
        self.assertNotIn('new Blob(["\\uFEFF"+csv]', SOURCE)
        self.assertNotIn("csv=plain", SOURCE)

    def test_final_csv_row_is_included_in_the_row_limit(self):
        self.assertIn(
            'if(row.length||cell!==""){row.push(cell);rows.push(row);if(rows.length>maxRows+1)throw Error("Too many songs for this browser.");}',
            SOURCE,
        )

    def test_songbook_rendering_does_not_use_html_injection(self):
        self.assertNotIn("innerHTML", SOURCE)


if __name__ == "__main__":
    unittest.main()
