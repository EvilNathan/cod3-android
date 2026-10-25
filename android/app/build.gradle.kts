plugins {
    id("com.android.application")
}

val repoRoot = project.projectDir.parentFile.parentFile.absolutePath.replace("\\", "/")

android {
    namespace = "com.project1944.cod3"
    compileSdk = 35
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.project1944.cod3"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "0.1.0-m2a"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DREX_PROJECT_ROOT=$repoRoot",
                )
                cppFlags += "-std=c++23"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.30.5"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isJniDebuggable = true
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Private local build only. Replace with a real keystore before
            // distributing anything, and never ship game-derived code.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // The Vulkan Xenos plugin is dlopen'd from nativeLibraryDir, so the
            // native libraries must be extracted on install.
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildFeatures {
        buildConfig = false
        prefab = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
