"""Contract for the upgraded Lyrics/Interlude stage-style editors (2026-11-26).

Every control persists through SessionController -> SettingsStore, repaints the
live MiniStagePreview, and is rendered by StageView. Measured device behavior
belongs to the emulator RuntimeChecks walk.
"""
import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'apps/android/app/src/main/java/ph/kanta/reborn'


def src(*parts):
    return (JAVA.joinpath(*parts)).read_text(encoding='utf-8')


class LyricsStyleContractTests(unittest.TestCase):
    def test_named_font_chips_and_custom_import(self):
        activity = src('MainActivity.java')
        chips = activity.split('private void addLyricsFontChips(', 1)[1].split('\n  }', 1)[0]
        for marker in (
            'kanta.lyrics.font.default',
            'kanta.lyrics.font.bold_videoke',
            'kanta.lyrics.font.clean_sans',
            'kanta.lyrics.font.retro_led',
            'kanta.lyrics.font.classic_serif',
            'kanta.lyrics.font.import',
            'chooseLyricFont',
            'HorizontalScrollView',
        ):
            self.assertIn(marker, chips)
        # The named presets resolve through the shared id table to real bundled faces.
        ids = activity.split('private static final String[] FONT_CHIP_IDS =', 1)[1].split(';', 1)[0]
        for font_id in ('anton-regular', 'sora-regular', 'vt323-regular', 'lora-regular'):
            self.assertIn('kanta-font://' + font_id, ids)
        self.assertNotIn('kanta.visuals.lyrics.font.change', activity)

    def test_retro_led_font_is_bundled_with_provenance(self):
        bundled = src('font/BundledLyricFonts.java')
        self.assertIn('"vt323-regular", "VT323", "vt323"', bundled)
        face = ROOT / 'apps/android/app/src/main/assets/lyric-fonts/vt323.ttf'
        license = ROOT / 'apps/android/app/src/main/assets/lyric-fonts/vt323.OFL.txt'
        self.assertTrue(face.exists() and face.stat().st_size > 100_000)
        self.assertIn('SIL OPEN FONT', license.read_text(encoding='utf-8'))
        provenance = json.loads((ROOT / 'apps/android/app/src/main/assets/lyric-fonts/PROVENANCE.json')
                                .read_text(encoding='utf-8'))
        vt = provenance['families']['vt323']
        self.assertEqual(vt['vt323.ttf']['sha256'],
                         hashlib.sha256(face.read_bytes()).hexdigest())
        self.assertEqual(len(vt['vt323.ttf']['git_blob']), 40)

    def test_lyric_style_controls_and_persistence(self):
        activity = src('MainActivity.java')
        controls = activity.split('private void addLyricsVisualControls(', 1)[1].split('\n  }', 1)[0]
        for tag in (
            'kanta.settings.auto-lyrics',
            'kanta.lyrics.allcaps',
            'kanta.lyrics.bold',
            'kanta.lyrics.outline',
            'kanta.lyrics.color',
            'kanta.lyrics.upcoming.dim',
            'kanta.lyrics.alignment',
            'kanta.lyrics.lines',
            'kanta.lyrics.title',
            'kanta.lyrics.watermark',
            'kanta.lyrics.countdown',
            'kanta.lyrics.reset',
        ):
            self.assertIn(tag, controls)
        store = src('settings/SettingsStore.java')
        for key in (
            'lyric.allCaps', 'lyric.bold', 'lyric.outline', 'lyric.alignment', 'lyric.lines',
            'lyric.showTitle', 'lyric.showWatermark', 'lyric.showCountdown',
            'lyric.activeColor', 'lyric.upcomingOpacity', 'lyric.showUpcoming',
        ):
            self.assertIn('"' + key + '"', store)
        session = src('SessionController.java')
        for accessor in (
            'lyricAllCaps()', 'lyricBold()', 'lyricOutline()', 'lyricActiveColor()',
            'lyricUpcomingOpacity()', 'lyricAlignment()', 'lyricLines()', 'lyricShowTitle()',
            'lyricShowWatermark()', 'lyricShowCountdown()', 'resetLyricStyle()',
        ):
            self.assertIn(accessor, session)
        # Reset goes back to defaults via the store, not a full prefs wipe.
        self.assertIn('settings.clearLyricStyle()', session)
        self.assertIn('public void clearLyricStyle()', store)

    def test_stageview_renders_the_full_style(self):
        stage = src('StageView.java')
        for marker in (
            'session.lyricBold()',
            'session.lyricActiveColor()',
            'session.interludeTextColor()',
            'oneToOneUpper',
            'applyOutline(lyricPaint, session.lyricOutline())',
            'Layout.Alignment.ALIGN_NORMAL : Layout.Alignment.ALIGN_CENTER',
            '"bottom".equals(align)',
            'session.lyricShowTitle() && session.current != null',
            'drawCountdownDots(canvas, w)',
            'session.countdownRemainingMs()',
            'session.interludeShowWatermark() : session.lyricShowWatermark()',
            'postInvalidateOnAnimation()',
        ):
            self.assertIn(marker, stage)
        # ALL CAPS must keep cue offsets valid: per-character, length-preserving.
        self.assertIn('Character.toUpperCase(out[i])', stage)

    def test_layout_supports_left_alignment(self):
        layout = src('stage/StageLyricLayout.java')
        self.assertIn('.setAlignment(alignment)', layout)
        self.assertIn('Layout.Alignment alignment', layout)

    def test_preview_mirrors_every_style_field(self):
        preview = src('ui/MiniStagePreview.java')
        style = preview.split('public static final class LyricStyle {', 1)[1].split('}', 1)[0]
        for field in ('activeColor', 'upcomingAlpha', 'allCaps', 'bold', 'outline',
                      'alignment', 'showUpcoming', 'showTitle', 'title', 'showWatermark'):
            self.assertIn(field, style)
        self.assertIn('applyLyricStyle', preview)
        self.assertIn('drawArtworkFull', preview)
        activity = src('MainActivity.java')
        refresh = activity.split('private void refreshVisualsPreview()', 1)[1].split(
            '\n  private String', 1)[0]
        for marker in ('MiniStagePreview.LyricStyle style', 'style.activeColor = session.lyricActiveColor()',
                       'style.showWatermark = session.lyricShowWatermark()',
                       'session.interludeTextColor()', 'session.interludeAnimation()',
                       'session.interludeImageScale()', 'session.interludeImagePlacement()'):
            self.assertIn(marker, refresh)


class InterludeStyleContractTests(unittest.TestCase):
    def test_live_message_and_presets(self):
        activity = src('MainActivity.java')
        controls = activity.split('private void addInterludeVisualControls(', 1)[1].split(
            '\n  }', 1)[0]
        for marker in (
            'addTextChangedListener',
            'session.setInterludeText(edited.toString())',
            'kanta.interlude.preset.',
            'interlude_preset_ready',
            'interlude_preset_birthday',
            'interlude_preset_singer',
        ):
            self.assertIn(marker, controls)
        # The separate Save button is gone; typing is the save.
        self.assertNotIn('kanta.visuals.interlude.save', activity)

    def test_animation_and_image_controls(self):
        activity = src('MainActivity.java')
        controls = activity.split('private void addInterludeVisualControls(', 1)[1].split(
            '\n  }', 1)[0]
        for marker in (
            'kanta.interlude.color',
            'kanta.interlude.animation',
            'interlude_anim_static',
            'interlude_anim_pulse',
            'interlude_anim_marquee',
            'interlude_size',
            'kanta.visuals.interlude.size.slider',
            'kanta.visuals.interlude.image.scale',
            'kanta.interlude.image.placement',
            'interlude_placement_above',
            'interlude_placement_background',
            'kanta.visuals.interlude.image.remove',
            'kanta.visuals.interlude.image.add',
            'kanta.interlude.up_next',
            'kanta.interlude.visualizer',
            'kanta.interlude.watermark',
            'kanta.visuals.interlude.reset',
        ):
            self.assertIn(marker, controls)
        session = src('SessionController.java')
        for accessor in ('interludeTextColor()', 'interludeAnimation()', 'interludeImageScale()',
                         'interludeImagePlacement()', 'interludeShowUpNext()',
                         'interludeShowVisualizer()', 'interludeShowWatermark()'):
            self.assertIn(accessor, session)
        # Reset interlude also clears the style prefs.
        reset = session.split('public synchronized void resetInterlude()', 1)[1].split('}', 1)[0]
        self.assertIn('settings.clearInterludeStyle()', reset)

    def test_interlude_motion_renders_in_stageview(self):
        stage = src('StageView.java')
        for marker in ('"pulse".equals(anim)', '"marquee".equals(anim)',
                       'session.interludeShowVisualizer()', 'drawVisualizerBars',
                       'session.interludeImageScale()', 'drawInterludeImage'):
            self.assertIn(marker, stage)

    def test_up_next_gate_honors_the_toggle(self):
        surface = src('StageSurface.java')
        self.assertIn('session.interludeShowUpNext()', surface)
        self.assertIn('session.queue.snapshot()', surface)

    def test_countdown_state_is_exposed(self):
        session = src('SessionController.java')
        self.assertIn('private long countdownEndsAt = -1;', session)
        self.assertIn('public synchronized long countdownRemainingMs()', session)


class SoundFontBudgetContractTests(unittest.TestCase):
    def test_budget_is_three_gib_in_code_and_tests(self):
        c = (ROOT / 'apps/android/app/src/main/cpp/soundfont.c').read_text(encoding='utf-8')
        self.assertIn('static const size_t budget = (size_t)3072u * 1024u * 1024u;', c)
        self.assertNotIn('1536u * 1024u * 1024u', c)
        test = (ROOT / 'apps/android/tests/soundfont-test.c').read_text(encoding='utf-8')
        self.assertIn('assert(budget == (size_t)3072u * 1024u * 1024u);', test)
        self.assertIn('SoundFont: 3072 MiB cap', test)


if __name__ == '__main__':
    unittest.main()
