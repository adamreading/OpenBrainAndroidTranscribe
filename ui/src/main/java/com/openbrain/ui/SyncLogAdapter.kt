package com.openbrain.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.openbrain.ui.databinding.ItemSyncLogBinding
import java.text.SimpleDateFormat
import java.util.*

data class SyncLogItem(
    val timestamp: Long,
    val status: String,
    val message: String
)

class SyncLogAdapter : RecyclerView.Adapter<SyncLogAdapter.ViewHolder>() {

    private val items = mutableListOf<SyncLogItem>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun submitList(list: List<SyncLogItem>) {
        items.clear()
        items.addAll(list.reversed()) // newest first
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSyncLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemSyncLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SyncLogItem) {
            binding.timestampTv.text = dateFormat.format(Date(item.timestamp))
            binding.statusTv.text = item.status
            binding.messageTv.text = item.message
            binding.statusTv.setTextColor(
                when (item.status) {
                    "success" -> Color.parseColor("#4CAF50")
                    "error" -> Color.parseColor("#F44336")
                    "retry" -> Color.parseColor("#FF9800")
                    else -> Color.GRAY
                }
            )
        }
    }
}
