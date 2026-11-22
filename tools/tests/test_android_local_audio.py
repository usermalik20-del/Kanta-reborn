from pathlib import Path
import re
import unittest


JAVA = (Path(__file__).resolve().parents[2]
        / "apps/android/app/src/main/java/ph/kanta/reborn")


class LocalAudioTest(unittest.TestCase):
    def test_countdown_waits_for_readiness_and_start_acknowledgement(self):
        controller = (JAVA / "SessionController.java").read_text()
        start_local = controller.split("private void startLocal(", 1)[1].split("private final ExecutorService", 1)[0]
        self.assertIn("prepare(entry, queued, seconds);", start_local)
        self.assertNotIn("countdownHost.show", start_local)
        self.assertIn("Runnable afterRoute = () -> showReady(candidate);", controller)
        cue = controller.split("private void showReady(", 1)[1].split("private void startReady(", 1)[0]
        self.assertIn("() -> startReady(candidate)", cue)
        self.assertNotIn("clearCountdown()", cue)
        started = controller.split("public void started(", 1)[1].split("public void completed(", 1)[0]
        self.assertLess(started.index("if (!stillPending(candidate)) return;"), started.index("clearCountdown();"))
        self.assertLess(started.index("clearCountdown();"), started.index("queue.remove(entry.id)"))
        stage = (JAVA / "stage/CountdownStage.java").read_text()
        finish = stage.split("private void finish()", 1)[1].split("@Override", 1)[0]
        self.assertIn("currentStep = -1;", finish)
        self.assertNotIn("setVisibility(GONE)", finish)

    def test_backends_acknowledge_observed_progress(self):
        media = (JAVA / "FileAudioPlayback.java").read_text()
        synth = (JAVA / "SoundFontPlayback.java").read_text()
        self.assertIn("player.getCurrentPosition() > startPosition", media)
        self.assertIn("head > startHead", synth)
        for source in (media, synth):
            self.assertIn("START_ACK_TIMEOUT_MS", source)

    def test_midi_prefills_and_refills_without_blocking_worker(self):
        synth = (JAVA / "SoundFontPlayback.java").read_text()
        self.assertIn("fillOutput();\n            if (disposed || request != startRequest) return;\n            output.play();", synth)
        self.assertIn("writes < MAX_WRITES_PER_PUMP", synth)
        self.assertIn("AudioTrack.WRITE_NON_BLOCKING", synth)
        self.assertIn("if (written == 0) return;", synth)

    def test_pause_retires_unacknowledged_resume(self):
        controller = (JAVA / "SessionController.java").read_text()
        synth = (JAVA / "SoundFontPlayback.java").read_text()
        media = (JAVA / "FileAudioPlayback.java").read_text()
        self.assertIn("if (!resuming || !foreground) return;", controller)
        self.assertIn("if (player != null && (playing || resuming))", controller)
        self.assertIn("!disposed && acknowledged == startRequest", synth)
        self.assertIn("public void pause() {\n    ++startRequest;", synth)
        self.assertIn("awaitingStart = false;\n      main.removeCallbacks(pollStart);", media)

    def test_monitor_does_not_force_a_wired_media_strategy_route(self):
        monitor = (JAVA / "MicrophoneMonitor.java").read_text()
        self.assertIn("track.setPreferredDevice(run.device)", monitor)
        self.assertIn("run.guard.verify(routed(track)", monitor)
        self.assertIn("writeSilence(track, warmup);", monitor)
        self.assertIn("worker.execute(() -> main.post(ready));", monitor)

    def test_monitor_raises_ceiling_only_for_verified_wired_headsets(self):
        monitor = (JAVA / "MicrophoneMonitor.java").read_text()
        self.assertIn("DEFAULT_GAIN = .16f", monitor)
        self.assertIn("run != null && !run.cancelled && run.ready", monitor)
        self.assertIn("? .60f : .35f", monitor)
        self.assertIn("Math.min(maxGain(), value)", monitor)
        self.assertIn("Math.min(gain, maxGain(run))", monitor)
        wired = monitor.split("static boolean currentWiredHeadset(int type) {", 1)[1].split("}", 1)[0]
        self.assertEqual(set(re.findall(r"AudioDeviceInfo\.TYPE_(\w+)", wired)), {
            "WIRED_HEADSET", "WIRED_HEADPHONES", "USB_HEADSET",
        })


if __name__ == "__main__":
    unittest.main()
