package com.example.dualmapper.view

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.dualmapper.R
import java.io.File

open class MappedKeyView(context: Context) : FrameLayout(context) {
    private val iconView: ImageView
    private val labelView: TextView
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.TRANSPARENT
    }

    private var isEditing = false
    private var isSelected = false
    private var onLongPressListener: (() -> Unit)? = null
    private var onPositionChangedListener: ((x: Int, y: Int) -> Unit)? = null

    // 手势检测器：拖动与长按
    private val gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (isEditing) {
                onLongPressListener?.invoke()
            }
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (isEditing) {
                val parent = parent as? ViewGroup ?: return false
                val params = layoutParams as FrameLayout.LayoutParams
                val newLeft = (params.leftMargin - distanceX.toInt()).coerceIn(0, parent.width - width)
                val newTop = (params.topMargin - distanceY.toInt()).coerceIn(0, parent.height - height)
                params.leftMargin = newLeft
                params.topMargin = newTop
                layoutParams = params
                onPositionChangedListener?.invoke(newLeft, newTop)
                return true
            }
            return false
        }
    })

    // 缩放手势检测器
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleX = (scaleX * detector.scaleFactor).coerceIn(0.5f, 3.0f)
            scaleY = (scaleY * detector.scaleFactor).coerceIn(0.5f, 3.0f)
            applyScale()
            return true
        }
    })

    var keyId: String = ""
    var actionType: String = "tap"
    var targetX: Float = 0f
    var targetY: Float = 0f
    var endX: Float = 0f
    var endY: Float = 0f
    var duration: Long = 50
    var playerIndex: Int = 1
    var scaleX: Float = 1f
    var scaleY: Float = 1f
    var rotation: Float = 0f
    var iconPath: String? = null

    var label: String
        get() = labelView.text.toString()
        set(value) { labelView.text = value }

    var currentAlpha: Float
        get() = alpha
        set(value) { setOpacity((value * 255).toInt()) }

    var selected: Boolean
        get() = isSelected
        set(value) {
            isSelected = value
            invalidate()
        }

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.view_mapped_key, this, true)
        iconView = findViewById(R.id.key_icon)
        labelView = findViewById(R.id.key_label)
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        isLongClickable = true
        applyTheme(true)
    }

    fun setEditing(editing: Boolean) {
        isEditing = editing
        if (!editing) selected = false
        invalidate()
    }

    fun setOnLongPressListener(listener: () -> Unit) { onLongPressListener = listener }
    fun setOnPositionChangedListener(listener: (x: Int, y: Int) -> Unit) { onPositionChangedListener = listener }

    fun setLabel(text: String) { labelView.text = text }
    fun setLabelTextColor(color: Int) { labelView.setTextColor(color) }

    fun setIcon(drawableRes: Int) { iconView.setImageResource(drawableRes) }

    fun setIconFromFile(file: File) {
        Glide.with(context)
            .load(file)
            .override(128, 128)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_default_floating)
            .error(R.drawable.ic_default_floating)
            .circleCrop()
            .into(iconView)
    }

    fun applyIcon() {
        if (!iconPath.isNullOrEmpty()) {
            val file = File(context.filesDir, iconPath!!)
            if (file.exists()) {
                setIconFromFile(file)
                return
            }
        }
        setIcon(R.drawable.ic_default_floating)
    }

    open fun setOpacity(alpha: Int) {
        this.alpha = alpha / 255f
        iconView.alpha = alpha / 255f
        labelView.alpha = alpha / 255f
    }

    fun applyScale() {
        iconView.scaleX = scaleX
        iconView.scaleY = scaleY
        labelView.scaleX = scaleX
        labelView.scaleY = scaleY
        requestLayout()
    }

    fun setFocused(focused: Boolean) {
        isSelected = focused
        invalidate()
    }

    fun applyTheme(isDark: Boolean) {
        val bgColor = ContextCompat.getColor(context, if (isDark) R.color.dark_surface else R.color.light_surface)
        val borderColor = ContextCompat.getColor(context, R.color.primary_blue)
        val cornerRadius = 8f.dpToPx(context).toFloat()
        val strokeWidth = 1f.dpToPx(context)

        val drawable = GradientDrawable().apply {
            setColor(bgColor)
            this.cornerRadius = cornerRadius
            setStroke(strokeWidth, borderColor)
        }
        iconView.background = drawable
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isEditing) {
            borderPaint.color = if (isSelected) {
                ContextCompat.getColor(context, R.color.primary_blue)
            } else {
                ContextCompat.getColor(context, R.color.text_primary)
            }
            borderPaint.strokeWidth = if (isSelected) 8f else 4f
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditing) return super.onTouchEvent(event)

        // 优先处理缩放，如果正处于缩放中，消费事件
        val scaleHandled = scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) {
            return scaleHandled
        }

        // 再处理拖动与长按
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun Number.dpToPx(context: Context): Int {
        return (this.toFloat() * context.resources.displayMetrics.density).toInt()
    }
}
