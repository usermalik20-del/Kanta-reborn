# B2 — custom lyric fonts (local candidate)

My Set → **Lyric font** uses the stored content-root selection or opens a folder
picker. Select `KANTA/` itself, or its parent. Put `.ttf`/`.otf` files in `KANTA/font/`.
The picker includes **Built-in font** and **Choose another content folder…**.
The app takes persistable read permission and stores the selected font URI through
`SettingsStore` (`lyric.font`). It never changes the original font file.

`FontStore` is an app-context singleton with one serial worker and a bounded queue.
Discovery and typeface decoding happen off the main Looper. The selected face is
restored asynchronously after process restart; until ready, or if the provider,
permission, file size or decoder fails, the original built-in face is used. Copies
are bounded at 8 MiB, placed in cache and deleted after decoding. Failed selections
reset to built-in. New selections invalidate stale completions. Saturation reports
failure rather than silently dropping a font load.

The embedded `LyricStage` and fullscreen/external `StageView` read only the cached
face, apply it to their lyric paints and invalidate text-layout caches on change.
Neither renderer opens files or providers. Clock, cues, scoring and playback owners
are unchanged. ContentScanner now handles choosing KANTA itself and uses document
IDs (not tree-root IDs) to traverse children. KANTA takes priority over the legacy
fallback. This corrects traversal required to reach `font/`; no database migration.

## Validation

- Python source guards cover renderer wiring, size/failure handling, SAF permission
  and selection persistence code paths.
- Device-side `FontStoreChecks` is registered in `RuntimeChecks`: valid system font,
  malformed, oversized and missing font, and SettingsStore round-trip checks.
- These device checks **have not run** in this iSH environment. Actual rendering,
  provider revoke/regrant and restart acceptance remain pending on a supported
  Android runtime. A source guard or syntax parse is not a build or device test.

This work remains local and uncommitted based on `00d97f0`.
