package com.example.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView

object IconSize {
    fun apply(imageView: ImageView, icon: Drawable?, fromPack: Boolean = false) {
        val sizeTheme = Prefs.getIconSize()
        val ctx = imageView.context
        val iconPack = Prefs.getIconPack()

        val size = when (sizeTheme) {
            "small" -> 36f.dpToPx(ctx)
            "rounded" -> 40f.dpToPx(ctx)
            else -> 48f.dpToPx(ctx)
        }

        imageView.layoutParams.width = size
        imageView.layoutParams.height = size

        if (iconPack.isNotBlank() && icon != null && fromPack) {
            // Icon-pack icon: draw as-is, NEVER tint.
            // Monochrome vectors are black; on dark themes they need a subtle
            // background or they become invisible.
            imageView.setImageDrawable(icon)
            imageView.colorFilter = null
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER

            val theme = Prefs.getTheme()
            if (theme == "dark" || theme == "oled") {
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(Color.parseColor("#22FFFFFF"))
                imageView.background = bg
                imageView.clipToOutline = true
            } else {
                imageView.background = null
                imageView.clipToOutline = false
            }
        } else {
            // Default / system icon: keep original colors
            imageView.colorFilter = null
            imageView.setImageDrawable(icon)
            when (sizeTheme) {
                "rounded" -> {
                    val bg = GradientDrawable()
                    bg.shape = GradientDrawable.RECTANGLE
                    bg.cornerRadius = 8f.dpToPx(ctx).toFloat()
                    bg.setColor(Color.parseColor("#20FFFFFF"))
                    imageView.background = bg
                    imageView.clipToOutline = true
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                }
                else -> {
                    imageView.background = null
                    imageView.clipToOutline = false
                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                }
            }
        }
    }

    private fun Float.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
