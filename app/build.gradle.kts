plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ghostmago.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ghostmago.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 55
        versionName = "1.9.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    // ── Signing (Release) ──────────────────────────────────────────
    // Env vars (local or GitHub Actions):
    //   KEYSTORE_PATH     optional path to .jks (default: mishkat-release.jks at project root)
    //   KEYSTORE_PASSWORD required for release
    //   KEY_PASSWORD      optional (defaults to KEYSTORE_PASSWORD)
    //   KEY_ALIAS         optional (default: mrelf)
    // Keystore: RSA 4096, SHA256withRSA, CN=Mr Elf, alias=mrelf, file=mishkat-release.jks
    val keystorePathEnv = System.getenv("KEYSTORE_PATH")
    val keystorePasswordEnv = System.getenv("KEYSTORE_PASSWORD")
    val keyPasswordEnv = System.getenv("KEY_PASSWORD") ?: keystorePasswordEnv
    val keyAliasEnv = System.getenv("KEY_ALIAS") ?: "mrelf"
    val keystoreFileCandidate = when {
        !keystorePathEnv.isNullOrBlank() -> file(keystorePathEnv)
        file("${rootProject.projectDir}/mishkat-release.jks").exists() ->
            file("${rootProject.projectDir}/mishkat-release.jks")
        file("${projectDir}/mishkat-release.jks").exists() ->
            file("${projectDir}/mishkat-release.jks")
        else -> file("${rootProject.projectDir}/mishkat-release.jks")
    }
    val canSignRelease =
        !keystorePasswordEnv.isNullOrBlank() && keystoreFileCandidate.exists()

    signingConfigs {
        create("release") {
            if (canSignRelease) {
                storeFile = keystoreFileCandidate
                storePassword = keystorePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    val camerax = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
}
