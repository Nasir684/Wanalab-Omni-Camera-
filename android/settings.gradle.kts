// WanaLab God Eye v2.2 - settings.gradle.kts
// Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
// PRO Rs. 2,500 / Year
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3" apply false
        id("com.android.library") version "8.7.3" apply false
        id("org.jetbrains.kotlin.android") version "2.0.21" apply false
        id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "app"
include(":app")
include(":capacitor-android")
project(":capacitor-android").projectDir = File("capacitor-android")
