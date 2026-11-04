# KANTA Reborn OpenStage: License Audit Required

This package is a development candidate, not a final redistributable open-sample release.

Before replacing or adding any audio asset, the repository maintainer must:

1. Verify the license for every individual sample, mapping, impulse response, script, and document.
2. Confirm that the audio itself is covered, not only repository code or SFZ mappings.
3. Record the upstream repository, exact commit, original path, file hash, license identifier, author/source, and required attribution.
4. Preserve upstream license and attribution files in `THIRD_PARTY_LICENSES/`.
5. Exclude assets with unclear, conflicting, non-redistributable, or non-commercial terms.
6. Do not use samples extracted from commercial keyboards, ROMs, firmware, expansion packs, factory styles, or proprietary sound banks.
7. Re-run the audit whenever an upstream commit or source file changes.
8. Review the final combined bank before public or commercial distribution.

The included SF2 currently contains only KANTA's independently generated procedural audio. The verified open-source candidates are pinned in `open_source_candidates.lock.json` for the repository's audited acquisition and integration workflow.
