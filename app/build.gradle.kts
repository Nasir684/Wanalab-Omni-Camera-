plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.godeye.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.godeye.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 167
        versionName = "1.6.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Coroutines - Needed for FaceEngine, FaceAlert, FaceLog
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle - Needed for CameraActivity
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // CameraX - Single Mobile Camera Only
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // Media3 - Home / Workplace Viewer, 3 Independent Players
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Offline + Online Protocols - All Free, All Inside dependencies
    implementation("androidx.media3:media3-exoplayer-rtsp:1.4.1")
    implementation("androidx.media3:media3-exoplayer-rtmp:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.java-websocket:Java-WebSocket:1.5.6")

    // ===== BARCODE - 4 ENGINES - WAS MISSING =====
    // Engine 1: ML Kit - Fastest, Native, Offline
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // Engine 2: ZXing - Most Accurate, All Formats
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Engine 3: CameraX ImageAnalysis - For Quagga fallback in WebView
    // Engine 4: Manual - No dependency, EditText

    // ===== FACE - Offline Only, MediaPipe + TFLite =====
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("com.google.mlkit:face-detection:16.1.7")

    // ===== OCR - Tesseract - For Doc Scan / Export =====
    implementation("com.rmtheis:tess-two:9.1.0")

    // ===== SHOP + EXPORT - PDF / XLSX / CSV =====
    implementation("com.itextpdf:itext7-core:8.0.5")
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // ===== STORAGE - Room + DataStore =====
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ===== WORK - For MQTT / Sync Background =====
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
