package com.example.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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

        // Always start clean — kill any recycled background or clip
        imageView.background = null
        imageView.clipToOutline = false

        if (iconPack.isNotBlank() && icon != null && fromPack) {
            imageView.setImageDrawable(icon)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER

            val theme = Prefs.getTheme()
            if (theme == "dark" || theme == "oled") {
                // Invert black monochrome icons to white so they remain visible
                val matrix = ColorMatrix(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f,  1f,  0f
                ))
                imageView.colorFilter = ColorMatrixColorFilter(matrix)
            } else {
                imageView.colorFilter = null
            }
        } else {
            // Default / system icons: keep original colors, no filter
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
                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                }
            }
        }
    }

    private fun Float.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
