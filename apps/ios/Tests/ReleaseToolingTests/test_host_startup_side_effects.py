"""Prevent eager microphone/iCloud infrastructure setup during Host render.

This is a source contract, not a simulator crash test. The latter is the
capture-ui workflow and must pass before a release can ship.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class HostStartupSideEffectsTests(unittest.TestCase):
    def test_apple_speech_engine_allocated_only_after_permission(self):
        code = (ROOT / "Sources/App/KantaVoiceSearch.swift").read_text()
        self.assertIn("private var engine: AVAudioEngine?", code)
        self.assertNotIn("private let engine = AVAudioEngine()", code)
        self.assertEqual(code.count("AVAudioEngine()"), 1)
        self.assertIn("guard granted else", code)
        self.assertLess(code.index("guard granted else"), code.index("let voiceEngine = AVAudioEngine()"))
        self.assertIn("engine = nil", code)

    def test_cloud_kvs_not_initialized_while_app_services_construct(self):
        code = (ROOT / "Sources/App/ICloudSyncCoordinator.swift").read_text()
        self.assertIn("private var keyValueStore: NSUbiquitousKeyValueStore", code)
        self.assertNotIn("private let keyValueStore =", code)
        self.assertIn("NSUbiquitousKeyValueStore.default", code)

    def test_audio_diagnostics_are_lazy_and_observer_is_removed(self):
        code = (ROOT / "Sources/Features/Host/KantaAudioInputPanel.swift").read_text()
        constructor = code.split("final class KantaAudioInputDiagnostics:", 1)[1].split(
            "    func activate()", 1
        )[0]
        self.assertNotIn("AVAudioSession.sharedInstance()", constructor)
        self.assertIn("private var routeObserver: NSObjectProtocol?", code)
        self.assertIn("func deactivate()", code)
        self.assertIn("NotificationCenter.default.removeObserver(routeObserver)", code)
        self.assertIn(".onAppear { diagnostics.activate() }", code)
        self.assertIn(".onDisappear { diagnostics.deactivate() }", code)

    def test_saved_intermission_track_does_not_decode_on_host_entry(self):
        code = (ROOT / "Sources/Features/Host/KantaIntermissionMusic.swift").read_text()
        constructor = code.split("    init(audioOwnership:", 1)[1].split("    private static func boundedVolume", 1)[0]
        self.assertNotIn("AVAudioPlayer(contentsOf:", constructor)
        self.assertIn("if shouldPlay, player == nil, let trackURL", code)
        self.assertIn("player = nil", code)
        self.assertIn("trackURL = nil", code)

    def test_intermission_volume_never_recursively_writes_published_property(self):
        code = (ROOT / "Sources/Features/Host/KantaIntermissionMusic.swift").read_text()
        self.assertIn("@Published private(set) var volume: Float = 0.23", code)
        self.assertIn("func setVolume(_ requested: Float)", code)
        self.assertIn("let bounded = Self.boundedVolume(requested)", code)
        self.assertIn("guard volume != bounded else { return }", code)
        self.assertIn('defaults.set(bounded, forKey: "kanta.intermission.volume")', code)
        self.assertIn("set: { intermission.setVolume(Float($0)) }", code)
        self.assertIn("volume = Self.boundedVolume(restoredVolume)", code)
        self.assertNotIn("didSet {\n            volume =", code)
        self.assertNotIn("set: { intermission.volume =", code)

    def test_intermission_volume_does_not_regain_published_observer(self):
        """Guard against the observed Host main-thread stack overflow."""
        code = (ROOT / "Sources/Features/Host/KantaIntermissionMusic.swift").read_text()
        self.assertNotRegex(
            code,
            r"@Published[^\n]*\bvar\s+volume\s*:[^\n{]*\{",
            "Volume must remain an observer-free @Published stored property",
        )
        self.assertIn("func setVolume(_ requested: Float)", code)
        self.assertIn("guard raw.isFinite else { return 0 }", code)

    def test_host_demo_does_not_start_bonjour(self):
        code = (ROOT / "Sources/Features/Host/HostView.swift").read_text()
        self.assertIn("if !isDemoMode {\n                networkHost.start()", code)


if __name__ == "__main__":
    unittest.main()
