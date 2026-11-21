package com.godeye.app.face

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
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
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: FaceOverlayView
    private lateinit var statusText: TextView
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var faceEngine: FaceEngine

    private var lastProcessed = 0L
    private val faceScope = CoroutineScope(Dispatchers.Default)
    private var isProcessing = false

    companion object {
        private const val REQUEST_CODE = 1001
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

        val viewerBtn = Button(this).apply {
            text = "View Home / Workplace Cameras"
            setOnClickListener {
                startActivity(Intent(this@CameraActivity, HomeCameraViewerActivity::class.java))
            }
        }
        val params = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(24, 0, 24, 40)
        }
        root.addView(viewerBtn, params)
        setContentView(root)

        // Face Engine - Offline, init in background
        faceEngine = FaceEngine(this)
        faceScope.launch {
            try {
                faceEngine.init()
                runOnUiThread { statusText.text = "Face: Offline Ready - GPU/CPU" }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Face: Model missing, check assets" }
            }
        }

        // Network Auto - Offline / Online status
        NetworkAutoManager.start(this) { online, wifi ->
            runOnUiThread {
                statusText.text = if (online) {
                    "Face: Offline Ready | Network: ONLINE - Cloud Auto ON"
                } else {
                    "Face: Offline Ready | Network: OFFLINE - LAN Only"
                }
            }
        }

        if (allPermissionsGranted()) {
            startSingleCamera()
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE)
        }
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

            // Face Analysis - 1 frame per 300ms, to save battery, offline
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
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
            // Throttle - only 3 FPS for face, saves battery
            if (now - lastProcessed < 300 || isProcessing) {
                imageProxy.close()
                return
            }
            lastProcessed = now
            isProcessing = true

            val bitmap = imageProxy.toBitmap() // CameraX helper, API 24+ via ImageProxy
            // If toBitmap() not available on your CameraX version, use YUV converter below
            faceScope.launch {
                try {
                    val results = faceEngine.processFrame("mobile_camera", bitmap)
                    runOnUiThread {
                        overlayView.setFaces(results)
                        if (results.isNotEmpty()) {
                            val known = results.count { it.isKnown }
                            val unknown = results.size - known
                            statusText.text = "Face: $known Known, $unknown Unknown | Offline"
                        }
                    }
                    // Online only - send face event if internet
                    if (NetworkAutoManager.isOnline && results.isNotEmpty()) {
                        // MQTT / Cloud sync will auto-start here, offline it is skipped
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isProcessing = false
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        try { faceEngine.close() } catch (e: Exception) {}
    }
}
