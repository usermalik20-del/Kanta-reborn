"""Source integration guards only; numeric signal regressions run in Gradle coreRegression."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


class SpectrumSourceContractTests(unittest.TestCase):
    def test_framework_free_and_immutable_snapshot(self):
        source = (JAVA / 'core/SpectrumAnalyzer.java').read_text()
        self.assertNotIn('import android.', source)
        self.assertIn('private final double[] power;', source)
        self.assertNotIn('public final double[]', source)
        self.assertIn('Arrays.fill(real, 0)', source)
        self.assertIn('Arrays.fill(imaginary, 0)', source)
        self.assertIn('bin < edges[band + 1]', source)

    def test_existing_throttled_analysis_owns_spectrum(self):
        source = (JAVA / 'MicrophoneInput.java').read_text()
        analyze = source[source.index('private void analyze(Run run, long measuredAt)'):]
        self.assertIn('analysis.execute(', analyze)
        self.assertIn('spectrumAnalyzer.analyze(run.analysisFrame)', analyze)
        self.assertIn('if (current(run)) run.reading = value;', analyze)
        self.assertIn('analysisBusy.compareAndSet(false, true)', source)
        self.assertIn('now - lastAnalysis >= 125', source)
        self.assertIn('sink.offer(block, count)', source)
        self.assertEqual(source.count('new AudioRecord.Builder()'), 1)

    def test_signal_regressions_are_registered(self):
        tests = ROOT / 'apps/android/app/src/test/java/ph/kanta/reborn/core'
        self.assertIn('checks += SpectrumRegressionTests.run();',
                      (tests / 'CoreRegressionTests.java').read_text())
        self.assertTrue((tests / 'SpectrumRegressionTests.java').is_file())


if __name__ == '__main__':
    unittest.main()
