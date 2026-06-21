package net.asklab.caption

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Region
import android.graphics.RegionIterator
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins

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

    class CaptionBarBinding internal constructor(
        private val titleView: TextView,
        private val actionButton: CaptionActionButton?,
        private val activeTextColor: Int,
        private val inactiveTextColor: Int,
    ) {
        private val debugListeners = mutableListOf<(CaptionDebug) -> Unit>()
        internal fun dispatch(debug: CaptionDebug) {
            debugListeners.forEach { it(debug) }
        }
        fun addDebugListener(listener: (CaptionDebug) -> Unit) {
            debugListeners.add(listener)
        }

        fun setTitle(text: String) {
            titleView.text = text
        }

        fun setActionClickListener(listener: (() -> Unit)?) {
            actionButton?.setOnClickListener {
                listener?.invoke()
            }
        }

        fun setActive(active: Boolean) {
            val color = if (active) activeTextColor else inactiveTextColor
            titleView.setTextColor(color)
            actionButton?.setDotColor(color)
        }

        fun handleActionTouch(event: MotionEvent): Boolean {
            return actionButton?.handleRawTouch(event) ?: false
        }
    }

    fun setWindowTitle(
        window: Window,
        titleText: String,
        captionColor: Int? = null,
        useChromeCaptionBackground: Boolean = false,
        placeActionAfterDrawableArea: Boolean = false,
        titleTextColor: Int = 0xFFFFFFFF.toInt(),
        inactiveTitleTextColor: Int = applyAlpha(titleTextColor, 0.45f),
        actionContentDescription: String? = null,
        onActionClick: (() -> Unit)? = null,
        onTransparentStatus: (String) -> Unit = {},
    ): CaptionBarBinding {
        val decor = window.decorView as ViewGroup
        val context = decor.context
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
        val actionButton = CaptionActionButton(context, titleTextColor).apply {
            visibility = if (onActionClick == null) View.GONE else View.VISIBLE
            contentDescription = actionContentDescription
            setOnClickListener {
                onActionClick?.invoke()
            }
        }
        header.addView(
            actionButton,
            FrameLayout.LayoutParams(
                dpToPx(header, 32f),
                dpToPx(header, 40f),
                Gravity.TOP or Gravity.START,
            ),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        decor.post {
            if (header.parent != null) return@post
            runCatching {
                decor.addView(
                    header,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP,
                    ),
                )
                header.bringToFront()
                header.requestApplyInsets()
                Log.d("CaptionBarUtils", "caption visual layer attached to decor")
            }.onFailure { ex ->
                Log.e("CaptionBarUtils", "caption visual layer attach failed", ex)
            }
        }

        val bgColor = when {
            captionColor != null -> captionColor
            useChromeCaptionBackground -> context.getColor(R.color.caption_chrome_bg)
            else -> context.getColor(R.color.caption_default_bg)
        }
        val colors = intArrayOf(bgColor, bgColor)
        val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
        header.background = gradient

        val binding = CaptionBarBinding(titleView, actionButton, titleTextColor, inactiveTitleTextColor)
        titleView.text = titleText

        val titleBackground = if (useChromeCaptionBackground) {
            null
        } else {
            GradientDrawable().apply {
                setStroke(1, 0x99FFFFFF.toInt())
                captionColor?.let { setColor(it) }
            }
        }

        applyTransparentCaptionBar(window, window.decorView) { status ->
            onTransparentStatus(status)
        }

        ViewCompat.setOnApplyWindowInsetsListener(header) { _, insets ->
            val headerCaptionRects = boundingRects(insets)
            val sourceInsets = if (headerCaptionRects.isEmpty()) {
                ViewCompat.getRootWindowInsets(decor) ?: insets
            } else {
                insets
            }
            val captionInsets = sourceInsets.getInsets(WindowInsetsCompat.Type.captionBar())
            val statusInsets = sourceInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val captionRects = boundingRects(sourceInsets)

            val rectBottomPx = captionRects.maxOfOrNull { it.bottom } ?: 0
            val insetTopPx = maxOf(captionInsets.top, statusInsets.top, rectBottomPx)
            val captionHeightPx = if (insetTopPx > 0) insetTopPx else dpToPx(header, 40f)

            // Apply height to header and title.
            header.updateLayoutParams<ViewGroup.LayoutParams> {
                height = captionHeightPx
            }
            titleView.background = titleBackground
            titleView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = captionHeightPx
            }
            val buttonWidthPx = minOf(captionHeightPx, dpToPx(header, 32f))

            header.doOnLayout { v ->
                val headerWidthPx = v.width
                val drawableArea = findDrawableArea(
                    Rect(0, 0, headerWidthPx, captionHeightPx),
                    captionRects,
                )
                val actionWidthPx = if (actionButton.visibility == View.VISIBLE) {
                    if (placeActionAfterDrawableArea) {
                        buttonWidthPx
                    } else {
                        minOf(buttonWidthPx, drawableArea.width()).coerceAtLeast(0)
                    }
                } else {
                    0
                }
                val actionStartPx = if (placeActionAfterDrawableArea && actionWidthPx > 0) {
                    drawableArea.right
                } else {
                    drawableArea.right - actionWidthPx
                }
                titleView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    width = (actionStartPx - drawableArea.left).coerceAtLeast(0)
                    height = drawableArea.height().coerceAtLeast(0)
                    updateMargins(left = drawableArea.left, top = drawableArea.top)
                }
                actionButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    width = buttonWidthPx
                    height = captionHeightPx
                    updateMargins(left = actionStartPx, top = drawableArea.top)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && actionWidthPx > 0) {
                    val actionExclusionRect = Rect(
                        actionStartPx,
                        drawableArea.top,
                        actionStartPx + actionWidthPx,
                        drawableArea.top + captionHeightPx,
                    )
                    header.systemGestureExclusionRects = listOf(actionExclusionRect)
                    Log.d("CaptionBarUtils", "caption action exclusion=$actionExclusionRect")
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    header.systemGestureExclusionRects = emptyList()
                }
                binding.dispatch(
                    CaptionDebug(
                        captionTop = captionInsets.top,
                        statusTop = statusInsets.top,
                        captionHeightPx = captionHeightPx,
                        captionRects = captionRects,
                        drawableStartPx = drawableArea.left,
                        drawableEndPx = drawableArea.right,
                        headerWidthPx = headerWidthPx,
                    ),
                )
                Log.d(
                    "CaptionBarUtils",
                    "caption layout header=$headerWidthPx drawable=$drawableArea actionStart=$actionStartPx actionEnd=${actionStartPx + actionWidthPx} actionWidth=$actionWidthPx rects=$captionRects",
                )
            }

            insets
        }
        if (header.parent != null) {
            header.requestApplyInsets()
        }
        return binding
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

    private fun dpToPx(view: View, dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, view.resources.displayMetrics).toInt()

    /**
     * Compute the widest drawable rect after subtracting stay-away rects from the caption area.
     */
    fun findDrawableArea(captionArea: Rect, captionRects: List<Rect>): Rect {
        if (captionArea.isEmpty) return Rect()
        if (captionRects.isEmpty()) return Rect(captionArea)

        val region = Region(captionArea)
        captionRects.forEach { rect ->
            region.op(rect, Region.Op.DIFFERENCE)
        }
        if (region.isEmpty) return Rect()

        val widest = Rect()
        val iterator = RegionIterator(region)
        val rect = Rect()
        while (iterator.next(rect)) {
            if (rect.width() > widest.width()) {
                widest.set(rect)
            }
        }
        return widest
    }

    private fun boundingRects(insets: WindowInsetsCompat): List<Rect> {
        return runCatching {
            val platform = insets.toWindowInsets() ?: return@runCatching emptyList<Rect>()
            val method = platform.javaClass.getMethod("getBoundingRects", Int::class.javaPrimitiveType)
            @Suppress("UNCHECKED_CAST")
            (method.invoke(platform, android.view.WindowInsets.Type.captionBar()) as? List<Rect>).orEmpty()
        }.getOrElse { emptyList() }
    }

    internal class CaptionActionButton(context: Context, iconColor: Int) : View(context) {
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            style = Paint.Style.FILL
        }
        private val iconPath = Path()

        init {
            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            background = selectableItemBackgroundBorderless(context)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            val triangleWidth = minOf(width * 0.34f, height * 0.18f)
            val triangleHeight = triangleWidth * 0.62f
            iconPath.reset()
            iconPath.moveTo(centerX - triangleWidth / 2f, centerY - triangleHeight / 2f)
            iconPath.lineTo(centerX + triangleWidth / 2f, centerY - triangleHeight / 2f)
            iconPath.lineTo(centerX, centerY + triangleHeight / 2f)
            iconPath.close()
            canvas.drawPath(iconPath, iconPaint)
        }

        fun setDotColor(color: Int) {
            iconPaint.color = color
            invalidate()
        }

        override fun onHoverEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER ||
                event.actionMasked == MotionEvent.ACTION_HOVER_EXIT
            ) {
                Log.d(
                    "CaptionBarUtils",
                    "action hover action=${event.actionMasked} x=${event.x.toInt()} y=${event.y.toInt()} hovered=$isHovered",
                )
            }
            return super.onHoverEvent(event)
        }

        fun handleRawTouch(event: MotionEvent): Boolean {
            if (visibility != View.VISIBLE || !isShown || !isEnabled) return false
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val inside = event.rawX >= loc[0] &&
                    event.rawX <= loc[0] + width &&
                    event.rawY >= loc[1] &&
                    event.rawY <= loc[1] + height
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
                Log.d(
                    "CaptionBarUtils",
                    "action touch action=${event.actionMasked} raw=${event.rawX.toInt()},${event.rawY.toInt()} bounds=${loc[0]},${loc[1]},${loc[0] + width},${loc[1] + height} inside=$inside pressed=$isPressed",
                )
            }
            if (!inside && !isPressed) return false
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val wasPressed = isPressed
                    isPressed = false
                    if (inside && wasPressed) {
                        performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    true
                }
                else -> isPressed
            }
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private fun selectableItemBackgroundBorderless(context: Context) =
        TypedValue().let { outValue ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            context.getDrawable(outValue.resourceId)
        }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val alphaInt = (((color ushr 24) and 0xFF) * alpha).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alphaInt shl 24)
    }
}
