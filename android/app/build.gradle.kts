plugins { id("com.android.application") }
val signingValues = listOf("ZLPLAY_ANDROID_KEYSTORE", "ZLPLAY_ANDROID_ALIAS", "ZLPLAY_ANDROID_STORE_PASSWORD", "ZLPLAY_ANDROID_KEY_PASSWORD").associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = signingValues.values.all { !it.isNullOrBlank() }
android {
    namespace = "com.onebytelabs.zlplay"
    compileSdk = 36
    ndkVersion = "27.2.12479018"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    defaultConfig {
        applicationId = "com.onebytelabs.zlplay"
        minSdk = 26
        manifestPlaceholders["appLabel"] = "@string/app_name"
        externalNativeBuild { cmake { arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON" } }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        targetSdk = 36
        testInstrumentationRunner = "com.onebytelabs.zlplay.PadSmokeTest"
        versionCode = 24
        versionName = "0.11.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    signingConfigs {
        if (hasReleaseSigning) create("distribution") {
            storeFile = file(signingValues.getValue("ZLPLAY_ANDROID_KEYSTORE")!!)
            storePassword = signingValues.getValue("ZLPLAY_ANDROID_STORE_PASSWORD")
            keyAlias = signingValues.getValue("ZLPLAY_ANDROID_ALIAS")
            keyPassword = signingValues.getValue("ZLPLAY_ANDROID_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".dev"; versionNameSuffix = "-dev" }
        release {
            applicationIdSuffix = ".mobile"
            isDebuggable = false
            isMinifyEnabled = false
            manifestPlaceholders["appLabel"] = "ZLPlay Mobile"
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("distribution")
        }
    }
}

val prepareLicenseAssets by tasks.registering(Copy::class) {
    from(rootProject.file("../LICENSE")) { rename { "AGPL-3.0.txt" } }
    into(layout.buildDirectory.dir("generated/licenseAssets/licenses"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/licenseAssets").get().asFile)
tasks.named("preBuild") { dependsOn(prepareLicenseAssets) }

val prepareAudioAssets by tasks.registering(Copy::class) {
    from(rootProject.file("../app/Meuclick.wav"))
    into(layout.buildDirectory.dir("generated/audioAssets"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/audioAssets").get().asFile)
tasks.named("preBuild") { dependsOn(prepareAudioAssets) }

val jlayerSources by configurations.creating { isCanBeConsumed = false; isTransitive = false }
dependencies { implementation("javazoom:jlayer:1.0.1"); implementation("com.google.zxing:core:3.5.3"); jlayerSources("javazoom:jlayer:1.0.1:sources") }
val prepareDecoderLicenses by tasks.registering(Copy::class) {
    from(rootProject.file("licenses"))
    from(jlayerSources)
    into(layout.buildDirectory.dir("generated/decoderLicenseAssets/licenses"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/decoderLicenseAssets").get().asFile)
tasks.named("preBuild") { dependsOn(prepareDecoderLicenses) }

val preparePitchLicenses by tasks.registering(Copy::class) {
    from(rootProject.file("../native/vendor/signalsmith-stretch-main/LICENSE.txt")) { rename { "Signalsmith-Stretch-MIT.txt" } }
    from(rootProject.file("../native/vendor/linear-main/LICENSE.txt")) { rename { "Signalsmith-Linear-MIT.txt" } }
    into(layout.buildDirectory.dir("generated/pitchLicenseAssets/licenses"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/pitchLicenseAssets").get().asFile)
tasks.named("preBuild") { dependsOn(preparePitchLicenses) }

// Never produce a distributable unsigned release by silently omitting credentials.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst { check(hasReleaseSigning) { "Release signing is required. Use scripts/Build-AndroidRelease.ps1 or provide the four ZLPLAY_ANDROID signing environment variables." } }
}
