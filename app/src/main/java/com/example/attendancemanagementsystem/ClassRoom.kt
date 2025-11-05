package com.example.attendancemanagementsystem

data class ClassRoom(
    val id: String,
    var name: String,
    var subject: String = "",
    val studentIds: MutableList<String> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis()
)
