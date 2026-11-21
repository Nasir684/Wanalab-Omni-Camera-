package com.godeye.app.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.room.*
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facemesh.FaceMesh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

// --- ROOM DB - Offline, Local Only ---

@Entity(tableName = "known_faces")
data class KnownFaceEntity(
    @PrimaryKey val personId: String,
    val name: String,
    val embedding: FloatArray,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnownFaceEntity) return false
        return personId == other.personId
    }
    override fun hashCode(): Int = personId.hashCode()
}

class Converters {
    @TypeConverter
    fun fromFloatArray(value: FloatArray): String {
        return value.joinToString(",")
    }
    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        if (value.isBlank()) return FloatArray(0)
        return value.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
    }
}

@Dao
interface FaceDao {
    @Query("SELECT * FROM known_faces")
    suspend fun getAll(): List<KnownFaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(face: KnownFaceEntity)

    @Query("DELETE FROM known_faces WHERE personId = :id")
    suspend fun delete(id: String)
}

@Database(entities = [KnownFaceEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class FaceDatabase : RoomDatabase() {
    abstract fun dao(): FaceDao
    companion object {
        @Volatile private var INSTANCE: FaceDatabase? = null
        fun get(context: Context): FaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FaceDatabase::class.java, "face_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- FACE RESULT ---

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

    companion object {
        private const val TAG = "FaceEngine"
    }

    private var faceDetector: FaceDetector? = null
    private var faceMesh: FaceMesh? = null
    private var recognizer: Interpreter? = null
    private val db = FaceDatabase.get(context)

    // FIX 1: 0.60 -> 0.75 - No false alerts in shop
    private val matchThreshold = 0.75f
    private val scope = CoroutineScope(Dispatchers.IO)

    // FIX 2: Cache - Don't query DB every frame
    @Volatile private var cachedFaces: List<KnownFaceEntity> = emptyList()

    fun init() {
        // Load cache once
        refreshCache()

        // 1. Face Detector - Try GPU, Fallback CPU
        try {
            val detectorOptions = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setDelegate(Delegate.GPU).setModelAssetPath("blaze_face_short_range.tflite").build())
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(0.5f)
                .build()
            faceDetector = FaceDetector.createFromOptions(context, detectorOptions)
            Log.d(TAG, "Detector: GPU OK")
        } catch (e: Exception) {
            Log.e(TAG, "Detector GPU failed, trying CPU: ${e.message}")
            try {
                val detectorOptions = FaceDetector.FaceDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setDelegate(Delegate.CPU).setModelAssetPath("blaze_face_short_range.tflite").build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setMinDetectionConfidence(0.5f)
                    .build()
                faceDetector = FaceDetector.createFromOptions(context, detectorOptions)
                Log.d(TAG, "Detector: CPU OK")
            } catch (e2: Exception) {
                Log.e(TAG, "Detector MISSING MODEL blaze_face_short_range.tflite in assets/: ${e2.message}")
            }
        }

        // 2. Face Mesh - 468 landmarks
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
            Log.d(TAG, "Mesh: GPU OK")
        } catch (e: Exception) {
            Log.e(TAG, "Mesh GPU failed, trying CPU: ${e.message}")
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
                Log.d(TAG, "Mesh: CPU OK")
            } catch (e2: Exception) {
                Log.e(TAG, "Mesh MISSING MODEL face_landmarker.task in assets/: ${e2.message}")
            }
        }

        // 3. MobileFaceNet - 112x112 -> 192 embedding
        try {
            val model = context.assets.open("mobilefacenet.tflite").readBytes()
            val buffer = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder())
            buffer.put(model)
            buffer.rewind()
            recognizer = Interpreter(buffer, Interpreter.Options().setNumThreads(4))
            Log.d(TAG, "Recognizer: OK")
        } catch (e: Exception) {
            Log.e(TAG, "Recognizer MISSING MODEL mobilefacenet.tflite in assets/: ${e.message}")
        }
    }

    fun refreshCache() {
        scope.launch {
            try {
                cachedFaces = db.dao().getAll()
                Log.d(TAG, "Cache refreshed: ${cachedFaces.size} faces")
            } catch (e: Exception) {
                Log.e(TAG, "Cache refresh failed: ${e.message}")
            }
        }
    }

    suspend fun processFrame(cameraId: String, bitmap: Bitmap): List<FaceResult> = withContext(Dispatchers.Default) {
        val detector = faceDetector ?: return@withContext emptyList()
        val mpImage = BitmapImageBuilder(bitmap).build()
        try {
            val detectionResult = detector.detect(mpImage)
            val meshResult = faceMesh?.detect(mpImage)

            val results = mutableListOf<FaceResult>()
            // FIX 2: Use cache, not DB query every frame
            val knownFaces = cachedFaces

            for (i in detectionResult.detections().indices) {
                val detection = detectionResult.detections()[i]
                val box = detection.boundingBox()
                val rect = RectF(box.left, box.top, box.right, box.bottom)

                val faceCrop = cropAndResize(bitmap, rect, 112, 112) ?: continue
                val embedding = getEmbedding(faceCrop)
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
            mpImage.close()
        }
    }

    // FIX 3: Enroll with 1 photo - Kept for backward compat, but use enrollPerson3() below
    suspend fun enrollPerson(name: String, personId: String, bitmap: Bitmap): Boolean = withContext(Dispatchers.Default) {
        val results = processFrame("enroll", bitmap)
        if (results.isEmpty()) return@withContext false
        val embedding = results[0].embedding
        db.dao().insert(KnownFaceEntity(personId = personId, name = name, embedding = embedding, createdAt = System.currentTimeMillis()))
        refreshCache()
        return@withContext true
    }

    // FIX 3: NEW - Enroll with 3 photos - Front, Left, Right - Average = Works in night / shop light
    // Call from FaceEnrollActivity: enrollPerson3(name, id, listOf(frontBitmap, leftBitmap, rightBitmap))
    suspend fun enrollPerson3(name: String, personId: String, bitmaps: List<Bitmap>): Boolean = withContext(Dispatchers.Default) {
        if (bitmaps.isEmpty()) return@withContext false
        val embeddings = mutableListOf<FloatArray>()
        for (bmp in bitmaps) {
            val results = processFrame("enroll", bmp)
            if (results.isNotEmpty()) {
                embeddings.add(results[0].embedding)
            }
        }
        if (embeddings.isEmpty()) return@withContext false
        // Average all embeddings, then normalize
        val size = embeddings[0].size
        val avg = FloatArray(size)
        for (emb in embeddings) {
            for (i in 0 until size) avg[i] += emb[i]
        }
        for (i in 0 until size) avg[i] /= embeddings.size
        val normalized = normalize(avg)
        db.dao().insert(KnownFaceEntity(personId = personId, name = name, embedding = normalized, createdAt = System.currentTimeMillis()))
        refreshCache()
        Log.d(TAG, "Enrolled $name with ${embeddings.size} photos, avg embedding saved")
        return@withContext true
    }

    suspend fun deletePerson(personId: String) = withContext(Dispatchers.IO) {
        db.dao().delete(personId)
        refreshCache()
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
            // FIX: Recycle cropped to avoid memory leak
            if (cropped != scaled) cropped.recycle()
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
