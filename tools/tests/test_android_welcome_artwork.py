"""Source and asset contracts for the launch/welcome screens.

The welcome surface (``MainActivity.attachStartupContent``) draws one full-bleed
artwork. A landscape device must not be served the portrait composition: because the
drawable is scaled with ``FIT_CENTER`` inside a wide, short content area, the portrait
art is letterboxed into a narrow vertical strip with the rest of the screen empty.

The splash already ships both variants (`drawable-nodpi` and `drawable-land-nodpi`).
These checks keep the welcome artwork on the same orientation-aware contract.
"""

from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'apps/android/app/src/main'
JAVA = MAIN / 'java/ph/kanta/reborn'
RES = MAIN / 'res'


def webp_dimensions(path):
    """Reads width/height from a VP8/VP8L/VP8X WebP RIFF header."""
    data = path.read_bytes()
    if data[:4] != b'RIFF' or data[8:12] != b'WEBP':
        raise AssertionError(f'{path.name} is not a WebP image')
    chunk = data[12:16]
    if chunk == b'VP8X':
        width = int.from_bytes(data[24:27], 'little') + 1
        height = int.from_bytes(data[27:30], 'little') + 1
        return width, height
    if chunk == b'VP8 ':
        start = data.index(b'\x9d\x01\x2a', 20)
        width, height = struct.unpack_from('<HH', data, start + 3)
        return width & 0x3FFF, height & 0x3FFF
    if chunk == b'VP8L':
        bits = int.from_bytes(data[21:25], 'little')
        return (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1
    raise AssertionError(f'{path.name} uses an unsupported WebP chunk {chunk!r}')


class WelcomeArtworkTest(unittest.TestCase):
    def setUp(self):
        self.activity = (JAVA / 'MainActivity.java').read_text()

    def test_welcome_screen_references_the_drawable_name(self):
        attach = self.activity.split('private void attachStartupContent()', 1)[1] \
            .split('\n  }', 1)[0]
        self.assertIn('R.drawable.kanta_startup_art', attach)
        # CENTER_CROP fills the dialog on phones of any aspect ratio; the orientation
        # variant supplies the matching composition.
        self.assertIn('ImageView.ScaleType.CENTER_CROP', attach)

    def test_both_orientation_variants_are_packaged(self):
        portrait = RES / 'drawable-nodpi/kanta_startup_art.webp'
        landscape = RES / 'drawable-land-nodpi/kanta_startup_art.webp'
        self.assertTrue(portrait.is_file(), 'portrait welcome artwork is missing')
        self.assertTrue(landscape.is_file(), 'landscape welcome artwork is missing')

    def test_variant_aspect_ratios_match_their_orientation(self):
        portrait = webp_dimensions(RES / 'drawable-nodpi/kanta_startup_art.webp')
        landscape = webp_dimensions(RES / 'drawable-land-nodpi/kanta_startup_art.webp')
        self.assertGreater(portrait[1], portrait[0], f'portrait art is not tall: {portrait}')
        self.assertGreater(landscape[0], landscape[1], f'landscape art is not wide: {landscape}')

    def test_welcome_art_stays_within_the_apk_budget(self):
        # The reference PNG was 6 MB; the packaged WebP must stay far below that.
        for name in ('drawable-nodpi/kanta_startup_art.webp',
                     'drawable-land-nodpi/kanta_startup_art.webp'):
            size = (RES / name).stat().st_size
            self.assertLess(size, 600_000, f'{name} is {size} bytes')

    def test_no_oversized_png_welcome_artwork_is_shipped(self):
        strays = list(RES.glob('drawable*/kanta_startup_art.png'))
        self.assertEqual([], [str(p) for p in strays])


if __name__ == '__main__':
    unittest.main()
