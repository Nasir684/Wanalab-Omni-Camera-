// WanaLab God Eye v2.2 - settings.gradle.kts - COMMITTED - Capacitor 8
// Single source of truth: AGP 8.7.3 + Kotlin 2.0.21 + KSP 2.0.21-1.0.28
// CI does NOT rewrite this file

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3" apply false
        id("org.jetbrains.kotlin.android") version "2.0.21" apply false
        id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android"
include(":app")
include(":capacitor-android")
project(":capacitor-android").projectDir = File("../node_modules/@capacitor/android/capacitor")
include(":capacitor-app")
project(":capacitor-app").projectDir = File("../node_modules/@capacitor/app/android")
include(":capacitor-haptics")
project(":capacitor-haptics").projectDir = File("../node_modules/@capacitor/haptics/android")
include(":capacitor-keyboard")
project(":capacitor-keyboard").projectDir = File("../node_modules/@capacitor/keyboard/android")
include(":capacitor-status-bar")
project(":capacitor-status-bar").projectDir = File("../node_modules/@capacitor/status-bar/android")
