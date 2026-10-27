# Android third-party notices

JLayer 1.0.1 (javazoom:jlayer:1.0.1), JavaZoom and its contributors, is used unmodified for MP3 decoding. The source headers license it under the GNU Library General Public License version 2 or, at your option, any later version. The complete license and original Maven sources JAR are packaged in APK assets/licenses. The ZLPlay source and Gradle dependency allow rebuilding/relinking the application with a modified library.

Original sources: https://repo.maven.apache.org/maven2/javazoom/jlayer/1.0.1/jlayer-1.0.1-sources.jar
## Inter typeface

Inter variable font (optical size and weight), unmodified. Copyright 2020 The Inter Project Authors (https://github.com/rsms/inter). Licensed separately under SIL Open Font License 1.1; full copyright and license in Inter-OFL-1.1.txt, also packaged in APK assets/licenses and accessible from About and licenses.

Upstream font: https://github.com/google/fonts/blob/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf
Upstream license: https://github.com/google/fonts/blob/main/ofl/inter/OFL.txt
Bundled font SHA-256: 29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031

## Native Android pitch processing

Signalsmith Stretch, copyright 2022 Geraint Luff / Signalsmith Audio Ltd., and Signalsmith Linear, copyright 2025 Signalsmith Audio, are used unmodified under MIT. Their sources and license notices are retained under native/vendor/ in the repository. Full notices are copied into APK assets/licenses as Signalsmith-Stretch-MIT.txt and Signalsmith-Linear-MIT.txt and are accessible from About and licenses.

The Android native library is compiled with Android NDK 27.2.12479018. LLVM / libc++ notices from that NDK toolchain are preserved in LLVM-NOTICE.txt and packaged in the APK; this does not mean that the compiler itself is distributed with the app. The project JNI bridge and Java playback/cache code remain AGPL-3.0-only.

## ZXing
ZXing core 3.5.3, Copyright ZXing authors, Apache License 2.0. Used unmodified to read QR codes locally. License: ZXing-Apache-2.0.txt. Sources: https://github.com/zxing/zxing/tree/zxing-3.5.3/core
