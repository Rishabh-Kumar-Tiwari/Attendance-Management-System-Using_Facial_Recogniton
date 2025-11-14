package com.example.attendancemanagementsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var boundingBox: RectF? = null

    fun setBoundingBox(
        faceRect: Rect,
        bitmapWidth: Int,
        bitmapHeight: Int,
        previewWidth: Int,
        previewHeight: Int,
        isFrontCamera: Boolean
    ) {
        val scaleX = previewWidth.toFloat() / bitmapWidth
        val scaleY = previewHeight.toFloat() / bitmapHeight

        boundingBox = RectF(
            faceRect.left * scaleX,
            faceRect.top * scaleY,
            faceRect.right * scaleX,
            faceRect.bottom * scaleY
        )
        invalidate()
    }

    fun clear() {
        boundingBox = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        boundingBox?.let { canvas.drawRect(it, paint) }
    }
}
