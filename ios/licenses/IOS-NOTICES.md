# ZLPlay Mobile for iOS — components and corresponding sources

Own application code: AGPL-3.0-only, root LICENSE. This development target embeds the project's native engine; its corresponding sources are native/, ios/ and the pinned build inputs in ios/CMakeLists.txt. Preserve original license texts supplied with this app.

- JUCE 9.0.4: AGPLv3 path; full JUCE license included. Embedded third-party notices for its codecs, Unicode shaping, raster/SVG and compression libraries are included in JUCE-THIRD-PARTY.txt from the pinned JUCE sources (including optional libraries even when disabled on iOS).
- Signalsmith Stretch and Signalsmith Linear: MIT; original notices included.
- libebur128: MIT; original COPYING included. Its BSD sys/queue.h notice remains in the corresponding source file.
- Inter: SIL Open Font License 1.1, original notice included. Font bytes are reused unchanged from Android 4025815adc75812122cffcadc18369d7ae945a31, android/app/src/main/res/font/inter.ttf.
- Apple system frameworks: UIKit, Foundation, AVFoundation, AVKit, AudioToolbox/CoreAudio, Security, CoreGraphics/CoreText and CommonCrypto. These are supplied by iOS; the app does not bundle their implementations or Android's runtime.

The iOS QR scanner uses AVFoundation. ZXing and JLayer are Android dependencies and are not used or bundled here. Electron, node-qrcode, Whisper models and Windows/ASIO/NSIS are not part of this iOS target. Apple SDK tools are build prerequisites, not application assets. User songs, covers and imported Pads are not licensed or bundled by this project.
