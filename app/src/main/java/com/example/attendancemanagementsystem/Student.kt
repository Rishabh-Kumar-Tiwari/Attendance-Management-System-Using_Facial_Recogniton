package com.example.attendancemanagementsystem

data class Student(
    val id: String,
    val roll: String,
    val name: String,
    val classId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
