package io.github.hetraeus.kimfuzzy

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.core.graphics.toColorInt

object IconSize {
    fun apply(imageView: ImageView, icon: Drawable?, fromPack: Boolean = false) {
        val sizeTheme = Prefs.getIconSize()
        val ctx = imageView.context
        val iconPack = Prefs.getIconPack(Prefs.currentBackgroundBucket())

        val size = when (sizeTheme) {
            "small" -> 36f.dpToPx(ctx)
            "rounded" -> 40f.dpToPx(ctx)
            else -> 48f.dpToPx(ctx)
        }

        imageView.layoutParams.width = size
        imageView.layoutParams.height = size

        imageView.background = null
        imageView.clipToOutline = false
        imageView.colorFilter = null

        if (iconPack.isNotBlank() && icon != null && fromPack) {
            val theme = Prefs.getTheme()
            val iconTint = when (theme) {
                "dark" -> Color.WHITE
                else -> "#3E2B1F".toColorInt()
            }

            val mutableIcon = icon.mutate()
            mutableIcon.setTint(iconTint)
            mutableIcon.setTintMode(PorterDuff.Mode.SRC_IN)

            val bgColor = when (theme) {
                "dark" -> Color.BLACK
                else -> "#F4ECD8".toColorInt()
            }

            val bg = GradientDrawable()
            bg.shape = GradientDrawable.RECTANGLE
            bg.setColor(bgColor)
            imageView.background = bg

            imageView.setImageDrawable(mutableIcon)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        } else {
            imageView.colorFilter = null
            imageView.setImageDrawable(icon)
            when (sizeTheme) {
                "rounded" -> {
                    val bg = GradientDrawable()
                    bg.shape = GradientDrawable.RECTANGLE
                    bg.cornerRadius = 8f.dpToPx(ctx).toFloat()
                    bg.setColor("#20FFFFFF".toColorInt())
                    imageView.background = bg
                    imageView.clipToOutline = true
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                }
                else -> {
                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                }
            }
        }
    }

    private fun Float.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
