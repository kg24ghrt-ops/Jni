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
            version = "3.28.3"
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
    // 1. THE FIX: Add the XML Material Library back for the themes.xml to compile
    implementation("com.google.android.material:material:1.12.0")

    // 2. Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // 3. Core & Lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // 4. Jetpack Compose UI & Material 3 (This is for your actual UI code)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // 5. Jetpack Ink API
    implementation("androidx.ink:ink-brush:1.0.0-alpha01")

    // 6. Advanced Graphics & Paths
    implementation("androidx.graphics:graphics-path:1.1.0")

    // 7. Math rendering
    implementation("ru.noties:jlatexmath-android:0.2.0")

    // 8. Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}