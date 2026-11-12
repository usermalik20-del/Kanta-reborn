# B1 — real-PCM spectrum analysis (local candidate)

## Tapping decision and ownership

`MicrophoneInput` remains the sole opt-in microphone capture owner. Its existing
single analysis worker computes the spectrum from the same **dry** 4096-sample,
48-kHz frame used by the pitch detector. Capture still sends original short PCM
to the optional monitor sink first. No player, recorder, permission or worker is
added, and pitch/scoring/monitor data and timing are unchanged.

The existing `analysisBusy` fence permits one in-flight analysis and the existing
125 ms throttle bounds scheduling. A result is published only for the current
capture run. `reading()` retains its 750 ms freshness limit and returns no result
after microphone stop. The immutable `Reading.spectrum` snapshot contains only
band powers; raw PCM is not stored or logged, and FFT workspaces are cleared.

## Signal definition

`core.SpectrumAnalyzer` uses a Hann window and full complex radix-2 FFT. It reports
16 logarithmic bands spanning nominally 80–8000 Hz. Bin resolution is 11.71875 Hz;
edges round upward to bins. Intervals are half-open, disjoint and contiguous, so no
bin is counted twice. Power sums include **both** real and imaginary components
and are normalized by frame length and window power (one-sided mean-square PCM).
For an in-band sine of amplitude 0.5 the total is approximately 0.125. These are
relative digital powers, **not calibrated sound-pressure measurements**.

Invalid frame length, nonfinite samples or samples outside [-1,1] return unavailable.
The entire frame is validated before copying; the dry input is never modified.
Mutable workspaces stay analysis-worker-confined. Snapshots expose scalar reads,
not mutable arrays, and remain valid after subsequent frames.

## Tests and limits

`SpectrumRegressionTests.run()` is registered in the existing `coreRegression`
runner. It covers silence, seven tone frequencies, band boundaries, phase tolerance,
normalized power, amplitude scaling, mixed tones, invalid input, input preservation
and snapshot lifetime. `tools/tests/test_android_spectrum.py` checks source wiring
only; it does not run signal processing or compile Java.

Run `cd apps/android && ./gradlew :app:coreRegression` on a working Java toolchain.
The local JVM fails initialization on `getcpu(2)`, so the Java signal tests and
Android build have **not** run here. Physical capture overhead, latency and battery
use remain unmeasured. No UI consumer or unwired spectrum view is added; this
implements B1's band-plan/tapping scope, not a claim of a visible music visualizer.
No exact-SHA CI, device, signing or release acceptance is claimed.
