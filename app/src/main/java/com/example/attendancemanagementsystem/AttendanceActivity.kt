package com.example.attendancemanagementsystem

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.attendancemanagementsystem.databinding.ActivityAttendanceBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AttendanceActivity : BaseActivity() {

    private lateinit var binding: ActivityAttendanceBinding
    private val adapter = AttendanceAdapter()
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var currentDate = Date()
    private var classId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarAttendance)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        classId = intent.getStringExtra("classId") ?: ""

        if (classId.isBlank()) {
            Toast.makeText(this, getString(R.string.msg_no_class_specified), Toast.LENGTH_LONG).show()
        }

        updateDateDisplay()
        loadRecordsFor(currentDate)
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnPickDate.setOnClickListener { showDatePicker() }
        binding.btnExport.setOnClickListener { exportMasterCsv() }
        binding.btnRefresh.setOnClickListener { loadRecordsFor(currentDate) }
        adapter.onDeleteClick = { record -> deleteRecord(record) }
    }

    private fun updateDateDisplay() {
        binding.txtDate.text = dayFormat.format(currentDate)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance().apply { time = currentDate }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                currentDate = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }.time
                updateDateDisplay()
                loadRecordsFor(currentDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadRecordsFor(date: Date) {
        lifecycleScope.launch(Dispatchers.IO) {
            val records = ClassAttendanceManager.getRecordsForDate(this@AttendanceActivity, date, classId)
            withContext(Dispatchers.Main) {
                adapter.submitList(records)
                binding.txtCount.text = getString(R.string.records_count, records.size)
            }
        }
    }

    private fun exportMasterCsv() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val csvFile = ClassAttendanceManager.getMasterCsvFile(this@AttendanceActivity, classId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AttendanceActivity, getString(R.string.msg_master_csv_ready, csvFile.name), Toast.LENGTH_SHORT).show()
                    shareFile(csvFile, "text/csv")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AttendanceActivity, getString(R.string.msg_export_failed, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteRecord(record: AttendanceRecord) {
        lifecycleScope.launch(Dispatchers.IO) {
            val removed = ClassAttendanceManager.removeRecord(this@AttendanceActivity, currentDate, classId, record)
            withContext(Dispatchers.Main) {
                val message = if (removed) getString(R.string.msg_record_removed) else getString(R.string.msg_remove_failed)
                Toast.makeText(this@AttendanceActivity, message, Toast.LENGTH_SHORT).show()
                if (removed) loadRecordsFor(currentDate)
            }
        }
    }

    private fun shareFile(file: File, mimeType: String) {
        val uri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.msg_share_csv)))
    }

    override fun onResume() {
        super.onResume()
        loadRecordsFor(currentDate)
    }
}
