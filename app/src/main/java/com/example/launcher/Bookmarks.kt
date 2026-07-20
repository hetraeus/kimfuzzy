package com.example.launcher

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.launcher.databinding.ItemBookmarkBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.abs

/** The bottom bookmarks grid: layout, drag-to-reorder, tap/long-press, and swipe-up-to-search. */

internal fun MainActivity.createBookmarkDragListener(): BookmarkAdapter.DragListener {
    val activity = this
    return object : BookmarkAdapter.DragListener {
        override fun onDragStart(
            holder: BookmarkAdapter.ViewHolder,
            app: AppInfo,
            touchX: Float,
            touchY: Float
        ) {
            dragSourcePos = holder.bindingAdapterPosition
            if (dragSourcePos == RecyclerView.NO_POSITION) return

            draggedApp = app
            dragTouchOffsetX = touchX
            dragTouchOffsetY = touchY

            val floatBinding = ItemBookmarkBinding.inflate(layoutInflater)
            floatBinding.name.text = app.label
            floatBinding.name.setTextColor(ThemeUtils.getTextColor())
            IconSize.apply(floatBinding.icon, app.icon, app.iconFromPack)

            val view = floatBinding.root
            view.measure(
                View.MeasureSpec.makeMeasureSpec(holder.binding.root.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(holder.binding.root.height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)

            val loc = IntArray(2)
            holder.binding.root.getLocationOnScreen(loc)

            val contentLoc = IntArray(2)
            binding.contentArea.getLocationOnScreen(contentLoc)

            val params = FrameLayout.LayoutParams(view.measuredWidth, view.measuredHeight)
            params.leftMargin = loc[0] - contentLoc[0]
            params.topMargin = loc[1] - contentLoc[1]

            binding.contentArea.addView(view, params)
            floatingView = view

            holder.binding.root.visibility = View.INVISIBLE
        }

        override fun onDragMove(rawX: Float, rawY: Float) {
            val view = floatingView ?: return
            val contentLoc = IntArray(2)
            binding.contentArea.getLocationOnScreen(contentLoc)

            val params = view.layoutParams as FrameLayout.LayoutParams
            params.leftMargin = (rawX - dragTouchOffsetX - contentLoc[0]).toInt()
            params.topMargin = (rawY - dragTouchOffsetY - contentLoc[1]).toInt()
            view.layoutParams = params
        }

        override fun onDragEnd(rawX: Float, rawY: Float) {
            floatingView?.let { binding.contentArea.removeView(it) }
            floatingView = null

            val dropZoneLoc = IntArray(2)
            binding.dropZone.getLocationOnScreen(dropZoneLoc)
            val dropZoneHeight = binding.dropZone.height
            if (dropZoneHeight > 0 && rawY >= dropZoneLoc[1] && rawY <= dropZoneLoc[1] + dropZoneHeight) {
                if (dragSourcePos != -1 && draggedApp != null) {
                    Prefs.removeBookmark(draggedApp!!.id)
                    loadBookmarks()
                    Toast.makeText(activity, "Hidden: ${draggedApp!!.label}", Toast.LENGTH_SHORT).show()
                }
                dragSourcePos = -1
                draggedApp = null
                return
            }

            val rvLoc = IntArray(2)
            binding.bookmarksGrid.getLocationOnScreen(rvLoc)
            val dropX = rawX - rvLoc[0]
            val dropY = rawY - rvLoc[1]
            val child = binding.bookmarksGrid.findChildViewUnder(dropX, dropY)
            val targetPos = if (child != null) binding.bookmarksGrid.getChildAdapterPosition(child) else -1

            if (targetPos != -1 && targetPos != dragSourcePos && dragSourcePos != -1) {
                val mutable = bookmarkAdapter.getItems().toMutableList()
                if (mutable[targetPos] == null) {
                    mutable[targetPos] = mutable[dragSourcePos]
                    mutable[dragSourcePos] = null
                    bookmarkAdapter.submitList(mutable)
                    Prefs.saveBookmarks(mutable.map { it?.id ?: "" })
                } else {
                    bookmarkAdapter.notifyItemChanged(dragSourcePos)
                }
            } else {
                if (dragSourcePos != -1) bookmarkAdapter.notifyItemChanged(dragSourcePos)
            }
            dragSourcePos = -1
            draggedApp = null
        }
    }
}

internal fun MainActivity.setupBookmarks() {
    bookmarkAdapter = BookmarkAdapter(
        onRename = { app -> renameBookmark(app) },
        dragListener = if (Prefs.getEditMode()) createBookmarkDragListener() else null
    )
    val activity = this
    binding.bookmarksGrid.apply {
        layoutManager = object : GridLayoutManager(activity, calculateSpanCount()) {
            override fun canScrollVertically(): Boolean = false
            override fun canScrollHorizontally(): Boolean = false
        }
        adapter = bookmarkAdapter
    }
}

private fun MainActivity.calculateSpanCount(): Int = 5

internal fun MainActivity.setupGridTouchListener() {
    val swipeThreshold = 60f * resources.displayMetrics.density
    val clickSlop = 20f * resources.displayMetrics.density

    binding.bookmarksGrid.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var downTime = 0L
        private var hasMoved = false
        private val touchHandler = Handler(Looper.getMainLooper())
        private var dialogRunnable: Runnable? = null

        private fun cancelPending() {
            dialogRunnable?.let { touchHandler.removeCallbacks(it) }
            dialogRunnable = null
        }

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            if (Prefs.getEditMode()) return false

            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.x
                    startY = e.y
                    downTime = System.currentTimeMillis()
                    hasMoved = false

                    val child = rv.findChildViewUnder(e.x, e.y)
                    if (child != null) {
                        val position = rv.getChildAdapterPosition(child)
                        val app = bookmarkAdapter.getItemAt(position)
                        if (app != null) {
                            dialogRunnable = Runnable {
                                if (!hasMoved) {
                                    showBookmarkOptions(app)
                                }
                            }
                            touchHandler.postDelayed(dialogRunnable!!, 1400)
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.x - startX
                    val dy = startY - e.y

                    if (abs(dx) > clickSlop || abs(dy) > clickSlop) {
                        hasMoved = true
                        cancelPending()
                    }

                    if (dy > swipeThreshold && dy > abs(dx) * 2) {
                        cancelPending()
                        showFilter()
                        return true
                    }

                    return false
                }

                MotionEvent.ACTION_UP -> {
                    val dx = e.x - startX
                    val dy = e.y - startY
                    val duration = System.currentTimeMillis() - downTime

                    cancelPending()

                    if (abs(dx) < clickSlop && abs(dy) < clickSlop && duration < 2000) {
                        val child = rv.findChildViewUnder(e.x, e.y)
                        if (child != null) {
                            val position = rv.getChildAdapterPosition(child)
                            val app = bookmarkAdapter.getItemAt(position)
                            if (app != null) {
                                launchApp(app)
                            }
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_CANCEL -> {
                    cancelPending()
                    return false
                }

                else -> return false
            }
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
    })
}

internal fun MainActivity.loadBookmarks() {
    val bookmarked = Prefs.getBookmarks()
    val maxSlots = calculateSpanCount() * 7
    val appsMap = allApps.associateBy { it.id }

    val grid = MutableList<AppInfo?>(maxSlots) { null }

    bookmarked.forEachIndexed { index, id ->
        if (index < maxSlots && id.isNotEmpty()) {
            appsMap[id]?.let { app ->
                val customLabel = Prefs.getCustomLabel(app.id)
                grid[index] = if (customLabel != null) app.copy(label = customLabel) else app
            }
        }
    }

    bookmarkAdapter.submitList(grid)
}

internal fun MainActivity.toggleBookmark(app: AppInfo) {
    if (Prefs.isBookmarked(app.id)) {
        Prefs.removeBookmark(app.id)
        loadBookmarks()
    } else {
        showAddBookmarkDialog(app)
    }
}

private fun MainActivity.showAddBookmarkDialog(app: AppInfo) {
    val input = EditText(this).apply {
        setText(app.label)
        selectAll()
        setTextColor(ThemeUtils.getTextColor())
        setHintTextColor(ThemeUtils.getSecondaryTextColor())
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
    }

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle("Set bookmark label")
        .setView(input)
        .setPositiveButton("Add") { _, _ ->
            val customLabel = input.text?.toString()?.trim() ?: ""
            Prefs.setCustomLabel(app.id, customLabel)
            Prefs.addBookmark(app.id)
            loadBookmarks()
        }
        .setNegativeButton("Cancel", null)
        .create()

    dialog.setOnShowListener {
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    dialog.show()
}

internal fun MainActivity.renameBookmark(app: AppInfo) {
    val input = EditText(this).apply {
        setText(app.label)
        selectAll()
        setTextColor(ThemeUtils.getTextColor())
        setHintTextColor(ThemeUtils.getSecondaryTextColor())
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
    }

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle("Rename bookmark")
        .setView(input)
        .setPositiveButton("Save") { _, _ ->
            val newLabel = input.text?.toString()?.trim() ?: ""
            Prefs.setCustomLabel(app.id, newLabel)
            loadBookmarks()
        }
        .setNegativeButton("Cancel", null)
        .create()

    dialog.setOnShowListener {
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    dialog.show()
}

private fun MainActivity.showBookmarkOptions(app: AppInfo) {
    val options = arrayOf("Rename bookmark", "Hide bookmark")
    MaterialAlertDialogBuilder(this)
        .setTitle(app.label.ifEmpty { "Bookmark" })
        .setItems(options) { _, which ->
            when (which) {
                0 -> renameBookmark(app)
                1 -> hideBookmark(app)
            }
        }
        .show()
}

private fun MainActivity.hideBookmark(app: AppInfo) {
    Prefs.removeBookmark(app.id)
    loadBookmarks()
}
