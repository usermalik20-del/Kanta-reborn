import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'apps/android/app/src/main'
PDF = MAIN / 'java/ph/kanta/reborn/pdf'


class PdfReaderTest(unittest.TestCase):
    def test_reader_wiring(self):
        reader = (PDF / 'PdfSongbooks.java').read_text()
        viewport = (PDF / 'PdfPageView.java').read_text()
        store = (PDF / 'PdfSongbookStore.java').read_text()
        labels = ElementTree.parse(MAIN / 'res/values/pdf_songbook_strings.xml')
        self.assertEqual(set(re.findall(r'R.string.(pdf_songbooks_\w+)', reader)),
                         {node.attrib['name'] for node in labels.getroot()})
        self.assertIn('page.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1))', reader)
        self.assertIn('worker.submit', reader)
        self.assertIn('rendered.image.recycle()', reader)
        self.assertIn('PAGE_PIXELS = 1800', store)
        self.assertNotIn('Bitmap.createBitmap', viewport)
        self.assertIn('requestDisallowInterceptTouchEvent(true)', viewport)
        self.assertIn('canvas.drawBitmap(image, null, destination, paint)', viewport)
        self.assertLess(reader.index('page.addView(preview'), reader.index('page.addView(controls'))
        self.assertIn('next < 0 || next >= pageCount', reader)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_fit_zoom_and_pan(self):
        with tempfile.TemporaryDirectory() as tmp:
            host = Path(tmp) / 'PdfBoundsCheck.java'
            host.write_text('''package ph.kanta.reborn.pdf;
public final class PdfBoundsCheck {
  static void near(float expected, float actual) {
    if (!(Math.abs(expected - actual) < .001f))
      throw new AssertionError(expected + " != " + actual);
  }
  public static void main(String[] args) {
    PdfPageBounds b = new PdfPageBounds();
    b.layout(600, 300, 400, 800, PdfPageBounds.FIT_WIDTH);
    near(600, b.width);
    near(1200, b.height);
    b.pan(-1000, -1000);
    near(0, b.left);
    near(-900, b.top);
    b.zoomAt(2, 300, 150, 300, 150);
    near(-300, b.left);
    near(-1950, b.top);
    b.zoomAt(10, 300, 150, 350, 150);
    near(4, b.resolvedZoom());
    near(-850, b.left);
    b.zoomAt(PdfPageBounds.FIT_PAGE, 300, 150, 300, 150);
    near(150, b.width);
    near(225, b.left);
    near(300, b.height);
    near(0, b.top);
    if (b.canZoomOut()) throw new AssertionError("Already fitted");
    b.layout(300, 800, 800, 400, PdfPageBounds.FIT_WIDTH);
    near(300, b.width);
    near(325, b.top);
    near(1, PdfPageBounds.savedZoom(Float.NaN));
  }
}
''')
            subprocess.run(['javac', '-d', tmp, str(PDF / 'PdfPageBounds.java'), str(host)], check=True)
            subprocess.run(['java', '-cp', tmp, 'ph.kanta.reborn.pdf.PdfBoundsCheck'], check=True)


if __name__ == '__main__':
    unittest.main()
