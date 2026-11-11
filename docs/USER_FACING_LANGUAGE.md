# User-facing language

## Rule

Write for someone who wants to sing, not someone who builds the app. Apply this to app labels, messages, help, the public website, download descriptions and the root README.

Keep implementation names, debugging notes, architecture terms and detailed build evidence in developer documentation, source comments, logs or clearly labeled diagnostics. "Internal" here means intended for contributors; files in this public repository are not confidential.

## How to write

- Describe what the person can do, what happened, or what they should do next.
- Use familiar words and short sentences. Expand or explain an unfamiliar term on first use when it is necessary.
- Keep the same name for the same action. Prefer existing clear labels such as Search, Queue and Settings over invented technical names.
- Keep necessary file names and formats recognizable. For example: "karaoke files (MIDI/KAR)" and "instrument sound libraries (SoundFonts)".
- Explain limitations in everyday language without hiding them. A shorter page must still explain installation requirements, possible data loss, privacy choices, unavailable features and incomplete device testing.
- Distinguish planned features, features in source, tested builds and published downloads. Never simplify "automated tests passed" into "works on every device".
- Put technical detail behind a relevant developer link rather than a long section or a collapsed engineering log on the user-facing page.

## Examples

These are writing examples, not a claim that any particular feature is available.

| Engineering wording | User-facing wording |
| --- | --- |
| Persistent stage projection | The karaoke screen stays visible while you browse. |
| FIFO reservation queue | Songs play in the order they are added. |
| BGV selection | Background video |
| Opt-in microphone capture | Turn on the microphone when you want to use it. |
| DSP monitoring latency | You may hear a delay in your voice. |
| Runtime acceptance pending | This still needs testing on a real device. |
| Unsigned IPA artifact | iPhone/iPad test download; a separate signing tool is needed before installation. |
| CI artifact retention | This test download is available for a limited time. |
| OTA signer mismatch | You may need to uninstall the old version first. This can erase its saved data. |

## Root README maintenance

Keep the root README a short introduction with download/help links, an overview of useful features, important limitations and a small contributor section. Aim for no more than 100 lines; avoid wide feature matrices and dated implementation updates.

Do not prepend incident reports, commit IDs, test transcripts, workflow changes or release-engineering notes. Put those in the relevant developer document or repair ledger, and link from the contributor section only when useful.

The historical README is preserved in [README_HISTORY.md](README_HISTORY.md). It contains dated and superseded claims and must not be treated as current product guidance. Do not add new entries there; use the maintained engineering documents instead.

When an implementation change requires updating the root README under the documentation-sync rules, update only the affected user explanation, limitation or contributor link. Put the detailed engineering evidence in the other required owner documents.

## Review checklist

1. Can a new user understand the wording without knowing the codebase?
2. Does it say what they can do or what they should do next?
3. Are the platform, installation, safety and privacy limitations still accurate?
4. Are feature and verification claims supported by current evidence?
5. Can debugging detail move to a developer document instead?
