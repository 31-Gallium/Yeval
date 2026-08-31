package com.mobilecontroller

import android.animation.ValueAnimator
import android.content.Context
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

    private val cornerRadius = 48f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val baseBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#1b2333")
    }

    private val staticOfflineBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#181d28")
    }

    // Outer soft frosted ambient halo glow (full complete outline)
    private val haloGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }

    // Crisp high-contrast frosted white light core (full complete outline)
    private val coreStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val cardRect = RectF()
    private val shaderMatrix = Matrix()
    private var sweepGradient: SweepGradient? = null
    private var cx = 0f
    private var cy = 0f

    init {
        setWillNotDraw(false)
        orientation = VERTICAL
        setPadding(56, 64, 56, 56)
    }

    private fun startGlowAnimation() {
        if (glowAnimator != null) return
        glowAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 4500L // Smooth 4.5s clockwise 360° orbit
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            currentPlayTime = ((glowAngle / 360f) * 4500L).toLong()
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
        // Preserve glowAngle instead of resetting to 0f, so it resumes seamlessly if reattached
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
        cx = w / 2f
        cy = h / 2f
        updateShader()
    }

    private fun updateShader() {
        if (cx <= 0f || cy <= 0f) return

        // Symmetrical closed-loop continuous sweep gradient (0° and 360° are identical #182233)
        // High-contrast frosted highlight at 180° sweeps smoothly clockwise
        val colors = if (isConnected) {
            intArrayOf(
                Color.parseColor("#182233"), // 0 deg (dormant slate)
                Color.parseColor("#1a365d"), // 60 deg
                Color.parseColor("#0284c7"), // 120 deg (vibrant sky blue)
                Color.parseColor("#38bdf8"), // 160 deg (frost cyan)
                Color.parseColor("#ffffff"), // 180 deg (frosted silver highlight peak)
                Color.parseColor("#38bdf8"), // 200 deg (frost cyan)
                Color.parseColor("#0284c7"), // 240 deg (vibrant sky blue)
                Color.parseColor("#1a365d"), // 300 deg
                Color.parseColor("#182233")  // 360 deg (identical to 0 deg)
            )
        } else {
            intArrayOf(
                Color.parseColor("#182233"),
                Color.parseColor("#1e293b"),
                Color.parseColor("#334155"),
                Color.parseColor("#7dd3fc"),
                Color.parseColor("#ffffff"),
                Color.parseColor("#7dd3fc"),
                Color.parseColor("#334155"),
                Color.parseColor("#1e293b"),
                Color.parseColor("#182233")
            )
        }

        val positions = floatArrayOf(
            0.0f,
            0.17f,
            0.33f,
            0.44f,
            0.50f,
            0.56f,
            0.67f,
            0.83f,
            1.0f
        )

        sweepGradient = SweepGradient(cx, cy, colors, positions)
        haloGlowPaint.shader = sweepGradient
        coreStrokePaint.shader = sweepGradient
    }

    override fun onDraw(canvas: Canvas) {
        if (isOnline) {
            // 1. Dark frosted slate background fill
            bgPaint.color = Color.parseColor("#131622")
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

            // 2. Base subtle dark border frame
            baseBorderPaint.color = if (isConnected) Color.parseColor("#162b3d") else Color.parseColor("#1a2232")
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, baseBorderPaint)

            // 3. Full Complete Outline with Clockwise 360° Rotating Frost Glow
            if (sweepGradient != null) {
                shaderMatrix.setRotate(glowAngle, cx, cy)
                sweepGradient?.setLocalMatrix(shaderMatrix)

                // Soft outer ambient halo
                haloGlowPaint.alpha = if (isConnected) 120 else 75
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, haloGlowPaint)

                // Crisp bright core outline
                coreStrokePaint.alpha = 255
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, coreStrokePaint)
            }
        } else {
            // Offline Card: Dark Obsidian muted stealth styling
            bgPaint.color = Color.parseColor("#090b10")
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, staticOfflineBorderPaint)
        }

        super.onDraw(canvas)
    }
}
