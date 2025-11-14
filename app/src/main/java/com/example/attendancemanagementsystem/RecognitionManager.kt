package com.example.attendancemanagementsystem

import kotlin.math.sqrt

data class MatchResult(val id: String, val confidence: Float)

object RecognitionManager {

    private val embeddingStore: MutableMap<String, MutableList<FloatArray>> = mutableMapOf()
    private const val DEFAULT_THRESHOLD = 0.75f

    fun enroll(studentId: String, embeddings: List<FloatArray>) {
        val studentEmbeddings = embeddingStore.getOrPut(studentId) { mutableListOf() }
        studentEmbeddings.addAll(embeddings)
    }

    fun remove(studentId: String) {
        embeddingStore.remove(studentId)
    }

    fun clear() {
        embeddingStore.clear()
    }

    fun recognize(queryEmbedding: FloatArray, threshold: Float = DEFAULT_THRESHOLD): MatchResult? {
        var bestMatchId: String? = null
        var bestSimilarity = -1f

        for ((studentId, embeddings) in embeddingStore) {
            for (embedding in embeddings) {
                val similarity = calculateCosineSimilarity(queryEmbedding, embedding)
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestMatchId = studentId
                }
            }
        }

        return if (bestMatchId != null && bestSimilarity >= threshold) {
            MatchResult(bestMatchId, bestSimilarity)
        } else {
            null
        }
    }

    private fun calculateCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        val length = minOf(vectorA.size, vectorB.size)
        for (i in 0 until length) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }
}
