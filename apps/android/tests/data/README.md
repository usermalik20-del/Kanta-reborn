# tone.sf3 — synthetic SF3/Ogg regression bank

`tone.sf3` is a minimal SoundFont 3 bank: one preset, one instrument, one
sample, and a single Ogg Vorbis stream of a 0.6 s 440 Hz mono sine tone
(generated locally with `oggenc -q 3`, `vorbis-tools`). Sample type carries
the compressed flag (`0x21`), the decoded loop window is 8000-20000 frames
(continuous loop, generator 54 must precede the sampleID generator 53 because
the loader commits the zone at sampleID), and the sample root key is 69 so
note 69 renders at 440 Hz. It was generated during the 2026-11-04 import
rework with a small RIFF assembler; it contains no third-party audio.

Checks it provides:
- `sf_validate` accepts a compressed bank (byte-offset container checks),
- `kanta_sf_open` decodes the Ogg stream, `synth->sampleTotal` reflects the
  decoded frame count, and rendering produces audible output,
- truncations are rejected by the structural validator.