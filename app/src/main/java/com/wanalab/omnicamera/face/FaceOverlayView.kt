package com.wanalab.godeye

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Locale

// WanaLab God Eye v2.2 - FaceOverlayView.kt
// Face Box Overlay - GREEN Known / YELLOW Unknown - Front Camera Mirror Fix
// Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
// PRO Rs. 2,500 / Year

class FaceOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val VERSION = "2.2"
        const val APP_NAME = "WanaLab God Eye"
        const val PRICING = "PRO Rs. 2,500 / Year"
        // v2.2 - Universal
        var credentialsShop: String = ""
        var credentialsOffice: String = ""
        var credentialsDepartment: String = ""
        var gpsTimestamp: Boolean = true
        var gpsShare: Boolean = true
        var antiFakeGps: Boolean = true
        var wanalabWatermark: Boolean = true
    }

    private var faces: List<FaceResult> = emptyList()
    private var previewWidth = 720
    private var previewHeight = 1280
    private var isFrontCamera = false

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#80000000")
        style = Paint.Style.FILL
    }

    fun setFaces(results: List<FaceResult>) {
        faces = results
        invalidate()
    }

    fun setPreviewSize(width: Int, height: Int, frontCamera: Boolean = false) {
        if (width > 0) previewWidth = width
        if (height > 0) previewHeight = height
        isFrontCamera = frontCamera
    }

    fun clear() {
        faces = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faces.isEmpty()) return
        if (width == 0 || height == 0) return

        // FIXED: Use real preview size, not hardcoded 720x1280
        val scaleX = width.toFloat() / previewWidth.toFloat()
        val scaleY = height.toFloat() / previewHeight.toFloat()

        for (face in faces) {
            var left = face.box.left * scaleX
            var right = face.box.right * scaleX
            // FIXED: Mirror box for front camera, otherwise box is on wrong side
            if (isFrontCamera) {
                val mirroredLeft = width - right
                val mirroredRight = width - left
                left = mirroredLeft
                right = mirroredRight
            }
            val box = RectF(
                left,
                face.box.top * scaleY,
                right,
                face.box.bottom * scaleY
            )
            boxPaint.color = if (face.isKnown) Color.GREEN else Color.YELLOW
            canvas.drawRect(box, boxPaint)

            val label = if (face.isKnown) {
                "${face.personName} ${String.format(Locale.US, "%.0f%%", face.confidence * 100)}"
            } else {
                "Unknown ${String.format(Locale.US, "%.0f%%", face.confidence * 100)}"
            }
            val labelWidth = textPaint.measureText(label) + 20
            val bgRight = (box.left + labelWidth).coerceAtMost(width.toFloat())
            canvas.drawRect(box.left, box.bottom, bgRight, box.bottom + 60, bgPaint)
            canvas.drawText(label, box.left + 10, box.bottom + 45, textPaint)
        }
    }
}
