import array
import pathlib
import unittest
import wave

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'apps/android/app/src/main/assets/party'


class PartyAssetTests(unittest.TestCase):
    def test_all_eight_normalized_short_pcm_assets(self):
        content = []
        for slot in range(8):
            with self.subTest(slot=slot):
                with wave.open(str(ASSETS / f'pad-{slot}.wav'), 'rb') as wav:
                    self.assertEqual((wav.getnchannels(), wav.getsampwidth(), wav.getframerate()), (1, 2, 22050))
                    self.assertGreater(wav.getnframes(), 0)
                    self.assertLessEqual(wav.getnframes(), 3 * 22050)
                    pcm = wav.readframes(wav.getnframes())
                samples = array.array('h', pcm)
                peak = max(abs(value) for value in samples)
                self.assertTrue(22500 <= peak <= 23500, (slot, peak))
                self.assertLess(len(pcm), 1024 * 1024)
                content.append(pcm)
        self.assertEqual(len(set(content)), 8)
        self.assertTrue((ASSETS / 'SOUND_LICENSES.txt').is_file())

    def test_builtin_loader_uses_packaged_audio_and_custom_overrides(self):
        text = (ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn/SessionController.java').read_text()
        self.assertIn('context.getAssets().open("party/pad-" + i + ".wav")', text)
        self.assertIn('party.loadBuiltins(padFiles)', text)
        self.assertIn('validatePad(slot, clip, false)', text)
        self.assertNotIn('PartySounds.wave(i)', text)


if __name__ == '__main__':
    unittest.main()
