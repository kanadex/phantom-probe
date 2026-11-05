plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "me.kanadex.phantomprobe"
    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "me.kanadex.phantomprobe"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.1.0"

        // The probe itself is only meaningful on arm64 phones, but an emulator image is the
        // cheapest way to watch the protocol, so all three widely used ABIs are built.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

        externalNativeBuild {
            cmake {
                // Children never return to the JVM, so the native layer needs neither libc++
                // nor exception unwinding. Fewer moving parts in the forked image.
                arguments += listOf("-DANDROID_STL=none")
                cppFlags += "-fno-exceptions"
            }
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    buildTypes {
        release {
            // Symbols for 100 lines of C cost nothing and make a ROM-specific bugreport readable.
            isMinifyEnabled = false
        }
    }

    packaging { jniLibs.keepDebugSymbols += "**/*.so" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
}
