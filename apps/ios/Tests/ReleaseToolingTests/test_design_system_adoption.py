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


# ---------------------------------------------------------------------------
# Stage v2 visual-contract guards.
#
# Context: a "redesign" once shipped from this repo while changing nothing a
# user could see.  It moved the canvas from #000000 to #070810 (~6% channel
# change) and the accent from #B866D7 to #BD5CDE (~4%), and left the canvas a
# flat fill.  Every assertion below is written to fail on exactly that class of
# non-change: exact hex evidence (not keyword presence), absence of the
# pre-redesign hexes, and structural proof that the canvas is a gradient with
# blooms rather than one flat colour.
# ---------------------------------------------------------------------------

COLORS = DESIGN_SYSTEM / "KantaColors.swift"
BACKGROUND = DESIGN_SYSTEM / "KantaBackground.swift"
COVER_ART = DESIGN_SYSTEM / "KantaCoverArt.swift"
SONG_ROW = DESIGN_SYSTEM / "KantaSongRow.swift"

# CONTRACT.md "Palette - exact values" / mock/stage-v2.html :root.
CONTRACT_HEXES = ("0A0614", "140A26", "FF3D8B", "A24BFF", "3DE0A8", "4DE3F0")

# The palette that was on disk at the last commit (bf71e5d) - i.e. the look the
# redesign was supposed to replace.  Reusing any of these means no redesign.
STATUS_QUO_HEXES = ("070810", "11131D", "BD5CDE", "63DDE8")

# pre-redesign value -> contract value, for the hue families the contract
# deliberately moved.  A 4% nudge (BD5CDE -> B866D7) scores ~0.03 here.
HUE_FAMILY_MOVES = (
    ("primaryHot", "FF3D8B", ("D96CF1", "BD5CDE", "B866D7")),
    ("live", "3DE0A8", ("63DDE8",)),
)
# Real moves score 0.17-0.27; the nudge that shipped scored 0.00-0.05.
MIN_HUE_FAMILY_DISTANCE = 0.12

HEX_LITERAL = re.compile(r'Color\(hex:\s*"([0-9A-Fa-f]{6})"\)')
NAMED_HEX = re.compile(r'static\s+let\s+(\w+)\s*=\s*Color\(hex:\s*"([0-9A-Fa-f]{6})"\)')

FROZEN_SONG_ROW_PARAMETERS = (
    "code",
    "title",
    "subtitle",
    "isLive",
    "actionTitle",
    "actionIcon",
    "onAction",
)

# The iPhone/iPad walkthrough taps these; CONTRACT.md rule 3 freezes them.
STAGE_IDENTIFIERS = (
    "stage.playPause",
    "stage.searchQuery",
    "stage.reserve",
    "stage.keypad.",
    "stage.controls",
)


def hex_rgb(value):
    value = value.lstrip("#").upper()
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def rgb_distance_fraction(left, right):
    """Channel distance between two hex colours: 0.0 identical, 1.0 black<->white."""
    first, second = hex_rgb(left), hex_rgb(right)
    squared = sum((a - b) ** 2 for a, b in zip(first, second))
    return (squared ** 0.5) / (3 ** 0.5 * 255)


def host_sources():
    files = sorted(HOST.glob("*.swift"))
    assert files, "Host feature directory is empty"
    return {path.name: path.read_text() for path in files}


def balanced_arguments(text, call):
    """Argument text of the first `call(...)`, parens balanced (so call-site
    arguments are read as a unit instead of by scanning nearby lines)."""
    start = text.index(call) + len(call)
    depth, index = 0, start
    while index < len(text):
        char = text[index]
        if char in "([{":
            depth += 1
        elif char in ")]}":
            if depth == 0:
                return text[start:index]
            depth -= 1
        index += 1
    raise AssertionError("unbalanced parentheses after " + call)


class PaletteContractTests(unittest.TestCase):
    """The redesign must be visible in the tokens, not merely renamed."""

    def setUp(self):
        self.text = COLORS.read_text()
        self.literals = {value.upper() for value in HEX_LITERAL.findall(self.text)}
        self.bindings = {
            name: value.upper() for name, value in NAMED_HEX.findall(self.text)
        }

    def test_contract_hexes_are_present_as_real_colour_literals(self):
        """Not a comment/substring check: each hex must appear inside an actual
        Color(hex:) literal, so a doc string cannot satisfy the guard."""
        missing = [h for h in CONTRACT_HEXES if h not in self.literals]
        self.assertEqual(
            missing, [], "contract palette absent from KantaColors.swift: " + repr(missing)
        )

    def test_status_quo_hexes_are_gone(self):
        """The failed attempt left the old near-black canvas and the muted
        accent in place; any survivor means the old look is still shipping."""
        remaining = [h for h in STATUS_QUO_HEXES if h in self.literals]
        self.assertEqual(
            remaining,
            [],
            "pre-redesign colours are still declared in KantaColors.swift: "
            + repr(remaining),
        )

    def test_canvas_family_is_bound_to_the_contract_values(self):
        """Exact per-name binding, so reusing a name for a different colour
        cannot pass."""
        expected = {
            "canvas": "0A0614",
            "canvasTop": "140A26",
            "canvasBottom": "0C0618",
            "primary": "A24BFF",
            "primaryHot": "FF3D8B",
            "live": "3DE0A8",
            "cyan": "4DE3F0",
        }
        for name, hex_value in expected.items():
            self.assertEqual(
                self.bindings.get(name),
                hex_value,
                f"KantaColors.{name} is {self.bindings.get(name)}, contract says #{hex_value}",
            )

    def test_hue_families_left_the_status_quo_neighbourhood(self):
        """The invisible-redesign detector.  A 4% accent nudge scores ~0.03 on
        this metric; the approved palette scores 0.17-0.27."""
        for name, contract_hex, old_values in HUE_FAMILY_MOVES:
            declared = self.bindings.get(name)
            self.assertIsNotNone(declared, f"KantaColors.{name} disappeared")
            nearest = min(rgb_distance_fraction(declared, old) for old in old_values)
            self.assertGreaterEqual(
                nearest,
                MIN_HUE_FAMILY_DISTANCE,
                f"KantaColors.{name} (#{declared}) is within {nearest:.3f} of the "
                "pre-redesign value family - that is a nudge, not a redesign",
            )

    def test_both_named_gradients_use_the_contract_stops(self):
        expected = {
            "primaryGradient": ("FF3D8B", "A24BFF"),
            "accentGradient": ("A24BFF", "6D3BFF"),
        }
        for name, stops in expected.items():
            match = re.search(
                name + r"\s*=\s*LinearGradient\(colors:\s*\[([^\]]*)\]", self.text
            )
            self.assertIsNotNone(match, f"{name} is not declared as a LinearGradient")
            declared = tuple(h.upper() for h in HEX_LITERAL.findall(match.group(1)))
            self.assertEqual(
                declared, stops, f"{name} stops are {declared}, contract says {stops}"
            )


class CanvasDepthTests(unittest.TestCase):
    """A flat fill is the defect: the mock paints a 160deg gradient plus three
    colour blooms, which is what makes the redesign legible."""

    def setUp(self):
        self.text = BACKGROUND.read_text()

    def test_canvas_is_a_gradient_not_a_flat_fill(self):
        match = re.search(r"LinearGradient\(\s*colors:\s*\[([^\]]*)\]", self.text)
        self.assertIsNotNone(
            match,
            "KantaBackground declares no LinearGradient - the canvas is still a "
            "flat fill, so a token-only change is invisible",
        )
        stops = match.group(1)
        self.assertIn("canvasTop", stops, "gradient start is not the canvasTop token")
        self.assertIn("canvasBottom", stops, "gradient end is not the canvasBottom token")

    def test_blooms_are_named_contract_tokens(self):
        for bloom in ("bloomPurple", "bloomPink", "bloomCyan"):
            self.assertIn(
                "KantaColors." + bloom,
                self.text,
                f"{bloom} is declared but the canvas does not paint it",
            )
        self.assertIn("RadialGradient", self.text, "blooms are not radial gradients")

    def test_blooms_are_not_improvised_system_colours(self):
        improvised = re.findall(
            r"Color\.(?:blue|purple|indigo|mint|teal|pink|gray|black)\b", self.text
        )
        self.assertEqual(
            improvised,
            [],
            "canvas uses ad-hoc system colours instead of the contract blooms: "
            + repr(sorted(set(improvised))),
        )


class CoverArtContractTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(
            COVER_ART.exists(),
            f"{COVER_ART.name} is missing from disk - HostView/KantaSongRow "
            "references would not compile",
        )
        self.text = COVER_ART.read_text()

    def test_declares_the_frozen_component(self):
        self.assertRegex(self.text, r"struct\s+KantaCoverArt\s*:\s*View")

    def test_exposes_the_frozen_parameters_with_contract_defaults(self):
        for pattern in (
            r"\bseed:\s*String\b",
            r"\binitials:\s*String\?\s*=\s*nil\b",
            r"\bcornerRadius:\s*CGFloat\s*=\s*22\b",
        ):
            self.assertRegex(self.text, pattern)

    def test_gradient_is_deterministic_across_launches(self):
        """hashValue is seeded per process, so the artwork would change colour
        on every cold start; the contract requires a plain byte hash."""
        self.assertNotRegex(
            self.text,
            r"\bhashValue\b",
            "seed hashing uses hashValue, which is not stable across launches",
        )
        for nondeterministic in (r"Int\.random", r"arc4random", r"\.randomElement\("):
            self.assertNotRegex(
                self.text, nondeterministic, "cover art seed is not deterministic"
            )
        self.assertIn(".utf8", self.text, "seed hash does not read the UTF-8 bytes")

    def test_renders_one_or_two_uppercase_initials(self):
        self.assertIn(".uppercased()", self.text, "initials are not uppercased")
        self.assertRegex(self.text, r"prefix\(2\)", "initials are not capped at two")


class CoverArtAdoptionTests(unittest.TestCase):
    def test_song_row_renders_cover_art_seeded_from_title_and_subtitle(self):
        """The row signature is frozen, so the artwork seed has to be derived
        inside the row - otherwise the new component is dead code."""
        text = SONG_ROW.read_text()
        self.assertIn(
            "KantaCoverArt(",
            text,
            "KantaSongRow never renders KantaCoverArt, so the gradient artwork "
            "is not actually on screen",
        )
        arguments = balanced_arguments(text, "KantaCoverArt(")
        self.assertIn("title", arguments, "artwork seed ignores the row title")
        self.assertIn("subtitle", arguments, "artwork seed ignores the row subtitle")


class FrozenSignatureTests(unittest.TestCase):
    """CONTRACT.md rule 2: other agents call these files in parallel, so a
    changed signature breaks their work even when this file still compiles."""

    def test_song_row_parameter_list_is_unchanged(self):
        body = SONG_ROW.read_text().split("var body", 1)[0]
        declared = tuple(
            re.findall(
                r"^    (?:private\s+)?(?:let|var)\s+([A-Za-z_]\w*)\s*:", body, re.MULTILINE
            )
        )
        self.assertEqual(
            declared,
            FROZEN_SONG_ROW_PARAMETERS,
            "KantaSongRow's stored parameters changed; every call site must be re-checked",
        )

    def test_host_call_site_still_matches_the_frozen_signature(self):
        host = (HOST / "HostView.swift").read_text()
        self.assertIn("KantaSongRow(", host)
        arguments = balanced_arguments(host, "KantaSongRow(")
        for label in FROZEN_SONG_ROW_PARAMETERS[:-1]:  # onAction arrives as a trailing closure
            self.assertRegex(
                arguments,
                r"\b" + label + r"\s*:",
                f"HostView call site no longer passes {label}:",
            )


class WalkthroughIdentifierAdoptionTests(unittest.TestCase):
    def test_stage_identifiers_survive_in_the_host_sources(self):
        combined = "\n".join(host_sources().values())
        missing = [i for i in STAGE_IDENTIFIERS if i not in combined]
        self.assertEqual(
            missing, [], "walkthrough identifiers dropped from Features/Host: " + repr(missing)
        )

    def test_stage_identifiers_are_attached_to_real_views(self):
        """A bare string in a comment passes a substring check; require the
        accessibilityIdentifier modifier itself."""
        combined = "\n".join(host_sources().values())
        declared = re.findall(r'accessibilityIdentifier\("([^"]+)"', combined)
        for identifier in STAGE_IDENTIFIERS:
            self.assertTrue(
                any(found.startswith(identifier) for found in declared),
                f"{identifier} is no longer attached to an accessibilityIdentifier",
            )


class PythonLeakTests(unittest.TestCase):
    """A generated edit leaked a Python expression into a Swift file
    (`KantaTypography.subheadline if hasattr(...) else .subheadline`), which
    cost a full CI cycle. Python cannot compile as Swift."""

    PYTHON_ISMS = (
        "hasattr(",
        "isinstance(",
        "elif ",
    )

    def swift_files(self):
        return sorted(SOURCES.rglob("*.swift"))

    def test_no_python_expressions_in_swift_sources(self):
        for path in self.swift_files():
            text = path.read_text()
            for ism in self.PYTHON_ISMS:
                self.assertNotIn(
                    ism, text, f"{path.name} contains Python syntax: {ism}"
                )

    def test_no_single_quoted_string_literals(self):
        """Swift requires double quotes; a single-quoted literal is the
        signature of a generated edit that was never compiled."""
        # Only an identifier-like literal counts: English apostrophes inside a
        # double-quoted sentence are legal Swift and must not be flagged.
        pattern = re.compile(r"[\\(\\[,=:]\\s*'([A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_]\\w*)*)'")
        for path in self.swift_files():
            for number, line in enumerate(path.read_text().splitlines(), 1):
                if line.strip().startswith("//"):
                    continue
                match = pattern.search(line)
                self.assertIsNone(
                    match,
                    f"{path.name}:{number} uses a single-quoted literal "
                    f"({match.group(0) if match else ''}); Swift needs double quotes",
                )


if __name__ == "__main__":
    unittest.main()
