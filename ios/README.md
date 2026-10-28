# ZLPlay Mobile — iPhone / iPad

Development port from the macOS audio engine. This is a **0.1.0 development foundation**, not an installable release or a completed Android/Mac feature port. Desktop releases and tags are unchanged. Own code remains AGPL-3.0-only; preserve the root license and dependency notices.

## Current implementation

- Universal iPhone/iPad JUCE target, iOS 15+; landscape left/right only on both devices, including launch.
- The existing C++ engine is embedded in process using `native/embedded-engine.h`. The desktop console transport remains the default build. Audio streaming/DSP retain their native worker/callback.
- Native touch controls for audio import, play/pause/stop, seek, track gain, Master and project-clock Click/BPM.
- Audio selected through the system picker is copied to private app storage on a worker while the picker grant is valid; the callback reads only the engine cache. Up to 32 mono/stereo files, at most 1 GiB each in this first importer. Originals are not modified.
- New interface text in pt-BR/en/es. Leaving the application pauses playback; this first target does not claim background playback.

Initial read-only folder import now supports basic RPP, version-1 `.zlplay` and version-1 `.zlset` manifests. Not yet implemented/validated here: arrangements/region editing and jumps, pad-bank UI, interactive musical-key selection, QR remote client, iOS output-routing/interruption handling, restoration and complete About/license UI. The desktop C++ core contains many of these primitives, but UI/platform integration must be completed. This target does not host Electron and does not run a desktop executable as a subprocess.

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

Debug builds expose an Open > Synthetic test menu action, which creates an original eight-second stereo sine fixture in temporary app storage on a worker. This exercises the same probe/load/native transport without requiring a signed-in document provider in Simulator. It does not replace testing the real iOS picker or physical audio. The menu action and generator are excluded by JUCE_DEBUG in Release builds. No test recording or user music is bundled.


## Import during development

The iOS Files picker has its own storage; it does not expose the host Mac's Downloads or music folders. The app creates Documents and enables file sharing/opening documents in place, so its folder can appear in Files. In Simulator, copy a **synthetic fixture** into the app's Documents directory (obtain the container with `xcrun simctl get_app_container booted com.onebytelabs.zlplay.ios.dev data`), then choose it in Import audio. Debug Synthetic test also creates a fixture under Documents/ZLPlay Examples. Use Open > Project / setlist folder for projects, including the referenced audio; audio-file import remains a separate option.

Import probes audio and prepares the engine offline, so viewing tracks, waveform and gain controls does not start the audio device. Play opens the actual output and reloads at the selected position; offline import success does not certify playback. The main view follows Android's header/song/timeline/transport/mixer organization, with native safe-area insets. The app is designed for horizontal use; portrait is excluded in both the bundle and the JUCE runtime orientation mask. It is still missing Android's project/setlist/key/pad/remote screens. The first two Intel simulator audio attempts aborted with a CoreAudio RPC timeout. A later imported-project simulator test opened output successfully: the position advanced, Pause held 6.3 seconds and Stop returned to zero. This is a runtime/transport observation, not a physical acoustic, pitch-quality or latency measurement. No physical-device test has been performed.


## Project and setlist folder import

Choose **Open > Project / setlist folder** in the system Files picker. The selected tree is copied to private storage while the folder grant is held; the original project/audio files are never rewritten. A selector lists every project when there are multiple candidates, including distinct RPP filenames/keys; the word Original does not give a file priority. A `.zlset` respects saved song order and an explicit saved source. Missing/ambiguous sources produce an error; missing songs are retained in the list rather than replaced with arbitrary audio.

The first readers preserve basic track gain/pan/mute/solo, RPP folder gain/mute/solo, item positions/offsets/lengths, fixed tempo, region bounds and saved Original/Play in keys (including native transposition for eligible tracks). Timeline peaks are sampled from the arranged clips at project time, not drawn as a decorative waveform. Setlist target overrides apply to pitch-enabled tracks. Existing application audio remains native, with offline preparation before opening the real output on Play.

Limits: 32 tracks, 128 items, 512 regions, 256 setlist songs, 24 folder levels, 2048 supported files, 1 GiB per file and 8 GiB per imported folder; JSON/RPP manifests up to 4 MiB. Symlinks and path escapes are rejected. Relocated absolute Windows/Mac audio paths use a basename only when unique inside that song; relative paths are preferred. Only WAV/MP3/AIFF/FLAC mono/stereo sources are accepted. No REAPER plugins or project code are executed. Item stretch/pitch/gain/pan requiring unimplemented processing and built-in REAPER CLICK sources are rejected: render those parts to audio first. Plugin effects, fades, tempo envelopes and saved automation/settings not yet supported produce a compatibility confirmation before using the imported audio; they are not claimed to be reproduced. Full desktop/Android DSP/session parity is not certified. Project edits and session restoration are not implemented yet.

The embedded host check also covers structural readers, saved keys, setlist order, missing/ambiguous paths, path escape rejection, unchanged folder-copy bytes and actual arranged PCM (a silent leading gap followed by nonzero clip audio). These checks use synthetic fixtures. Simulator/provider and device tests must be reported separately.


## Performance layout

The presentation follows the Android Mobile reference (`MainActivity`, `PerformanceButton`, `SongDisplay`, commit 4025815): compact brand/setlist/key/import/settings header, horizontal song cards with effective-key badges, a single aggregate waveform with colored region panels, and a fixed transport row. A tablet shows vertical channel strips below the timeline; a phone opens the mixer as a dedicated page with Back navigation. This replaces the earlier engineering screen with individual waveform rows and horizontal sliders. The approved existing logo is reused; no new branding is generated.

Transport controls include rewind, Play/Pause, Stop, native Loop for the selected region (or full song), Click, numeric BPM +/- and Tap, clock and Mixer. Tap uses the six-tap average / two-second reset window; its brief highlight is independent of BPM calculation. Key fields edit Original/Play in for the current session, preserving pitch-disabled tracks; they do not rewrite project files or implement session saving. Channel strips expose vertical gain, dB value, native live meters, M/S and per-track pitch inclusion. Native gain/pan and the existing audio callback remain in use. Zoom buttons and panning keep the active cursor visible; no artificial waveform or meter is drawn for a missing source.

Pad-bank/remote/output-settings/arrangement editing screens are still pending and are not represented by fake working controls. Settings exposes implemented import/help actions and bundled original license texts. The presentation is native iOS/JUCE with the Android organization/colors; it is not an Android runtime or a web screenshot. UI validation on simulators is separate from acoustic or physical-device testing.
