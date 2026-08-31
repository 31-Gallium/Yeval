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
            if (field != value) {
                field = value
                updateShader()
                invalidate()
            }
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
        strokeWidth = 3.5f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }

    private val staticBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#181d28")
    }

    private val cardRect = RectF()
    private var sweepGradient: SweepGradient? = null

    init {
        setWillNotDraw(false)
        orientation = VERTICAL
        setPadding(56, 64, 56, 56)
    }

    private fun updateShader() {
        if (width <= 0 || height <= 0) return
        val cx = width / 2f
        val cy = height / 2f

        // Smooth, symmetrical, closed-loop gradient:
        // 0° and 360° have the identical dark slate base color (#18202f).
        // The frosted cyan/white light beam sweeps smoothly across 120°-240°.
        // This guarantees zero cutoff seams, zero jumps, and 100% continuous fluid rotation.
        val glowColors = if (isConnected) {
            intArrayOf(
                Color.parseColor("#141b27"), // 0 deg
                Color.parseColor("#141b27"), // 90 deg
                Color.parseColor("#38bdf8"), // 150 deg (glow start)
                Color.parseColor("#ffffff"), // 180 deg (frosted highlight peak)
                Color.parseColor("#38bdf8"), // 210 deg (glow end)
                Color.parseColor("#141b27"), // 270 deg
                Color.parseColor("#141b27")  // 360 deg (identical to 0 deg!)
            )
        } else {
            intArrayOf(
                Color.parseColor("#18202d"), // 0 deg
                Color.parseColor("#18202d"), // 90 deg
                Color.parseColor("#64748b"), // 150 deg (glow start)
                Color.parseColor("#f8fafc"), // 180 deg (frosted highlight peak)
                Color.parseColor("#94a3b8"), // 210 deg (glow end)
                Color.parseColor("#18202d"), // 270 deg
                Color.parseColor("#18202d")  // 360 deg (identical to 0 deg!)
            )
        }

        val glowPositions = floatArrayOf(
            0.0f, 0.25f, 0.42f, 0.50f, 0.58f, 0.75f, 1.0f
        )

        sweepGradient = SweepGradient(cx, cy, glowColors, glowPositions)
        strokePaint.shader = sweepGradient
        glowPaint.shader = sweepGradient
    }

    private fun startGlowAnimation() {
        if (glowAnimator != null) return
        glowAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 5000L // Smooth 5-second clockwise orbit
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
        glowAngle = 0f
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
        updateShader()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        if (isOnline) {
            // Online Background Fill
            bgPaint.color = Color.parseColor("#131622")
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

            if (sweepGradient != null) {
                shaderMatrix.setRotate(glowAngle, cx, cy)
                sweepGradient?.setLocalMatrix(shaderMatrix)

                // 1. Soft Frosted Outer Glow
                glowPaint.alpha = if (isConnected) 140 else 90
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, glowPaint)

                // 2. Crisp Seamless Rotating Border Stroke
                strokePaint.alpha = 255
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, strokePaint)
            }
        } else {
            // Offline Card: Dark Obsidian muted styling
            bgPaint.color = Color.parseColor("#090b10")
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, staticBorderPaint)
        }

        super.onDraw(canvas)
    }
}
