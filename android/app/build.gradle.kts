plugins { id("com.android.application") }
android {
    namespace = "com.onebytelabs.zlplay"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.onebytelabs.zlplay.dev"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "com.onebytelabs.zlplay.PadSmokeTest"
        versionCode = 5
        versionName = "0.3.1-dev"
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
