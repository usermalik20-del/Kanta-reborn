# Built-in / browser Companion Remote checkpoint

October 18, 2026. Candidate on PR #51, based on `b46f6c7`. Not merged, signed, deployed or released.

## Implemented candidate

- Approved navy/purple/cyan responsive remote, conventional 0–9 keypad, editable title/artist/code lookup, explicit result reservation, singer metadata, queue cancellation, now playing and local Play/Pause/Next/Stop.
- Explicit Host / Companion Remote entry in both native apps. The same locally served page opens inside KANTA; guests can also enter the displayed full HTTPS address in a browser.
- Private-interface HTTPS host on both platforms, session-generated certificates, native certificate pinning, authenticated guest tokens, expiry, bounded parsing, origin validation, replay suppression and foreground cleanup.
- Host adapters project the existing authoritative native queue; existing encrypted KCP1 and legacy entry points remain available.

## Trust bootstrap and limitations

Select a displayed private Wi-Fi address on the host and enable hosting. The host shows `https://<private-ip>:45732`, a private pairing code, and the certificate's SHA-256 fingerprint. Native guests enter the private IP and exact certificate fingerprint before the page opens, then enter the code. Browser guests must inspect and compare the actual certificate fingerprint with the host before entering the code; an unverified certificate warning is insufficient. Do not install a system-wide CA or configure a developer workstation. A browser that cannot expose/accept the local certificate safely is not accepted for this flow; use the built-in pinned client. Safari/Chrome mobile usability is still unverified.

The bootstrap is manual and has no independent security audit or forward-secrecy/device acceptance claim. TLS provides transport encryption; verified fingerprint comparison supplies host identity. Three incorrect pairing-code attempts lock new pairing until hosting restarts. A correct code rejected only because all four guest slots are occupied does not consume that budget; when a guest leaves or expires within the five-minute pairing window, a replacement guest can pair. Pairing closes at five minutes, guests expire at ten minutes, hosting at thirty minutes. Hosting/background revocation affects all guests immediately at the server boundary. Closing a client discards its token; network failure can prevent delivery of Leave, in which case the host retains only the expiring authorization. No automatic action replay/reconnect is attempted after ambiguous network results.

YouTube remains controlled by its visible official host player. Previous/seek, Favorites, Host Cam and Audio FX are disabled with explanatory text. No microphone, recording, camera streaming, arbitrary file serving, catalog dump or cloud relay is added. This is not complete parity for those expanded shortcuts.

## Local evidence

- Java 17 core production and regression sources compiled; all **524 core checks passed**, including **45 real TLS socket checks**: fresh certificate/SAN, shell headers/no code leakage, unauthenticated access, cross-origin pairing, token bounds, command replay/conflict, distinct reservations, revocation, guest cap and pairing lockout.
- PinnedBrowserTransport compiled against the official Android API 36 jar. This is not a full APK build or emulator result.
- All **55 release-tooling tests**, Swift layout/presentation and original starter-resource guards passed. Existing captured Songbook browser interoperability passed. New JavaScript and the real-browser harness pass Node syntax checks.
- A real Chrome contract harness is wired to CI using the actual HTTPS server and shipped assets. Local Chrome could not initialize because the execution environment rejects its Unix-domain singleton socket (`socket(): Operation not permitted`). No UI/browser success is inferred from that failure; the hosted Chrome run remains required.
- Swift parser/command/certificate regressions and a new real simulator role/input-rejection case are wired. Focused smoke now requires nine named native/UI passes, retaining all prior eight. The full iPhone/iPad walkthrough now requires eight UI cases.
- Android instrumentation adds actual HTTPS → SessionController search, replay/distinct reservations, cancellation, native Play/Pause/Stop, sensor isolation and background revocation. Hosted execution pending.

## Remaining acceptance

Hosted Swift/package/app compilation; Android build/lint and actual emulator checks; actual Chrome UI with TLS; exact-head nine-case iPhone smoke; full eight-case iPhone/iPad walkthrough; four native host-client combinations; mobile browser trust/certificate setup; rotation/large text; real-device audio/media/privacy and independent security review. No signing, public binary, website feature claim or production deployment changes.


### First-head diagnostics and repair candidate

Source 339b8f6 passed hosted Android APK/instrumentation assembly, lint, native sanitizer, 524 core checks and permission/notices checks. Reconstruction 39126848617 failed Swift test compilation (missing explicit throwing-parser try) and Chrome's first unverified-pairing assertion; emulator execution was skipped. Smoke 39126848616 failed the lazy AppServices initializer's isolation diagnostic. Repairs preserve every assertion, compose the host explicitly, match WebKit completion annotations, establish real-browser document/foreground readiness and retain nonsecret diagnostics. Android preparation is claimed before dispatch, and oversized replies now enter the operation ledger as bounded errors on both platforms to prevent duplicate side effects. The expanded local core suite passes 528 checks, including 49 real TLS checks. Changed-head hosted results remain pending; see the failure/repair ledger for confirmed causes versus hypotheses.


### Guest-capacity recovery regression — 2026-11-02

The server counts only incorrect pairing codes toward the three-attempt lockout. A correct code rejected because four guest slots are occupied is a temporary capacity response, not an authentication failure. `BrowserRemoteRegressionTests` verifies three capacity denials do not prevent a new pairing after an existing guest leaves, while the independent three-wrong-code lockout remains enforced. Hosted changed-head Android validation is required before accepting this repair.
