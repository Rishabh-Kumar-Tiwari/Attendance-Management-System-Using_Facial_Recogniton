package com.example.attendancemanagementsystem

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.attendancemanagementsystem.databinding.ActivityClassDetailBinding

class ClassDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityClassDetailBinding
    private var classId: String = ""
    private var className: String = ""
    private val displayList = mutableListOf<String>()
    private val idList = mutableListOf<String>()
    private val headerTextSp = 18f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classId = intent.getStringExtra("classId") ?: ""
        val room = ClassStorage.getClass(this, classId)
        className = room?.name ?: "Class"

        setSupportActionBar(binding.topAppBar)

        binding.btnAddStudent.setTextSize(TypedValue.COMPLEX_UNIT_SP, headerTextSp)
        binding.btnDeleteClass.setTextSize(TypedValue.COMPLEX_UNIT_SP, headerTextSp)
        binding.btnAddStudent.setTypeface(null, Typeface.NORMAL)
        binding.btnDeleteClass.setTypeface(null, Typeface.NORMAL)

        // Set up listeners
        binding.btnAddStudent.setOnClickListener {
            val i = Intent(this, EnrollmentActivity::class.java)
            i.putExtra("classId", classId)
            startActivity(i)
        }

        binding.btnDeleteClass.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete class?")
                .setMessage("This will permanently delete the class and all student data. This cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    ClassStorage.deleteClass(this, classId)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.listStudents.setOnItemClickListener { _, _, pos, _ ->
            val sid = idList.getOrNull(pos) ?: return@setOnItemClickListener
            val name = displayList.getOrNull(pos) ?: return@setOnItemClickListener
            showStudentOptions(sid, name)
        }

        // Load students and update XML headerView
        loadStudents()
    }

    private fun loadStudents() {
        displayList.clear()
        idList.clear()
        val room = ClassStorage.getClass(this, classId)
        if (room == null) {
            binding.headerView.text = "Total Students : 0"
            binding.listStudents.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
            return
        }

        val items = room.studentIds.map { sid -> sid to StudentStorage.getStudent(this, sid) }

        val sorted = items.sortedWith(compareBy(
            { it.second?.roll?.toIntOrNull() ?: Int.MAX_VALUE },
            { it.second?.name ?: "" },
            { it.first }
        ))

        for ((sid, meta) in sorted) {
            val display = if (meta != null) "${meta.name} (${meta.roll}) — $sid" else sid
            displayList.add(display)
            idList.add(sid)
        }

        binding.headerView.text = "Total Students : ${displayList.size}"
        binding.listStudents.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
    }

    private fun showStudentOptions(studentId: String, displayName: String) {
        val options = arrayOf("Edit student", "Remove from class", "Delete enrollment (embeddings + metadata)")
        AlertDialog.Builder(this)
            .setTitle(displayName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val i = Intent(this, EnrollmentActivity::class.java)
                        i.putExtra("classId", classId)
                        i.putExtra("studentId", studentId)
                        startActivity(i)
                    }
                    1 -> {
                        AlertDialog.Builder(this)
                            .setTitle("Remove from class?")
                            .setMessage("This will remove the student from this class but keep their data.")
                            .setPositiveButton("Remove") { _, _ ->
                                ClassStorage.removeStudentFromClass(this, classId, studentId)
                                loadStudents()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    2 -> {
                        AlertDialog.Builder(this)
                            .setTitle("Delete enrollment?")
                            .setMessage("This will permanently delete the student's embeddings and metadata. This cannot be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                ClassStorage.removeStudentFromClass(this, classId, studentId)
                                StudentStorage.deleteStudent(this, studentId)
                                EmbeddingStorage.removeEnrollment(this, studentId)
                                RecognitionManager.remove(studentId)
                                loadStudents()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadStudents()
    }
}
