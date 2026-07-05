package com.example.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView

object IconSize {
    fun apply(imageView: ImageView, icon: android.graphics.drawable.Drawable?) {
        val theme = Prefs.getIconSize()
        val ctx = imageView.context

        when (theme) {
            "small" -> applySized(imageView, icon, 36f.dpToPx(ctx))
            "rounded" -> applyRounded(imageView, icon, 40f.dpToPx(ctx))
            else -> applyDefault(imageView, icon, 48f.dpToPx(ctx))
        }
    }

    private fun applyDefault(imageView: ImageView, icon: android.graphics.drawable.Drawable?, size: Int) {
        imageView.layoutParams.width = size
        imageView.layoutParams.height = size
        imageView.setImageDrawable(icon)
        imageView.background = null
        imageView.clipToOutline = false
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun applySized(imageView: ImageView, icon: android.graphics.drawable.Drawable?, size: Int) {
        imageView.layoutParams.width = size
        imageView.layoutParams.height = size
        imageView.setImageDrawable(icon)
        imageView.background = null
        imageView.clipToOutline = false
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun applyRounded(imageView: ImageView, icon: android.graphics.drawable.Drawable?, size: Int) {
        imageView.layoutParams.width = size
        imageView.layoutParams.height = size

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = 8f.dpToPx(imageView.context).toFloat()
        bg.setColor(Color.parseColor("#20FFFFFF"))
        imageView.background = bg
        imageView.clipToOutline = true
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setImageDrawable(icon)
    }

    private fun Float.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
