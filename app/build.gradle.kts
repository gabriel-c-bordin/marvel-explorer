import java.util.Properties

buildscript {
    repositories { google() }
    dependencies {
        if (rootProject.file("app/google-services.json").isFile) {
            classpath("com.google.gms:google-services:4.5.0")
        }
    }
}

plugins {
    id("com.android.application")
}

// Firebase can be wired in as soon as app/google-services.json is supplied.
if (rootProject.file("app/google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

val localSecrets = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.isFile) {
        secretsFile.inputStream().use { load(it) }
    }
}
val comicVineApiKey = localSecrets.getProperty("comicvine.apiKey", "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.marvelexplorer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.marvelexplorer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "COMICVINE_API_KEY", "\"$comicVineApiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Gemini Nano runs on-device where Android AICore supports it; the app keeps a data lookup fallback.
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    implementation("androidx.core:core:1.17.0")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
