package com.mobilecontroller

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.view.View
import androidx.core.content.ContextCompat

class BatteryGamepadView(context: Context) : View(context) {

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val bgArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#151822")
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#ffffff")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        color = Color.parseColor("#f8fafc")
    }

    private var batteryLevel = 0.5f // 0 to 1
    private var isCharging = false
    
    private var pulseAlpha = 255
    private var pulseAnimator: ValueAnimator? = null

    private var gamepadDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_gamepad)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    batteryLevel = (level / scale.toFloat()).coerceIn(0f, 1f)
                }
                
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL
                
                if (isCharging) startPulse() else stopPulse()
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(batteryReceiver)
        stopPulse()
    }

    private fun startPulse() {
        if (pulseAnimator != null) return
        pulseAnimator = ValueAnimator.ofInt(160, 255).apply {
            duration = 1200
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

    private fun updateShader() {
        val colors = if (isCharging) {
            intArrayOf(Color.parseColor("#38bdf8"), Color.parseColor("#ffffff"))
        } else {
            intArrayOf(Color.parseColor("#38bdf8"), Color.parseColor("#7dd3fc"))
        }
        
        val shader = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            colors, null, Shader.TileMode.CLAMP
        )
        arcPaint.shader = shader
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateShader()
        
        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(width, height) / 2f - 16f

        val startAngle = 135f
        val maxSweep = 270f
        
        // Draw background dark groove
        canvas.drawArc(
            cx - radius, cy - radius, cx + radius, cy + radius,
            startAngle, maxSweep, false, bgArcPaint
        )

        // Draw battery highlighted arc
        val sweepAngle = maxSweep * batteryLevel
        if (isCharging) {
            arcPaint.alpha = pulseAlpha
            dotPaint.alpha = pulseAlpha
        } else {
            arcPaint.alpha = 255
            dotPaint.alpha = 255
        }
        
        canvas.drawArc(
            cx - radius, cy - radius, cx + radius, cy + radius,
            startAngle, sweepAngle, false, arcPaint
        )

        // Draw glowing dot at the end
        if (sweepAngle > 0) {
            val endAngle = Math.toRadians((startAngle + sweepAngle).toDouble())
            val dotX = cx + radius * Math.cos(endAngle).toFloat()
            val dotY = cy + radius * Math.sin(endAngle).toFloat()
            
            val glowPaint = Paint(dotPaint).apply { 
                color = Color.parseColor("#38bdf8")
                alpha = if (isCharging) pulseAlpha / 2 else 80 
                maskFilter = android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(dotX, dotY, 9f, glowPaint)
            canvas.drawCircle(dotX, dotY, 5f, dotPaint)
        }

        // Draw Gamepad silhouette in center
        val iconSize = (radius * 1.15f).toInt()
        gamepadDrawable?.setBounds(
            (cx - iconSize/2).toInt(), 
            (cy - iconSize/2).toInt() - 2, 
            (cx + iconSize/2).toInt(), 
            (cy + iconSize/2).toInt() - 2
        )
        gamepadDrawable?.setTint(Color.parseColor("#222838"))
        gamepadDrawable?.draw(canvas)

        // Draw Battery Percentage Text over the gamepad icon
        val pct = (batteryLevel * 100).toInt().coerceIn(0, 100)
        val pctText = "${pct}%"
        
        textPaint.textSize = radius * 0.42f
        textPaint.color = if (isCharging) Color.parseColor("#38bdf8") else Color.parseColor("#f8fafc")
        
        val fontMetrics = textPaint.fontMetrics
        val textY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(pctText, cx, textY, textPaint)
    }
}
