plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.reps100.app"
    compileSdk = 36

    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }

    defaultConfig {
        applicationId = "com.reps100.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "11.0"
    }
}


dependencies {
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
