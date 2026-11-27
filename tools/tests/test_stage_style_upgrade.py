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
            'addQuickMessageChips(target, message)',
        ):
            self.assertIn(marker, controls)
        # Batch F replaces the static preset chips with editable quick messages:
        # built-ins + custom chips live in the helper, tokens resolve per render.
        quick = activity.split('private void addQuickMessageChips(', 1)[1].split(
            '\n  }', 1)[0]
        for marker in (
            'kanta.interlude.preset.add',
            'kanta.interlude.preset',
            'interlude_preset_ready',
            'interlude_preset_birthday',
            'interlude_preset_singer',
            'promptQuickMessageEdit',
            'removeQuickMessage',
        ):
            self.assertIn(marker, quick)
        session = src('SessionController.java')
        for marker in ('resolveInterludeTokens', 'resolvedInterludeText',
                       '{next_song}', '{next_artist}', '{queue_count}', '{time}'):
            self.assertIn(marker, session)
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
    def test_budget_is_dynamic_with_three_gib_floor(self):
        c = (ROOT / 'apps/android/app/src/main/cpp/soundfont.c').read_text(encoding='utf-8')
        self.assertIn('static const size_t MIN_BUDGET = (size_t)3072u * 1024u * 1024u;', c)
        self.assertNotIn('static const size_t budget =', c)
        for marker in ('size_t sf_effective_budget(size_t available_bytes)',
                       'available_bytes / 10',
                       'dynamic > MIN_BUDGET ? dynamic : MIN_BUDGET',
                       'MemAvailable:',
                       'current_budget()'):
            self.assertIn(marker, c)
        # Both allocation paths must consult the live budget.
        self.assertEqual(c.count('size_t budget = current_budget();'), 2)
        test = (ROOT / 'apps/android/tests/soundfont-test.c').read_text(encoding='utf-8')
        for marker in ('sf_effective_budget(0) == 3072u * 1024u * 1024u',
                       'sf_effective_budget(2u * gib) == 3072u * 1024u * 1024u',
                       'sf_effective_budget(4u * gib) == 4u * gib - 4u * gib / 10',
                       'current_budget() >= 3072u * 1024u * 1024u',
                       'budget_checks()'):
            self.assertIn(marker, test)

    def test_embedded_stage_receives_the_style(self):
        # The embedded renderer must not hardcode the old white/gold look.
        lyric = (JAVA / 'stage/LyricStage.java').read_text(encoding='utf-8')
        for marker in ('public void setStyle(StageStyle style)',
                       'fadeColor(style.interludeColor, interludeAlpha)',
                       'android.text.Layout.Alignment align',
                       'oneToOneUpper(cue.line)',
                       '"background".equals(style.imagePlacement)',
                       'postInvalidateOnAnimation()'):
            self.assertIn(marker, lyric)
        presenter = (JAVA / 'stage/StagePresenterImpl.java').read_text(encoding='utf-8')
        for marker in ('lyricStage.setStyle(new StageStyle(',
                       'session.lyricActiveColor()',
                       'session.interludeTextSizeSp()',
                       'countdownStage.setVisualsEnabled(session.lyricShowCountdown())',
                       'songTitleHeader(styleSong)',
                       'session.preSongInterludeActive() && !session.interludeShowUpNext()'):
            self.assertIn(marker, presenter)
        import re
        snapshot = (JAVA / 'stage/StageStyle.java').read_text(encoding='utf-8')
        for field in ('activeColor', 'upcomingAlpha', 'allCaps', 'bold', 'outline', 'alignment',
                      'showUpcoming', 'showTitle', 'title', 'interludeColor', 'animation',
                      'interludeSizeSp', 'imageScale', 'imagePlacement'):
            self.assertRegex(
                snapshot, r'public final \w+ ' + field + r';')

    def test_title_header_includes_the_singer(self):
        presenter = (JAVA / 'stage/StagePresenterImpl.java').read_text(encoding='utf-8')
        header = presenter.split('public static String songTitleHeader', 1)[1].split('}', 1)[0]
        self.assertIn('song.title', header)
        self.assertIn('song.artist', header)
        surface = (JAVA / 'StageView.java').read_text(encoding='utf-8')
        self.assertIn('StagePresenterImpl.songTitleHeader(session.current)', surface)
        activity = (JAVA / 'MainActivity.java').read_text(encoding='utf-8')
        self.assertIn('StagePresenterImpl.songTitleHeader(song)', activity)

    def test_marquee_uses_a_readable_pace(self):
        surface = (JAVA / 'StageView.java').read_text(encoding='utf-8')
        # Seconds, not milliseconds, as the time base; px/s scaled to text size.
        self.assertIn('(now / 1000.0 * speed) % total', surface)
        self.assertNotIn('(double) now * speed', surface)
        lyric = (JAVA / 'stage/LyricStage.java').read_text(encoding='utf-8')
        # Batch F: the readable base pace is kept; the user's Slow/Normal/Fast
        # selector only scales it (0.5x / 1x / 2x).
        self.assertIn('float speed = 30f * density', lyric)
        self.assertIn('(activePaint.getTextSize() / (40f * density)) * speedMul;', lyric)
        self.assertIn('style.marqueeSpeed == 0 ? .5f', lyric)
        self.assertIn('style.marqueeSpeed == 2 ? 2f : 1f', lyric)

    def test_countdown_visual_can_be_disabled(self):
        countdown = (JAVA / 'stage/CountdownStage.java').read_text(encoding='utf-8')
        self.assertIn('public void setVisualsEnabled(boolean enabled)', countdown)
        self.assertIn('if (!visualsEnabled) return;', countdown)


class InterludeFadeAndFixedRowsTests(unittest.TestCase):
    def test_session_exposes_countdown_window(self):
        session = src('SessionController.java')
        self.assertIn('private long countdownTotalMs = 0;', session)
        self.assertIn('public synchronized long countdownTotalMs()', session)
        self.assertEqual(
            session.count('countdownTotalMs = countdownSeconds * 1000L;'), 2)
        self.assertIn('countdownTotalMs = 0;', session)

    def test_interlude_fades_across_the_countdown(self):
        surface = src('StageView.java')
        for marker in ('interludeFadeAlpha()', 'boolean interludeVisible',
                       'fadeColor(session.interludeTextColor(), interludeAlpha)',
                       'drawInterludeImage(canvas, w, h, width, lyricHeight, interludeAlpha)',
                       'session.countdownTotalMs()'):
            self.assertIn(marker, surface)
        lyric = src('stage/LyricStage.java')
        for marker in ('public void setCountdownFade(long startMs, long durationMs)',
                       'countdownFadeDurationMs',
                       'fadeColor(style.interludeColor, interludeAlpha)',
                       'withAlpha(canvas, interludeAlpha'):
            self.assertIn(marker, lyric)
        presenter = src('stage/StagePresenterImpl.java')
        self.assertIn('lyricStage.setCountdownFade(android.os.SystemClock.elapsedRealtime(),', presenter)
        self.assertEqual(presenter.count('lyricStage.setCountdownFade(-1, 0);'), 2)

    def test_lyric_rows_are_fixed_and_cross_fade(self):
        # The sung line dissolves in place; the waiting line never moves up.
        for path, ref in [('StageView.java', 'float refHeight = font * 2.4f + dp(18);'),
                          ('stage/LyricStage.java',
                           'float refHeight = activePaint.getTextSize() * 2.4f + 16 * density;')]:
            source = src(path)
            self.assertIn(ref, source)
            self.assertIn('LINE_FADE_MS = 450', source)
            self.assertIn('ghostLyrics', source)
            self.assertIn('float fadeIn', source)
            self.assertIn('withAlpha', source)
        # The sung line fades out while the successor fades in at the same anchor.
        surface = src('StageView.java')
        self.assertIn('withAlpha(canvas, 1f - fadeIn,', surface)
        self.assertIn('() -> ghostLyrics.draw(canvas, ghostX, ghostTop, 0, 0)', surface)

    def test_marquee_time_base_is_seconds(self):
        lyric = src('stage/LyricStage.java')
        self.assertIn('(((now / 1000.0) * speed) % span)', lyric)
        self.assertNotIn('(float) (now / 1000.0) * speed % span', lyric)
        surface = src('StageView.java')
        self.assertIn('(now / 1000.0 * speed) % total', surface)

    def test_score_sound_runs_to_completion(self):
        # The celebration no longer stops at the song boundary; only Stop /
        # focus loss / leaving the app silences it.
        session = src('SessionController.java')
        start_local = session.split('private void startLocal(ReservationQueue.Entry entry', 1)[1]
        self.assertNotIn('party.stopCelebration()', start_local)
        self.assertIn('runs to natural completion across the song boundary', session)
        # The uploaded sound is the bundled one (byte-identical, 16.25 s container
        # with ~12 s of audible content) - it must stay in the party assets.
        asset = ROOT / 'apps/android/app/src/main/assets/party/score-celebration.mp3'
        self.assertTrue(asset.exists())
        self.assertEqual(hashlib.sha256(asset.read_bytes()).hexdigest(),
                         '3d828f5a127e714b2d79e5feabb93d4034c91cf5af0f54c35896920b5f6fadf1')

if __name__ == '__main__':
    unittest.main()
