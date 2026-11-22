"""Source guards for the voice setup, input ownership, and muted route handshake."""
from pathlib import Path
import unittest

JAVA = (Path(__file__).resolve().parents[2]
        / 'apps/android/app/src/main/java/ph/kanta/reborn')


def source(name):
    return (JAVA / (name + '.java')).read_text()


class MicMonitorRecoveryTest(unittest.TestCase):
    def test_picker_arms_only_explicit_supported_output_and_projects_state(self):
        main = source('MainActivity')
        picker = main.split('void toggleMonitoring()', 1)[1].split('private void clearMonitorSetup', 1)[0]
        self.assertLess(picker.index('outputs.isEmpty()'), picker.index('pendingMonitorOutput = output;'))
        self.assertLess(picker.index('.setItems'), picker.index('startMicrophoneWithPermission()'))
        self.assertIn('finishMonitorSetup();', main)
        self.assertIn('micVoiceScreen.setMicrophoneState(', main)
        self.assertIn('micVoiceScreen.setMonitorState(pendingMonitorOutput != null', main)

    def test_output_interruptions_do_not_stop_dry_capture(self):
        session = source('SessionController')
        events = session.split('.setOnAudioFocusChangeListener(', 1)[1].split('context.registerReceiver', 1)[0]
        self.assertIn('interruptMonitor(', events)
        self.assertNotIn('microphone.stop()', events)
        capture = session.split('void startMicrophone()', 1)[1].split('void startMonitor(', 1)[0]
        self.assertNotIn('requestAudioFocus', capture)
        background = session.split('void hostVisible(boolean visible)', 1)[1].split('private void updateIntermission', 1)[0]
        self.assertIn('microphone.stop()', background)

    def test_only_startup_can_wait_on_mismatched_route(self):
        monitor = source('MicrophoneMonitor')
        start = monitor.index('writeSilence(track, warmup);')
        settle = monitor.index('run.guard.verifyMutedStartup(routed(track)', start)
        live = monitor.index('run.guard.verify(routed(track)', settle)
        self.assertLess(monitor.index('track.setVolume(0)'), settle)
        self.assertLess(settle, live)
        self.assertLess(live, monitor.index('track.write(block,', live))
        self.assertNotIn('case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:', monitor)
        self.assertNotIn('case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:', monitor)


if __name__ == '__main__':
    unittest.main()
