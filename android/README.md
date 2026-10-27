# ZLPlay Android — audio prototype

This standalone Android project is an early, foreground-only playback prototype. It does not change the desktop application or its release packages.

## Build

Open this directory in Android Studio. Install Android SDK Platform 36, Build Tools 36.0.0, NDK 27.2.12479018 and CMake 3.22.1. The native pitch target reuses the MIT-licensed Signalsmith sources checked into native/vendor/. The wrapper uses Gradle 9.1.0 with verified distribution SHA-256 and Android Gradle Plugin 9.0.1. JDK 17 or later compatible with Gradle is required; Java 25 requires Gradle 9.1 or later. SDK paths belong in ignored `local.properties` or `ANDROID_HOME`.

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
- Standalone click uses the desktop Meuclick.wav default, with separately importable normal/accent samples. Twelve user-imported pads are copied into app-private storage and streamed by note. Production playback requires imported pads; a synthetic pad is retained only in isolated debug tests.
- Pausing/stopping the song leaves independently enabled click/pad active; disable their buttons to silence them. Leaving the activity or losing audio focus silences all audio. Playback does not continue in the background in this prototype.
- This is an audio experiment, not a completed Android port. Native pitch shifting and app-owned pad/click imports are available. Output routing through Android AudioTrack is available when Android exposes the channels; physical USB multichannel and audio latency certification remain unverified. `.zlplay` and `.rpp` import and ordered `.zlplay` arrangement jumps are supported within the limits below. Selected document URIs and mix gains are restored automatically when the provider grants persistent read access; this is not full desktop project persistence.
- The Java mixer is isolated from Android for deterministic tests. Signalsmith pitch DSP runs on a bounded prefetch worker; its bridge is Android-only and performance must be measured on each target device. ASIO and Windows-specific packaging are not used.
- The app requests no microphone, network, broad storage or USB device permissions. USB multichannel functionality has not been implemented.
- UI resources include English, Brazilian Portuguese and Spanish.

## License

The project's own source remains AGPL-3.0-only; see `../LICENSE` and `../THIRD_PARTY_NOTICES.md`. The Gradle wrapper is from the official Gradle 9.1.0 distribution/source and carries its upstream notices. Android tooling is obtained separately under its own terms; SDK license acceptance must be completed by the developer. This prototype does not bundle the desktop JUCE/ASIO SDK or personal audio.

## Device Pad regression

Build assembleDebugAndroidTest, install both debug APKs and run:

    adb shell am instrument -r -w com.onebytelabs.zlplay.dev.test/com.onebytelabs.zlplay.PadSmokeTest

The runner clicks the real Pad button, verifies standalone PCM output and frame delivery to AudioTrack, tests zero/restored Pad level and fade-out, and closes its test activity. It does not record the microphone or certify physical audibility, latency or the external audio route. AudioSession exposes stream RMS and written-frame counters for this test; these are diagnostics, not mixer meters.

## WAV import (0.2.0-dev)

Import WAV opens the system document picker with multiple selection. Supported: little-endian RIFF/WAVE PCM integer 8/16/24/32-bit and IEEE float32, mono/stereo, including supported WAVEFORMATEXTENSIBLE subtypes. Source rate conversion currently uses linear interpolation to the 48 kHz internal mixer; it is not a finalized high-quality resampler. MP3 support uses Android decoding as described below. FLAC and RF64 are not supported yet.

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


## Desktop projects and MP3 (0.4.2-dev)

Open project asks for a folder grant through Android Storage Access Framework. Select the song folder containing its .zlplay or .rpp and audio, including any Media subfolders. Choose the exact project when several are present. No broad storage or network permission is requested. Folder scans are bounded to 16384 entries / 16 levels; manifests to 4 MiB. Read-only grants and project selection persist privately when supported by the provider. Mobile fader and BPM overrides persist separately from the desktop files.

Supported projects contain up to 32 audio tracks / 64 clips total, mono/stereo WAV or MP3, basic track/item gain and pan, offsets, positions and trims, and named regions (including overlaps). Names are retained exactly; repeated Verse 1 regions are not renumbered. REAPER folder gain/pan/mute/solo is flattened into initial per-track levels. Relocated desktop paths resolve only to a unique basename inside the granted folder; ambiguity/missing files and paths escaping the selected tree are rejected. Native REAPER CLICK sources prefer a matching track-name.wav or an unambiguous click/clique/claqueta/metronome audio file in the granted folder. If none is available or candidates are ambiguous, the app generates a sample-clock click in that track at the project tempo/meter and displays a translated notice. Ordinary missing media remains an import error.

The project header regions are selectable: while stopped/paused they seek immediately; during playback selection queues a jump at the current region end, and a second selection cancels that queue. While playing, the entire region card queues a jump and never seeks the playhead. While paused, dragging the playhead seeks; dragging elsewhere pans. Pinch changes the viewport zoom. .zlplay arrangement rules execute in saved order at the source region end; Stop resets runtime rules. Manual selection takes precedence and suppresses the automatic jump for the explicitly selected region. Current-region Loop blocks automatic arrangement jumps. Automatic targets are prefetched; a late source can still cause buffering. Before an automatic or queued manual jump, the Guide/Cue source previews the target cue using up to eight seconds preceding the target, bounded by the previous region start, as in desktop. It replaces the current guide rather than doubling it, and follows the Guide fader/pan. A separate buffered source protects the current reading position. Current-region Loop disables this preview. Late preview data falls back to the current guide while normal song playback continues.

REAPER plugins/instruments are never executed. MIDI/SECTION/non-audio sources, item pitch/rate changes, stretch markers, clips extending past the available source (including unrendered source loops), and execution-rate changes are rejected rather than played at the wrong pitch/speed. Canonical .zlplay global live-transpose settings are applied by the native pitch cache when the original key is known. Effects, fades, tempo maps, custom routing, and saved project click/pad settings are not reproduced and receive a compatibility notice. Native/project click selection and imported pads remain independent of the VS bus. Projects whose mix depends on unsupported processing should use rendered audio. This is not full REAPER project equivalence.

MP3 is decoded sequentially by JLayer on the import worker (MediaExtractor is used only for metadata) into private temporary 16-bit PCM WAV, then uses the same bounded streaming engine as WAV. This avoids per-frame MediaCodec IPC; PCM writes are buffered and free-space checks are bounded by byte intervals. Xing/Info/VBRI seek frames and the 529-sample decoder delay are handled to match the platform output timeline. No compressed file is decoded or read by the render callback. A repeated MP3 URI is decoded once per import, even if used in several clips. Temporary audio consumes storage (roughly sample rate × channels × 2 bytes per second) until the project closes; insufficient space is reported without replacing the old project. Original audio remains unchanged. Normal close removes temporary files; the next decoder initialization also removes only app-generated orphaned MP3 temporaries left by a prior process. MP3 decoding is performed again on reopen; no permanent duplicate library is created. Encoder delay/padding metadata is trimmed when exposed by Android; missing or inaccurate metadata cannot reconstruct lost sample alignment. WAV remains the reference format for sample-accurate project preparation. See [Android MediaFormat metadata](https://developer.android.com/reference/android/media/MediaFormat#KEY_ENCODER_DELAY).

Project fixture regression (unlocked physical phone, debug and test APK installed):

    adb shell am instrument -r -w -e mode project com.onebytelabs.zlplay.dev.test/com.onebytelabs.zlplay.PadSmokeTest

It tests synthetic .zlplay/.rpp opening, exact region names, Windows-path relocation and ambiguity/traversal rejection, ordered automatic/manual jumps, MP3 PCM reaching AudioTrack, unsupported-source rollback, and temporary-file cleanup. The MP3 fixture is an original two-second 440 Hz mono signal generated with LAME/lameenc 1.8.1 at 44.1 kHz / 64 kbps, Base64 stored only in test assets. No user music is included. This test does not validate every document provider, MP3 encoder, USB interface, tablet or an entire live session.

Click Track / Click / clique and Guia / Guide / Cue aliases are recognized case-insensitively. Mixer labels are localized as Click and Guide (Guia in Portuguese, Guía/Clic in Spanish); original source/project names are not rewritten. The first guide track supplies jump previews. A project can allocate up to 64 main clip buffers plus 64 independent guide-preview buffers (about 80 MiB nominal PCM cache at the extreme stereo limit, plus overhead), so preparation may need fewer clips on memory-constrained devices.

MP3 timing/performance regression (synthetic input only):

    adb shell am instrument -r -w -e mode benchmark com.onebytelabs.zlplay.dev.test/com.onebytelabs.zlplay.PadSmokeTest

It measures a 60-second decode and compares plain, Info and Xing variants against the retained platform-decoder reference for onset, duration and PCM error. Timings depend on the phone/source/provider; this is not a fixed import-time guarantee. JLayer is unmodified and distributed with its LGPL-v2-or-later notice, full license and original sources JAR in APK assets/licenses; see licenses/THIRD-PARTY-NOTICES.md. The platform decoder remains a comparison reference; normal imports use the sequential decoder.


## Setlists and horizontal song cards (0.5.0-dev)

Repertório / Setlist opens a folder grant for the main directory containing the desktop .zlset (or legacy .zlplaylibrary), song subfolders, projects and audio. Choose the manifest when multiple are present. The reader supports desktop type zlplay-library / version 1, at most 256 ordered song entries and a 4 MiB manifest. It preserves order (including repeated entries), selected source, song identity and key metadata; null source fields fall back to project/rpp. Entries absent from the order are appended in saved song-array order. Malformed versions, unknown/ambiguous order references and traversal in manifest paths are rejected. Missing/ambiguous selected project files remain unavailable entries rather than silently selecting another version.

Cards are compact and horizontally scrollable above the waveform on phone and tablet. The active card is highlighted; a separate next-song label shows the following entry or end of setlist. Selecting a card prepares that project with its regions/arrangement/guide preview; it starts stopped and requires Play. There is no automatic advancement at song end. Only the selected project is decoded, so an MP3-heavy song still needs preparation when switched to; this is not whole-setlist preloading or seamless live transition. Song switching follows the existing import behavior and silences independently enabled click/pad. Phone mixer stays separate; tablets keep their inline mixer. Tablet behavior has not been physically validated.

Read grants, manifest choice and successful current index are saved privately on the phone when the provider supports persistent access. Reopening restores that index. Existing gain/BPM overrides remain per project. The .zlset, desktop projects and originals are never rewritten. Failed song preparation leaves the previous audio/project and selected index intact. Legacy absolute audio paths can relocate only to an unambiguous basename within that song folder; exact relative paths can still reference a shared file elsewhere inside the explicitly granted main tree. Identical Guide.wav names in different songs do not collide.

Saved original/target keys and per-project mobile overrides are applied to native pitch-enabled tracks. Click always bypasses pitch; guide/percussion and the plain Loop track bypass it by default. The mixer exposes per-track pitch switches and an overall selection dialog. Enharmonic keys such as C# and Db are equal; the UI uses flat names. Original desktop manifests/audio are never rewritten.

Device regression (synthetic WAV/projects only, unlocked phone):

    adb shell am instrument -r -w -e mode setlist com.onebytelabs.zlplay.dev.test/com.onebytelabs.zlplay.PadSmokeTest

It checks schema/version/null fields, explicit RPP/ZLPlay origins, preserved order, scoped duplicate media names, key equivalence/unsupported transposition, card selection/next label, selection restoration, real PCM, no autoplay and failed-switch rollback. Debug-only test intents may skip automatic restoration so fixtures do not load or change the user's saved selection. Production builds ignore that test flag. Real Storage Access Framework grants and filesystem relocation should also be tested manually with a copied desktop repertoire folder.

### Unprepared setlist entries (0.5.1-dev)

Setlist songs require an edited .rpp or .zlplay source. Audio-only folders are not imported as replacement projects. An unavailable entry keeps its original order position, has a translated project-required label and is disabled. Initial/restored selection moves to the next available project (wrapping to the first available when necessary); the next-song label skips unavailable entries. A partial setlist displays a nonblocking count, and a setlist with no usable projects displays an informational status while preserving the previous audio session. Song Play is disabled while no setlist song is selected; standalone Click/Pad remain available. Missing projects no longer produce an error that incorrectly suggests the entire repertoire failed. The fixture regression includes a raw WAV-only folder and verifies that it is never loaded.

### Overlapping regions and compatibility acknowledgement (0.5.2-dev)

Valid positive region intervals can overlap, as allowed by REAPER. Imports retain all boundaries; no original file is rewritten. Region hit-testing and the manual jump source follow the later drawn matching region. Saved arrangement rules still use their explicit source/target boundaries. Zero-length RPP region closings are ignored as on desktop; invalid/non-finite intervals still fail with a specific found-but-invalid-project message.

The general compatibility notice is acknowledged once in private app preferences (versioned V1), rather than blocking every song switch. The Compatibility button opens it explicitly at any time. Hard import/processing errors remain errors. Tests verify initial display, acknowledgement, subsequent RPP switching without a popup, manual consultation and overlapping-region import. Test preferences are restored after fixture execution.

## Performance interface (0.6.0-dev)

The landscape performance UI uses a dark navy/violet palette, compact horizontal setlist cards above the waveform, a flexible-height waveform with region cards and thin boundary dividers, and a single Play/Pause button. Pad and Solo Click are independent push-button toggles; pausing/stopping the song does not turn them off. Tap estimates BPM from up to six taps, resetting after a two-second gap. BPM changes and fader levels retain the existing private autosave behavior.

Phones open the narrow-fader mixer in its own full-window panel. Tablets retain the inline mixer and may open the expanded panel; returning restores the inline layout. No instrument icons, input meters or simulated controls are added. Actual tablet hardware remains to be validated.

Setlist navigation provides prepared-project rows and separate setlist/project import actions. General settings persist screen-awake, session restoration and confirmation before an in-progress song change. Language opens Android per-app language settings on Android 13+ (system app settings on older versions). English, Brazilian Portuguese and Spanish are declared. The licenses screen provides the bundled full AGPL, JLayer LGPL and Inter SIL OFL texts. The unmodified Inter font is bundled to keep interface typography consistent across OEM font customizations.

Audio settings originally reported the single stereo route; 0.8.0 adds device/format selection and bus routing as described below. Sounds is reserved for importing pads and normal/accent click samples; independent Pad and Solo Click toggles remain on the performance screens. Sound import and pitch controls became functional in 0.7.0. Physical USB multichannel routing must be validated with the target interface. This update changes the presentation and navigation, not the render engine or project import semantics.

UI regression on an unlocked test device, with debug and instrumentation APKs installed:

    adb shell am instrument -r -w -e mode interface com.onebytelabs.zlplay.dev.test/com.onebytelabs.zlplay.PadSmokeTest

It verifies usable waveform height, Play/Pause, auxiliary-button independence, the three settings categories and repertoire navigation. Continue running the existing mixer, project and setlist modes to cover real fader gestures, project audio, arrangement jumps and source selection.

## Native performance styling (0.6.1-dev)

PerformanceButton draws vector transport icons and stateful violet/cyan skins while retaining native Button accessibility. FaderSeekBar retains native seeking and gesture ownership, with custom thin tracks and light handles; displayed levels use dB derived from the actual linear gains. The waveform renders source peaks clipped into colored region cards, with a time ruler and playhead. The song name/artist appears only in the horizontal song card on the main screen; an individual project has one card. Main header actions and the left settings navigation use vector symbols. Sound settings contain import controls and their current availability, with no performance toggles.

For local visual review using synthetic names/peaks/tracks only, run instrumentation mode `visual`. Six screenshots are written under the test app files/ui-review directory. These are device-rendered test fixtures, not production recordings or proof of USB/pitch/custom-sound functionality. Existing interface, mixer, project and setlist instrumentation modes cover behavior separately.

## Functional playback controls (0.7.0-dev)

- Loop captures the current region boundaries and suppresses the guide while repeating. A queued region jump takes precedence and releases that loop. Without a mapped project, the test/audio-only mode can loop the whole source. Solo Click while playing enables the region loop and fades VS/guide out over 1.5 seconds; it preserves project click or the native click selected in the Click strip menu. With the song stopped it starts the standalone native click. Play leaves standalone click mode.
- Mixer groups separate Master, Click/Guide/Pad and BUS VS with its ordinary stems. Their gains are actual independent engine controls. Per-track pitch switches preserve original gains and source positions.
- Playing-region taps queue boundary jumps across the entire card body. The playhead cannot be dragged during playback. Paused playhead dragging seeks; dragging elsewhere pans. Default phone zoom is 200% (tablet 125%), with pinch limits of 100–2400% and playback follow after a manual-view grace period.
- Pad key follows each prepared song target, with a saved per-song manual override. The note popup uses C, Db, D, Eb, E, F, Gb, G, Ab, A, Bb, B. Original key comes from the setlist/manifest or explicit RPP filename; an unknown key can be supplied from More actions. Play in persists the target per project.
- Sound imports use Android document grants only while copying. A pad import requires twelve unique note filenames and durations of 60 seconds ±0.5; WAV or MP3 is accepted. MP3 is normalized to private WAV on the worker. The validated complete set is published atomically and replaces only the previous owned pad directory. Each copy is capped at 128 MiB with storage headroom. Click samples are limited to five seconds; normal and accent have separate imports. Restore default click never removes pads. Sounds has no transport toggles.
- PitchBank maintains immutable 8192-frame caches, continuous Signalsmith processors for normal playback and independent jump hints, and compensates DSP latency using source lookahead. Decoding, pitch processing, allocations and file access occur on workers; render reads cached PCM. Returning to the original key preserves duration and source placement. Preparing a changed key may briefly report buffering; this is not a zero-latency or seamless-transition guarantee.
- Settings category changes replace content within the same window, retaining audio and avoiding a flash of the performance screen.

Run instrumentation mode `features` for native frequency/duration/cache-continuity checks, fourteen-stem steady-state throughput, streaming WAV resampling/trim/seek, synthetic twelve-pad imports and reopen, audible custom-click replacement, safe click reset, key following, touchscreen jump/paused seek/pinch, and settings window reuse. It uses an isolated cache/preferences namespace and never replaces user sounds. A short synthetic throughput check is not a long live-session or tablet certification. Continue using the `interface`, `mixer`, `project`, `setlist` and `wav` modes for their respective regressions. Native library ABIs are arm64-v8a, armeabi-v7a and x86_64.


## Output and mixer controls (0.8.0-dev)

The main screen removes the duplicate next-song row and moves the larger elapsed/total timer between Tap and Mixer. Time-grid ticks continue inside the region cards at the same positions, including zoom and pan. The header audio shortcut is removed. The sliders icon opens quick settings for original key, preserving Pad during prepared-song changes, and native-click accent; imports remain in Repertoire/Sounds. The autosave notice stays beneath the fixed settings sidebar. About has ZLPlay branding, the existing 1Byte Labs mark/site, and separate license cards. Localized descriptions supplement the unchanged original legal texts, explicitly labeled English.

Audio settings select a connected output device, playback sample rate and channel count from the capabilities exposed by Android. Apply stops playback, attempts to open that AudioTrack format, and saves only on success. Failure preserves the previous output configuration. A selected disconnected device does not silently become another device; unique product/type matching allows reconnecting an interface whose Android ID changed. Removal stops playback. Android may resample or downmix downstream, so successful AudioTrack initialization does not certify physical DAC rate or independent USB channels.

The engine retains its 48 kHz project clock and pitch processors; non-48 kHz output uses continuous linear interpolation on the audio worker with preallocated buffers. This remains a prototype resampler, not a claim of bit-perfect or final mastering quality. The supported output selection is 44.1/48/88.2/96 kHz where exposed, and 1–16 channels where exposed. Unknown device capabilities offer stereo and probe the chosen format. USB access uses Android AudioTrack rather than a separate USB driver.

Master, Click, Guide, Pad and BUS VS can follow Master or be directed to mono channel indices and stereo pairs. Direct bus routes bypass the Master fader/mute; summed routes pass through Master. The number of selectable destinations comes from the applied output channel count. Engine tests verify channel isolation and eight-channel PCM using synthetic sources; only stereo 44.1/48 kHz was physically exercised on the connected phone. Additional physical USB channels require an interface test before stage use.

Mixer strips add pan, independent mute/solo and post-fader output peak meters, with slightly wider strips. Double-tapping a fader sets exactly unity gain (0 dB). Original project mute/solo flags remain editable rather than being baked into a zero source gain. Pan, mute and solo overrides are stored privately per project; original project files are unchanged. Guide/Click aliases remain grouped in their special strips. Meter displays are output signals, not microphone inputs.

Instrumentation mode `routing` opens real stereo AudioTracks at 44.1 and 48 kHz with the activity in the foreground, checks audible PCM counters, source pan and double-tap unity reset. The JVM `RoutingTest` checks channel separation, direct-output Master bypass, meters, mute/solo, pan and eight-channel PCM. Existing interface/features/mixer/project/setlist/wav regressions cover the remaining transport and import paths. Device captures in mode `visual` now also include quick settings and About; all media/names are synthetic.


## Repertoire library and musical timeline (0.9.0-dev)

The header repertoire name is a selector for previously imported repertoires. Importing still lives only in the Repertoire page, alongside opening a project and ordering songs. Successfully parsed imports with persistent document grants are catalogued privately by tree URI and manifest path; importing the same pair updates its entry. Original manifests/audio are not copied or rewritten. A revoked grant must be restored by importing its folder again.

Holding a repertoire row starts an in-window drag gesture. An insertion line shows before/after placement, with continuous edge autoscroll. Reordering preserves the loaded song object, updates its selected index and saves an ID-based local order. New source songs append after saved entries. The order is local to this Android installation, not an edit of the desktop .zlset.

Song cards remove numbering, retain title above artist and use compact square covers (56 dp phone cards, 68 dp tablet cards). Missing covers show the ZLPlay mark. Cover actions on repertoire rows and individual projects import a private downsampled PNG from a valid image up to 8 MiB, with a maximum thumbnail dimension of 512 pixels; failed imports preserve the old cover. The app also discovers cover.jpg, cover.png, capa.jpg, capa.png or folder.jpg beside the song/project within the granted folder. Decoding runs off the UI/audio threads. No image upload or new storage-wide permission is added.

The musical ruler and interior card lines use quarter-note BPM plus the project numerator/denominator, anchored at timeline zero. A 4/4 project remains 4/4; the 6/8 fixture verifies a different meter rather than overriding imports. Low zoom omits lines at whole-measure multiples to keep the grid readable. Playback follow eases the viewport to bring the cursor near the left edge, while retaining the manual-pan grace period. Current playback is cyan, loop boundaries gold and pending jumps violet, with localized badges. Loop from Solo Click uses the same visual state. Paused cursor drag reconciles its final position on finger release.

Pan sliders are removed from the mixer; imported pan values remain respected. Grouped audio settings list individual tracks, which default to their VS/Guide/Click bus. Explicit track destinations route directly and bypass VS/Master, while preserving source gain and track mute/solo. The native/project click choice moves to a quick-settings switch, enabled only when a project click exists; the mixer labels the actual standalone-native or selected project source. Accent remains on the first beat of each measure. Pad gain now has the same unity midpoint and 200% range as the other faders.

Faders accept direct touch gestures only on the handle (13 dp horizontal hit allowance in the control local coordinates). Rail swipes are available to the mixer scroll and do not change gain. Double-tapping the handle resets unity. Strips are 65 dp wide. Settings/license labels remain localized in en/pt-BR/es; complete upstream license texts are unchanged.

Instrumentation mode `queue` uses a private fixture namespace for catalogue/order/cover persistence, invalid-cover rollback, current-song-safe reorder, long-press drag, musical meter, eased follow, equal Pad unity, and real mixer scrolling away from the handle. `routing` and `mixer` exercise handle-only gestures and output AudioTracks. `visual` additionally captures playing/loop/pending-region states with synthetic fixtures. Existing `features`, `project`, `setlist` and core regressions remain applicable. Physical tablet and external USB multichannel validation are still separate from phone tests.


## Pad duration correction (0.9.1-dev)

Pad imports accept any nonempty decoded audio up to and including 60 seconds. The former 59.5–60.5 second window is removed. Note naming and the complete twelve-note bank remain required. Device fixtures cover 1, 58 and 60 second pads, and reject 61 seconds without replacing the previous bank.


## Pad status and song Play (0.9.2-dev)

Sounds shows a larger pad card with twelve read-only note status tiles, based on the loaded validated pad bank. Tiles are not buttons or playback controls. Importing remains transactional; failed replacements leave previous indicators/bank intact. Starting or resuming the song through either UI Play button also enables the loaded pad at its selected/song-following note. Without an imported bank, song Play continues normally without generating a production synthetic pad or opening the picker. Pause retains existing independent-pad behavior. Interface instrumentation verifies audible imported-pad start and all twelve noninteractive status tiles.


## Signed APK distribution

The installable distribution is **ZLPlay Mobile**, application ID `com.onebytelabs.zlplay.mobile`, currently version `0.11.0` / versionCode `23`. It is a test-stage Mobile release with its own version, independent of the desktop release tag. Debug retains `com.onebytelabs.zlplay.dev` and its existing data; the two apps can coexist. They do not share private imported pads, document grants or preferences: import projects/sounds into Mobile after installing it.

Release builds require a permanent private signing key and fail before packaging if credentials are missing. They never fall back to the Android debug key. The certificate is self-signed RSA-4096/SHA256withRSA with 10,000-day validity. APK v2/v3 signatures are verified for the supported Android 8+ devices; v1 is not required at this minimum SDK. The public certificate SHA-256 identifies the signing key; it is distinct from the APK file checksum.

### Windows key setup and recovery

Run `pwsh -File scripts/Setup-AndroidSigning.ps1` locally. Enter and confirm the keystore password only in the local console. The script uses keytool environment-password input, keeps the private keystore outside the checkout in the current user's LocalAppData under `1ByteLabs/ZLPlayMobile/signing`, restricts local directory access and protects the local convenience password with Windows DPAPI. Never commit keystores, credentials or private backup archives.

The private backup ZIP is written under ignored `local-notes/signing-backup/`. It includes the password-protected PKCS12 key, its alias/application ID, the public certificate and restoration instructions. It contains no password and no machine-bound DPAPI secret. Save that backup privately and retain the original password: formatting the PC destroys access to its old DPAPI-protected copy. To restore on a new Windows setup, extract the backup and run `pwsh -File scripts/Setup-AndroidSigning.ps1 -KeystoreToRestore "PATH/zlplay-mobile-release.p12"`, then enter the original password. Existing different keys are not overwritten.

Generate a signed APK with `pwsh -File scripts/Build-AndroidRelease.ps1` (`-Offline` only after SDK/Gradle/native dependencies are cached). Set JAVA_HOME/ANDROID_HOME if your tools are installed elsewhere. The script decrypts only into process memory, runs assembleRelease/lintRelease without a persistent daemon or configuration cache, verifies the permanent certificate and APK alignment, and writes the APK, SHA256SUMS-Android.txt and a public signature report to ignored `dist/android/`. Signing passwords are cleared from its process environment on exit. Keep generated Gradle build intermediates private as well.

On macOS/Linux, use the same recovered keystore and alias with the four process environment variables: ZLPLAY_ANDROID_KEYSTORE, ZLPLAY_ANDROID_ALIAS, ZLPLAY_ANDROID_STORE_PASSWORD, ZLPLAY_ANDROID_KEY_PASSWORD, then run the standard Gradle assembleRelease/lintRelease tasks. Obtain passwords interactively/through a private secret store; never put them in shell history or source files. The PowerShell/DPAPI helper is Windows-specific; the signing key and Gradle configuration are portable.

Only APK/checksum/public signature information may be published. Never upload the private signing backup. Before attaching Mobile to an existing desktop release, preserve its assets/notes/tag/Latest state and provide the corresponding public source revision and dependency notices. A source build or an older release tag is not proof of the exact sources used for a new APK.


## Windows remote mode

Mobile 0.10.0 adds a separate native-TLS remote mode for the updated Windows desktop. Local playback remains independent. See [connection, protocol and limits](../docs/CONTROLE-REMOTO.md). Remote mode does not open the Android audio engine. macOS support is not enabled in this iteration.

Remote pairing now uses a generated QR code and local camera scanning. No manual address/port/code or certificate dialog is shown. TLS is pinned from the QR. The remote timeline is compact and region-only; transport controls are larger. Camera frames stay in memory.

Mobile 0.11.0 integrates remote setup into Settings, corrects camera preview for both landscape rotations, confirms remote exit, adds the support action to About, and shows effective play-in keys on song cards. Remote transport follows the approved two-row layout with local cursor interpolation. Player rendering uses 128-frame mixer lock quanta within 512-frame AudioTrack writes, precomputed channel factors and nonblocking transport display reads. The 25-stem instrumentation fixture checks real streaming with concurrent UI controls; results do not certify every device/sample-rate/transposition workload.
