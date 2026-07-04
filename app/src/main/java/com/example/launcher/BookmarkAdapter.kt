package com.example.launcher

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.launcher.databinding.ItemBookmarkBinding

class BookmarkAdapter(
    private val onClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, BookmarkAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBookmarkBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.icon.setImageDrawable(app.icon)
            binding.name.text = app.label
            binding.root.setOnClickListener { onClick(app) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(old: AppInfo, new: AppInfo) =
            old.packageName == new.packageName

        override fun areContentsTheSame(old: AppInfo, new: AppInfo) = old == new
    }
}
