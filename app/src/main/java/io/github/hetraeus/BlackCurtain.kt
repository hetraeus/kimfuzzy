package io.github.hetraeus.kimfuzzy

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
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
                        binding.blackCurtain.isVisible = false
                        setSystemUiVisibility(hide = false)
                        showFilter()
                        return true
                    }
                }
            }
            return true
        }
    })

    applyBlackCurtainState()
}

internal fun MainActivity.applyBlackCurtainState() {
    val shouldShow = Prefs.getBlackCurtain() && !binding.filterContainer.isVisible
    binding.blackCurtain.isVisible = shouldShow
    setSystemUiVisibility(hide = shouldShow)
}
