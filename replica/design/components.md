# KANTA Karaoke Stage Overlays — Component Specs

Based on `replica/recon.md` screens S07 (handset player), S08 (TV player), S12 (settings), S15 (remote status), S16 (search/reserve overlays), S17 (queue overlay).
Designed in **KANTA's existing visual language** (KantaColors, KantaTypography, KantaCard) — not SongHub's.

---

## 1. LyricStage

**Purpose**: Render scrolling timed lyrics with active/inactive/upcoming states, synced to `RenderedPlaybackClock`.

**Android timing contract**: render text from `MidiLyrics.Cue.line`, compare cue timestamps in milliseconds, and obtain the current position through `RenderedPlaybackClock.position(playedFrames)`. The stage host supplies the monotonically increasing rendered-frame count via `LyricStage.setPlayedFrames(long)`; do not substitute wall-clock time or microsecond fields.

**Variants**: handset (portrait), TV (landscape, focus-driven)

**Sizes**: full-width, safe-area inset

**States**:
- `idle` — no song loaded, shows placeholder
- `loading` — lyrics parsing, spinner on `accent`
- `active` — scrolling, current line highlighted
- `paused` — clock stopped, current line stays highlighted
- `error` — parse failed, shows retry action

**Tokens**:
- Background: `canvas` (transparent, draws over StageSurface)
- Active line: `lyricActive` / `typography.lyricActive` (24sp bold)
- Inactive sung lines: `lyricInactive` / `typography.lyricInactive` (20sp regular)
- Upcoming lines: `lyricUpcoming` / `typography.lyricUpcoming` (16sp regular)
- Line spacing: `spacing.scale[4]` (16dp)
- Container padding: `spacing.scale[6]` (24dp) horizontal, `spacing.scale[8]` (32dp) vertical
- Scroll physics: spring damping 0.85, mass 1.0

**A11y**:
- `contentDescription` per line: "Lyric, [text], [timestamp]"
- Active line announced via `AccessibilityEvent.TYPE_ANNOUNCEMENT`
- TalkBack: swipe navigation between lines, double-tap to seek
- Large text: scales per system font scale (max 2.0x)

**Used on**: S07, S08

---

## 2. CountdownStage

**Purpose**: Full-screen countdown (3-2-1-GO) before playback starts, with animated ring.

**Variants**: handset, TV

**Sizes**: full-screen overlay

**States**:
- `hidden` — default
- `counting` — animating 3→2→1→GO
- `completed` — fades out, triggers `StagePresenter.onCountdownComplete()`

**Tokens**:
- Background: `canvas` at 0.92 alpha
- Number: `countdownNumber` / `typography.countdown` (72sp heavy)
- Ring: `countdownRing`, stroke 8dp, animated `strokeDasharray` 0→2π
- Ring track: `hairline` at 0.3 alpha, stroke 8dp
- "GO" flash: `live` color, scale 1.0→1.2→1.0 over 300ms
- Animation: `motion.normal` (250ms) per number, `easeOut`

**A11y**:
- Announces each number via `TYPE_ANNOUNCEMENT`
- Screen reader: "Countdown, 3", "Countdown, 2", "Countdown, 1", "Go, sing now"
- Respects `prefersReducedMotion` — shows numbers without ring animation

**Used on**: S07, S08

---

## 3. ScoreOverlay

**Purpose**: Post-song score breakdown (overall, pitch, timing, bonus) with tier badges.

**Variants**: handset (bottom sheet), TV (center modal)

**Sizes**: handset 85% height, TV 60% width × 70% height

**States**:
- `hidden`
- `entering` — slides up (handset) / fades + scales (TV)
- `visible` — interactive, dismissible
- `exiting` — reverse animation

**Tokens**:
- Background: `surface` / `KantaCard` (radius `xl` 24dp)
- Overall score: `scoreValue` / `typography.scoreValue` (48sp heavy)
- Tier label: `scoreLabel` / `typography.scoreLabel` (14sp semibold)
  - Perfect: `success` (#3DE0A8)
  - Great: `cyan` (#4DE3F0)
  - Good: `warning` (#FFB03A)
  - Miss: `destructive` (#FF5A6E)
- Breakdown rows: `textPrimary` / `textSecondary`, `typography.metadata`
- Row icons: 20dp, tinted per tier color
- Close button: `KantaCard` ghost variant, `hairline` border, `textSecondary`
- Padding: `spacing.scale[6]` (24dp)
- Row gap: `spacing.scale[3]` (12dp)

**A11y**:
- Modal dialog role, traps focus
- Score announced: "Your score: 94 percent, Perfect tier"
- Breakdown rows as list items with `contentDescription`
- Close button: "Dismiss score", `contentDescription`
- Dismiss: back gesture, back button, tap outside (handset), D-pad back (TV)

**Used on**: S07, S08

---

## 4. BackgroundStage

**Purpose**: Render background media (solid, photo, camera, BGV video) behind lyrics.

**Variants**: handset, TV

**Sizes**: full-screen, behind `StageSurface`

**States**:
- `solid` — `canvas` gradient (canvasTop → canvasBottom)
- `photo` — user image, `ImageView.ScaleType.CENTER_CROP`
- `camera` — `CameraStage` preview (existing)
- `bgv` — Media3 `ExoPlayer` on `TextureView`, muted, looping
- `transitioning` — cross-fade 400ms `easeInOut` between sources

**Tokens**:
- Video overlay: `backgroundVideoOverlay` (#000000CC) for lyric legibility
- Photo scrim: `canvas` at 0.4 alpha, bottom 40% gradient to transparent
- Solid gradient: `canvasTop` → `canvasBottom`
- Placeholder: `surface` with `hairline` border, `textTertiary` "No background"

**A11y**:
- Background is decorative — `importantForAccessibility = NO_HIDE_DESCENDANTS`
- Camera: requests permission, announces "Camera background active"
- Video: no audio, no captions needed (decoration only)
- Reduced motion: disables BGV loop, shows first frame only

**Used on**: S07, S08, S09, S10

---

## 5. AdjustmentOverlay

**Purpose**: Real-time key/melody/tempo/volume adjustment during playback.

**Variants**: handset (bottom sheet, 40% height), TV (side panel, 30% width)

**Sizes**: as above

**States**:
- `hidden`
- `entering` — slide up (handset) / slide right (TV)
- `visible` — 4 knobs, live preview
- `exiting`

**Tokens**:
- Background: `elevated` / `KantaCard` (radius `xl` top-only on handset)
- Knob track: `adjustmentTrack`, height 4dp, radius `full`
- Knob thumb: `adjustmentKnob`, 24dp circle, shadow `md`
- Active knob: `primaryHot`, scale 1.15
- Labels: `adjustmentLabel` / `typography.adjustmentLabel` (12sp medium)
- Values: `adjustmentValue` / `typography.adjustmentValue` (18sp bold)
- Reset button: `textSecondary`, `typography.caption`, tap resets to 0
- Sections: key | melody | tempo | volume (equal width)
- Padding: `spacing.scale[5]` (20dp) horizontal, `spacing.scale[4]` (16dp) vertical
- Gap: `spacing.scale[6]` (24dp) between sections

**A11y**:
- Each knob: `SeekBar` role, `contentDescription` "Key adjustment, current value +2 semitones"
- Value announced on change: "Key, plus 2 semitones"
- Reset: "Reset key to default", double-tap
- D-pad: left/right changes value, center resets
- TalkBack: swipe up/down to adjust, double-tap to reset

**Used on**: S07, S08

---

## 6. IntroCard

**Purpose**: Song metadata card shown before countdown (title, artist, duration, key, tempo).

**Variants**: handset (center modal), TV (center modal, larger)

**Sizes**: handset 80% width, TV 50% width

**States**:
- `hidden`
- `entering` — fade + scale 0.95→1.0, `motion.normal`
- `visible` — auto-dismisses after 3s or on user tap
- `exiting` — fade + scale 1.0→0.95

**Tokens**:
- Background: `introCardBackground` (canvas at 0.9 alpha), radius `xl`
- Border: `introCardBorder`, stroke 1dp
- Title: `introTitle` / `typography.introTitle` (28sp heavy), `textPrimary`
- Artist: `introArtist` / `typography.introArtist` (16sp medium), `textSecondary`
- Metadata row: `introMetadata` / `typography.introMetadata` (13sp regular), `textTertiary`
  - Icons: 16dp, `textTertiary`
  - Items: duration | original key | tempo | genre
- Divider: `hairline`, margin `spacing.scale[3]` (12dp) vertical
- Padding: `spacing.scale[6]` (24dp)
- Glow: `shadows.glow` on enter

**A11y**:
- Announces on enter: "Now playing [title] by [artist], duration 3:42, key C major, tempo 120 BPM"
- Dismiss: tap anywhere, back button, auto-dismiss after 3s
- Not focus-trapping — lightweight toast-like behavior

**Used on**: S07, S08

---

## 7. SearchOverlay (S16)

**Purpose**: Type-ahead search with reserve action, modal over player.

**Variants**: handset (bottom sheet, 70% height), TV (center modal)

**Sizes**: as above

**States**:
- `hidden`
- `entering` — slide up (handset) / fade+scale (TV)
- `empty` — no query, shows recent/history
- `loading` — spinner on `accent`
- `results` — list with reserve buttons
- `no-results` — empty state `KantaEmptyState`
- `exiting`

**Tokens**:
- Background: `surface` / `KantaCard` (radius `xl` top-only handset)
- Search field: `elevated` background, `hairline` border, `textPrimary` input, `textTertiary` hint
- Field radius: `md` (12dp), padding `spacing.scale[4]` (16dp)
- Result item: `textPrimary` title, `textSecondary` artist, `metadata` metadata
- Reserve button: `accent` background, `onAccent` text, `radius.md`, `typography.songTitle`
- Reserve disabled: `surfaceHigh` background, `textTertiary` text
- Divider: `hairline`, inset `spacing.scale[4]` (16dp)
- List gap: `spacing.scale[2]` (8dp)
- Padding: `spacing.scale[5]` (20dp)

**A11y**:
- Search field: `EditText`, `inputType=text`, `contentDescription` "Search songs"
- Results: `RecyclerView`, each item `contentDescription` "[title] by [artist], duration [dur], tap to reserve"
- Reserve button: "Reserve [title]", `contentDescription`
- Empty state: `KantaEmptyState` announcements
- Dismiss: back, swipe down (handset), D-pad back (TV)

**Used on**: S16

---

## 8. QueueOverlay (S17)

**Purpose**: Read-only projection of `ReservationQueue` (FIFO, UUID identity), shows position, title, reserver.

**Variants**: handset (bottom sheet, 60% height), TV (side panel, 30% width)

**Sizes**: as above

**States**:
- `hidden`
- `entering`
- `empty` — `KantaEmptyState` "Queue is empty"
- `populated` — list with current playing highlighted
- `exiting`

**Tokens**:
- Background: `surface` / `KantaCard` (radius `xl` top-only handset)
- Current playing row: `elevated` background, `primary` left accent bar 4dp
- Queued rows: `transparent`, `textPrimary` title, `textSecondary` reserver
- Position badge: `badge` / `typography.badge` (12sp bold mono), `accent` background, `onAccent` text, radius `sm`
- Remove button (own reservations only): `destructive` icon, `contentDescription` "Remove from queue"
- Divider: `hairline`, full width
- Padding: `spacing.scale[5]` (20dp)
- Row height: 56dp

**A11y**:
- List role, `contentDescription` per row: "Position 3, Bohemian Rhapsody, reserved by Alex"
- Current playing: "Now playing, Bohemian Rhapsody"
- Remove: "Remove Bohemian Rhapsody from queue"
- Dismiss: back, swipe down (handset), D-pad back (TV)

**Used on**: S17

---

## 9. RemoteStatusView (S15)

**Purpose**: In-player indicator showing Smart Link host state and connected clients.

**Variants**: handset (toolbar inline), TV (overlay corner)

**Sizes**: handset 40dp height, TV 120dp × 60dp

**States**:
- `disconnected` — `textTertiary` icon, "Offline"
- `advertising` — `warning` pulsing icon, "Waiting for connection"
- `connected` — `success` icon, "2 connected"
- `error` — `destructive` icon, "Connection failed"

**Tokens**:
- Icon: 20dp, tinted per state color
- Label: `typography.caption` (12sp medium), per state color
- Pulse animation: `motion.slow` (350ms), opacity 0.4→1.0
- Background (TV): `elevated`, radius `md`, padding `spacing.scale[2]` (8dp)

**A11y**:
- `contentDescription`: "Smart Link: 2 devices connected"
- Announces state changes: "Smart Link connected", "Smart Link disconnected"
- Tap (handset): opens `Settings` → `Remote` screen

**Used on**: S07, S08, S15

---

## 10. Keypad (TV only, S08)

**Purpose**: On-screen numeric keypad for song code entry via D-pad.

**Variants**: TV only (landscape)

**Sizes**: 60% width × 70% height, anchored bottom-right

**States**:
- `hidden`
- `entering` — slide up, `motion.fast`
- `visible` — focusable grid
- `exiting`

**Tokens**:
- Background: `elevated`, radius `xl`
- Key: `keypadKey`, radius `md`, min 64dp × 64dp
- Key focused: `keypadKeyActive`, scale 1.08, shadow `lg`
- Key pressed: `primary`, scale 0.96
- Key text: `keypadKeyText` / `typography.keypad` (20sp semibold)
- Submit key (enter): `accent` background, `onAccent` text
- Backspace key: `destructive` icon, `surfaceHigh` background
- Grid gap: `spacing.scale[2]` (8dp)
- Padding: `spacing.scale[5]` (20dp)

**A11y**:
- D-pad navigation: up/down/left/right moves focus
- Center/Enter: activates key
- Back: dismisses keypad
- Announces key on focus: "Key 5", "Key enter", "Key backspace"
- `contentDescription` per key: numeric or action label

**Used on**: S08

---

## 11. Settings Groups (S12) — Karaoke Extensions

**Purpose**: Extend existing KANTA settings with karaoke-specific groups.

**Groups added**:
- `lyric` — font size, line spacing, active color, upcoming opacity
- `audio` — key range, tempo range, melody volume, guide volume
- `mic` — monitor level, gate threshold, effects (reverb/compressor)
- `score` — difficulty, mode (strict/lenient), show breakdown
- `behavior` — auto-reserve, countdown length, intro card, queue visibility
- `bgv` — source priority (video>photo>camera>solid), video loop, scrim opacity

**Tokens** (reuse existing):
- Row: `KantaCard` style, `textPrimary` label, `textSecondary` summary
- Slider: `accent` track, `primary` thumb, `surfaceHigh` track background
- Toggle: `accent` thumb on, `surfaceHigh` thumb off, `surfaceHigh` track
- Picker: `KantaCard` modal, `textPrimary` options, `accent` checkmark
- Password gate (diagnostic): `destructive` border, `textSecondary` hint

**A11y**:
- Standard Android settings patterns
- Sliders: `SeekBar` announcements
- Toggles: `Switch` announcements
- Pickers: `AlertDialog` with radio buttons

**Used on**: S12

---

## 12. Primitive Components (Reused from KANTA DesignSystem)

| Primitive | KANTA Source | Karaoke Adaptation |
|-----------|--------------|-------------------|
| `KantaCard` | `KantaCard.swift` | Android XML `CardView` with surface gradient + hairline stroke |
| `KantaColors` | `KantaColors.swift` | `colors.xml` + `ColorStateList` |
| `KantaTypography` | `KantaTypography.swift` | `TextAppearance` styles in `styles.xml` |
| `KantaEmptyState` | `KantaEmptyState.swift` | `EmptyStateView` custom view |
| `KantaTabBar` | `KantaTabBar.swift` | Not used in stage overlays |
| `KantaTransportBar` | `KantaTransportBar.swift` | Player controls — separate spec |

---

## Implementation Notes

### Android XML Resources
Generate from tokens:
- `res/values/colors.xml` — all color roles
- `res/values/dimens.xml` — spacing, radius, text sizes
- `res/values/styles.xml` — `TextAppearance.Karaoke.*`, `Widget.Karaoke.*`

### Custom Views
Each stage overlay = custom `View` or `ViewGroup`:
- `LyricStage extends View` — `onDraw` with `StaticLayout` cache
- `CountdownStage extends FrameLayout` — `ValueAnimator` for ring + numbers
- `ScoreOverlay extends BottomSheetDialogFragment` / `DialogFragment`
- `BackgroundStage extends FrameLayout` — delegates to `TextureView`/`ImageView`/`CameraStage`
- `AdjustmentOverlay extends BottomSheetDialogFragment` — 4 `SeekBar` + labels
- `IntroCard extends DialogFragment` — auto-dismiss timer
- `SearchOverlay extends BottomSheetDialogFragment` — `RecyclerView` + `SearchView`
- `QueueOverlay extends BottomSheetDialogFragment` — `RecyclerView` (read-only)
- `RemoteStatusView extends View` — state drawable + text
- `Keypad extends DialogFragment` — `GridLayout` + D-pad focus logic

### TV / D-pad Focus
- All overlays implement `FocusManager` for D-pad
- `nextFocus*` XML attrs on every focusable element
- `setOnFocusChangeListener` for focus highlight (scale + shadow)
- `dispatchKeyEvent` for Enter/Back handling

### Motion
- Use `MotionLayout` for complex transitions (IntroCard, CountdownStage)
- `ViewPropertyAnimator` for simple slides/fades
- Respect `Settings.Global.ANIMATOR_DURATION_SCALE` and `prefersReducedMotion`

### Testing
- Screenshot tests per component per variant (handset/TV) per state
- Contrast verified via `contrast.py` (already passing)
- A11y: `AccessibilityNodeProvider` verification, TalkBack manual pass
- Device: handset (Pixel), tablet, Android TV emulator
