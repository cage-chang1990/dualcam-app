package com.swapna.camera2sample

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * A draggable Picture-in-Picture container view.
 * Allows users to drag the PiP window around the screen.
 * Automatically snaps to the nearest corner when released.
 */
class DraggablePipView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // Edge margin in pixels
    private val edgeMargin = 48

    // Drag state
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var currentX = 0f
    private var currentY = 0f

    // Boundaries
    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var maxY = 0f

    // Click detection threshold
    private val clickThreshold = 10

    // Initial touch coordinates for click detection
    private var touchStartX = 0f
    private var touchStartY = 0f

    // Callbacks
    var onPositionChanged: ((Float, Float) -> Unit)? = null
    var onPipClicked: (() -> Unit)? = null

    // PiP child view reference
    private var pipTextureView: android.view.TextureView? = null

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateBounds()

        // Set initial position to top-right corner
        post {
            if (currentX == 0f && currentY == 0f) {
                currentX = maxX
                currentY = edgeMargin.toFloat()
                updatePosition()
            }
        }
    }

    private fun calculateBounds() {
        val viewWidth = width.coerceAtLeast(1)
        val viewHeight = height.coerceAtLeast(1)

        val aspectRatio = 16f / 9f

        // Limit PiP to max 30% of screen width
        val maxPipWidth = (viewWidth * 0.3f).toInt().coerceAtLeast(200)
        val pipWidth = maxPipWidth
        val pipHeight = (pipWidth / aspectRatio).toInt()

        // Configure pipTextureView size if exists
        pipTextureView?.let { textureView ->
            textureView.layoutParams = LayoutParams(pipWidth, pipHeight)
        }

        // Calculate drag boundaries (full parent bounds)
        minX = edgeMargin.toFloat()
        maxX = viewWidth - pipWidth - edgeMargin.toFloat()
        minY = edgeMargin.toFloat()
        maxY = viewHeight - pipHeight - edgeMargin.toFloat()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                touchStartX = event.rawX
                touchStartY = event.rawY
                elevation = 16f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastTouchX
                val dy = event.rawY - lastTouchY

                // Detect drag if moved beyond threshold
                if (!isDragging && (abs(dx) > clickThreshold || abs(dy) > clickThreshold)) {
                    isDragging = true
                }

                if (isDragging) {
                    currentX = (currentX + dx).coerceIn(minX, maxX)
                    currentY = (currentY + dy).coerceIn(minY, maxY)
                    updatePosition()
                }

                lastTouchX = event.rawX
                lastTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                elevation = 8f

                if (!isDragging) {
                    // This was a click
                    onPipClicked?.invoke()
                } else {
                    // Snap to nearest corner
                    snapToNearestCorner()
                    onPositionChanged?.invoke(currentX, currentY)
                }

                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePosition() {
        x = currentX
        y = currentY
    }

    private fun snapToNearestCorner() {
        val corners = listOf(
            Pair(edgeMargin.toFloat(), edgeMargin.toFloat()),  // Top-left
            Pair(maxX, edgeMargin.toFloat()),                  // Top-right
            Pair(edgeMargin.toFloat(), maxY),                  // Bottom-left
            Pair(maxX, maxY)                                  // Bottom-right
        )

        val nearest = corners.minByOrNull {
            abs(it.first - currentX) + abs(it.second - currentY)
        } ?: corners[1]

        currentX = nearest.first
        currentY = nearest.second
        updatePosition()
    }

    /**
     * Restore PiP position from saved coordinates
     */
    fun restorePosition(x: Float, y: Float) {
        currentX = x.coerceIn(minX, maxX)
        currentY = y.coerceIn(minY, maxY)
        updatePosition()
    }

    /**
     * Get current position for saving
     */
    fun getPosition(): Pair<Float, Float> = Pair(currentX, currentY)

    /**
     * Set the TextureView to be controlled by this PiP container
     */
    fun setPipTextureView(textureView: android.view.TextureView) {
        this.pipTextureView = textureView
    }
}
