package com.example.attendancemanagementsystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream

class FaceDetectorHelper(context: Context) {

    private val detector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
        detector = FaceDetection.getClient(options)
    }

    fun detect(inputImage: InputImage, onResult: (List<Face>) -> Unit) {
        detector.process(inputImage)
            .addOnSuccessListener { faces -> onResult(faces) }
            .addOnFailureListener { exception ->
                Log.w("FaceDetectorHelper", "Face detection failed: ${exception.message}")
                onResult(emptyList())
            }
    }

    fun mediaImageToBitmap(mediaImage: Image, rotationDegrees: Int): Bitmap {
        val nv21Data = convertYuv420ToNv21(mediaImage)
        val yuvImage = YuvImage(nv21Data, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)

        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 100, outputStream)
        val jpegData = outputStream.toByteArray()

        var bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)

        if (rotationDegrees != 0) {
            val rotationMatrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotationMatrix, true)
        }

        return bitmap
    }

    private fun convertYuv420ToNv21(image: Image): ByteArray {
        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer

        val ySize = yPlane.remaining()
        val uSize = uPlane.remaining()
        val vSize = vPlane.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yPlane.get(nv21, 0, ySize)

        val uData = ByteArray(uSize)
        val vData = ByteArray(vSize)
        uPlane.get(uData)
        vPlane.get(vData)

        var position = ySize
        var index = 0
        while (index < uSize) {
            nv21[position++] = vData[index]
            nv21[position++] = uData[index]
            index++
        }

        return nv21
    }
}
