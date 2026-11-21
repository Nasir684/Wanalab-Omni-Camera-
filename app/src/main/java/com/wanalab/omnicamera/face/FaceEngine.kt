package com.wanalab.omnicamera.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facemesh.FaceMesh
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class FaceResult(
    val cameraId: String,
    val personName: String?, // null = unknown
    val personId: String?,
    val confidence: Float,
    val box: RectF,
    val embedding: FloatArray,
    val landmarks: List<Float>, // 468 x 3 = 1404 floats
    val isKnown: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class FaceEngine(private val context: Context) {

    private var faceDetector: FaceDetector? = null
    private var faceMesh: FaceMesh? = null
    private var recognizer: Interpreter? = null
    private val db = FaceDatabase.get(context)
    private val matchThreshold = 0.60f // cosine similarity

    fun init() {
        // 1. Face Detector - MediaPipe, offline, GPU
        val detectorOptions = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setDelegate(Delegate.GPU).setModelAssetPath("blaze_face_short_range.tflite").build())
            .setRunningMode(RunningMode.IMAGE)
            .setMinDetectionConfidence(0.5f)
            .build()
        faceDetector = FaceDetector.createFromOptions(context, detectorOptions)

        // 2. Face Mesh - 468 landmarks, offline
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

        // 3. MobileFaceNet - TFLite, offline, 112x112 -> 192 embedding
        val model = context.assets.open("mobilefacenet.tflite").readBytes()
        val buffer = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder())
        buffer.put(model)
        recognizer = Interpreter(buffer, Interpreter.Options().setNumThreads(4))
    }

    // Main call - pass Bitmap frame from camera
    suspend fun processFrame(cameraId: String, bitmap: Bitmap): List<FaceResult> = withContext(Dispatchers.Default) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val detectionResult = faceDetector?.detect(mpImage) ?: return@withContext emptyList()
        val meshResult = faceMesh?.detect(mpImage)

        val results = mutableListOf<FaceResult>()
        val knownFaces = db.dao().getAll() // local Room, offline

        for (i in detectionResult.detections().indices) {
            val detection = detectionResult.detections()[i]
            val box = detection.boundingBox()
            val rect = RectF(box.left, box.top, box.right, box.bottom)

            // Crop + align face for recognition
            val faceCrop = cropAndResize(bitmap, rect, 112, 112)

            // Embedding from MobileFaceNet
            val embedding = getEmbedding(faceCrop)

            // Landmarks - 468 points if available
            val landmarks = mutableListOf<Float>()
            if (meshResult != null && i < meshResult.faceLandmarks().size) {
                val lm = meshResult.faceLandmarks()[i]
                for (point in lm.landmarks()) {
                    landmarks.add(point.x())
                    landmarks.add(point.y())
                    landmarks.add(point.z())
                }
            }

            // Match against local DB - cosine similarity
            var bestName: String? = null
            var bestId: String? = null
            var bestScore = 0f
            for (known in knownFaces) {
                val score = cosineSimilarity(embedding, known.embedding)
                if (score > bestScore) {
                    bestScore = score
                    bestName = known.name
                    bestId = known.personId
                }
            }

            val isKnown = bestScore >= matchThreshold
            results.add(
                FaceResult(
                    cameraId = cameraId,
                    personName = if (isKnown) bestName else null,
                    personId = if (isKnown) bestId else null,
                    confidence = if (isKnown) bestScore else detection.categories()[0].score(),
                    box = rect,
                    embedding = embedding,
                    landmarks = landmarks,
                    isKnown = isKnown
                )
            )
        }
        return@withContext results
    }

    // Enroll new person - save to local DB, offline
    suspend fun enrollPerson(name: String, personId: String, bitmap: Bitmap): Boolean = withContext(Dispatchers.Default) {
        val results = processFrame("enroll", bitmap)
        if (results.isEmpty()) return@withContext false
        val embedding = results[0].embedding
        db.dao().insert(KnownFaceEntity(personId = personId, name = name, embedding = embedding, createdAt = System.currentTimeMillis()))
        return@withContext true
    }

    // MobileFaceNet inference - 112x112 RGB normalized to -1 to 1
    private fun getEmbedding(faceCrop: Bitmap): FloatArray {
        val input = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(112 * 112)
        faceCrop.getPixels(pixels, 0, 112, 0, 0, 112, 112)
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 127.5f - 1f
            val g = ((pixel shr 8) and 0xFF) / 127.5f - 1f
            val b = (pixel and 0xFF) / 127.5f - 1f
            input.putFloat(r); input.putFloat(g); input.putFloat(b)
        }
        val output = Array(1) { FloatArray(192) }
        recognizer?.run(input, output)
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
        return dot // already normalized, dot = cosine
    }

    private fun cropAndResize(bitmap: Bitmap, rect: RectF, w: Int, h: Int): Bitmap {
        // Expand box by 20 percent for forehead and chin
        val left = (rect.left - rect.width() * 0.2f).coerceAtLeast(0f).toInt()
        val top = (rect.top - rect.height() * 0.2f).coerceAtLeast(0f).toInt()
        val width = (rect.width() * 1.4f).coerceAtMost((bitmap.width - left).toFloat()).toInt()
        val height = (rect.height() * 1.4f).coerceAtMost((bitmap.height - top).toFloat()).toInt()
        val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
        return Bitmap.createScaledBitmap(cropped, w, h, true)
    }

    fun close() {
        faceDetector?.close()
        faceMesh?.close()
        recognizer?.close()
    }
}
