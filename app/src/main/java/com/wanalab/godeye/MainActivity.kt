package com.wanalab.godeye

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingPermissionRequest: PermissionRequest? = null

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val cameraGranted =
                result[Manifest.permission.CAMERA] == true
            val microphoneGranted =
                result[Manifest.permission.RECORD_AUDIO] == true

            val resources = pendingPermissionRequest?.resources ?: return@registerForActivityResult
            if (cameraGranted || microphoneGranted) {
                pendingPermissionRequest?.grant(resources)
            } else {
                pendingPermissionRequest?.deny()
            }
            pendingPermissionRequest = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                url: String
            ): Boolean {
                view.loadUrl(url)
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val requiredPermissions = mutableListOf<String>()

                if (Manifest.permission.CAMERA in
                    request.resources.map {
                        when (it) {
                            "android.webkit.resource.VIDEO_CAPTURE" ->
                                Manifest.permission.CAMERA
                            "android.webkit.resource.AUDIO_CAPTURE" ->
                                Manifest.permission.RECORD_AUDIO
                            else -> it
                        }
                    }
                ) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requiredPermissions += Manifest.permission.CAMERA
                    }
                }

                if (request.resources.contains(
                        "android.webkit.resource.AUDIO_CAPTURE"
                    )
                ) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requiredPermissions += Manifest.permission.RECORD_AUDIO
                    }
                }

                if (requiredPermissions.isEmpty()) {
                    request.grant(request.resources)
                } else {
                    pendingPermissionRequest = request
                    permissionsLauncher.launch(requiredPermissions.toTypedArray())
                }
            }
        }

        webView.loadUrl("https://nasir684.github.io/Wanalab-Omni-Camera-/")
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.clearHistory()
        super.onDestroy()
    }
}
