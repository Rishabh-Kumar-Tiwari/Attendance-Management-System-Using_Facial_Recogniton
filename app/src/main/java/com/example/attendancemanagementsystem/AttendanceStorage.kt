package com.example.attendancemanagementsystem

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

object AttendanceStorage {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val percentFormat = DecimalFormat("0")

    private fun safeClassFileName(classNameOrId: String): String {
        val trimmed = classNameOrId.trim()
        if (trimmed.isEmpty()) return "global"
        return trimmed.replace("\\s+".toRegex(), "_").replace("[^A-Za-z0-9_\\-]".toRegex(), "")
    }

    private fun isSummaryRow(name: String?): Boolean {
        if (name == null) return false
        val normalized = name.trim().lowercase(Locale.ROOT)
        return normalized == "total present" || normalized == "total absent"
    }

    fun getMasterCsvFile(context: Context, classId: String): File {
        val className = ClassStorage.getClass(context, classId)?.name ?: classId
        val safeName = safeClassFileName(className)
        return File(context.filesDir, "attendance-$safeName.csv")
    }

    private fun fileForDate(context: Context, classId: String, date: Date): File {
        val safeClass = if (classId.isBlank()) "global" else classId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val fileName = "attendance_${safeClass}_${dayFormat.format(date)}.json"
        return File(context.filesDir, fileName)
    }

    fun load(context: Context, classId: String, date: Date): MutableList<AttendanceRecord> {
        val file = fileForDate(context, classId, date)
        if (!file.exists()) return mutableListOf()

        val jsonArray = org.json.JSONArray(file.readText())
        val records = mutableListOf<AttendanceRecord>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            records.add(
                AttendanceRecord(
                    roll = obj.optString("roll", ""),
                    name = obj.optString("name", ""),
                    timestamp = obj.optLong("timestamp", 0L),
                    status = obj.optString("status", "Present"),
                    studentId = obj.optString("studentId", ""),
                    classId = obj.optString("classId", "")
                )
            )
        }
        return records
    }

    fun save(context: Context, classId: String, date: Date, records: List<AttendanceRecord>, studentIds: List<String>? = null) {
        val file = fileForDate(context, classId, date)
        val jsonArray = org.json.JSONArray()

        records.forEachIndexed { index, record ->
            val jsonObj = org.json.JSONObject().apply {
                put("roll", record.roll)
                put("name", record.name)
                put("timestamp", record.timestamp)
                put("status", record.status)
                if (record.studentId.isNotEmpty()) put("studentId", record.studentId)
                if (record.classId.isNotEmpty()) put("classId", record.classId)
                if (studentIds != null && index < studentIds.size) put("studentId", studentIds[index])
            }
            jsonArray.put(jsonObj)
        }
        file.writeText(jsonArray.toString())
    }

    fun ensureMasterCsvHasRoster(context: Context, classId: String) {
        try {
            val csvFile = getMasterCsvFile(context, classId)
            val enrolledStudents = getEnrolledStudents(context, classId)

            if (!csvFile.exists()) {
                createNewCsvWithRoster(csvFile, enrolledStudents)
                return
            }

            val lines = csvFile.readLines().toMutableList()
            if (lines.isEmpty()) {
                createNewCsvWithRoster(csvFile, enrolledStudents)
                return
            }

            val existingStudents = extractExistingStudents(lines)
            val newStudents = enrolledStudents.filterNot { existingStudents.contains(it) }

            if (newStudents.isNotEmpty()) {
                appendNewStudentsToCsv(csvFile, lines, newStudents)
            }
        } catch (e: Exception) {
            Log.w("AttendanceStorage", "ensureMasterCsvHasRoster failed: ${e.message}")
        }
    }

    fun updateMasterCsv(context: Context, classId: String, date: Date) {
        try {
            val csvFile = getMasterCsvFile(context, classId)
            val dateStr = dayFormat.format(date)

            val enrolledStudents = getEnrolledStudentsDetailed(context, classId)
            val presentToday = load(context, classId, date).map { it.roll to it.name }.toSet()

            val headers = loadOrCreateHeaders(csvFile, dateStr)
            val studentRows = loadExistingRows(csvFile, headers)

            updateStudentAttendance(enrolledStudents, presentToday, studentRows, headers, dateStr)
            calculateAttendancePercentages(studentRows, headers)

            val sortedRows = sortStudentsByRoll(studentRows)
            val totalRows = calculateTotalRows(sortedRows, headers)

            writeCsvFile(csvFile, headers, sortedRows, totalRows)
        } catch (e: Exception) {
            Log.w("AttendanceStorage", "updateMasterCsv failed: ${e.message}")
        }
    }

    fun removeStudentFromMasterCsv(context: Context, classId: String, studentId: String) {
        try {
            val csvFile = getMasterCsvFile(context, classId)
            if (!csvFile.exists()) return

            val (roll, name) = parseStudentId(studentId)
            val lines = csvFile.readLines()
            if (lines.isEmpty()) return

            val filteredLines = lines.filterIndexed { index, line ->
                if (index == 0) return@filterIndexed true
                val parts = splitCsvLine(line)
                if (parts.size < 3) return@filterIndexed false
                val rowRoll = parts[1]
                val rowName = parts[2]
                !(rowRoll == roll && rowName == name)
            }

            FileWriter(csvFile, false).use { writer ->
                filteredLines.forEachIndexed { index, line ->
                    if (index == 0) {
                        writer.appendLine(line)
                    } else {
                        val parts = splitCsvLine(line)
                        if (parts.size >= 3 && !isSummaryRow(parts[2])) {
                            val roll = parts.getOrNull(1) ?: ""
                            val name = parts.getOrNull(2) ?: ""
                            if (roll.trim().isNotEmpty() || name.trim().isNotEmpty()) {
                                writer.appendLine(line)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AttendanceStorage", "removeStudentFromMasterCsv failed: ${e.message}")
        }
    }

    private fun getEnrolledStudents(context: Context, classId: String): List<Pair<String, String>> {
        val enrolledIds = ClassStorage.getClass(context, classId)?.studentIds ?: emptyList()
        return enrolledIds.map { parseStudentId(it) }
    }

    private data class StudentInfo(val id: String, val roll: String, val name: String)

    private fun getEnrolledStudentsDetailed(context: Context, classId: String): List<StudentInfo> {
        val enrolledIds = ClassStorage.getClass(context, classId)?.studentIds ?: emptyList()
        return enrolledIds.map { id ->
            val (roll, name) = parseStudentId(id)
            StudentInfo(id, roll, name)
        }
    }

    private fun createNewCsvWithRoster(file: File, students: List<Pair<String, String>>) {
        FileWriter(file, false).use { writer ->
            writer.appendLine("S.No.,Roll_No.,Full_Name,Attendance %")
            students.forEachIndexed { index, (roll, name) ->
                writer.appendLine("${index + 1},${escapeCsv(roll)},${escapeCsv(name)},")
            }
        }
    }

    private fun extractExistingStudents(lines: List<String>): Set<Pair<String, String>> {
        val existing = mutableSetOf<Pair<String, String>>()
        for (i in 1 until lines.size) {
            val parts = splitCsvLine(lines[i])
            if (parts.size >= 3) {
                val roll = parts[1]
                val name = parts[2]
                if (!isSummaryRow(name) && (roll.trim().isNotEmpty() || name.trim().isNotEmpty())) {
                    existing.add(roll to name)
                }
            }
        }
        return existing
    }

    private fun appendNewStudentsToCsv(file: File, lines: List<String>, newStudents: List<Pair<String, String>>) {
        val header = splitCsvLine(lines[0])
        val existingRows = mutableListOf<List<String>>()

        for (i in 1 until lines.size) {
            val parts = splitCsvLine(lines[i])
            if (parts.size >= 3) {
                val name = parts[2]
                val roll = parts.getOrNull(1) ?: ""
                if (!isSummaryRow(name) && (roll.trim().isNotEmpty() || name.trim().isNotEmpty())) {
                    existingRows.add(parts)
                }
            }
        }

        newStudents.forEach { (roll, name) ->
            val newRow = MutableList(header.size) { "" }
            newRow[1] = roll
            newRow[2] = name
            existingRows.add(newRow)
        }

        FileWriter(file, false).use { writer ->
            writer.appendLine(header.joinToString(",") { escapeCsv(it) })
            existingRows.forEachIndexed { index, row ->
                val paddedRow = row.toMutableList()
                while (paddedRow.size < header.size) paddedRow.add("")
                paddedRow[0] = (index + 1).toString()
                writer.appendLine(paddedRow.mapIndexed { idx, v -> if (idx == 0) v else escapeCsv(v) }.joinToString(","))
            }
        }
    }

    private fun loadOrCreateHeaders(file: File, dateStr: String): MutableList<String> {
        val headers = mutableListOf("S.No.", "Roll_No.", "Full_Name")

        if (file.exists()) {
            FileReader(file).use { reader ->
                val lines = reader.readLines()
                if (lines.isNotEmpty()) {
                    val existingHeaders = splitCsvLine(lines[0]).map {
                        when (it.trim()) {
                            "Roll" -> "Roll_No."
                            "Name" -> "Full_Name"
                            else -> it
                        }
                    }
                    headers.clear()
                    headers.addAll(existingHeaders)
                }
            }
        }

        if (!headers.contains("Attendance %")) headers.add("Attendance %")
        if (!headers.contains(dateStr)) {
            val insertIndex = headers.indexOf("Attendance %").let { if (it >= 0) it else headers.size }
            headers.add(insertIndex, dateStr)
        }

        return headers
    }

    private fun loadExistingRows(file: File, headers: List<String>): LinkedHashMap<Pair<String, String>, MutableList<String>> {
        val rowMap = linkedMapOf<Pair<String, String>, MutableList<String>>()

        if (file.exists()) {
            FileReader(file).use { reader ->
                val lines = reader.readLines()
                for (i in 1 until lines.size) {
                    val parts = splitCsvLine(lines[i]).toMutableList()
                    if (parts.size < 3) continue

                    val name = parts[2]
                    val roll = parts.getOrNull(1) ?: ""

                    if (isSummaryRow(name) || (roll.trim().isEmpty() && name.trim().isEmpty())) continue

                    while (parts.size < headers.size) parts.add("")
                    rowMap[roll to name] = parts
                }
            }
        }
        return rowMap
    }

    private fun updateStudentAttendance(
        students: List<StudentInfo>,
        presentToday: Set<Pair<String, String>>,
        rowMap: LinkedHashMap<Pair<String, String>, MutableList<String>>,
        headers: List<String>,
        dateStr: String
    ) {
        val dateIndices = headers.indices.filter { headers[it].matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        val todayIndex = headers.indexOf(dateStr)

        for (student in students) {
            val key = student.roll to student.name
            val isPresent = presentToday.contains(key)
            val mark = if (isPresent) "P" else "A"

            if (rowMap.containsKey(key)) {
                val row = rowMap[key]!!
                while (row.size < headers.size) row.add("")
                if (todayIndex >= 0) row[todayIndex] = mark
            } else {
                val newRow = MutableList(headers.size) { "" }
                newRow[1] = student.roll
                newRow[2] = student.name
                dateIndices.forEach { newRow[it] = "A" }
                if (todayIndex >= 0) newRow[todayIndex] = mark
                rowMap[key] = newRow
            }
        }

        rowMap.keys.toList().forEach { key ->
            val row = rowMap[key]!!
            while (row.size < headers.size) row.add("")
            if (todayIndex >= 0 && row[todayIndex].isBlank()) row[todayIndex] = "A"
        }
    }

    private fun calculateAttendancePercentages(rowMap: LinkedHashMap<Pair<String, String>, MutableList<String>>, headers: List<String>) {
        val dateIndices = headers.indices.filter { headers[it].matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        val percentIndex = headers.indexOf("Attendance %")
        if (percentIndex < 0 || dateIndices.isEmpty()) return

        rowMap.values.forEach { row ->
            val presentCount = dateIndices.count { row.getOrNull(it)?.trim()?.equals("P", ignoreCase = true) == true }
            val percentage = (presentCount * 100.0 / dateIndices.size)
            while (row.size <= percentIndex) row.add("")
            row[percentIndex] = percentFormat.format(percentage)
        }
    }

    private fun sortStudentsByRoll(rowMap: LinkedHashMap<Pair<String, String>, MutableList<String>>): List<MutableList<String>> {
        return rowMap.values.sortedWith(Comparator { a, b ->
            val rollA = a.getOrNull(1) ?: ""
            val rollB = b.getOrNull(1) ?: ""
            val numA = rollA.toIntOrNull()
            val numB = rollB.toIntOrNull()
            when {
                numA != null && numB != null -> numA.compareTo(numB)
                numA != null -> -1
                numB != null -> 1
                else -> rollA.compareTo(rollB, ignoreCase = true)
            }
        }).mapIndexed { index, row ->
            row[0] = (index + 1).toString()
            row
        }
    }

    private fun calculateTotalRows(studentRows: List<MutableList<String>>, headers: List<String>): Pair<List<String>, List<String>> {
        val dateIndices = headers.indices.filter { headers[it].matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        val totalPresent = MutableList(headers.size) { "" }
        val totalAbsent = MutableList(headers.size) { "" }

        totalPresent[2] = "Total Present"
        totalAbsent[2] = "Total Absent"

        dateIndices.forEach { dateIndex ->
            val presentCount = studentRows.count { it.getOrNull(dateIndex)?.trim()?.equals("P", ignoreCase = true) == true }
            val absentCount = studentRows.size - presentCount
            totalPresent[dateIndex] = presentCount.toString()
            totalAbsent[dateIndex] = absentCount.toString()
        }

        return totalPresent to totalAbsent
    }

    private fun writeCsvFile(file: File, headers: List<String>, studentRows: List<List<String>>, totals: Pair<List<String>, List<String>>) {
        FileWriter(file, false).use { writer ->
            writer.appendLine(headers.joinToString(",") { escapeCsv(it) })
            studentRows.forEach { row ->
                writer.appendLine(row.mapIndexed { idx, v -> if (idx == 0) v else escapeCsv(v) }.joinToString(","))
            }
            writer.appendLine("")
            writer.appendLine(totals.first.mapIndexed { idx, v -> if (idx == 0) "" else escapeCsv(v) }.joinToString(","))
            writer.appendLine(totals.second.mapIndexed { idx, v -> if (idx == 0) "" else escapeCsv(v) }.joinToString(","))
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            when (val char = line[i]) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> {
                    if (!inQuotes) {
                        result.add(current.toString())
                        current.clear()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString())

        return result.map { value ->
            var trimmed = value.trim()
            if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length - 1)
            }
            trimmed.replace("\"\"", "\"")
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }

    private fun parseStudentId(studentId: String): Pair<String, String> {
        val separatorIndex = studentId.indexOf('_')
        return if (separatorIndex <= 0) {
            "" to studentId
        } else {
            studentId.substring(0, separatorIndex) to studentId.substring(separatorIndex + 1).replace("_", " ")
        }
    }
}
