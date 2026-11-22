package com.wanalab.godeye

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facemesh.FaceMesh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

// --- FACE RESULT ---
// NOTE v2.2: KnownFaceEntity / FaceDatabase / FaceDao / Converters are in FaceDatabase.kt ONLY - Do NOT redefine here

data class FaceResult(
    val cameraId: String,
    val personName: String?,
    val personId: String?,
    val confidence: Float,
    val box: RectF,
    val embedding: FloatArray,
    val landmarks: List<Float>,
    val isKnown: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class FaceEngine(private val context: Context) {

    private var faceDetector: FaceDetector? = null
    private var faceMesh: FaceMesh? = null
    private var recognizer: Interpreter? = null
    private val db = FaceDatabase.get(context)

    private val matchThreshold = 0.75f

    @Volatile
    private var cachedFaces: List<KnownFaceEntity> = emptyList()

    companion object {
        private const val TAG = "FaceEngine"
    }

    suspend fun refreshCache() = withContext(Dispatchers.IO) {
        try {
            cachedFaces = db.dao().getAll()
            Log.d(TAG, "Cache refreshed, known faces=${cachedFaces.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Cache refresh failed: ${e.message}")
        }
    }

    fun getCachedCount(): Int = cachedFaces.size

    fun init() {
        try {
            val detectorOptions = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setDelegate(Delegate.GPU).setModelAssetPath("blaze_face_short_range.tflite").build())
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(0.5f)
                .build()
            faceDetector = FaceDetector.createFromOptions(context, detectorOptions)
            Log.d(TAG, "Detector GPU OK")
        } catch (e: Exception) {
            Log.e(TAG, "Detector GPU failed, trying CPU: ${e.message}")
            try {
                val detectorOptions = FaceDetector.FaceDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setDelegate(Delegate.CPU).setModelAssetPath("blaze_face_short_range.tflite").build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setMinDetectionConfidence(0.5f)
                    .build()
                faceDetector = FaceDetector.createFromOptions(context, detectorOptions)
                Log.d(TAG, "Detector CPU OK")
            } catch (e2: Exception) {
                Log.e(TAG, "Detector missing - Check assets/blaze_face_short_range.tflite : ${e2.message}")
            }
        }

        try {
            val meshOptions = FaceMesh.FaceMeshOptions.builder()
                .setBaseOptions(BaseOptions.builder().setDelegate(Delegate.GPU).setModelAssetPath("face_landmarker.task").build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(5)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setOutputFaceBlendshapes(false)
                .setOutputFacialTransformationMatrixes(false)
                .build()
            faceMesh = FaceMesh.createFromOptions(context, meshOptions)
            Log.d(TAG, "FaceMesh GPU OK")
        } catch (e: Exception) {
            Log.e(TAG, "FaceMesh GPU failed, trying CPU: ${e.message}")
            try {
                val meshOptions = FaceMesh.FaceMeshOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setDelegate(Delegate.CPU).setModelAssetPath("face_landmarker.task").build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumFaces(5)
                    .setMinFaceDetectionConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setOutputFaceBlendshapes(false)
                    .setOutputFacialTransformationMatrixes(false)
                    .build()
                faceMesh = FaceMesh.createFromOptions(context, meshOptions)
            } catch (e2: Exception) {
                Log.e(TAG, "FaceMesh missing - Overlay will work without landmarks: ${e2.message}")
                faceMesh = null
            }
        }

        try {
            val model = context.assets.open("mobilefacenet.tflite").readBytes()
            val buffer = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder())
            buffer.put(model)
            buffer.rewind()
            recognizer = Interpreter(buffer, Interpreter.Options().setNumThreads(4))
            Log.d(TAG, "MobileFaceNet OK")
        } catch (e: Exception) {
            Log.e(TAG, "MobileFaceNet missing - Check assets/mobilefacenet.tflite : ${e.message}")
            recognizer = null
        }
    }

    suspend fun processFrame(cameraId: String, bitmap: Bitmap): List<FaceResult> = withContext(Dispatchers.Default) {
        val detector = faceDetector ?: return@withContext emptyList()
        val mpImage = BitmapImageBuilder(bitmap).build()
        try {
            val detectionResult = detector.detect(mpImage)
            val meshResult = if (detectionResult.detections().isNotEmpty()) {
                try { faceMesh?.detect(mpImage) } catch (e: Exception) { null }
            } else null

            val results = mutableListOf<FaceResult>()
            val knownFaces = cachedFaces

            for (i in detectionResult.detections().indices) {
                val detection = detectionResult.detections()[i]
                val box = detection.boundingBox()
                val rect = RectF(box.left, box.top, box.right, box.bottom)

                val faceCrop = cropAndResize(bitmap, rect, 112, 112) ?: continue
                val embedding = getEmbedding(faceCrop)
                try { if (faceCrop != bitmap) faceCrop.recycle() } catch (e: Exception) {}
                if (embedding.isEmpty()) continue

                val landmarks = mutableListOf<Float>()
                if (meshResult != null && i < meshResult.faceLandmarks().size) {
                    val lm = meshResult.faceLandmarks()[i]
                    for (point in lm.landmarks()) {
                        landmarks.add(point.x()); landmarks.add(point.y()); landmarks.add(point.z())
                    }
                }

                var bestName: String? = null
                var bestId: String? = null
                var bestScore = 0f
                for (known in knownFaces) {
                    if (known.embedding.size != embedding.size) continue
                    val score = cosineSimilarity(embedding, known.embedding)
                    if (score > bestScore) {
                        bestScore = score
                        bestName = known.name
                        bestId = known.personId
                    }
                }

                val isKnown = bestScore >= matchThreshold
                val detScore = if (detection.categories().isNotEmpty()) detection.categories()[0].score() else 0.5f

                results.add(
                    FaceResult(
                        cameraId = cameraId,
                        personName = if (isKnown) bestName else null,
                        personId = if (isKnown) bestId else null,
                        confidence = if (isKnown) bestScore else detScore,
                        box = rect,
                        embedding = embedding,
                        landmarks = landmarks,
                        isKnown = isKnown
                    )
                )
            }
            return@withContext results
        } catch (e: Exception) {
            Log.e(TAG, "processFrame error: ${e.message}")
            return@withContext emptyList()
        } finally {
            try { mpImage.close() } catch (e: Exception) {}
        }
    }

    suspend fun enrollPerson(name: String, personId: String, bitmaps: List<Bitmap>): Boolean = withContext(Dispatchers.Default) {
        if (bitmaps.isEmpty()) return@withContext false
        val embeddings = mutableListOf<FloatArray>()
        for (bmp in bitmaps) {
            val results = processFrame("enroll", bmp)
            if (results.isNotEmpty() && results[0].embedding.isNotEmpty()) {
                embeddings.add(results[0].embedding)
            }
        }
        if (embeddings.isEmpty()) return@withContext false
        val avg = FloatArray(192) { 0f }
        for (emb in embeddings) {
            for (i in 0 until 192) {
                if (i < emb.size) avg[i] += emb[i]
            }
        }
        for (i in avg.indices) avg[i] /= embeddings.size.toFloat()
        val finalEmbedding = normalize(avg)
        // FIX v2.2: FaceDatabase.kt version has photoPath - pass null, keep signature compatible
        db.dao().insert(KnownFaceEntity(personId = personId, name = name, embedding = finalEmbedding, createdAt = System.currentTimeMillis(), photoPath = null))
        refreshCache()
        Log.d(TAG, "Enrolled $name with ${embeddings.size} photos")
        return@withContext true
    }

    suspend fun enrollPerson(name: String, personId: String, bitmap: Bitmap): Boolean {
        return enrollPerson(name, personId, listOf(bitmap))
    }

    private fun getEmbedding(faceCrop: Bitmap): FloatArray {
        val interpreter = recognizer ?: return FloatArray(0)
        val input = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(112 * 112)
        faceCrop.getPixels(pixels, 0, 112, 0, 0, 112, 112)
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 127.5f - 1f
            val g = ((pixel shr 8) and 0xFF) / 127.5f - 1f
            val b = (pixel and 0xFF) / 127.5f - 1f
            input.putFloat(r); input.putFloat(g); input.putFloat(b)
        }
        input.rewind()
        val output = Array(1) { FloatArray(192) }
        try {
            interpreter.run(input, output)
        } catch (e: Exception) {
            Log.e(TAG, "Recognizer run failed: ${e.message}")
            return FloatArray(0)
        }
        return normalize(output[0])
    }

    private fun normalize(vec: FloatArray): FloatArray {
        var sum = 0f
        for (v in vec) sum += v * v
        val norm = sqrt(sum)
        return if (norm > 0) vec.map { it / norm }.toFloatArray() else vec
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    private fun cropAndResize(bitmap: Bitmap, rect: RectF, w: Int, h: Int): Bitmap? {
        val left = (rect.left - rect.width() * 0.2f).coerceIn(0f, (bitmap.width - 1).toFloat()).toInt()
        val top = (rect.top - rect.height() * 0.2f).coerceIn(0f, (bitmap.height - 1).toFloat()).toInt()
        val right = (rect.right + rect.width() * 0.2f).coerceIn((left + 1).toFloat(), bitmap.width.toFloat()).toInt()
        val bottom = (rect.bottom + rect.height() * 0.2f).coerceIn((top + 1).toFloat(), bitmap.height.toFloat()).toInt()
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null
        return try {
            val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
            val scaled = Bitmap.createScaledBitmap(cropped, w, h, true)
            try { if (cropped != scaled) cropped.recycle() } catch (e: Exception) {}
            scaled
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        try { faceDetector?.close() } catch (e: Exception) {}
        try { faceMesh?.close() } catch (e: Exception) {}
        try { recognizer?.close() } catch (e: Exception) {}
        faceDetector = null
        faceMesh = null
        recognizer = null
    }
}
