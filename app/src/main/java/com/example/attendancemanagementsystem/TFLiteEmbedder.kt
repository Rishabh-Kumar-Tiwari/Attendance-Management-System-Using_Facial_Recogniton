package com.example.attendancemanagementsystem

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class TFLiteEmbedder private constructor(
    private val interpreter: Interpreter,
    private val inputShape: IntArray,
    private val outputShape: IntArray
) {

    private val inferenceLock = Any()

    companion object {
        private const val TAG = "TFLiteEmbedder"
        private const val NORMALIZATION_OFFSET = 127.5f
        private const val NORMALIZATION_SCALE = 128f

        fun createFromAssets(context: Context, modelFileName: String): TFLiteEmbedder {
            val fileDescriptor = context.assets.openFd(modelFileName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel

            val modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )

            val interpreterOptions = Interpreter.Options().apply {
                setNumThreads(1)
            }

            val interpreter = Interpreter(modelBuffer, interpreterOptions)
            val inputShape = interpreter.getInputTensor(0).shape()
            val outputShape = interpreter.getOutputTensor(0).shape()

            Log.i(TAG, "Model loaded - Input: ${inputShape.contentToString()}, Output: ${outputShape.contentToString()}")

            return TFLiteEmbedder(interpreter, inputShape, outputShape)
        }
    }

    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        val inputHeight = inputShape.getOrNull(1) ?: throw IllegalStateException("Invalid input height")
        val inputWidth = inputShape.getOrNull(2) ?: throw IllegalStateException("Invalid input width")

        val resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, inputWidth, inputHeight, true)
        val inputBuffer = prepareInputBuffer(resizedBitmap, inputHeight, inputWidth)

        val embeddingDimension = outputShape.getOrNull(1) ?: throw IllegalStateException("Invalid output dimension")
        val outputBuffer = Array(1) { FloatArray(embeddingDimension) }

        synchronized(inferenceLock) {
            try {
                interpreter.run(inputBuffer, outputBuffer)
            } catch (e: Throwable) {
                Log.e(TAG, "Inference failed: ${e.message}", e)
                throw e
            }
        }

        return normalizeEmbedding(outputBuffer[0])
    }

    private fun prepareInputBuffer(bitmap: Bitmap, height: Int, width: Int): ByteBuffer {
        val bufferSize = 4 * 1 * height * width * 3
        val inputBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())

        val pixels = IntArray(height * width)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var pixelIndex = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[pixelIndex++]
                val red = (pixel shr 16 and 0xFF)
                val green = (pixel shr 8 and 0xFF)
                val blue = (pixel and 0xFF)

                inputBuffer.putFloat((red - NORMALIZATION_OFFSET) / NORMALIZATION_SCALE)
                inputBuffer.putFloat((green - NORMALIZATION_OFFSET) / NORMALIZATION_SCALE)
                inputBuffer.putFloat((blue - NORMALIZATION_OFFSET) / NORMALIZATION_SCALE)
            }
        }

        inputBuffer.rewind()
        return inputBuffer
    }

    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        var sumOfSquares = 0f
        for (value in embedding) {
            sumOfSquares += value * value
        }

        val norm = sqrt(sumOfSquares)
        if (norm == 0f) throw IllegalStateException("Embedding norm is zero")

        return FloatArray(embedding.size) { i -> embedding[i] / norm }
    }

    fun close() {
        try {
            interpreter.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close interpreter: ${e.message}")
        }
    }
}
