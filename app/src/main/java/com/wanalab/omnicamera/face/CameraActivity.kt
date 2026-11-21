package com.godeye.app.face

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private var cameraProvider: ProcessCameraProvider? = null

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
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(previewView)

        val viewerBtn = Button(this).apply {
            text = "View Home / Workplace Cameras"
            setOnClickListener {
                startActivity(Intent(this@CameraActivity, HomeCameraViewerActivity::class.java))
            }
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(24, 0, 24, 40)
        }

        root.addView(viewerBtn, params)
        setContentView(root)

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
            provider.bindToLifecycle(this, selector, preview)
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

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
    }
}
