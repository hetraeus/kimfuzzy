package io.github.hetraeus.kimfuzzy

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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.hetraeus.kimfuzzy.databinding.ActivityMainBinding
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

    internal var pendingBackgroundBucket: String = "light"

    internal val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                Prefs.setBackgroundImage(pendingBackgroundBucket, it.toString())
                applyBackgroundImage()
                Toast.makeText(this, "Background image set", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to set background image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    internal val importSettingsLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.use { stream ->
                    val json = stream.bufferedReader().use { reader -> reader.readText() }
                    if (Prefs.import(json)) {
                        Toast.makeText(this, "Settings imported. Restarting...", Toast.LENGTH_SHORT).show()
                        recreate()
                    } else {
                        Toast.makeText(this, "Invalid settings JSON", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to read settings file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    internal val exportSettingsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                contentResolver.openOutputStream(it)?.use { stream ->
                    val json = Prefs.export()
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    Toast.makeText(this, "Settings exported to file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to save settings file", Toast.LENGTH_SHORT).show()
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.settingsView.isVisible -> resetToBookmarks()
                    binding.filterContainer.isVisible || isKeyboardVisible -> resetToBookmarks()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

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
        loadApps()
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
        binding.filterContainer.isVisible = false
        binding.bookmarksGrid.isVisible = true
        binding.appList.isVisible = false
        binding.emptyState.isVisible = false
        binding.settingsView.isVisible = false
        loadBookmarks()
        applyBlackCurtainState()
    }

    internal fun handleConfirmPinShortcut(intent: Intent) {
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
                    Toast.makeText(this, "Shortcut bookmarked: ${shortcutInfo.shortLabel}", Toast.LENGTH_SHORT).show()
                    loadApps()
                    handler.postDelayed({ loadApps() }, 500)
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

            binding.mainContent.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                if (ime.bottom > 0) ime.bottom else systemBars.bottom
            )
            binding.settingsView.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
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
}
