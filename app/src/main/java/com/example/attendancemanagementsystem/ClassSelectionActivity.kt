package com.example.attendancemanagementsystem

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.attendancemanagementsystem.databinding.ActivityClassSelectionBinding

class ClassSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityClassSelectionBinding
    private val adapterItems = mutableListOf<String>()
    private val adapterIds = mutableListOf<String>()

    private val headerTextSp = 18f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        binding.btnCreateClass.setTextSize(TypedValue.COMPLEX_UNIT_SP, headerTextSp)
        binding.btnManageClasses.setTextSize(TypedValue.COMPLEX_UNIT_SP, headerTextSp)
        binding.btnCreateClass.setTypeface(null, Typeface.NORMAL)
        binding.btnManageClasses.setTypeface(null, Typeface.NORMAL)

        binding.btnCreateClass.setOnClickListener { showCreateDialog() }

        binding.btnManageClasses.setOnClickListener { showManageChooser() }

        binding.listClasses.setOnItemClickListener { _, _, position, _ ->
            val id = adapterIds[position]
            val name = adapterItems[position]
            val intent = Intent().apply {
                putExtra("classId", id)
                putExtra("className", name)
            }
            setResult(RESULT_OK, intent)
            finish()
        }

        loadList()
    }

    private fun loadList() {
        adapterItems.clear()
        adapterIds.clear()
        val classes = ClassStorage.listClasses(this)

        val sorted = classes.sortedWith { a, b ->
            val na = a.name.trim()
            val nb = b.name.trim()
            val ra = leadingInt(na)
            val rb = leadingInt(nb)
            when {
                ra != null && rb != null -> ra.compareTo(rb)
                ra != null && rb == null -> -1
                ra == null && rb != null -> 1
                else -> na.compareTo(nb, ignoreCase = true)
            }
        }

        for (c in sorted) {
            adapterIds.add(c.id)
            adapterItems.add("${c.name} ${if (c.subject.isNotBlank()) "— ${c.subject}" else ""}")
        }

        binding.listClasses.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, adapterItems)
        binding.headerView.text = "Your Classes : ${adapterItems.size}"
    }

    private fun leadingInt(s: String): Int? {
        var i = 0
        val len = s.length
        while (i < len && s[i].isWhitespace()) i++
        val start = i
        while (i < len && s[i].isDigit()) i++
        if (i == start) return null
        return try {
            s.substring(start, i).toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun showCreateDialog() {
        val input = android.widget.EditText(this).apply { hint = "Class name" }
        val subj = android.widget.EditText(this).apply { hint = "Subject (optional)" }
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16f,
                resources.displayMetrics
            ).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(input)
            addView(subj)
        }

        AlertDialog.Builder(this)
            .setTitle("Create class")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                val subject = subj.text.toString().trim()
                if (name.isNotBlank()) {
                    val room = ClassStorage.createClass(this, name, subject)
                    loadList()
                    val intent = Intent().apply {
                        putExtra("classId", room.id)
                        putExtra("className", room.name)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                } else {
                    runOnUiThread {
                        android.widget.Toast.makeText(this, "Enter a class name", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManageChooser() {
        val classes = ClassStorage.listClasses(this)
        if (classes.isEmpty()) {
            runOnUiThread {
                android.widget.Toast.makeText(this, "No classes to manage. Create one first.", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        val names = classes.map {
            "${it.name} ${if (it.subject.isNotBlank()) "— ${it.subject}" else ""}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select class to manage")
            .setItems(names) { _, which ->
                val id = classes[which].id
                val i = Intent(this, ClassDetailActivity::class.java)
                i.putExtra("classId", id)
                startActivity(i)
            }
            .show()
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        finishAffinity()
    }
}
