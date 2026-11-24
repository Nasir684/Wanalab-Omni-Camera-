# WanaLab God Eye v2.2 - proguard-rules.pro
# Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
# PRO Rs. 2,500 / Year
# GodEye - By WanaLab - v2.2 - ProGuard Rules
# Keep Capacitor + App - Your 3 lines - DO NOT DELETE
-keep class com.getcapacitor.** { *; }
-keep class com.wanalab.godeye.** { *; }
-dontwarn org.apache.**

# Capacitor - Bridge + Plugins
-keepclassmembers class * {
    @com.getcapacitor.annotation.CapacitorPlugin *;
}
-keep class com.getcapacitor.cordova.** { *; }
-keep class com.getcapacitor.community.** { *; }
-dontwarn com.getcapacitor.**

# MediaPipe - Face Mesh 468 Dots - v18
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-dontwarn com.google.mediapipe.**

# TensorFlow Lite - v18
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.**

# ML Kit - Face + Barcode
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX - Camera + Microscope
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Media3 ExoPlayer - IP Cam / RTSP / HLS
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room - Attendance / Bill / Stock DB
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.PrimaryKey <fields>;
}
-dontwarn androidx.room.**

# POI - Excel Export + iText - PDF Export
-keep class org.apache.poi.** { *; }
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**
-dontwarn org.apache.poi.**

# ZXing - Barcode / QR
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**

# Tesseract - OCR Urdu / Pashto / EN
-keep class com.googlecode.tesseract.android.** { *; }
-dontwarn com.googlecode.tesseract.**

# MQTT / WebSocket - IP Cam / Smart Home
-keep class org.eclipse.paho.** { *; }
-keep class org.java_websocket.** { *; }
-dontwarn org.eclipse.paho.**
-dontwarn org.java_websocket.**

# Gson / Kotlin
-keep class com.google.gson.** { *; }
-keep class kotlin.** { *; }
-dontwarn kotlin.**
