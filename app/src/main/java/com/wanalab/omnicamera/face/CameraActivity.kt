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
    private var isOnlineNow = false
    private var lastFaceText = "Face: Waiting..."

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

        // Tap face to enroll - captures current frame
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

        // 1. Viewer - Bottom 40
        val viewerBtn = Button(this).apply {
            text = "View Home / Workplace Cameras"
            setOnClickListener {
                startActivity(Intent(this@CameraActivity, HomeCameraViewerActivity::class.java))
            }
        }
        val viewerParams = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(24, 0, 24, 40)
        }
        root.addView(viewerBtn, viewerParams)

        // 2. Enroll - Bottom 110
        val enrollBtn = Button(this).apply {
            text = "Enroll Face / Known Faces"
            setOnClickListener {
                val bmp = previewView.bitmap
                if (bmp != null) {
                    FaceEnrollActivity.pendingBitmap = bmp.copy(Bitmap.Config.ARGB_8888, false)
                }
                startActivity(Intent(this@CameraActivity, FaceEnrollActivity::class.java))
            }
        }
        val enrollParams = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(24, 0, 24, 110)
        }
        root.addView(enrollBtn, enrollParams)

        // 3. Alert History - Bottom 180
        val alertBtn = Button(this).apply {
            text = "Alert History - Unknown Faces"
            setOnClickListener {
                startActivity(Intent(this@CameraActivity, FaceAlertHistoryActivity::class.java))
            }
        }
        val alertParams = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(24, 0, 24, 180)
        }
        root.addView(alertBtn, alertParams)

        // 4. Face Log - Bottom 250
        val logBtn = Button(this).apply {
            text = "Face Log - Who Came When"
            setOnClickListener {
                startActivity(Intent(this@CameraActivity, FaceLogActivity::class.java))
            }
        }
        val logParams = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(24, 0, 24, 250)
        }
        root.addView(logBtn, logParams)

        setContentView(root)

        // Face Engine - Offline
        faceEngine = FaceEngine(this)
        faceScope.launch {
            try {
                faceEngine.init()
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
        // FIXED: Use previewView.bitmap, NOT imageProxy.toBitmap()
        // FIXED: Close only once, at end
        try {
            val now = System.currentTimeMillis()
            if (now - lastProcessed < 300 || isProcessing) {
                return
            }
            lastProcessed = now
            isProcessing = true

            val bitmap = previewView.bitmap ?: run {
                isProcessing = false
                return
            }
            // Copy for FaceEngine + Alert, so recycle does not crash alert
            val safeCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)

            faceScope.launch {
                try {
                    val results = faceEngine.processFrame("mobile_camera", safeCopy)
                    if (results.isNotEmpty()) {
                        // Face Log - Save who came when, offline
                        FaceLogManager.logFaces(this@CameraActivity, results)
                        // Auto Alert - Unknown only
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
                } finally {
                    isProcessing = false
                    try { safeCopy.recycle() } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isProcessing = false
        } finally {
            try { imageProxy.close() } catch (e: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        try { faceEngine.close() } catch (e: Exception) {}
    }
}
