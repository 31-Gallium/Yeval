package com.mobilecontroller

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout

class FrostDeviceCardView(context: Context) : LinearLayout(context) {

    var isOnline: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    startGlowAnimation()
                } else {
                    stopGlowAnimation()
                }
                invalidate()
            }
        }

    var isConnected: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var glowAngle: Float = 0f
    private var glowAnimator: ValueAnimator? = null
    private val shaderMatrix = Matrix()

    private val cornerRadius = 48f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }

    private val staticBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#181d28")
    }

    private val cardRect = RectF()

    init {
        setWillNotDraw(false)
        orientation = VERTICAL
        setPadding(56, 64, 56, 56)
    }

    private fun startGlowAnimation() {
        if (glowAnimator != null) return
        glowAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 6000L // Slow, elegant clockwise rotation
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                glowAngle = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopGlowAnimation() {
        glowAnimator?.cancel()
        glowAnimator = null
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isOnline) startGlowAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopGlowAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val strokeInset = 4f
        cardRect.set(strokeInset, strokeInset, w.toFloat() - strokeInset, h.toFloat() - strokeInset)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        if (isOnline) {
            // Online Background
            bgPaint.color = Color.parseColor("#131622")
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

            // Dynamic Clockwise Rotating Frost Glow
            val colors = if (isConnected) {
                intArrayOf(
                    Color.parseColor("#ffffff"),
                    Color.parseColor("#38bdf8"),
                    Color.parseColor("#0ea5e9"),
                    Color.parseColor("#1e293b"),
                    Color.parseColor("#38bdf8"),
                    Color.parseColor("#ffffff")
                )
            } else {
                intArrayOf(
                    Color.parseColor("#ffffff"),
                    Color.parseColor("#94a3b8"),
                    Color.parseColor("#1e293b"),
                    Color.parseColor("#475569"),
                    Color.parseColor("#e2e8f0"),
                    Color.parseColor("#ffffff")
                )
            }

            val positions = floatArrayOf(0f, 0.2f, 0.45f, 0.7f, 0.9f, 1.0f)
            val sweep = SweepGradient(cx, cy, colors, positions)
            shaderMatrix.setRotate(glowAngle, cx, cy)
            sweep.setLocalMatrix(shaderMatrix)

            // 1. Soft Frosted Outer Glow
            glowPaint.shader = sweep
            glowPaint.alpha = if (isConnected) 120 else 75
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, glowPaint)

            // 2. Sharp Clockwise Rotating Border Stroke
            strokePaint.shader = sweep
            strokePaint.alpha = 240
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, strokePaint)
        } else {
            // Offline Card: Dark Obsidian muted styling
            bgPaint.color = Color.parseColor("#090b10")
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, staticBorderPaint)
        }

        super.onDraw(canvas)
    }
}
