package com.example.captionbardemo.caption

import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsetsController
import android.widget.TextView
import android.widget.FrameLayout
import android.view.Gravity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import com.example.captionbardemo.R

object CaptionBarUtils {
    data class CaptionDebug(
        val captionTop: Int,
        val statusTop: Int,
        val captionHeightPx: Int,
        val captionRects: List<Rect>,
        val drawableStartPx: Int,
        val drawableEndPx: Int,
        val headerWidthPx: Int,
    )

    class CaptionBarBinding internal constructor() {
        private val debugListeners = mutableListOf<(CaptionDebug) -> Unit>()
        internal fun dispatch(debug: CaptionDebug) {
            debugListeners.forEach { it(debug) }
        }
        fun addDebugListener(listener: (CaptionDebug) -> Unit) {
            debugListeners.add(listener)
        }
    }

    /**
     * Make the system caption bar transparent when supported (Android 15+). Runs after the decor view
     * is ready; reports the result via [onResult].
     */
    fun applyTransparentCaptionBar(window: Window, decorView: View, onResult: (String) -> Unit = {}) {
        decorView.post {
            val controller = window.insetsController
            val status = if (controller != null) {
                runCatching {
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
                        WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
                    )
                    "Transparent caption applied (platform controller)"
                }.getOrElse { ex ->
                    "Transparent caption failed: ${ex.javaClass.simpleName}"
                }
            } else {
                "No insets controller; cannot set transparent caption"
            }
            Log.d("CaptionBarUtils", status)
            onResult(status)
        }
    }

    /**
     * Bind a view-based caption bar: measures caption insets/rects, computes drawable span, and
     * positions the title TextView accordingly.
     */
    fun setWindowTitle(
        window: Window,
        titleText: String,
        captionColor: Int? = null,
        titleTextColor: Int = 0xFFFFFFFF.toInt(),
        onTransparentStatus: (String) -> Unit = {},
    ): CaptionBarBinding {
        val content = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val context = content.context
        val header = FrameLayout(context).apply { tag = "caption_bar_container" }
        val titleView = TextView(context).apply {
            setTextColor(titleTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            paint.isFakeBoldText = true
            setPadding(dpToPx(this, 6f), 0, dpToPx(this, 6f), 0)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        header.addView(
            titleView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL,
            ),
        )
        val headerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        )
        content.addView(header, headerParams)
        header.bringToFront()

        val bgColor = captionColor ?: context.getColor(R.color.caption_default_bg)
        val colors = intArrayOf(bgColor, bgColor)
        val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
        header.background = gradient

        val binding = CaptionBarBinding()
        titleView.text = titleText

        // Border + optional fill for the drawable box.
        val stroke = GradientDrawable().apply {
            setStroke(1, 0x99FFFFFF.toInt())
            captionColor?.let { setColor(it) }
        }

        applyTransparentCaptionBar(window, window.decorView) { status ->
            onTransparentStatus(status)
        }

        ViewCompat.setOnApplyWindowInsetsListener(header) { _, insets ->
            val captionInsets = insets.getInsets(WindowInsetsCompat.Type.captionBar())
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val captionRects = boundingRects(insets)

            val rectBottomPx = captionRects.maxOfOrNull { it.bottom } ?: 0
            val insetTopPx = maxOf(captionInsets.top, statusInsets.top, rectBottomPx)
            val captionHeightPx = if (insetTopPx > 0) insetTopPx else dpToPx(header, 40f)

            // Apply height to header and title.
            header.updateLayoutParams<ViewGroup.LayoutParams> {
                height = captionHeightPx
            }
            titleView.background = stroke
            titleView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = captionHeightPx
            }

            header.doOnLayout { v ->
                val headerWidthPx = v.width
                val (startPx, endPx) = findDrawableArea(headerWidthPx, captionRects)
                val widthPx = (endPx - startPx).coerceAtLeast(0)
                titleView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    width = widthPx
                    updateMargins(left = startPx)
                }
                binding.dispatch(
                    CaptionDebug(
                        captionTop = captionInsets.top,
                        statusTop = statusInsets.top,
                        captionHeightPx = captionHeightPx,
                        captionRects = captionRects,
                        drawableStartPx = startPx,
                        drawableEndPx = endPx,
                        headerWidthPx = headerWidthPx,
                    ),
                )
            }

            insets
        }
        header.requestApplyInsets()
        return binding
    }

    private fun dpToPx(view: View, dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, view.resources.displayMetrics).toInt()

    /**
     * Compute widest drawable span after subtracting stay-away rects from the header width.
     */
    fun findDrawableArea(headerWidthPx: Int, captionRects: List<Rect>): Pair<Int, Int> {
        if (headerWidthPx <= 0) return 0 to 0
        val sorted = captionRects.sortedBy { it.left }
        val merged = mutableListOf<Rect>()
        for (r in sorted) {
            if (merged.isEmpty()) {
                merged.add(Rect(r))
            } else {
                val last = merged.last()
                if (r.left <= last.right) {
                    last.right = maxOf(last.right, r.right)
                    last.top = minOf(last.top, r.top)
                    last.bottom = maxOf(last.bottom, r.bottom)
                } else {
                    merged.add(Rect(r))
                }
            }
        }
        val segments = mutableListOf<Pair<Int, Int>>()
        var cursor = 0
        merged.forEach { rect ->
            if (rect.left > cursor) segments.add(cursor to rect.left)
            cursor = maxOf(cursor, rect.right)
        }
        if (cursor < headerWidthPx) segments.add(cursor to headerWidthPx)
        if (segments.isEmpty()) return 0 to headerWidthPx
        return segments.maxByOrNull { it.second - it.first } ?: (0 to headerWidthPx)
    }

    private fun boundingRects(insets: WindowInsetsCompat): List<Rect> {
        return runCatching {
            val platform = insets.toWindowInsets() ?: return@runCatching emptyList<Rect>()
            val method = platform.javaClass.getMethod("getBoundingRects", Int::class.javaPrimitiveType)
            @Suppress("UNCHECKED_CAST")
            (method.invoke(platform, android.view.WindowInsets.Type.captionBar()) as? List<Rect>).orEmpty()
        }.getOrElse { emptyList() }
    }
}
