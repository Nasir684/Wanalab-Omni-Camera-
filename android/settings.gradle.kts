// WanaLab God Eye v2.2 - settings.gradle.kts - COMPLETE - FIXED for Line 16
// Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
// PRO Rs. 2,500 / Year
// FIX: Changed FAIL_ON_PROJECT_REPOS to PREFER_SETTINGS - So build.gradle won't fail on repositories
// FIX: KSP plugin not found - pluginManagement + gradlePluginPortal + KSP 2.0.0-1.0.24
// FIX: Capacitor paths - Real location is ../node_modules/@capacitor/... NOT android/capacitor-android

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.5.2" apply false
        id("org.jetbrains.kotlin.android") version "2.0.0" apply false
        id("com.google.devtools.ksp") version "2.0.0-1.0.24" apply false
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
