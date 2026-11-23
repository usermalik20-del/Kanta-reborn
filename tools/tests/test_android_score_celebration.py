from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
VIEW = ROOT / "apps/android/app/src/main/java/ph/kanta/reborn/stage/ScoreCelebrationView.java"


class AndroidScoreCelebrationSourceContractTests(unittest.TestCase):
    def test_petals_use_their_planned_hue_before_canvas_draw(self):
        source = VIEW.read_text(encoding="utf-8")
        start = source.index("protected void onDraw(Canvas canvas)")
        end = source.index("private void drawPetal", start)
        draw_body = source[start:end]

        self.assertIn("petalPaint.setColor(hueColor(particle.hue));", draw_body)
        self.assertIn("drawPetal(canvas, x, y, radius);", draw_body)
        self.assertLess(
            draw_body.index("petalPaint.setColor(hueColor(particle.hue));"),
            draw_body.index("drawPetal(canvas, x, y, radius);"),
        )


if __name__ == "__main__":
    unittest.main()
