package com.example.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.core.graphics.drawable.DrawableCompat

object IconSize {
    fun apply(imageView: ImageView, icon: android.graphics.drawable.Drawable?) {
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

        if (iconPack.isNotBlank() && icon != null) {
            // Icon pack active: tint with text color, no background, just the drawing
            val tinted = DrawableCompat.wrap(icon.mutate())
            DrawableCompat.setTint(tinted, ThemeUtils.getTextColor())
            imageView.setImageDrawable(tinted)
            imageView.background = null
            imageView.clipToOutline = false
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        } else {
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
