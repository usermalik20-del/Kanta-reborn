"""Batch H input ownership contracts plus executable production Java DSP/route tests."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


class MicrophoneInputTests(unittest.TestCase):
    def test_executable_dsp_and_route_policy(self):
        self.assertIsNotNone(shutil.which('javac'), 'Java compiler required for DSP regression')
        with tempfile.TemporaryDirectory() as output:
            subprocess.run(['javac', '-d', output,
                            str(JAVA / 'core/MicrophoneInputDsp.java'),
                            str(JAVA / 'core/InputRouteGuard.java'),
                            str(ROOT / 'apps/android/app/src/test/java/ph/kanta/reborn/core/MicrophoneInputRegressionTests.java')],
                           check=True, capture_output=True, text=True)
            result = subprocess.run(['java', '-cp', output,
                                     'ph.kanta.reborn.core.MicrophoneInputRegressionTests'],
                                    check=True, capture_output=True, text=True)
            self.assertIn('30 checks passed', result.stdout)

    def test_capture_verifies_before_consumers_and_gate_stays_monitor_only(self):
        source = (JAVA / 'MicrophoneInput.java').read_text()
        self.assertIn('AudioManager.GET_DEVICES_INPUTS', source)
        self.assertLess(source.index('input.setPreferredDevice('), source.index('input.startRecording()'))
        self.assertLess(source.index('input.getRoutedDevice()'), source.index('run.dsp.applyGain('))
        self.assertLess(source.index('State.REJECTED'), source.index('run.dsp.applyGain('))
        self.assertLess(source.index('run.dsp.applyGain('), source.index('estimator.offer(block, count)'))
        self.assertLess(source.index('frame[filled++]'), source.index('run.dsp.applyMonitorGate('))
        self.assertLess(source.index('estimator.offer(block, count)'), source.index('run.dsp.applyMonitorGate('))
        self.assertLess(source.index('run.dsp.applyMonitorGate('), source.index('sink.offer(block, count)'))
        self.assertEqual(source.count('new AudioRecord.Builder()'), 1)
        self.assertIn('preferred = null;', source.split('void stop()', 1)[1])
        self.assertNotIn('SharedPreferences', source)

    def test_selection_retires_monitor_without_implicit_activation(self):
        source = (JAVA / 'SessionController.java').read_text()
        selection = source.split('void selectMicrophone(', 1)[1].split('void startMicrophone()', 1)[0]
        self.assertLess(selection.index('stopMicrophone()'), selection.index('microphone.selectInput(device)'))
        self.assertNotIn('microphone.start()', selection)
        self.assertNotIn('monitor.start(', selection)
        self.assertNotIn('putInt', selection)
        self.assertIn('"micInputGainDb"', source)
        self.assertIn('"micInputGateDb"', source)
        self.assertIn('catch (ClassCastException invalid)', source)
        activity = (JAVA / 'MainActivity.java').read_text()
        self.assertIn('if (microphoneInputDialog != null) microphoneInputDialog.dismiss();', activity)
        self.assertIn('session.microphone.clipping()', activity)
        self.assertIn('session.microphone.inputRms()', activity)


if __name__ == '__main__':
    unittest.main()
