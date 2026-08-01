package io.github.hetraeus.kimfuzzy

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * A LinearLayout that explicitly overrides performClick().
 *
 * item_bookmark.xml's root view has a custom OnTouchListener (for
 * drag-to-reorder) alongside a click listener, which the plain LinearLayout
 * doesn't do automatically. Overriding performClick() here ensures the
 * accessibility click event and click sound fire consistently whenever
 * performClick() is invoked — including by TalkBack — not just when our
 * touch handler happens to call it directly.
 */
class ClickableLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
