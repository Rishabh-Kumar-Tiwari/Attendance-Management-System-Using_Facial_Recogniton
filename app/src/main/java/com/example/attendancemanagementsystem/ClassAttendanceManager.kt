package com.example.attendancemanagementsystem

import android.content.Context
import java.io.File
import java.util.*

object ClassAttendanceManager {

    /**
     * Mark a student present if not already marked for the same class and date.
     * Returns true if newly marked, false if already present.
     */
    fun markIfNotMarked(context: Context, classId: String, studentId: String, name: String, roll: String): Boolean {
        val today = Date()
        val list = AttendanceStorage.load(context, classId, today)

        val already = list.any { it.roll == roll && it.name == name }
        if (already) return false

        val record = AttendanceRecord(roll, name, System.currentTimeMillis(), "Present", studentId, classId)
        val newList = list.toMutableList()
        newList.add(record)

        AttendanceStorage.save(context, classId, today, newList)

        try {
            AttendanceStorage.updateMasterCsv(context, classId, today)
        } catch (e: Exception) {
            android.util.Log.w("ClassAttendanceManager", "updateMasterCsv failed: ${e.message}")
        }

        return true
    }

    fun getRecordsForDate(context: Context, date: Date, classId: String): List<AttendanceRecord> {
        return AttendanceStorage.load(context, classId, date)
    }

    fun removeRecord(context: Context, date: Date, classId: String, record: AttendanceRecord): Boolean {
        val list = AttendanceStorage.load(context, classId, date)
        val idx = list.indexOfFirst { it.roll == record.roll && it.name == record.name && it.timestamp == record.timestamp }
        if (idx < 0) return false

        val newList = list.toMutableList()
        newList.removeAt(idx)
        AttendanceStorage.save(context, classId, date, newList)

        try {
            AttendanceStorage.updateMasterCsv(context, classId, date)
        } catch (e: Exception) {
            android.util.Log.w("ClassAttendanceManager", "updateMasterCsv failed after remove: ${e.message}")
        }

        return true
    }

    /**
     * Export the master CSV file for the class.
     */
    fun getMasterCsvFile(context: Context, classId: String): File {
        try {
            AttendanceStorage.updateMasterCsv(context, classId, Date())
        } catch (e: Exception) {
            android.util.Log.w("ClassAttendanceManager", "updateMasterCsv before export failed: ${e.message}")
        }
        return AttendanceStorage.getMasterCsvFile(context, classId)
    }
}
