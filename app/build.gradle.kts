import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

val vCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val avashKs = rootProject.file("avash.jks")

android {
    namespace = "com.jarvis.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = vCode
        versionName = "1.0.$vCode"
    }
    lint { abortOnError = false; checkReleaseBuilds = false }
    signingConfigs {
        create("avash") {
            if (avashKs.exists()) {
                storeFile = avashKs
                storePassword = "AvashStudio2026Key"
                keyAlias = "avash"
                keyPassword = "AvashStudio2026Key"
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false   // keeps llama.cpp JNI safe from shrinking
            signingConfig = signingConfigs.getByName(if (avashKs.exists()) "avash" else "debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val bom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

}
