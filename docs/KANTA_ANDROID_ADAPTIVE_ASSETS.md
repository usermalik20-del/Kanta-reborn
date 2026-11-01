# KANTA Android adaptive asset integration

## Source and intent

The project owner supplied `Kanta_Android_Adaptive_Assets.zip` for the reference-led Android UI enhancement. This manifest records the selected runtime assets extracted from that pack and how the app uses them. The pack is presentation-only; it does not replace playback, queue, session, camera, microphone, storage or companion implementations.

## Integrated Android resources

### Native VectorDrawables

Installed under `apps/android/app/src/main/res/drawable/`:

- `kanta_home_filled.xml`
- `kanta_search_outline.xml`
- `kanta_queue_outline.xml`
- `kanta_visuals_outline.xml`
- `kanta_microphone_outline.xml`
- `kanta_pitch_outline.xml`
- `kanta_settings_outline.xml`
- `kanta_library_outline.xml`
- `kanta_more_outline.xml`
- `kanta_song_code_outline.xml`

Home uses these resources for the brand/navigation, settings, search and code entry affordances, six-feature grid and quick-play cards. Icon color is supplied by the active KANTA theme rather than baked into the icon state.

### Stage artwork

Optimized from the pack's original JPEGs and installed under `apps/android/app/src/main/res/drawable-nodpi/`:

- `kanta_stage_phone.webp` — 540 × 960, portrait source
- `kanta_stage_tv.webp` — 960 × 540, landscape source

`KantaTheme.stageBackground(Context, wide)` applies the selected image plus a dark contrast layer. The Home hero uses portrait artwork. `KantaFullscreenStage` chooses portrait or landscape artwork from the active display orientation, not a device model.

## Deliberate exclusions

- The pack's seven empty-state SVG files are identical generic placeholder illustrations; they are not installed as if they were distinct, finished assets.
- `res/values/kanta_colors.xml` is not copied verbatim because it defines `kanta_surface`, which already exists in the app. Its red/yellow palette also does not match the supplied KANTA reference's navy/cyan/violet direction. The active design tokens in `KantaTheme` remain the single presentation palette.
- The pack's adaptive launcher XML is not copied because the app already has a configured KANTA launcher mark and foreground resource. Replacing it would be an unrelated branding change.
- The pack's tablet JPEG is not duplicated into the app: the optimized 16:9 landscape asset is used for wide stage composition, while portrait composition uses the phone artwork.

## Validation

The Android Gradle resource merge and packaging checks run through `assembleDebug` and `lintDebug`. These prove resource validity and references, not visual fidelity. Emulator screenshot capture and comparison against the supplied UI reference remain required before the branch can be accepted or merged.
