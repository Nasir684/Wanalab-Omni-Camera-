# ===== GOD EYE v1.6.7 - ProGuard Rules =====
# Keep your own code - You already have this
-keep class com.godeye.app.** { *; }
-keep class com.wanalab.godeye.** { *; }

# ===== Kotlin + Coroutines =====
-dontwarn kotlinx.**
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# ===== CameraX =====
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ===== ML Kit - Barcode + Face =====
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ===== ZXing - Barcode =====
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**

# ===== MediaPipe + TFLite - Face =====
-keep class com.google.mediapipe.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn org.tensorflow.**

# ===== Room - Face DB + Shop DB =====
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.**

# ===== MQTT + WebSocket =====
-keep class org.eclipse.paho.** { *; }
-keep class org.java_websocket.** { *; }
-dontwarn org.eclipse.paho.**
-dontwarn org.java_websocket.**

# ===== Media3 - ExoPlayer / RTSP / HLS =====
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ===== iText - PDF Export =====
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# ===== Apache POI - XLSX Export =====
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.commons.**

# ===== Tesseract - OCR =====
-keep class com.googlecode.tesseract.** { *; }
-keep class com.rmtheis.** { *; }
-dontwarn com.googlecode.tesseract.**

# ===== General - Keep Models / Data Classes =====
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes *Annotation*, Signature, EnclosingMethod, InnerClasses
-dontwarn java.lang.invoke.**
