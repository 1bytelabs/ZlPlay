# ZLPlay Mobile — iPhone / iPad

Development port from the macOS audio engine. This is a **0.1.0 development foundation**, not an installable release or a completed Android/Mac feature port. Desktop releases and tags are unchanged. Own code remains AGPL-3.0-only; preserve the root license and dependency notices.

## Current implementation

- Universal iPhone/iPad JUCE target, iOS 15+; iPhone portrait/landscape and iPad orientations.
- The existing C++ engine is embedded in process using `native/embedded-engine.h`. The desktop console transport remains the default build. Audio streaming/DSP retain their native worker/callback.
- Native touch controls for audio import, play/pause/stop, seek, track gain, Master and project-clock Click/BPM.
- Audio selected through the system picker is copied to private app storage on a worker while the picker grant is valid; the callback reads only the engine cache. Up to 32 mono/stereo files, at most 1 GiB each in this first importer. Originals are not modified.
- New interface text in pt-BR/en/es. Leaving the application pauses playback; this first target does not claim background playback.

Not yet implemented/validated here: RPP/ZLPlay/setlist import, arrangements/region editing and jumps, pad-bank UI, musical-key selection, QR remote client, iOS output-routing/interruption handling, restoration and complete About/license UI. The desktop C++ core contains many of these primitives, but UI/platform integration must be completed. This target does not host Electron and does not run a desktop executable as a subprocess.

## Build

Install the official **Universal Xcode compatible with the host Mac**, open it once, and complete the Apple license/component setup yourself. On this Intel Sequoia host, the inspected Apple download was Xcode 26.3 Universal. Do not download the Apple-silicon-only archive. Requirements may change; consult https://developer.apple.com/xcode/system-requirements.

From repository root, with Node 22+ and CMake 3.24+ available:

```sh
# Uses DEVELOPER_DIR for this process; does not change global xcode-select.
node scripts/build-ios.mjs
# Generate only, then open the printed project in Xcode:
node scripts/build-ios.mjs --configure-only
# Physical-device build requires your authorized development team:
ZLPLAY_APPLE_TEAM=YOUR_TEAM_ID node scripts/build-ios.mjs --device
```

Optional `DEVELOPER_DIR` selects a differently named Xcode install. Optional `ZLPLAY_JUCE_SOURCE` selects an existing JUCE 9.0.4 source directory; otherwise the pinned/hash-verified desktop JUCE revision is fetched. The generated Xcode project and products remain in `.cache/ios-iphonesimulator/` or `.cache/ios-iphoneos/`. The development bundle ID is `com.onebytelabs.zlplay.ios.dev`; signing keys and provisioning profiles never belong in Git. A development device build is not an App Store or TestFlight release.

## Host regression before iOS SDK is available

```sh
cmake -S ios -B .cache/ios-host-check -DZLPLAY_HOST_CHECK=ON -DCMAKE_BUILD_TYPE=Release
cmake --build .cache/ios-host-check --config Release
# Pass an absolute path to a synthetic stereo WAV of at least two seconds.
.cache/ios-host-check/ZLPlayEmbeddedCheck_artefacts/Release/ZLPlayEmbeddedCheck /path/to/synthetic.wav
```

This checks the actual embedded C++ engine's probe, rendered PCM, seek, zero gain and Stop. It is not an iOS simulator/device or physical-audio test. The normal desktop engine and its existing tests must also continue to pass before merging changes. iPhone and iPad hardware, route changes, interruptions, memory/performance and licensing resources in the final app must be validated before any distribution.

Debug builds expose a translated Synthetic test button, which creates an original eight-second stereo sine fixture in temporary app storage on a worker. This exercises the same probe/load/native transport without requiring a signed-in document provider in Simulator. It does not replace testing the real iOS picker or physical audio. The button and generator are excluded by JUCE_DEBUG in Release builds. No test recording or user music is bundled.
