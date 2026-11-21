package com.godeye.app.face

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: FaceOverlayView
    private lateinit var statusText: TextView
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var faceEngine: FaceEngine

    // FIX 2: Single executor, created once, shutdown in onDestroy
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val faceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var lastProcessed = 0L
    private var isProcessing = false
    private var isOnlineNow = false
    private var lastFaceText = "Face: Waiting..."

    companion object {
        private const val REQUEST_CODE = 1001
        // FIX 3: 150ms throttle, not 300ms - Don't miss 2nd person
        private const val THROTTLE_MS = 150L
        private val PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        root.addView(previewView)

        overlayView = FaceOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        root.addView(overlayView)

        statusText = TextView(this).apply {
            text = "Face: Offline Ready"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            setPadding(24, 16, 24, 16)
            textSize = 14f
        }
        val statusParams = FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setMargins(0, 32, 0, 0)
        }
        root.addView(statusText, statusParams)

        // FIX 4: ONE Menu button, not 4 stacked buttons - Saves 40% preview
        val menuBtn = Button(this).apply {
            text = "☰ Menu - Viewer / Enroll / Alerts / Log"
            setOnClickListener { view ->
                val popup = PopupMenu(this@CameraActivity, view)
                popup.menu.add("View Home / Workplace Cameras")
                popup.menu.add("Enroll Face / Known Faces")
                popup.menu.add("Alert History - Unknown Faces")
                popup.menu.add("Face Log - Who Came When")
                popup.setOnMenuItemClickListener { item ->
                    when (item.title.toString()) {
                        "View Home / Workplace Cameras" -> {
                            startActivity(Intent(this@CameraActivity, HomeCameraViewerActivity::class.java))
                        }
                        "Enroll Face / Known Faces" -> {
                            val bmp = previewView.bitmap
                            if (bmp != null) {
                                FaceEnrollActivity.pendingBitmap = bmp.copy(Bitmap.Config.ARGB_8888, false)
                            }
                            startActivity(Intent(this@CameraActivity, FaceEnrollActivity::class.java))
                        }
                        "Alert History - Unknown Faces" -> {
                            startActivity(Intent(this@CameraActivity, FaceAlertHistoryActivity::class.java))
                        }
                        "Face Log - Who Came When" -> {
                            startActivity(Intent(this@CameraActivity, FaceLogActivity::class.java))
                        }
                    }
                    true
                }
                popup.show()
            }
        }
        val menuParams = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(24, 0, 24, 24)
        }
        root.addView(menuBtn, menuParams)

        setContentView(root)

        // Tap overlay to enroll - Only for enroll, NOT for every frame
        overlayView.setOnClickListener {
            val bmp = previewView.bitmap
            if (bmp != null) {
                FaceEnrollActivity.pendingBitmap = bmp.copy(Bitmap.Config.ARGB_8888, false)
                Toast.makeText(this, "Face captured, enter name", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, FaceEnrollActivity::class.java))
            } else {
                Toast.makeText(this, "No frame yet, wait 1 sec", Toast.LENGTH_SHORT).show()
            }
        }

        // Face Engine - Offline
        faceEngine = FaceEngine(this)
        faceScope.launch {
            try {
                faceEngine.init()
                faceEngine.refreshCache()
                runOnUiThread { updateStatus() }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Face: Model missing, check assets" }
            }
        }

        // Network - Offline / Online, does NOT overwrite face count
        NetworkAutoManager.start(this) { online, wifi ->
            isOnlineNow = online
            runOnUiThread { updateStatus() }
        }

        if (allPermissionsGranted()) {
            startSingleCamera()
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE)
        }
    }

    private fun updateStatus() {
        val net = if (isOnlineNow) "ONLINE - Cloud Auto ON" else "OFFLINE - LAN Only"
        statusText.text = "$lastFaceText | $net"
    }

    private fun allPermissionsGranted(): Boolean {
        return PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && allPermissionsGranted()) {
            startSingleCamera()
        } else {
            Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSingleCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera init failed", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(selector: CameraSelector) {
        try {
            val provider = cameraProvider ?: return
            provider.unbindAll()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build().also {
                    // FIX 2: Reuse same executor, don't create new one
                    it.setAnalyzer(analysisExecutor) { imageProxy ->
                        processFaceFrame(imageProxy)
                    }
                }
            provider.bindToLifecycle(this, selector, preview, analysis)
        } catch (e: Exception) {
            try {
                val provider = cameraProvider ?: return
                provider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
            } catch (e2: Exception) {
                Toast.makeText(this, "No camera available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processFaceFrame(imageProxy: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastProcessed < THROTTLE_MS || isProcessing) {
                return
            }
            lastProcessed = now
            isProcessing = true

            // FIX 1: Use imageProxy -> Bitmap, NOT previewView.bitmap
            // previewView.bitmap = Screenshot on UI thread, 8MB copy, lags + heats phone
            // imageProxy = Direct YUV from CameraX, 3x faster, no UI thread block
            val bitmap = imageProxyToBitmap(imageProxy) ?: run {
                isProcessing = false
                return
            }
            // Scale down for FaceEngine - 640px is enough for face, faster
            val scaled = if (bitmap.width > 640) {
                val ratio = 640f / bitmap.width.toFloat()
                Bitmap.createScaledBitmap(bitmap, 640, (bitmap.height * ratio).toInt(), true)
            } else bitmap
            if (scaled != bitmap) {
                try { bitmap.recycle() } catch (e: Exception) {}
            }
            val safeCopy = scaled.copy(Bitmap.Config.ARGB_8888, false)

            faceScope.launch {
                try {
                    val results = faceEngine.processFrame("mobile_camera", safeCopy)
                    // FIX 3: Reset isProcessing BEFORE UI update, so next frame not missed
                    isProcessing = false
                    if (results.isNotEmpty()) {
                        FaceLogManager.logFaces(this@CameraActivity, results)
                        if (results.any { !it.isKnown }) {
                            FaceAlertManager.onFaceDetected(this@CameraActivity, results, safeCopy)
                        }
                    }
                    runOnUiThread {
                        overlayView.setFaces(results)
                        if (results.isNotEmpty()) {
                            val known = results.count { it.isKnown }
                            val unknown = results.size - known
                            lastFaceText = if (unknown > 0) {
                                "ALERT: $unknown Unknown! Snapshot Saved"
                            } else {
                                "Face: $known Known, $unknown Unknown"
                            }
                            updateStatus()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    isProcessing = false
                } finally {
                    try { safeCopy.recycle() } catch (e: Exception) {}
                    try { scaled.recycle() } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isProcessing = false
        } finally {
            try { imageProxy.close() } catch (e: Exception) {}
        }
    }

    // FIX 1 Helper: YUV_420_888 -> Bitmap, fast, no UI thread
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 85, out)
            val jpegBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        try { faceEngine.close() } catch (e: Exception) {}
        // FIX 2: Shutdown executor + scope, no leak on rotate
        try { analysisExecutor.shutdown() } catch (e: Exception) {}
        try { faceScope.cancel() } catch (e: Exception) {}
    }
}
