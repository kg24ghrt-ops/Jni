// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    
    // THIS IS THE FIX: The Compose Compiler plugin MUST match your Kotlin version exactly
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false 
}