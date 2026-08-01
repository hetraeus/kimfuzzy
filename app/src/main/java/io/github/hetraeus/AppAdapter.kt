package io.github.hetraeus.kimfuzzy

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.hetraeus.kimfuzzy.databinding.ItemAppBinding

class AppAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.name.text = app.displayName
            binding.name.setTextColor(ThemeUtils.getTextColor())
            IconSize.apply(binding.icon, app.icon, app.iconFromPack)
            binding.root.setOnClickListener { onClick(app) }
            binding.root.setOnLongClickListener {
                onLongClick(app)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(old: AppInfo, new: AppInfo) =
            old.id == new.id

        override fun areContentsTheSame(old: AppInfo, new: AppInfo) =
            old.label == new.label &&
            old.displayName == new.displayName &&
            old.prefix == new.prefix &&
            old.icon == new.icon &&
            old.iconFromPack == new.iconFromPack
    }
}
