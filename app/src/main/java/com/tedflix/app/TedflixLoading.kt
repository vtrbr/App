package com.tedflix.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

object TedflixLoading {
    fun create(context: Context, message: String = "Por favor, aguarde"): LinearLayout {
        fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.rgb(5, 6, 9))
            setPadding(dp(28), dp(28), dp(28), dp(28))

            addView(ImageView(context).apply {
                setImageResource(R.drawable.tedflix_logo)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                contentDescription = "Tedflix"
            }, LinearLayout.LayoutParams(-1, dp(104)).apply {
                bottomMargin = dp(28)
            })

            addView(ProgressBar(context).apply {
                indeterminateTintList = ColorStateList.valueOf(Color.rgb(229, 28, 42))
            }, LinearLayout.LayoutParams(dp(50), dp(50)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            })

            addView(TextView(context).apply {
                text = message
                textSize = 17f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    fun show(view: View?, visible: Boolean) {
        view?.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
