plugins {
    id("com.android.application")
}

android {
    namespace = "com.remotenumpad"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.remotenumpad"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.3.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    useLibrary("android.test.runner")
    useLibrary("android.test.base")

    sourceSets["main"].assets.directories.add("../../LICENSES")

    signingConfigs {
        create("release") {
            val path = System.getenv("REMOTE_NUMPAD_STORE_FILE")
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = System.getenv("REMOTE_NUMPAD_STORE_PASS")
                keyAlias = System.getenv("REMOTE_NUMPAD_KEY_ALIAS")
                keyPassword = System.getenv("REMOTE_NUMPAD_KEY_PASS")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.matching { it.name == "validateSigningRelease" }.configureEach {
    doFirst {
        check(!System.getenv("REMOTE_NUMPAD_STORE_FILE").isNullOrBlank()) { "Release signing is required; see docs/release-signing.md." }
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    testImplementation("junit:junit:4.13.2")

}
