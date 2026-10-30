# Third-party software

Gradle 8.13 wrapper scripts/JAR: Copyright the original authors / Gradle contributors, Apache License 2.0. Retained wrapper headers and license: `gradle/wrapper/LICENSE.gradle`. Source: https://github.com/gradle/gradle/tree/v8.13.0 . Wrapper JAR SHA-256: `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`, verified against Gradle's published checksum. The distribution checksum is pinned in wrapper properties.

TinySoundFont by Bernhard Schelling, based on SFZero by Steve Folta, is bundled under the MIT license. The upstream header and license remain unchanged at revision `853a0a171759f1ddba0de1442133a75912bbeffa`; exact hashes and source are in `app/src/main/cpp/vendor/PROVENANCE.json`. Copyright notices remain in the header and license, and the complete license is available in My Set inside the app. The native wrapper, validation and scheduling are maintained in this repository. No upstream code or licenses were rewritten. First-party KANTA starter KAR and scenic images are copied byte-identically from recovered first-party resources; their provenance is recorded by the recovery/reconciliation manifests. Android framework APIs are system-provided. Server-side compiler/formatter/reference JARs are development tools and are not included in this repository or app.

Camera preview adds AndroidX Activity 1.10.1 and CameraX camera-camera2/camera-lifecycle/camera-view 1.5.3 from Google's Maven repository, under Apache License 2.0. Upstream sources: https://android.googlesource.com/platform/frameworks/support/ . Native application code remains Java/Views; no Compose, Room or DI framework is added.

The build resolves the pinned graph, copies embedded LICENSE/NOTICE resources unchanged into a generated offline runtime-notices.txt asset with dependency coordinates, and preserves upstream packaged metadata. My Set exposes this file separately from TinySoundFont's MIT license. Kotlin, AndroidX and concurrent-future support arrive through those declared dependencies; the CI dependency report records the resolved graph. No upstream library source or copyright header is rewritten. Store/public distribution remains a separate acceptance gate.

Pitch estimation adds the TensorFlow Lite runtime, `org.tensorflow:tensorflow-lite:2.16.1`, from
Maven Central, under Apache License 2.0. The AAR declares no permissions, pulls in no Google
Play services, and carries its upstream `LICENSE`, which the build copies unchanged into the
generated offline `runtime-notices.txt` asset. Upstream sources: https://github.com/tensorflow/tensorflow .

The SPICE pitch model, `app/src/main/assets/spice.tflite` (8,733,770 bytes, SHA-256
`b88f96ffa5d0691a50774f7843ba2356bbddd5fb33981ef1913c9b06afe25e07`), is the model published by
Google Research for the Kaggle "spice" TensorFlow Lite release, described upstream as
"A mobile-compatible pitch extraction model to recognize the dominant pitch in sung audio.
Trained (in a self-supervised way) on the MIR-1k dataset". It is a trained model file used
unchanged; no upstream source or licence text was rewritten, and the file is redistributed under
its upstream terms. Upstream sources: https://www.kaggle.com/models/google/spice . The model is
run locally on the device; no microphone audio leaves the device and no model input is uploaded.

The MKMD decoder (`core/import/MkmdDecoder.java`) is a port of the iOS LegacyCompatibility
MKMD format parser, included for legacy Meg container interoperability. It is a
read-only format decoder: AES-256-CBC decryption (via javax.crypto) and raw DEFLATE
inflation (via java.util.zip.Inflater) with key material supplied at runtime only —
never committed to the repo or CI (Option B per dim-3, Option 3b). The decoder never
touches the ReservationQueue or RenderedPlaybackClock owners. No upstream library
source or copyright header was modified. Meg container payloads remain encrypted; the
decoder is a parser only and does not weaken or bypass any encryption.
