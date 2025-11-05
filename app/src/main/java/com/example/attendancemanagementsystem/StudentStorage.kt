package com.example.attendancemanagementsystem

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object StudentStorage {
    private const val FILE_NAME = "students.json"
    private val gson = Gson()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun loadRaw(context: Context): MutableMap<String, Student> {
        val f = file(context)
        if (!f.exists()) return mutableMapOf()
        return try {
            val json = f.readText()
            val type = object : TypeToken<MutableMap<String, Student>>() {}.type
            gson.fromJson<MutableMap<String, Student>>(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveRaw(context: Context, map: Map<String, Student>) {
        val f = file(context)
        val json = gson.toJson(map)
        f.writeText(json)
    }

    fun createOrUpdate(context: Context, student: Student) {
        val map = loadRaw(context)
        map[student.id] = student
        saveRaw(context, map)
    }

    fun getStudent(context: Context, id: String): Student? {
        val map = loadRaw(context)
        return map[id]
    }

    fun deleteStudent(context: Context, id: String) {
        val map = loadRaw(context)
        map.remove(id)
        saveRaw(context, map)
    }
}
