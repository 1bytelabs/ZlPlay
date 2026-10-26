plugins { id("com.android.application") }
android {
    namespace = "com.onebytelabs.zlplay"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.onebytelabs.zlplay.dev"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "com.onebytelabs.zlplay.PadSmokeTest"
        versionCode = 9
        versionName = "0.5.0-dev"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { release { isMinifyEnabled = false } }
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
dependencies { implementation("javazoom:jlayer:1.0.1"); jlayerSources("javazoom:jlayer:1.0.1:sources") }
val prepareDecoderLicenses by tasks.registering(Copy::class) {
    from(rootProject.file("licenses"))
    from(jlayerSources)
    into(layout.buildDirectory.dir("generated/decoderLicenseAssets/licenses"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/decoderLicenseAssets").get().asFile)
tasks.named("preBuild") { dependsOn(prepareDecoderLicenses) }
