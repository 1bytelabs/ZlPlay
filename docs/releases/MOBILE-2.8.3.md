# ZLPlay Mobile 2.8.3

The Android app uses the same visible release number as Windows and macOS. Its Android versionCode is 27 and application ID is `com.onebytelabs.zlplay.mobile`. Each subsequent Android distribution must increase versionCode, including a platform-specific maintenance build. Release signing uses the permanent key; debug/test APKs are separate applications.

## Project and setlist editing

- Create projects from audio folders, edit title/original key/BPM/time signature, and save project copies through Android's document picker.
- Create and edit setlists, add songs, reorder entries, save the setlist and manage the local repertoire list.
- Region editing includes the desktop region preset list, pad changes, automatic jumps and guide clips. Built-in guides include Portuguese, English and Spanish, with male/female voice selection; external guide audio can also be imported.
- Track editing includes clip movement, trimming, splitting, duplication, deletion confirmation, clip gain and four-band EQ. Track mute, solo and level controls assist editing.
- Editor timelines share a BPM/time-signature grid, magnetic snapping, playhead seeking, pinch zoom, zoom buttons and fit-to-view. Grid lines are drawn beneath audio items.
- Mixer channels expose gain/EQ. Track organization keeps Click/Guide/Pad fixed above the VS bus and its user groups; musical tracks can be ordered and assigned to groups.
- Master, Click, Guide, Pad and VS output routes are device-wide. Musical track/group routes remain project settings. Special tracks are not repeated as ordinary route entries.

## Import and playback

Click and guide name classification follows the desktop rules, including case-insensitive separated names such as `Click_1`, `Guide_1`, `Track Click`, `Voz Guia`, metronome and regency aliases. Original names are preserved.

RPP imports produce an editable in-memory project without rewriting the RPP. Repeated cuts share compatible streaming sources; the project clip limit is 4096, with a separate bounded source-stream limit. REAPER-generated click sources prefer an unambiguous matching full-length WAV/MP3 click stem in the song folder. If none is usable, the native click is available; REAPER click generators/custom one-shot settings are not executed and are not reported as missing audio files. Other genuinely missing audio still requires recovery for complete editing.

Native click starts enabled when no saved choice exists; accent starts disabled. The user can change and save both. Rehearsal/live automation behavior and the remote-control entry remain available.

## Interface and validation

The responsive quick-adjustments screen groups editing, saving and playback. Project details use labeled fields; native selectors and confirmation buttons share the app's dark/purple styling. Tablet transport and mixer have more room and spacing.

Validation uses synthetic fixtures: the Node regression suite, Android-independent mixer/parser tests, Android instrumentation for editable RPP imports, shared-source cuts, group ordering, EQ and save/reopen round trips, and Android lint. Visual review covers the Android tablet emulator; prior development checks also used a physical Android phone. This does not certify every device, document provider, MIDI controller, USB interface or a full live session. Desktop audio drivers and REAPER plugins are not part of the Android app. Playback remains foreground-oriented.

Build and signing instructions: [Android README](../../android/README.md). Corresponding sources must be published with the APK's build revision; attaching the APK does not move the existing desktop release tag or replace Windows/macOS assets.
