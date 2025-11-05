package com.example.attendancemanagementsystem

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.*

object ClassStorage {
    private const val FILE_NAME = "classes.json"
    private val gson = Gson()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun listClasses(context: Context): List<ClassRoom> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val json = f.readText()
            val type = object : TypeToken<List<ClassRoom>>() {}.type
            gson.fromJson<List<ClassRoom>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAll(context: Context, classes: List<ClassRoom>) {
        val f = file(context)
        f.writeText(gson.toJson(classes))
    }

    fun createClass(context: Context, name: String, subject: String = ""): ClassRoom {
        val classes = listClasses(context).toMutableList()
        val id = UUID.randomUUID().toString()
        val room = ClassRoom(id, name, subject, mutableListOf())
        classes.add(room)
        saveAll(context, classes)
        return room
    }

    fun getClass(context: Context, id: String): ClassRoom? {
        return listClasses(context).firstOrNull { it.id == id }
    }

    fun addStudentToClass(context: Context, classId: String, studentId: String) {
        val classes = listClasses(context).toMutableList()
        val idx = classes.indexOfFirst { it.id == classId }
        if (idx >= 0) {
            val room = classes[idx]
            if (!room.studentIds.contains(studentId)) room.studentIds.add(studentId)
            saveAll(context, classes)

            try {
                AttendanceStorage.ensureMasterCsvHasRoster(context, classId)
            } catch (e: Exception) {
                android.util.Log.w("ClassStorage", "Failed to ensure master CSV roster: ${e.message}")
            }
        }
    }

    fun removeStudentFromClass(context: Context, classId: String, studentId: String) {
        val classes = listClasses(context).toMutableList()
        val idx = classes.indexOfFirst { it.id == classId }
        if (idx >= 0) {
            val room = classes[idx]
            if (room.studentIds.remove(studentId)) {
                saveAll(context, classes)

                try {
                    AttendanceStorage.removeStudentFromMasterCsv(context, classId, studentId)
                } catch (e: Exception) {
                    android.util.Log.w("ClassStorage", "Failed to remove from master CSV: ${e.message}")
                }
            }
        }
    }

    /**
     * Delete a class and remove all enrollments & metadata of students listed in the class.
     * This enforces class-specific embeddings.
     */
    fun deleteClass(context: Context, classId: String) {
        val classes = listClasses(context).toMutableList()
        val idx = classes.indexOfFirst { it.id == classId }
        if (idx < 0) return

        val room = classes[idx]
        // Remove enrollments and master CSV rows
        for (sid in room.studentIds) {
            try {
                EmbeddingStorage.removeEnrollment(context, sid)
            } catch (_: Exception) {}
            try {
                StudentStorage.deleteStudent(context, sid)
            } catch (_: Exception) {}
            try {
                RecognitionManager.remove(sid)
            } catch (_: Exception) {}
            try {
                AttendanceStorage.removeStudentFromMasterCsv(context, classId, sid)
            } catch (e: Exception) {
                android.util.Log.w("ClassStorage", "Failed to remove $sid from master CSV during class delete: ${e.message}")
            }
        }

        // Remove class entry and save
        classes.removeAt(idx)
        saveAll(context, classes)
    }
}
