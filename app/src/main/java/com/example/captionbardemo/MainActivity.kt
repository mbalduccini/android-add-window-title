package com.example.captionbardemo

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import com.example.captionbardemo.caption.CaptionBarUtils
import com.example.captionbardemo.caption.CaptionBarUtils.CaptionDebug

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val debugStatus = findViewById<TextView>(R.id.debug_status)
        val debugRects = findViewById<TextView>(R.id.debug_rects)
        val debugDrawable = findViewById<TextView>(R.id.debug_drawable)
        val debugInsets = findViewById<TextView>(R.id.debug_insets)

        // Bind caption layout and title into the system caption area (includes transparent call).
        val binding = CaptionBarUtils.setWindowTitle(
            window = window,
            titleText = "sample window title",
            captionColor = getColor(android.R.color.white),
            titleTextColor = getColor(android.R.color.black),
            onTransparentStatus = { status -> debugStatus.text = status },
        )
        binding.addDebugListener { info: CaptionDebug ->
            val rectText = info.captionRects.joinToString(prefix = "captionRects=[", postfix = "]") {
                "(${it.left},${it.top},${it.right},${it.bottom})"
            }
            debugRects.text = rectText
            debugInsets.text = "captionTop=${info.captionTop}px statusTop=${info.statusTop}px height=${info.captionHeightPx}px"
            debugDrawable.text = "drawable start=${info.drawableStartPx}px end=${info.drawableEndPx}px width=${info.drawableEndPx - info.drawableStartPx}px (headerWidth=${info.headerWidthPx}px)"
            // Push main content below the caption bar.
            findViewById<View>(R.id.main_container)?.let { container ->
                ViewCompat.setPaddingRelative(
                    container,
                    container.paddingStart,
                    info.captionHeightPx + dp(12),
                    container.paddingEnd,
                    container.paddingBottom,
                )
            }
        }
    }

    private fun dp(value: Int): Int =
        (resources.displayMetrics.density * value).toInt()
}
