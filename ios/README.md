# ZLPlay Mobile — iPhone / iPad

Development port from the macOS audio engine. This is a **0.1.0 development foundation**, not an installable release or a completed Android/Mac feature port. Desktop releases and tags are unchanged. Own code remains AGPL-3.0-only; preserve the root license and dependency notices.

## Current implementation

- Universal iPhone/iPad JUCE target, iOS 15+; landscape left/right only on both devices, including launch.
- The existing C++ engine is embedded in process using `native/embedded-engine.h`. The desktop console transport remains the default build. Audio streaming/DSP retain their native worker/callback.
- Native touch controls for audio import, play/pause/stop, seek, track gain, Master and project-clock Click/BPM.
- Audio selected through the system picker is copied to private app storage on a worker while the picker grant is valid; the callback reads only the engine cache. Up to 32 mono/stereo files, at most 1 GiB each in this first importer. Originals are not modified.
- New interface text in pt-BR/en/es. Leaving the application pauses local playback; the remote host is not paused by backgrounding the controller; this first target does not claim background playback.

Read-only folder import supports basic RPP, version-1 `.zlplay` and version-1 `.zlset` manifests. Internal pages follow Android's repertoire, settings, quick-settings and mixer workflows. The embedded macOS engine supplies the audio primitives; iOS handles file grants, routing, camera and network permissions. Arrangement editing, full DSP/session-file parity and physical interruption/background behavior remain outside the validated subset. This target does not host Electron or spawn a desktop executable.

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

No recording or user music is bundled. Use synthetic fixtures for development and distinguish file-provider tests from transport and physical-audio tests.

## Import during development

The iOS Files picker has its own storage; it does not expose the host Mac's Downloads or music folders. The app creates Documents and enables file sharing/opening documents in place, so its folder can appear in Files. In Simulator, copy a **synthetic fixture** into the app's Documents directory (obtain the container with `xcrun simctl get_app_container booted com.onebytelabs.zlplay.ios.dev data`), then import its containing project folder through Repertoire > Import setlist / Open project. Include the project manifests and referenced audio.

Import probes audio and prepares the engine offline, so viewing tracks, waveform and gain controls does not start the audio device. Play opens the actual output and reloads at the selected position; offline import success does not certify playback. The main view follows Android's header/song/timeline/transport/mixer organization, with native safe-area insets. The app is designed for horizontal use; portrait is excluded in both the bundle and the JUCE runtime orientation mask. Simulator transport success is not a physical acoustic, pitch-quality, interruption or latency measurement. Validate output routes and external interfaces on real devices before distribution.


## Project and setlist folder import

Choose **Repertoire > Import setlist / Open project**, then select its folder in the system Files picker. The selected tree is copied to private storage while the folder grant is held; the original project/audio files are never rewritten. A selector lists every project when there are multiple candidates, including distinct RPP filenames/keys; the word Original does not give a file priority. A `.zlset` respects saved song order and an explicit saved source. Missing/ambiguous sources produce an error; missing songs are retained in the list rather than replaced with arbitrary audio.

The first readers preserve basic track gain/pan/mute/solo, RPP folder gain/mute/solo, item positions/offsets/lengths, fixed tempo, region bounds and saved Original/Play in keys (including native transposition for eligible tracks). Timeline peaks are sampled from the arranged clips at project time, not drawn as a decorative waveform. Setlist target overrides apply to pitch-enabled tracks. Existing application audio remains native, with offline preparation before opening the real output on Play.

Limits: 32 tracks, 128 items, 512 regions, 256 setlist songs, 24 folder levels, 2048 supported files, 1 GiB per file and 8 GiB per imported folder; JSON/RPP manifests up to 4 MiB. Symlinks and path escapes are rejected. Relocated absolute Windows/Mac audio paths use a basename only when unique inside that song; relative paths are preferred. Only WAV/MP3/AIFF/FLAC mono/stereo sources are accepted. No REAPER plugins or project code are executed. Item stretch/pitch/gain/pan requiring unimplemented processing and built-in REAPER CLICK sources are rejected: render those parts to audio first. Plugin effects, fades, tempo envelopes and saved automation/settings not yet supported produce a compatibility confirmation before using the imported audio; they are not claimed to be reproduced. Full desktop/Android DSP/session parity is not certified. Project/arrangement file editing is not implemented. Session restoration uses private app preferences instead of rewriting project files.

The embedded host check also covers structural readers, saved keys, setlist order, missing/ambiguous paths, path escape rejection, unchanged folder-copy bytes and actual arranged PCM (a silent leading gap followed by nonzero clip audio). These checks use synthetic fixtures. Simulator/provider and device tests must be reported separately.


## Performance layout and internal pages

The positional reference is Android Mobile `MainActivity`, `PerformanceButton` and `SongDisplay` at commit 4025815adc75812122cffcadc18369d7ae945a31. The compact header keeps brand/setlist/Original/Play in/Setlist/settings/quick settings. Song cards show effective keys; the timeline aggregates real source peaks into colored regions. Primary transport order is rewind/Play-Pause/Stop/Loop, Click, Pad/note, minus/BPM/plus/Tap, clock and Mixer. A tablet follows timeline → transport → vertical channel strips. A phone opens Mixer as a page; its header contains Back/brand/title/pitch selection, clock and rewind/Play/Stop/Loop; its bottom row contains Click/Pad/note/BPM/Tap/output. Sizes use the Android logical dimensions, scaled inside the iOS safe area. The approved existing logo is reused.

Original key is displayed as text and edited in quick settings. Original/Play in edits, pitch exclusions and local mixer settings persist in private session preferences, without rewriting project files. Tap uses a six-tap average and two-second reset; the visual pulse is independent. Loop uses the selected region or full song. Standalone Click uses the same native `aux.soloEnabled` path as macOS, independent of song Play/Pause/Stop. Channel strips use native gain and level meters; VS gain/mute/solo applies to imported VS tracks. Guide/Click role aggregation follows the Android name-based roles; full desktop DSP/session-editing parity remains incomplete.

Repertoire is a full page with import/open actions, Song/Project/Key/Duration columns, cover selection, Open and drag handles. A handle also exposes one-position move actions. Reordering preserves the selected song identity; order and last selection are saved only in the app's private preferences. Restore session reopens the last private project, setlist or audio session without starting audio. The caption opens the remembered setlist library; the Setlist button opens the current repertoire page. Covers are copied to private storage. Original folders/manifests remain unchanged. The system file/folder picker remains native iOS.

Settings uses the Android four-category navigation: General, Audio output, Custom sounds and Remote. General includes player/remote mode, system-language settings, keep awake, restore session, confirm changes during playback, free storage, compatibility and original bundled licenses. Audio output uses the iOS route picker and reports actual CoreAudio format. Bus and individual-track routing use the same mono/stereo/master conventions as macOS `app/audio-routing.mjs`, limited to outputs reported by the device. Sample-rate application uses the existing native device configuration API. Physical USB/multichannel devices require separate validation. Custom sounds follows macOS `desktop/sound-bank.cjs`: 12 uniquely named notes, pads up to 60 seconds, normal/accented clicks up to 10 seconds, mono/stereo only, private copies and atomic bank metadata. Invalid batches preserve the previous bank. Empty banks are safe at first launch.

## Remote protocol

The iOS client uses the existing macOS/Windows `desktop/remote-host.cjs` protocol, reviewed alongside Android `QrInvitation` and `RemoteClient`. A `zlplay-remote:` QR invitation carries up to four private IPv4 LAN endpoints, a short-lived pairing key and the SHA-256 TLS certificate fingerprint. Public endpoints, malformed ports, expired invitations, redirects, unpinned certificates and oversized responses are rejected. Tokens stay in memory. HTTPS session requests run on a worker; commands have UUIDs/project guards and a bounded coalescing queue. There is no permissive trust-all handler or broad ATS exemption.

The client pairs at `/v1/pair`, polls `/v1/state`, sends `/v1/command`, and closes at `/v1/disconnect`. Supported controls use the host's existing Play/Pause/Stop/seek/region/Loop/Click/Pad/note/key/BPM/song/gain/mute/solo commands. Audio remains on the computer. Remote snapshots reuse the performance UI; unavailable meters remain empty. Import, cover editing, reordering and unsupported bus-state/pitch controls are disabled in remote mode. iOS camera scanning has a landscape presentation and localized privacy descriptions; the production connection UI exposes only QR scanning. Simulator protocol checks use a separate test executable, without an invitation field/button in the app.

Validation must distinguish protocol/mock checks, a synthetic local HTTPS host, the running desktop application, physical camera scanning and acoustic testing. Simulator success does not certify physical devices, network latency or App Store/signing readiness.

Quick settings is the Android page, not a mixer/zoom menu: Original key, native/project Click source, Keep Pad and accented Click. Without a project Click track, native Click is selected and its source switch is disabled. Native song-clock Click and standalone aux Click are selected independently of song playback so that standalone Click also works before a song is loaded or while paused. Guide/VS/Click/Pad gain and bus mute/solo remain separate.

Private metadata stores relative paths. Legacy absolute private paths are rebased only into the app's own `ZLPlayMobile` directory after iOS changes its container UUID; arbitrary external paths and traversal are rejected. Sound-bank tests cover this migration as well as unchanged imported bytes.

Developer checks: the host checker accepts `--state-only` (Tap, mute/solo, reorder, routing, invitation/protocol mock), `--sound-check <synthetic.wav>`, `--remote-check <synthetic-invitation.txt>` (real native HTTPS pin rejection/pair/commands/poll/disconnect), and `--remote-reject <expired-certificate-invitation.txt>`. The last two require a disposable local host using the desktop protocol. A Debug Simulator build also accepts `--zlplay-remote-test=.zlplay-test-<name>` to supply a synthetic QR payload from its own Documents directory to the same connection callback; it skips local restoration for isolation. This instrumentation is absent in Release and adds no invitation input or connection button to the app. Camera scanning still requires a physical-device test.

Local region taps during playback schedule the existing native jump at the end of the current region, matching Android selection behavior; tapping the pending region again cancels. Pause-side selection seeks to the selected start. Pending jump and Loop region badges use actual state; Stop clears pending jump and Loop. The bridge uses the desktop engine's existing jump ID/serial/boundary/target fields and does not modify its audio callback.
