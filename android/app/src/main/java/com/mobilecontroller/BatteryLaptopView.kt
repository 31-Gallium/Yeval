package com.mobilecontroller

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

class BatteryLaptopView(context: Context) : View(context) {

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val bgArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#151822")
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#ffffff")
    }

    private val laptopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#f0f9ff")
    }

    private val innerScreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0c1017")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    var batteryLevel = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var isConnected = false
        set(value) {
            field = value
            if (value) startPulse() else stopPulse()
            invalidate()
        }
    
    private var pulseAlpha = 255
    private var pulseAnimator: ValueAnimator? = null

    private fun startPulse() {
        if (pulseAnimator != null) return
        pulseAnimator = ValueAnimator.ofInt(180, 255).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                pulseAlpha = anim.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseAlpha = 255
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    private fun updateShader() {
        val colors = if (isConnected) {
            intArrayOf(Color.parseColor("#38bdf8"), Color.parseColor("#ffffff"))
        } else {
            intArrayOf(Color.parseColor("#64748b"), Color.parseColor("#cbd5e1"))
        }
        
        val shader = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            colors, null, Shader.TileMode.CLAMP
        )
        arcPaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Update shader if state changed
        updateShader()

        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(width, height) / 2f - 20f

        val startAngle = 135f
        val maxSweep = 270f
        
        // Draw background dark groove
        canvas.drawArc(
            cx - radius, cy - radius, cx + radius, cy + radius,
            startAngle, maxSweep, false, bgArcPaint
        )

        // Draw battery highlighted covered arc
        val sweepAngle = maxSweep * batteryLevel
        if (isConnected) {
            arcPaint.alpha = pulseAlpha
            dotPaint.alpha = pulseAlpha
            laptopPaint.color = Color.parseColor("#f0f9ff")
        } else {
            arcPaint.alpha = 255
            dotPaint.alpha = 255
            laptopPaint.color = Color.parseColor("#333a4c")
        }
        
        canvas.drawArc(
            cx - radius, cy - radius, cx + radius, cy + radius,
            startAngle, sweepAngle, false, arcPaint
        )

        // Draw glowing dot at the end of the covered arc
        if (sweepAngle > 0) {
            val endAngle = Math.toRadians((startAngle + sweepAngle).toDouble())
            val dotX = cx + radius * Math.cos(endAngle).toFloat()
            val dotY = cy + radius * Math.sin(endAngle).toFloat()
            
            val glowPaint = Paint(dotPaint).apply { 
                color = if (isConnected) Color.parseColor("#38bdf8") else Color.parseColor("#94a3b8")
                alpha = if (isConnected) pulseAlpha / 2 else 90 
                maskFilter = android.graphics.BlurMaskFilter(14f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(dotX, dotY, 12f, glowPaint)
            canvas.drawCircle(dotX, dotY, 7f, dotPaint)
        }

        // Draw Laptop Icon
        val screenWidth = radius * 1.05f
        val screenHeight = radius * 0.70f
        
        // Screen Outer Frame
        val screenRect = RectF(
            cx - screenWidth / 2,
            cy - screenHeight / 2 - 8f,
            cx + screenWidth / 2,
            cy + screenHeight / 2 - 8f
        )
        
        // Base / Keyboard Base
        val baseWidth = radius * 1.25f
        val baseRect = RectF(
            cx - baseWidth / 2,
            cy + screenHeight / 2 - 4f,
            cx + baseWidth / 2,
            cy + screenHeight / 2 + 10f
        )
        
        if (isConnected) {
            val glowPaint2 = Paint(laptopPaint).apply { 
                color = Color.parseColor("#38bdf8")
                alpha = pulseAlpha / 4
                maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(screenRect, 8f, 8f, glowPaint2)
            canvas.drawRoundRect(baseRect, 4f, 4f, glowPaint2)
        }
        
        canvas.drawRoundRect(screenRect, 8f, 8f, laptopPaint)
        canvas.drawRoundRect(baseRect, 4f, 4f, laptopPaint)

        // Inner Screen Display
        val innerScreenRect = RectF(
            screenRect.left + 5f,
            screenRect.top + 5f,
            screenRect.right - 5f,
            screenRect.bottom - 5f
        )
        innerScreenPaint.color = Color.parseColor("#0c1017")
        canvas.drawRoundRect(innerScreenRect, 5f, 5f, innerScreenPaint)

        // Battery Percentage Number inside the Screen
        val pct = (batteryLevel * 100).toInt().coerceIn(0, 100)
        val pctText = "${pct}%"
        
        textPaint.textSize = innerScreenRect.height() * 0.48f
        textPaint.color = if (isConnected) Color.parseColor("#38bdf8") else Color.parseColor("#8c96a5")
        
        val fontMetrics = textPaint.fontMetrics
        val textY = innerScreenRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(pctText, innerScreenRect.centerX(), textY, textPaint)
    }
}
