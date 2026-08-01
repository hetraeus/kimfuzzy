package io.github.hetraeus.kimfuzzy

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.github.hetraeus.kimfuzzy.databinding.ItemBookmarkBinding
import kotlin.math.abs

class BookmarkAdapter(
    private val onRename: ((AppInfo) -> Unit)? = null,
    private val dragListener: DragListener? = null
) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

    interface DragListener {
        fun onDragStart(holder: ViewHolder, app: AppInfo, touchX: Float, touchY: Float)
        fun onDragMove(rawX: Float, rawY: Float)
        fun onDragEnd(rawX: Float, rawY: Float)
    }

    private var items: List<AppInfo?> = emptyList()

    fun submitList(list: List<AppInfo?>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = list.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition]?.id == list[newItemPosition]?.id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = list[newItemPosition]
                if (old == null && new == null) return true
                if (old == null || new == null) return false
                return old.label == new.label && old.iconFromPack == new.iconFromPack
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items = list
        diffResult.dispatchUpdatesTo(this)
    }

    fun getItemAt(position: Int): AppInfo? = items.getOrNull(position)

    fun getItems(): List<AppInfo?> = items

    fun moveItem(fromPos: Int, toPos: Int) {
        if (fromPos in items.indices && toPos in items.indices) {
            val mutable = items.toMutableList()
            val temp = mutable[fromPos]
            mutable[fromPos] = mutable[toPos]
            mutable[toPos] = temp
            items = mutable
            notifyItemMoved(fromPos, toPos)
        }
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
            holder.binding.root.setOnTouchListener(null)
            holder.binding.root.setOnClickListener(null)
            holder.binding.root.isClickable = false
        } else {
            holder.binding.root.visibility = View.VISIBLE
            holder.binding.name.text = app.label
            holder.binding.name.setTextColor(ThemeUtils.getTextColor())
            IconSize.apply(holder.binding.icon, app.icon, app.iconFromPack)

            if (dragListener != null) {
                // Edit mode: drag to move, tap to rename
                val dragThreshold = 28f * holder.binding.root.context.resources.displayMetrics.density
                var hasMoved = false
                var startRawX = 0f
                var startRawY = 0f

                holder.binding.root.isClickable = true
                holder.binding.root.setOnClickListener {
                    onRename?.invoke(app)
                }
                holder.binding.root.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            hasMoved = false
                            startRawX = event.rawX
                            startRawY = event.rawY
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = abs(event.rawX - startRawX)
                            val dy = abs(event.rawY - startRawY)
                            if (!hasMoved && (dx > dragThreshold || dy > dragThreshold)) {
                                hasMoved = true
                                val loc = IntArray(2)
                                holder.binding.root.getLocationOnScreen(loc)
                                dragListener.onDragStart(
                                    holder, app,
                                    touchX = startRawX - loc[0],
                                    touchY = startRawY - loc[1]
                                )
                            } else if (hasMoved) {
                                dragListener.onDragMove(event.rawX, event.rawY)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (hasMoved) dragListener.onDragEnd(event.rawX, event.rawY)
                            else holder.binding.root.performClick()
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            if (hasMoved) dragListener.onDragEnd(event.rawX, event.rawY)
                            true
                        }
                        else -> false
                    }
                }
            } else {
                // Lock mode: parent OnItemTouchListener handles tap / long-press / swipe
                holder.binding.root.setOnTouchListener(null)
                holder.binding.root.setOnClickListener(null)
                holder.binding.root.isClickable = false
            }
        }
    }

    class ViewHolder(val binding: ItemBookmarkBinding) :
        RecyclerView.ViewHolder(binding.root)
}
