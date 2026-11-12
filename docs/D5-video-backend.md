# D5 — retain the Android background-video backend

## Owner decision — 2026-11-12

The owner selected **Keep current** rather than adopt Media3. Retain the existing
platform `MediaPlayer` / `TextureView` adapters: `BackgroundStage` for the embedded
stage and `StageSurface` for fullscreen. The earlier Media3 requirement
in `replica/architecture.md` and `replica/design/components.md` is superseded.
No dependency, playback migration or third-party notice addition is required.

The video is decoration: muted, looping, prepared asynchronously and released with
its surface on replacement/foreground loss. `SessionController` remains the song,
queue and playback owner. This decision does not change capture, scoring or camera
ownership and does not assert that video shares the song's transport position.

## Evidence and limits

- Decision recorded in this local working tree based on `00d97f0`.
- Source: `stage/BackgroundStage.java`, fullscreen `StageSurface.java`.
- Existing historical emulator evidence remains in `docs/TESTING.md`; it is not a
  new validation of this local candidate.
- Physical-device 30-minute video/camera/audio soak, codec behavior and resource
  measurements remain open. Choosing a backend does not close those gates.

No push, signing, release or deployment is authorized by this decision.
