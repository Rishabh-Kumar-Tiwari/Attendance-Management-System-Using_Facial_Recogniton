package com.example.attendancemanagementsystem

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.example.attendancemanagementsystem.databinding.ActivityClassDetailBinding

class ClassDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityClassDetailBinding
    private var classId: String = ""
    private val displayList = mutableListOf<String>()
    private val studentIdList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classId = intent.getStringExtra("classId") ?: ""
        setSupportActionBar(binding.topAppBar)

        setupListeners()
        loadStudents()
    }

    private fun setupListeners() {
        binding.btnAddStudent.setOnClickListener {
            navigateToEnrollment()
        }

        binding.btnDeleteClass.setOnClickListener {
            showDeleteClassDialog()
        }

        binding.listStudents.setOnItemClickListener { _, _, position, _ ->
            val studentId = studentIdList.getOrNull(position) ?: return@setOnItemClickListener
            val displayName = displayList.getOrNull(position) ?: return@setOnItemClickListener
            showStudentOptionsDialog(studentId, displayName)
        }
    }

    private fun loadStudents() {
        displayList.clear()
        studentIdList.clear()

        val classRoom = ClassStorage.getClass(this, classId)
        if (classRoom == null) {
            updateHeader(0)
            updateListView()
            return
        }

        val students = classRoom.studentIds.map { studentId ->
            studentId to StudentStorage.getStudent(this, studentId)
        }.sortedWith(compareBy(
            { it.second?.roll?.toIntOrNull() ?: Int.MAX_VALUE },
            { it.second?.name ?: "" },
            { it.first }
        ))

        students.forEach { (studentId, student) ->
            val display = student?.let { "${it.name} (${it.roll}) — $studentId" } ?: studentId
            displayList.add(display)
            studentIdList.add(studentId)
        }

        updateHeader(displayList.size)
        updateListView()
    }

    private fun updateHeader(count: Int) {
        binding.headerView.text = getString(R.string.msg_total_students, count)
    }

    private fun updateListView() {
        binding.listStudents.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
    }

    private fun navigateToEnrollment(studentId: String? = null) {
        val intent = Intent(this, EnrollmentActivity::class.java).apply {
            putExtra("classId", classId)
            studentId?.let { putExtra("studentId", it) }
        }
        startActivity(intent)
    }

    private fun showDeleteClassDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_delete_class_title))
            .setMessage(getString(R.string.dialog_delete_class_message))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                ClassStorage.deleteClass(this, classId)
                finish()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showStudentOptionsDialog(studentId: String, displayName: String) {
        val options = arrayOf(
            getString(R.string.option_edit_student),
            getString(R.string.option_remove_from_class),
            getString(R.string.option_delete_enrollment)
        )

        AlertDialog.Builder(this)
            .setTitle(displayName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> navigateToEnrollment(studentId)
                    1 -> showRemoveFromClassDialog(studentId)
                    2 -> showDeleteEnrollmentDialog(studentId)
                }
            }
            .show()
    }

    private fun showRemoveFromClassDialog(studentId: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_remove_title))
            .setMessage(getString(R.string.dialog_remove_message))
            .setPositiveButton(getString(R.string.btn_remove)) { _, _ ->
                ClassStorage.removeStudentFromClass(this, classId, studentId)
                loadStudents()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showDeleteEnrollmentDialog(studentId: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_delete_enrollment_title))
            .setMessage(getString(R.string.dialog_delete_enrollment_message))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                deleteStudentCompletely(studentId)
                loadStudents()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun deleteStudentCompletely(studentId: String) {
        ClassStorage.removeStudentFromClass(this, classId, studentId)
        StudentStorage.deleteStudent(this, studentId)
        EmbeddingStorage.removeEnrollment(this, studentId)
        RecognitionManager.remove(studentId)
    }

    override fun onResume() {
        super.onResume()
        loadStudents()
    }
}
