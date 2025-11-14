package com.example.attendancemanagementsystem

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object StudentStorage {

    private const val FILE_NAME = "students.json"
    private val gson = Gson()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun loadStudentMap(context: Context): MutableMap<String, Student> {
        val studentFile = file(context)
        if (!studentFile.exists()) return mutableMapOf()

        return try {
            val json = studentFile.readText()
            val type = object : TypeToken<MutableMap<String, Student>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveStudentMap(context: Context, studentMap: Map<String, Student>) {
        file(context).writeText(gson.toJson(studentMap))
    }

    fun createOrUpdate(context: Context, student: Student) {
        val studentMap = loadStudentMap(context)
        studentMap[student.id] = student
        saveStudentMap(context, studentMap)
    }

    fun getStudent(context: Context, studentId: String): Student? {
        return loadStudentMap(context)[studentId]
    }

    fun deleteStudent(context: Context, studentId: String) {
        val studentMap = loadStudentMap(context)
        studentMap.remove(studentId)
        saveStudentMap(context, studentMap)
    }
}
