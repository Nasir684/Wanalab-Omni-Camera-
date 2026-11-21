package com.godeye.app.face

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.godeye.app.face.mesh.MeshManager
import kotlinx.coroutines.launch

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var meshManager: MeshManager
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentMode: String = "SINGLE"

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE = 1001
        private val PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        setContentView(previewView)

        meshManager = MeshManager(this)

        if (allPermissionsGranted()) {
            startCameraSystem()
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
            startCameraSystem()
        } else {
            Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCameraSystem() {
        startSingleCamera()

        lifecycleScope.launch {
            meshManager.startMesh(
                onPeerFound = { peerCount ->
                    if (peerCount >= 2 && currentMode == "SINGLE") {
                        switchToMeshMode()
                    }
                },
                onPeerLost = { peerCount ->
                    if (peerCount < 2 && currentMode == "MESH") {
                        switchToSingleMode()
                    }
                },
                onMessage = { topic, payload ->
                    Log.d(TAG, "Mesh msg: $topic -> $payload")
                }
            )
        }
    }

    private fun startSingleCamera() {
        currentMode = "SINGLE"
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchToMeshMode() {
        currentMode = "MESH"
        Log.d(TAG, "Switching to 3 Camera Mesh Mode")
        runOnUiThread {
            Toast.makeText(this, "3 Camera Mesh Active", Toast.LENGTH_SHORT).show()
        }
        meshManager.setRole(MeshManager.Role.MESH_NODE)
        meshManager.publishStatus("mesh_active")
    }

    private fun switchToSingleMode() {
        currentMode = "SINGLE"
        Log.d(TAG, "Mesh lost, fallback to Single Camera")
        runOnUiThread {
            Toast.makeText(this, "Back to Single Camera", Toast.LENGTH_SHORT).show()
        }
        meshManager.setRole(MeshManager.Role.SINGLE_NODE)
        bindCamera(CameraSelector.DEFAULT_BACK_CAMERA)
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
            Log.e(TAG, "Bind failed: ${e.message}")
            try {
                val provider = cameraProvider ?: return
                provider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
            } catch (e2: Exception) {
                Log.e(TAG, "Front camera also failed")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        meshManager.stopMesh()
        cameraProvider?.unbindAll()
    }
}
