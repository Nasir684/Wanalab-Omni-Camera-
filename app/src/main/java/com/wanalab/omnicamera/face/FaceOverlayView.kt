package com.godeye.app.face

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class FaceOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var faces: List<FaceResult> = emptyList()
    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        style = Paint.Style.FILL
    }
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#80000000")
        style = Paint.Style.FILL
    }

    fun setFaces(results: List<FaceResult>) {
        faces = results
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faces.isEmpty()) return

        val scaleX = width.toFloat() / 720f
        val scaleY = height.toFloat() / 1280f

        for (face in faces) {
            val box = RectF(
                face.box.left * scaleX,
                face.box.top * scaleY,
                face.box.right * scaleX,
                face.box.bottom * scaleY
            )
            boxPaint.color = if (face.isKnown) Color.GREEN else Color.YELLOW
            canvas.drawRect(box, boxPaint)

            val label = if (face.isKnown) {
                "${face.personName} ${String.format("%.0f%%", face.confidence * 100)}"
            } else {
                "Unknown ${String.format("%.0f%%", face.confidence * 100)}"
            }
            canvas.drawRect(box.left, box.bottom, box.left + textPaint.measureText(label) + 20, box.bottom + 60, bgPaint)
            canvas.drawText(label, box.left + 10, box.bottom + 45, textPaint)
        }
    }
}
