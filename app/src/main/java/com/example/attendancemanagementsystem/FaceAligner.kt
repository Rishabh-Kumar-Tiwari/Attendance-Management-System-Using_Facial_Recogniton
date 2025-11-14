package com.example.attendancemanagementsystem

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import kotlin.math.max

object FaceAligner {

    fun alignFace(source: Bitmap, face: Face, outputSize: Int = 160): Bitmap {
        val boundingBox: Rect = face.boundingBox
        val maxDimension = max(boundingBox.width(), boundingBox.height())
        val margin = (0.4f * maxDimension).toInt()

        val expandedLeft = (boundingBox.left - margin).coerceAtLeast(0)
        val expandedTop = (boundingBox.top - margin).coerceAtLeast(0)
        val expandedRight = (boundingBox.right + margin).coerceAtMost(source.width)
        val expandedBottom = (boundingBox.bottom + margin).coerceAtMost(source.height)

        val expandedWidth = expandedRight - expandedLeft
        val expandedHeight = expandedBottom - expandedTop
        val squareSize = max(expandedWidth, expandedHeight)

        val centerX = expandedLeft + expandedWidth / 2
        val centerY = expandedTop + expandedHeight / 2

        var squareLeft = (centerX - squareSize / 2).coerceAtLeast(0)
        var squareTop = (centerY - squareSize / 2).coerceAtLeast(0)

        if (squareLeft + squareSize > source.width) {
            squareLeft = (source.width - squareSize).coerceAtLeast(0)
        }
        if (squareTop + squareSize > source.height) {
            squareTop = (source.height - squareSize).coerceAtLeast(0)
        }

        val croppedFace = Bitmap.createBitmap(source, squareLeft, squareTop, squareSize, squareSize)
        return Bitmap.createScaledBitmap(croppedFace, outputSize, outputSize, true)
    }
}
