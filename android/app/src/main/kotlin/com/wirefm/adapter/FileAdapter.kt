package com.wirefm.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wirefm.app.R
import com.wirefm.app.model.FileItem

class FileAdapter(
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem) -> Boolean
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(DiffCallback) {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tvIcon)
        val tvName: TextView = view.findViewById(R.id.tvFileName)
        val tvDetails: TextView = view.findViewById(R.id.tvFileDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvName.text = item.name

        if (item.isDir) {
            holder.tvIcon.text = "📁"
            holder.tvDetails.text = "Directory"
        } else {
            holder.tvIcon.text = getFileIconEmoji(item.ext)
            holder.tvDetails.text = formatFileSize(item.size)
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { onItemLongClick(item) }
    }

    private fun getFileIconEmoji(ext: String): String {
        return when (ext.lowercase()) {
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg" -> "🖼️"
            ".mp4", ".mkv", ".avi", ".mov" -> "🎬"
            ".mp3", ".wav", ".flac", ".ogg" -> "🎵"
            ".pdf", ".txt", ".md" -> "📄"
            ".doc", ".docx" -> "📝"
            ".zip", ".tar", ".gz", ".rar", ".7z" -> "🗜️"
            ".kt", ".java", ".go", ".py", ".js", ".ts", ".html", ".css", ".sh" -> "💻"
            ".apk" -> "📱"
            else -> "📄"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem) = oldItem.path == newItem.path
        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem) = oldItem == newItem
    }
}
