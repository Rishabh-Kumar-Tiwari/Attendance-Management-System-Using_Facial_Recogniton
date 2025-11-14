package com.example.attendancemanagementsystem

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancemanagementsystem.databinding.ItemAttendanceBinding
import java.text.SimpleDateFormat
import java.util.*

class AttendanceAdapter : ListAdapter<AttendanceRecord, AttendanceAdapter.ViewHolder>(DiffCallback) {

    var onDeleteClick: ((AttendanceRecord) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAttendanceBinding) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat(binding.root.context.getString(R.string.format_datetime), Locale.US)

        fun bind(record: AttendanceRecord) {
            binding.tvName.text = record.name
            binding.tvRoll.text = record.roll
            binding.tvTs.text = dateFormat.format(Date(record.timestamp))
            binding.btnDelete.setOnClickListener {
                onDeleteClick?.invoke(record)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AttendanceRecord>() {
        override fun areItemsTheSame(oldItem: AttendanceRecord, newItem: AttendanceRecord): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.roll == newItem.roll
        }

        override fun areContentsTheSame(oldItem: AttendanceRecord, newItem: AttendanceRecord): Boolean {
            return oldItem == newItem
        }
    }
}
