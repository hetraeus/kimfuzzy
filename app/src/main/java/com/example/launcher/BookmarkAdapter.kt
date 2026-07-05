package com.example.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.launcher.databinding.ItemBookmarkBinding

class BookmarkAdapter(
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

    private var items: List<AppInfo?> = emptyList()

    fun submitList(list: List<AppInfo?>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookmarkBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = items[position]
        if (app == null) {
            holder.binding.root.visibility = View.INVISIBLE
            holder.binding.icon.setImageDrawable(null)
            holder.binding.icon.colorFilter = null
            holder.binding.name.text = ""
            holder.binding.root.setOnClickListener(null)
            holder.binding.root.isClickable = false
        } else {
            holder.binding.root.visibility = View.VISIBLE
            holder.binding.name.text = app.label
            holder.binding.name.setTextColor(ThemeUtils.getTextColor())
            IconSize.apply(holder.binding.icon, app.icon, app.iconFromPack)
            holder.binding.root.setOnClickListener { onClick(app) }
            holder.binding.root.isClickable = true
        }
    }

    class ViewHolder(val binding: ItemBookmarkBinding) :
        RecyclerView.ViewHolder(binding.root)
}
