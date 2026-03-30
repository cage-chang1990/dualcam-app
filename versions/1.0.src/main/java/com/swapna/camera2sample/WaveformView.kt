package com.swapna.camera2sample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Audio waveform visualization view.
 * Displays real-time audio amplitude as a waveform.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val waveformPaint = Paint().apply {
        color = 0xFFFF1744.toInt() // Red color for recording indicator
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val bars = mutableListOf<Float>()
    private val maxBars = 50
    private var currentAmplitude = 0

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerY = height / 2f
        val barWidth = width / maxBars.toFloat()

        // Draw center line
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, waveformPaint)

        // Draw waveform bars
        for (i in bars.indices) {
            val x = i * barWidth + barWidth / 2
            val barHeight = bars[i] * height / 2 * 0.8f
            canvas.drawLine(x, centerY - barHeight, x, centerY + barHeight, waveformPaint)
        }
    }

    fun updateAmplitude(amplitude: Int) {
        // Normalize amplitude (0-32767 to 0-1 range)
        currentAmplitude = amplitude
        bars.add(amplitude / 32767f)
        if (bars.size > maxBars) {
            bars.removeAt(0)
        }
        invalidate()
    }

    fun clear() {
        bars.clear()
        invalidate()
    }
}
