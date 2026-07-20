package com.example.launcher

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.launcher.databinding.ActivityMainBinding
import com.example.launcher.databinding.ItemBookmarkBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    internal lateinit var binding: ActivityMainBinding
    internal lateinit var appAdapter: AppAdapter
    internal lateinit var bookmarkAdapter: BookmarkAdapter
    internal val handler = Handler(Looper.getMainLooper())
    internal val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    internal val dateFormat = SimpleDateFormat("d MMMM", Locale.getDefault())
    internal var allApps = listOf<AppInfo>()
    internal var isKeyboardVisible = false

    internal var floatingView: View? = null
    internal var dragTouchOffsetX = 0f
    internal var dragTouchOffsetY = 0f
    internal var dragSourcePos = -1
    internal var draggedApp: AppInfo? = null

    internal val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                Prefs.setBackgroundImage(it.toString())
                applyBackgroundImage()
                Toast.makeText(this, "Background image set", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to set background image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    internal val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_CHANGED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    if (intent.data?.schemeSpecificPart != packageName) {
                        loadApps()
                    }
                }
            }
        }
    }

    internal fun createBookmarkDragListener(): BookmarkAdapter.DragListener {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(this)
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindow()
        applyThemeColors()
        setupTopBar()
        setupBookmarks()
        setupAppList()
        setupFilter()
        setupKeyboardListener()
        setupSettings()
        setupGridTouchListener()
        setupBlackCurtain()
        applyBackgroundImage()

        loadApps()
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(packageReceiver)
    }

    override fun onStop() {
        super.onStop()
        if (Prefs.getEditMode()) {
            Prefs.setEditMode(false)
            updateEditModeIcon()
            binding.dropZone.visibility = View.GONE
            bookmarkAdapter = BookmarkAdapter(
                onRename = { app -> renameBookmark(app) },
                dragListener = null
            )
            binding.bookmarksGrid.adapter = bookmarkAdapter
            loadBookmarks()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    internal fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            "android.content.pm.action.CONFIRM_PIN_SHORTCUT" -> handleConfirmPinShortcut(intent)
            Intent.ACTION_MAIN -> {
                if (intent.categories?.contains(Intent.CATEGORY_HOME) == true) {
                    resetToBookmarks()
                }
            }
        }
    }

    internal fun resetToBookmarks() {
        binding.filter.text?.clear()
        binding.filter.clearFocus()
        hideKeyboard()
        binding.filterContainer.visibility = View.GONE
        binding.bookmarksGrid.visibility = View.VISIBLE
        binding.appList.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.settingsView.visibility = View.GONE
        loadBookmarks()
        applyBlackCurtainState()
    }

    internal fun handleConfirmPinShortcut(intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val request = launcherApps.getPinItemRequest(intent)
            if (request != null && request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                val shortcutInfo = request.shortcutInfo
                if (shortcutInfo != null) {
                    if (request.isValid) {
                        request.accept()
                        val pkg = shortcutInfo.`package`
                        val shortcutId = shortcutInfo.id
                        val id = "shortcut:$pkg:$shortcutId"
                        Prefs.addBookmark(id)
                        Toast.makeText(this, "Shortcut pinned: ${shortcutInfo.shortLabel}", Toast.LENGTH_SHORT).show()
                        loadApps()
                        handler.postDelayed({ loadApps() }, 500)
                    }
                }
            }
        }
    }

    internal fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()


    internal fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.filter, InputMethodManager.SHOW_IMPLICIT)
    }

    internal fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.filter.windowToken, 0)
    }

    internal fun setupKeyboardListener() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Apply insets padding to mainContent so the wallpaper stays
            // edge-to-edge behind status bar and navigation bar
            binding.mainContent.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                if (ime.bottom > 0) ime.bottom else systemBars.bottom
            )

            val wasVisible = isKeyboardVisible
            isKeyboardVisible = ime.bottom > systemBars.bottom

            if (wasVisible != isKeyboardVisible) {
                onKeyboardVisibilityChanged()
            }

            insets
        }
    }

    internal fun onKeyboardVisibilityChanged() {}

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            binding.settingsView.visibility == View.VISIBLE -> resetToBookmarks()
            binding.filterContainer.visibility == View.VISIBLE || isKeyboardVisible -> resetToBookmarks()
        }
    }
}
