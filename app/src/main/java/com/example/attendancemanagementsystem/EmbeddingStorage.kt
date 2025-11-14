package com.example.attendancemanagementsystem

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object EmbeddingStorage {

    private const val FILE_NAME = "enrollments.json"
    private val gson = Gson()

    fun saveEnrollment(context: Context, studentId: String, embeddings: List<FloatArray>) {
        val allEnrollments = loadRawData(context).toMutableMap()
        val embeddingsAsLists = embeddings.map { array -> array.map { it.toDouble() } }
        allEnrollments[studentId] = embeddingsAsLists

        file(context).writeText(gson.toJson(allEnrollments))
    }

    fun removeEnrollment(context: Context, studentId: String) {
        val enrollments = loadRawData(context).toMutableMap()
        if (enrollments.remove(studentId) != null) {
            file(context).writeText(gson.toJson(enrollments))
        }
    }

    fun loadAll(context: Context): Map<String, List<FloatArray>> {
        val rawData = loadRawData(context)
        return rawData.mapValues { (_, embeddingLists) ->
            embeddingLists.map { list ->
                FloatArray(list.size) { index -> list[index].toFloat() }
            }
        }
    }

    fun loadIntoRecognitionManager(context: Context, studentIds: List<String>) {
        RecognitionManager.clear()
        if (studentIds.isEmpty()) return

        val rawData = loadRawData(context)
        studentIds.forEach { studentId ->
            rawData[studentId]?.let { embeddingLists ->
                val embeddings = embeddingLists.map { list ->
                    FloatArray(list.size) { index -> list[index].toFloat() }
                }
                RecognitionManager.enroll(studentId, embeddings)
            }
        }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun loadRawData(context: Context): Map<String, List<List<Double>>> {
        val dataFile = file(context)
        if (!dataFile.exists()) return emptyMap()

        return try {
            val json = dataFile.readText()
            val type = object : TypeToken<Map<String, List<List<Double>>>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
