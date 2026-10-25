"""Guards for KANTA Stage design-system membership and adoption.

These are source invariants, not behavioural tests: the iOS target is built by
hosted macOS CI, so the cheap guard against silent rot belongs here.

Each assertion below corresponds to a defect that was actually introduced once
during this work, or to an identifier the iPhone/iPad walkthrough depends on.
"""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCES = ROOT / "Sources"
DESIGN_SYSTEM = SOURCES / "DesignSystem"
HOST = SOURCES / "Features/Host"
PROJECT = ROOT / "project.yml"

# Declaration forms that would collide if a second file repeated them.
DECLARATION = re.compile(
    r"^(?:public |private |internal |final )?(struct|enum|class|typealias)\s+([A-Za-z_]\w*)",
    re.MULTILINE,
)

# The design-system components the Host layers are expected to adopt.
COMPONENTS = (
    "KantaColors",
    "KantaTypography",
    "KantaBackground",
    "KantaCard",
    "KantaSongRow",
    "KantaEmptyState",
    "KantaTransportBar",
)


def design_system_sources():
    files = sorted(DESIGN_SYSTEM.glob("*.swift"))
    assert files, "DesignSystem directory is empty"
    return {path.name: path.read_text() for path in files}


def declared_component_names():
    names = set()
    for text in design_system_sources().values():
        for kind, symbol in DECLARATION.findall(text):
            if kind != "typealias":
                names.add(symbol)
    return names


class DesignSystemMembershipTests(unittest.TestCase):
    def test_directory_is_listed_in_the_app_target(self):
        """XcodeGen enumerates sources explicitly; an unlisted directory is
        never compiled, so the app would fail on unresolved symbols."""
        project = PROJECT.read_text()
        self.assertIn("- path: Sources/DesignSystem", project)

    def test_every_component_is_declared_in_a_real_file(self):
        """Files written through the agent's file writer have silently failed
        to land in this repo before, leaving a dangling reference in HostView.
        Each component the Host adopts must exist on disk."""
        declared = declared_component_names()
        missing = [name for name in COMPONENTS if name not in declared]
        self.assertEqual(missing, [], f"referenced but never declared: {missing}")

    def test_sources_are_plain_declarations(self):
        """A bare top-level string literal is not legal Swift in a non-main
        file and breaks the whole build."""
        for name, text in design_system_sources().items():
            first = next(
                line for line in text.splitlines() if line.strip()
                and not line.strip().startswith("//")
            )
            self.assertTrue(
                first.startswith(("import ", "@")),
                f"{name} starts with {first!r} instead of an import",
            )

    def test_no_duplicate_declarations_across_files(self):
        seen = {}
        for name, text in design_system_sources().items():
            for kind, symbol in DECLARATION.findall(text):
                self.assertNotIn(
                    symbol,
                    seen,
                    f"{symbol} declared in both {seen.get(symbol)} and {name}",
                )
                seen[symbol] = name

    def test_no_star_reexport_shim(self):
        """A `typealias KantaColors = <private enum>` re-export cannot work and
        previously shadowed the real components."""
        for name, text in design_system_sources().items():
            self.assertNotIn("typealias", text, f"{name} re-declares a component")

    def test_components_are_used_by_the_host(self):
        adopted = (HOST / "HostView.swift").read_text() + (
            HOST / "KantaStageSearchView.swift"
        ).read_text()
        for component in ("KantaSongRow(", "KantaEmptyState(", "KantaTransportBar("):
            self.assertIn(component, adopted, f"{component} is not adopted anywhere")

    def test_empty_state_stays_layout_agnostic(self):
        """It is embedded in List rows, so it must not claim full height."""
        text = (DESIGN_SYSTEM / "KantaEmptyState.swift").read_text()
        self.assertIn("case compact", text)
        self.assertNotIn("maxHeight: .infinity", text)


class TransportSingleSourceTests(unittest.TestCase):
    def test_bar_owns_no_transport_state(self):
        """The snapshot is the single source of truth; a private copy would
        desync from playback."""
        text = (DESIGN_SYSTEM / "KantaTransportBar.swift").read_text()
        self.assertNotIn("@State", text)
        self.assertIn("let snapshot: KaraokeSnapshot", text)

    def test_host_does_not_duplicate_transport_inline(self):
        """The always-on bar supersedes the old scroll-away copy."""
        host = (HOST / "HostView.swift").read_text()
        self.assertIn("KantaTransportBar(", host)
        self.assertNotIn('.accessibilityLabel("Next song")', host)

    def test_canvas_cannot_intercept_taps(self):
        """A full-bleed decorative layer in the presentation ZStack is the same
        class of defect as the stage dead-tap bug."""
        host = (HOST / "HostView.swift").read_text()
        self.assertIn(".background { KantaBackground().allowsHitTesting(false) }", host)
        self.assertNotIn("ZStack {\n            KantaBackground()", host)


class WalkthroughIdentifierTests(unittest.TestCase):
    def test_stage_identifiers_survive_the_restyle(self):
        search = (HOST / "KantaStageSearchView.swift").read_text()
        for identifier in ("stage.searchQuery", "stage.reserve", "stage.keypad."):
            self.assertIn(identifier, search, f"{identifier} was dropped")

    def test_walkthrough_transport_identity_is_untouched(self):
        player = (HOST / "KantaMainPlayerView.swift").read_text()
        self.assertIn('accessibilityIdentifier("stage.playPause")', player)
        self.assertIn('accessibilityIdentifier("stage.next")', player)
        # The walkthrough matches this button by identifier and asserts the
        # label flips between Pause and Play.
        self.assertIn('? "Pause" : "Play"', player)

    def test_queue_button_label_is_not_shadowed(self):
        """The walkthrough taps app.buttons["Queue"], so the new bar must not
        introduce a second control with that label."""
        bar = (DESIGN_SYSTEM / "KantaTransportBar.swift").read_text()
        self.assertNotIn('accessibilityLabel("Queue")', bar)
        self.assertNotIn('accessibilityLabel("Previous song")', bar)


if __name__ == "__main__":
    unittest.main()
