package com.example.attendancemanagementsystem

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.*

object ClassStorage {

    private const val FILE_NAME = "classes.json"
    private val gson = Gson()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun listClasses(context: Context): List<ClassRoom> {
        val classFile = file(context)
        if (!classFile.exists()) return emptyList()

        return try {
            val json = classFile.readText()
            val type = object : TypeToken<List<ClassRoom>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAll(context: Context, classes: List<ClassRoom>) {
        file(context).writeText(gson.toJson(classes))
    }

    fun createClass(context: Context, name: String, subject: String = ""): ClassRoom {
        val classes = listClasses(context).toMutableList()
        val newClass = ClassRoom(
            id = UUID.randomUUID().toString(),
            name = name,
            subject = subject,
            studentIds = mutableListOf()
        )
        classes.add(newClass)
        saveAll(context, classes)
        return newClass
    }

    fun getClass(context: Context, id: String): ClassRoom? {
        return listClasses(context).firstOrNull { it.id == id }
    }

    fun addStudentToClass(context: Context, classId: String, studentId: String) {
        val classes = listClasses(context).toMutableList()
        val classIndex = classes.indexOfFirst { it.id == classId }

        if (classIndex >= 0) {
            val classRoom = classes[classIndex]
            if (!classRoom.studentIds.contains(studentId)) {
                classRoom.studentIds.add(studentId)
                saveAll(context, classes)
                ensureMasterCsvRosterSafely(context, classId)
            }
        }
    }

    fun removeStudentFromClass(context: Context, classId: String, studentId: String) {
        val classes = listClasses(context).toMutableList()
        val classIndex = classes.indexOfFirst { it.id == classId }

        if (classIndex >= 0) {
            val classRoom = classes[classIndex]
            if (classRoom.studentIds.remove(studentId)) {
                saveAll(context, classes)
                removeStudentFromCsvSafely(context, classId, studentId)
            }
        }
    }

    fun deleteClass(context: Context, classId: String) {
        val classes = listClasses(context).toMutableList()
        val classIndex = classes.indexOfFirst { it.id == classId }
        if (classIndex < 0) return

        val classRoom = classes[classIndex]
        classRoom.studentIds.forEach { studentId ->
            removeStudentData(context, studentId, classId)
        }

        classes.removeAt(classIndex)
        saveAll(context, classes)
    }

    private fun removeStudentData(context: Context, studentId: String, classId: String) {
        runCatching { EmbeddingStorage.removeEnrollment(context, studentId) }
        runCatching { StudentStorage.deleteStudent(context, studentId) }
        runCatching { RecognitionManager.remove(studentId) }
        removeStudentFromCsvSafely(context, classId, studentId)
    }

    private fun ensureMasterCsvRosterSafely(context: Context, classId: String) {
        try {
            AttendanceStorage.ensureMasterCsvHasRoster(context, classId)
        } catch (e: Exception) {
            Log.w("ClassStorage", "Failed to ensure master CSV roster: ${e.message}")
        }
    }

    private fun removeStudentFromCsvSafely(context: Context, classId: String, studentId: String) {
        try {
            AttendanceStorage.removeStudentFromMasterCsv(context, classId, studentId)
        } catch (e: Exception) {
            Log.w("ClassStorage", "Failed to remove student from master CSV: ${e.message}")
        }
    }
}
