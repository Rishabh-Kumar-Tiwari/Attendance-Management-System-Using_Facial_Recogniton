package com.example.attendancemanagementsystem

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.attendancemanagementsystem.databinding.ActivityClassSelectionBinding

class ClassSelectionActivity : BaseActivity() {

    private lateinit var binding: ActivityClassSelectionBinding
    private val classNames = mutableListOf<String>()
    private val classIds = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        setupListeners()
        loadClasses()
    }

    private fun setupListeners() {
        binding.btnCreateClass.setOnClickListener { showCreateClassDialog() }
        binding.btnManageClasses.setOnClickListener { showManageClassesDialog() }

        binding.listClasses.setOnItemClickListener { _, _, position, _ ->
            val classId = classIds[position]
            val className = classNames[position]
            returnSelectedClass(classId, className)
        }
    }

    private fun loadClasses() {
        classNames.clear()
        classIds.clear()

        val classes = ClassStorage.listClasses(this).sortedWith { a, b ->
            val nameA = a.name.trim()
            val nameB = b.name.trim()
            val numA = extractLeadingNumber(nameA)
            val numB = extractLeadingNumber(nameB)

            when {
                numA != null && numB != null -> numA.compareTo(numB)
                numA != null -> -1
                numB != null -> 1
                else -> nameA.compareTo(nameB, ignoreCase = true)
            }
        }

        classes.forEach { classRoom ->
            classIds.add(classRoom.id)
            val displayName = if (classRoom.subject.isNotBlank()) {
                "${classRoom.name} — ${classRoom.subject}"
            } else {
                classRoom.name
            }
            classNames.add(displayName)
        }

        binding.listClasses.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, classNames)
        binding.headerView.text = getString(R.string.msg_existing_classes, classNames.size)
    }

    private fun extractLeadingNumber(text: String): Int? {
        var index = 0
        while (index < text.length && text[index].isWhitespace()) index++

        val startIndex = index
        while (index < text.length && text[index].isDigit()) index++

        return if (index > startIndex) text.substring(startIndex, index).toIntOrNull() else null
    }

    private fun showCreateClassDialog() {
        val nameInput = EditText(this).apply { hint = getString(R.string.hint_class_name) }
        val subjectInput = EditText(this).apply { hint = getString(R.string.hint_subject_optional) }

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
            addView(nameInput)
            addView(subjectInput)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_create_class_title))
            .setView(dialogLayout)
            .setPositiveButton(getString(R.string.btn_create)) { _, _ ->
                val className = nameInput.text.toString().trim()
                val subject = subjectInput.text.toString().trim()

                if (className.isNotBlank()) {
                    val newClass = ClassStorage.createClass(this, className, subject)
                    loadClasses()
                    returnSelectedClass(newClass.id, newClass.name)
                } else {
                    Toast.makeText(this, getString(R.string.msg_enter_class_name), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showManageClassesDialog() {
        val classes = ClassStorage.listClasses(this)
        if (classes.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_no_classes_to_manage), Toast.LENGTH_SHORT).show()
            return
        }

        val classDisplayNames = classes.map { classRoom ->
            if (classRoom.subject.isNotBlank()) {
                "${classRoom.name} — ${classRoom.subject}"
            } else {
                classRoom.name
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_select_class_title))
            .setItems(classDisplayNames) { _, which ->
                navigateToClassDetail(classes[which].id)
            }
            .show()
    }

    private fun navigateToClassDetail(classId: String) {
        val intent = Intent(this, ClassDetailActivity::class.java).apply {
            putExtra("classId", classId)
        }
        startActivity(intent)
    }

    private fun returnSelectedClass(classId: String, className: String) {
        val resultIntent = Intent().apply {
            putExtra("classId", classId)
            putExtra("className", className)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        super.onBackPressed()
        finishAffinity()
    }
}
