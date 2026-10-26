# ZLPlay Android — audio prototype

This standalone Android project is an early, foreground-only playback prototype. It does not change the desktop application or its release packages.

## Build

Open this directory in Android Studio. Install Android SDK Platform 36 and Build Tools 36.0.0. The wrapper uses Gradle 9.1.0 with verified distribution SHA-256 and Android Gradle Plugin 9.0.1. JDK 17 or later compatible with Gradle is required; Java 25 requires Gradle 9.1 or later. SDK paths belong in ignored `local.properties` or `ANDROID_HOME`.

```sh
./gradlew assembleDebug
# Windows: gradlew.bat assembleDebug
```

The APK is generated under `app/build/outputs/apk/debug/`. Install on an authorized test device using `adb install -r app/build/outputs/apk/debug/app-debug.apk`. No production signing credentials or device identifiers belong in Git. The app has a separate development application ID, `com.onebytelabs.zlplay.dev`.

Run the platform-independent mixer checks from the repository root with a JDK available:

```sh
node scripts/test-android-core.mjs
```

On Windows, if Java reports a Unix-domain socket/loopback error, configure `jdk.net.unixdomain.tmpdir` for this build process to a short, writable local directory (for example a directory under the ignored tools folder). This changes temporary socket placement, not firewall settings. Do not commit machine-specific paths.

## Prototype scope and limitations

- Native Android views and a single AudioTrack PCM output; synthetic stems or imported WAV/MP3 sources share one sample clock.
- Landscape transport, source-derived peaks and imported project regions (representative waveform/regions only in the demo), seeking and looping; slim volume faders and automatically saved gain/BPM preferences.
- Standalone click with adjustable BPM and a synthetic C pad with its own persisted gain control. The test chord uses midrange harmonics to be easier to hear on small speakers. These are test signals, not the final sound library.
- Pausing/stopping the song leaves independently enabled click/pad active; disable their buttons to silence them. Leaving the activity or losing audio focus silences all audio. Playback does not continue in the background in this prototype.
- This is an audio experiment, not a completed Android port. There is no `.zlset` import, pitch shifting, custom pads/click import, external multichannel routing or audio latency certification yet. `.zlplay` and `.rpp` import and ordered `.zlplay` arrangement jumps are supported within the limits below. Selected document URIs and mix gains are restored automatically when the provider grants persistent read access; this is not full desktop project persistence.
- The Java mixer is isolated from Android for deterministic tests. Final backend reuse/native DSP decisions require actual device measurements. ASIO and Windows-specific packaging are not used.
- The app requests no microphone, network, broad storage or USB device permissions. USB multichannel functionality has not been implemented.
- UI resources include English, Brazilian Portuguese and Spanish.

## License

The project's own source remains AGPL-3.0-only; see `../LICENSE` and `../THIRD_PARTY_NOTICES.md`. The Gradle wrapper is from the official Gradle 9.1.0 distribution/source and carries its upstream notices. Android tooling is obtained separately under its own terms; SDK license acceptance must be completed by the developer. This prototype does not bundle the desktop JUCE/ASIO SDK or personal audio.

## Device Pad regression

Build assembleDebugAndroidTest, install both debug APKs and run:

    adb shell am instrument -r -w com.onebytelabs.zlplay.dev.test/com.onebytelabs.zlplay.PadSmokeTest

The runner clicks the real Pad button, verifies standalone PCM output and frame delivery to AudioTrack, tests zero/restored Pad level and fade-out, and closes its test activity. It does not record the microphone or certify physical audibility, latency or the external audio route. AudioSession exposes stream RMS and written-frame counters for this test; these are diagnostics, not mixer meters.

## WAV import (0.2.0-dev)

Import WAV opens the system document picker with multiple selection. Supported: little-endian RIFF/WAVE PCM integer 8/16/24/32-bit and IEEE float32, mono/stereo, including supported WAVEFORMATEXTENSIBLE subtypes. Rate conversion currently uses linear interpolation to the 48 kHz prototype output; it is not a finalized high-quality resampler. MP3 support uses Android decoding as described below. FLAC and RF64 are not supported yet.

Header validation, initial prefetch and a sampled source-peak preview run on a worker thread before replacing the active session. Subsequent decoding runs on a separate prefetch worker. The 0.2.0 implementation decoded whole sources and imposed a memory limit; it has been replaced by the 0.3.0 block-streaming implementation below. WAV source files are not copied or modified. MP3 sources require a private decoded temporary file. Up to 32 sources can be selected. New and old sessions may coexist during transactional preparation. Unsupported/truncated/oversized files leave the previous active session intact.

The app retains read-only document permissions and automatically saves selected URI references and gains when the provider supports it. Missing/revoked files are reported. No broad storage permission is requested, and original audio is never uploaded. Provider session-only access is identified in the UI.

Run the on-device WAV regression with the test APK installed:

    adb shell am instrument -r -w -e mode wav com.onebytelabs.zlplay.dev.test/com.onebytelabs.zlplay.PadSmokeTest

The test creates and deletes its own temporary WAV fixtures in app cache, imports a 10-minute / 115 MB WAV at 48 kHz stereo and a short 44.1 kHz mono WAV, checks AudioTrack output, pause/loop/stop, independent gains and rollback after invalid input. It does not validate a user's document provider, persistent grants after process death, acoustic quality or long sessions.

## Bounded streaming (0.3.0-dev)

The former 64 MiB whole-file decoding limit is removed from the import path. Read-only seekable WAV documents are opened using file descriptors; one background worker per session decodes immutable 8192-frame blocks. Each source retains two intro blocks, up to six rotating cache blocks and up to two jump-prefetch blocks. At 32 stereo sources, nominal retained PCM cache storage is about 20 MiB, plus in-flight/referenced blocks, previews and Android overhead. GC and device/provider throughput still affect real performance; this is not an unlimited-track or latency guarantee.

The renderer only reads published blocks. Missing blocks hold the song cursor until all needed sources are ready, preserving their shared clock; the UI reports buffering. Pad remains independent, and the song-linked click is muted during buffering. Seek and loop restart use prefetch/intro blocks. Provider read failure stops the song and is reported rather than substituting demo tracks. Foreground playback keeps the screen awake; manual locking/backgrounding still silences the prototype as before.

The document provider must support random access. Unsupported cloud/pipe providers get a message to select a local stored WAV; the app does not silently duplicate it. RIFF PCM/float mono/stereo supports direct reads. MP3 is decoded to a temporary seekable WAV before playback. Waveform preview is sampled for quick import and may miss short transients; it is not a full peak or loudness analysis.

Import failures are shown in a dialog and retained in the status instead of being hidden by Play. Source selection and gains remain private app state. The JVM regression opens a 10-minute WAV under a 48 MiB heap and tests bounded cache, seek, pause, loop, EOF and source loss. Device instrumentation requires an unlocked, active screen; it checks playback portions and jumps of the long fixture, not a full 10-minute soak test.

## Phone mixer and fader gestures (0.3.1-dev)

On phones, Mixer opens in a dedicated full-window panel with taller faders and a Back button. The transport/waveform scroll view is not the parent of the open mixer. Returning does not restart or stop audio. Tablets retain the inline mixer below the timeline. FaderSeekBar reserves the gesture from ACTION_DOWN through UP/CANCEL so parent vertical/horizontal scroll views do not steal a volume drag; scrolling outside the fader remains available. Android accessibility behavior is inherited from SeekBar.

Run the device UI regression on an unlocked phone:

    adb shell am instrument -r -w -e mode mixer com.onebytelabs.zlplay.dev.test/com.onebytelabs.zlplay.PadSmokeTest

It opens the actual mixer window, sends a touchscreen drag to a fader, verifies the gain and audio state, restores the original gain and returns to transport. Tablet layout has not been physically tested in this round.


## Desktop projects and MP3 (0.4.0-dev)

Open project asks for a folder grant through Android Storage Access Framework. Select the song folder containing its .zlplay or .rpp and audio, including any Media subfolders. Choose the exact project when several are present. No broad storage or network permission is requested. Folder scans are bounded to 4096 entries / 16 levels; manifests to 4 MiB. Read-only grants and project selection persist privately when supported by the provider. Mobile fader and BPM overrides persist separately from the desktop files.

Supported projects contain up to 32 audio tracks / 64 clips total, mono/stereo WAV or MP3, basic track/item gain and pan, offsets, positions and trims, and non-overlapping named regions. Names are retained exactly; repeated Verse 1 regions are not renumbered. REAPER folder gain/pan/mute/solo is flattened into initial per-track levels. Relocated desktop paths resolve only to a unique basename inside the granted folder; ambiguity/missing files and paths escaping the selected tree are rejected. Native REAPER CLICK sources can use a matching track-name.wav in the granted folder; otherwise render them first.

The project header regions are selectable: while stopped/paused they seek immediately; during playback selection queues a jump at the current region end, and a second selection cancels that queue. The waveform body remains draggable for seeking. .zlplay arrangement rules execute in saved order at the source region end; Stop resets runtime rules. Manual selection takes precedence and suppresses the automatic jump for the explicitly selected region. Whole-song Loop blocks automatic arrangement jumps. Automatic targets are prefetched; a late source can still cause buffering. Pre-jump guide previews are not implemented.

REAPER plugins/instruments are never executed. MIDI/SECTION/non-audio sources, item pitch/rate changes, stretch markers, clips extending past the available source (including unrendered source loops), and .zlplay live transpose/execution-rate changes are rejected rather than played at the wrong pitch/speed. Effects, fades, tempo maps, custom routing, and saved project click/pad settings are not reproduced and receive a compatibility notice. Native prototype click and synthetic Pad remain independent. Projects whose mix depends on unsupported processing should use rendered audio. This is not full REAPER project equivalence.

MP3 is decoded by the device MediaExtractor/MediaCodec on the import worker into private temporary 16-bit PCM WAV, then uses the same bounded streaming engine as WAV. No compressed file is decoded or read by the render callback. A repeated MP3 URI is decoded once per import, even if used in several clips. Temporary audio consumes storage (roughly sample rate × channels × 2 bytes per second) until the project closes; insufficient space is reported without replacing the old project. Original audio remains unchanged. Normal close removes temporary files; app cache can retain orphaned temporaries after abrupt process death until cleared by Android/user. MP3 decoding is performed again on reopen; no permanent duplicate library is created. Encoder delay/padding metadata is trimmed when exposed by Android; missing or inaccurate metadata cannot reconstruct lost sample alignment. WAV remains the reference format for sample-accurate project preparation. See [Android MediaFormat metadata](https://developer.android.com/reference/android/media/MediaFormat#KEY_ENCODER_DELAY).

Project fixture regression (unlocked physical phone, debug and test APK installed):

    adb shell am instrument -r -w -e mode project com.onebytelabs.zlplay.dev.test/com.onebytelabs.zlplay.PadSmokeTest

It tests synthetic .zlplay/.rpp opening, exact region names, Windows-path relocation and ambiguity/traversal rejection, ordered automatic/manual jumps, MP3 PCM reaching AudioTrack, unsupported-source rollback, and temporary-file cleanup. The MP3 fixture is an original two-second 440 Hz mono signal generated with LAME/lameenc 1.8.1 at 44.1 kHz / 64 kbps, Base64 stored only in test assets. No user music is included. This test does not validate every document provider, MP3 encoder, USB interface, tablet or an entire live session. .zlset/repertoire import remains a subsequent step.
