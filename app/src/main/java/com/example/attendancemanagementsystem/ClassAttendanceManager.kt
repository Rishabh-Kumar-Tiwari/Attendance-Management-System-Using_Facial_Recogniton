package com.example.attendancemanagementsystem

import android.content.Context
import android.util.Log
import java.io.File
import java.util.*

object ClassAttendanceManager {

    fun markIfNotMarked(context: Context, classId: String, studentId: String, name: String, roll: String): Boolean {
        val today = Date()
        val existingRecords = AttendanceStorage.load(context, classId, today)

        if (existingRecords.any { it.roll == roll && it.name == name }) {
            return false
        }

        val newRecord = AttendanceRecord(roll, name, System.currentTimeMillis(), "Present", studentId, classId)
        val updatedRecords = existingRecords.toMutableList().apply { add(newRecord) }

        AttendanceStorage.save(context, classId, today, updatedRecords)
        updateMasterCsvSafely(context, classId, today)

        return true
    }

    fun getRecordsForDate(context: Context, date: Date, classId: String): List<AttendanceRecord> {
        return AttendanceStorage.load(context, classId, date)
    }

    fun removeRecord(context: Context, date: Date, classId: String, record: AttendanceRecord): Boolean {
        val records = AttendanceStorage.load(context, classId, date).toMutableList()
        val removed = records.removeIf {
            it.roll == record.roll && it.name == record.name && it.timestamp == record.timestamp
        }

        if (removed) {
            AttendanceStorage.save(context, classId, date, records)
            updateMasterCsvSafely(context, classId, date)
        }

        return removed
    }

    fun getMasterCsvFile(context: Context, classId: String): File {
        updateMasterCsvSafely(context, classId, Date())
        return AttendanceStorage.getMasterCsvFile(context, classId)
    }

    private fun updateMasterCsvSafely(context: Context, classId: String, date: Date) {
        try {
            AttendanceStorage.updateMasterCsv(context, classId, date)
        } catch (e: Exception) {
            Log.w("ClassAttendanceManager", "Failed to update master CSV: ${e.message}")
        }
    }
}
