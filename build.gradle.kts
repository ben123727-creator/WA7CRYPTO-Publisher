plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wa7crypto.publisher"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.wa7crypto.publisher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
}
