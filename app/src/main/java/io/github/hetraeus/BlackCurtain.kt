package io.github.hetraeus.kimfuzzy

import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import kotlin.math.abs

/**
 * Black Curtain: an opaque overlay for distraction-free PiP video watching.
 * Sits on top of everything, blocking touches to the bookmarks grid and
 * other buttons, except: its own settings button (mirrors the real one)
 * and a swipe-up gesture, which still opens search.
 * Also hides system status bar icons (battery, network, etc.) when active.
 */

internal fun MainActivity.setupBlackCurtain() {
    binding.curtainSettingsBtn.setOnClickListener {
        showSettingsView()
    }

    binding.curtainBackBtn.setOnClickListener {
        Prefs.setBlackCurtain(false)
        resetToBookmarks()
        Toast.makeText(this, "Black Curtain disabled", Toast.LENGTH_SHORT).show()
    }

    val swipeThreshold = 60f * resources.displayMetrics.density
    binding.blackCurtain.setOnTouchListener(object : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - startX
                    val dy = startY - event.y
                    if (dy > swipeThreshold && dy > abs(dx) * 2) {
                        binding.blackCurtain.visibility = View.GONE
                        setSystemUiVisibility(hide = false)
                        showFilter()
                        return true
                    }
                }
            }
            // Consume every other touch so nothing behind the curtain
            // (bookmarks, top bar buttons, etc.) ever receives it.
            return true
        }
    })

    applyBlackCurtainState()
}

/** Shows/hides the curtain based on the pref, but never over the search view. */
internal fun MainActivity.applyBlackCurtainState() {
    val shouldShow = Prefs.getBlackCurtain() && binding.filterContainer.visibility != View.VISIBLE
    binding.blackCurtain.visibility = if (shouldShow) View.VISIBLE else View.GONE
    setSystemUiVisibility(hide = shouldShow)
}
