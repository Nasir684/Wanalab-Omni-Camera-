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
        id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
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

// Capacitor - MUST be ../node_modules because this file is inside android/
include(":capacitor-android")
project(":capacitor-android").projectDir = File("../node_modules/@capacitor/android/capacitor")
include(":capacitor-app")
project(":capacitor-app").projectDir = File("../node_modules/@capacitor/app/android/capacitor")
include(":capacitor-haptics")
project(":capacitor-haptics").projectDir = File("../node_modules/@capacitor/haptics/android/capacitor")
include(":capacitor-keyboard")
project(":capacitor-keyboard").projectDir = File("../node_modules/@capacitor/keyboard/android/capacitor")
include(":capacitor-status-bar")
project(":capacitor-status-bar").projectDir = File("../node_modules/@capacitor/status-bar/android/capacitor")
