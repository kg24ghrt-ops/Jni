plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    
    // THIS IS THE FIX: Apply the Compose plugin here instead of using the old composeOptions block
    id("org.jetbrains.kotlin.plugin.compose") 
}

android {
    namespace = "com.example.homecil"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.homecil"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --------------------------------------------------------------
        // OFFICIAL SHADER COMPILATION BLOCK
        // --------------------------------------------------------------
        shaders {
            glslcArgs += listOf("-c", "-g")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
                arguments += "-DANDROID_STL=c++_shared"
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
    }
    
    // NOTE: The old 'composeOptions' block has been completely removed. 
    // The new 'org.jetbrains.kotlin.plugin.compose' handles this automatically in Kotlin 2.0+

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Latest Stable Compose BOM (Aligned with Kotlin 2.0.21)
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core & Lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose UI & Material 3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Jetpack Ink API
    implementation("androidx.ink:ink-brush:1.0.0-alpha01")

    // Advanced Graphics & Paths
    implementation("androidx.graphics:graphics-path:1.1.0")

    // Math rendering (kept from your original file)
    implementation("ru.noties:jlatexmath-android:0.2.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}