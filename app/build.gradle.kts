plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.runestone.app"
    compileSdk = 35
    ndkVersion = "23.1.7779620"

    defaultConfig {
        applicationId = "com.runestone.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "0.7.3"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/main/java",
                "../native/mkxp-z-android/app/src/main/java"
            )
            res.srcDirs(
                "src/main/res",
                "../native/mkxp-z-android/app/src/main/res"
            )
            assets.srcDirs("src/main/assets", "../native/mkxp-z-android/app/src/main/assets")
        }
    }
    
    // Native build is optional - enable after running setup-native-build.sh
    // externalNativeBuild {
    //     ndkBuild {
    //         path = file("../native/mkxp-z-android/app/jni/Android.mk")
    //     }
    // }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.palette:palette:1.0.0")
    implementation("com.intuit.sdp:sdp-android:1.1.0")
}
