// WanaLab God Eye v2.2 - settings.gradle.kts
// Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
// PRO Rs. 2,500 / Year
// FIX: KSP plugin not found - Added pluginManagement + gradlePluginPortal + KSP version

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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android"
include(":app")
include(":capacitor-android")
project(":capacitor-android").projectDir = File("capacitor-android")
include(":capacitor-app")
project(":capacitor-app").projectDir = File("capacitor-android/capacitor-app")
include(":capacitor-haptics")
project(":capacitor-haptics").projectDir = File("capacitor-android/capacitor-haptics")
include(":capacitor-keyboard")
project(":capacitor-keyboard").projectDir = File("capacitor-android/capacitor-keyboard")
include(":capacitor-status-bar")
project(":capacitor-status-bar").projectDir = File("capacitor-android/capacitor-status-bar")
