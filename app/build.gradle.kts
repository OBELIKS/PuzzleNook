plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.puzzlenook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.puzzlenook"
        minSdk = 26
        // Stay on 35: with targetSdk 36 Android 16 ignores portrait-only on large screens.
        targetSdk = 35
        versionCode = 33
        versionName = "0.15.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // Offers the languages that have string resources in the Android 13+ per-app language list.
    androidResources {
        generateLocaleConfig = true
    }
    // One APK per CPU type instead of a 44 MB universal one (native code from ML Kit and
    // the Onyx SDK dominates the size). arm64 covers the S22 Ultra and Boox Note Air 5C;
    // x86_64 is for the emulator.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }
    packaging {
        // Several Onyx SDK modules each bundle the same C++ runtime.
        jniLibs.pickFirsts += "lib/*/libc++_shared.so"
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
    testOptions {
        unitTests.all {
            // Half the hardware threads: the generators have wall-clock budgets, and a fully
            // loaded CPU would starve them and change the puzzles they produce.
            it.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// APK names: PuzzleNook-<version>-<abi>[-debug].apk (release is unsigned here: "-unsigned";
// the signed copy drops it). arm64-v8a is shortened to arm64.
// VariantOutputImpl is internal AGP API (checked with AGP 8.13.2, root build.gradle.kts); if an
// AGP upgrade breaks it, rename the APKs in a task after assemble instead.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                ?.identifier?.replace("arm64-v8a", "arm64") ?: "universal"
            val suffix = when (variant.buildType) {
                "debug" -> "-debug"
                "release" -> "-unsigned"
                else -> "-${variant.buildType}"
            }
            (output as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName.set(
                output.versionName.map { "PuzzleNook-$it-$abi$suffix.apk" }
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Handwriting: Google ML Kit digital ink (primary recogniser)
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")

    // Onyx Boox low-latency pen input (only activated on Onyx devices)
    implementation("com.onyx.android.sdk:onyxsdk-pen:1.5.9")
    implementation("com.onyx.android.sdk:onyxsdk-device:1.3.7")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
