package com.example.gammaplayer

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.SeekBar
import kotlin.math.abs

class VerticalSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatSeekBar(context, attrs, defStyleAttr) {

    private var startY = 0f
    private var lastProgress = 0
    private var isDragging: Boolean = false
        private set
    private var onStartTrackingTouch: ((SeekBar) -> Unit)? = null
    private var onStopTrackingTouch: ((SeekBar) -> Unit)? = null
    private var onProgressChanged: ((SeekBar, Int, Boolean) -> Unit)? = null


    override fun setOnSeekBarChangeListener(listener: OnSeekBarChangeListener) {
        onStartTrackingTouch = { bar -> listener.onStartTrackingTouch(bar) }
        onStopTrackingTouch = { bar -> listener.onStopTrackingTouch(bar) }
        onProgressChanged = { bar, progress, fromUser ->
            listener.onProgressChanged(bar, progress, fromUser)
        }
    }

    init {
        // Set progress drawable if not set in XML
        progressDrawable = context.getDrawable(R.drawable.vertical_seekbar)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onDraw(c: Canvas) {
        // Rotate the canvas and draw the seekbar vertically
        c.rotate(-90f)
        c.translate(-height.toFloat(), 0f)
        super.onDraw(c)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                startY = event.y
                if (event.x > width || event.x < 0) {
                    // Touch outside seekbar width - let parent handle it
                    return false
                }
                isDragging = true
                parent.requestDisallowInterceptTouchEvent(true)
                onStartTrackingTouch?.invoke(this)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                isDragging = false
                if (!isDragging) {
                    // Check if this is a vertical swipe (not horizontal)
                    if (abs(event.y - startY) > abs(event.x - event.x)) {
                        isDragging = true
                        parent.requestDisallowInterceptTouchEvent(true)
                        onStartTrackingTouch?.invoke(this)
                    } else {
                        return false // Let parent handle horizontal swipes
                    }
                }

                val progress = max - (max * event.y / height).toInt()
                val clampedProgress = progress.coerceIn(0, max)
                if (clampedProgress != lastProgress) {
                    lastProgress = clampedProgress
                    setProgress(clampedProgress)
                    onProgressChanged?.invoke(this, clampedProgress, true)
                }
                onSizeChanged(width, height, 0, 0)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    onStopTrackingTouch?.invoke(this)
                    parent.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                return false
            }
        }
        return false
    }

    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        onSizeChanged(width, height, 0, 0)
    }
}