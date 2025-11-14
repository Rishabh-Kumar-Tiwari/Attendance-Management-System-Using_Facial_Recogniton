package com.example.attendancemanagementsystem

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face

class CameraAnalyzer(
    private val detectorHelper: FaceDetectorHelper,
    private val onFaceDetected: (faceBitmap: Bitmap, faceBoundingBox: android.graphics.Rect, bitmapWidth: Int, bitmapHeight: Int) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    private var active = true

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (!active) {
            imageProxy.close()
            return
        }

        val mediaImage: Image? = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        detectorHelper.detect(inputImage) { faces ->
            try {
                if (faces.isNotEmpty()) {
                    val largestFace = selectLargestFace(faces)
                    val rotatedBitmap = detectorHelper.mediaImageToBitmap(mediaImage, rotation)
                    val alignedFace = FaceAligner.alignFace(rotatedBitmap, largestFace)
                    onFaceDetected(alignedFace, largestFace.boundingBox, rotatedBitmap.width, rotatedBitmap.height)
                }
            } catch (e: Exception) {
                Log.e("CameraAnalyzer", "Face detection error: ${e.message}", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun selectLargestFace(faces: List<Face>): Face {
        return faces.maxByOrNull { face ->
            face.boundingBox.width() * face.boundingBox.height()
        } ?: faces.first()
    }

    fun stop() {
        active = false
    }
}
