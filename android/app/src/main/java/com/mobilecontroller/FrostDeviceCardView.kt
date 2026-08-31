package com.mobilecontroller

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
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
                invalidate()
            }
        }

    private var glowProgress: Float = 0f
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

    // Outer soft frosted ambient halo glow
    private val haloGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }

    // Crisp high-contrast frosted white light core
    private val coreStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val cardRect = RectF()
    private val borderPath = Path()
    private val glowSegmentPath = Path()
    private val coreSegmentPath = Path()
    private val pathMeasure = PathMeasure()
    private var pathLength = 0f

    init {
        setWillNotDraw(false)
        orientation = VERTICAL
        setPadding(56, 64, 56, 56)
    }

    private fun startGlowAnimation() {
        if (glowAnimator != null) return
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4500L // Smooth 4.5s clockwise orbit
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                glowProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopGlowAnimation() {
        glowAnimator?.cancel()
        glowAnimator = null
        glowProgress = 0f
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
        
        borderPath.reset()
        borderPath.addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)
        pathMeasure.setPath(borderPath, true)
        pathLength = pathMeasure.length
    }

    /**
     * Extracts a path segment across distance boundaries, seamlessly wrapping around 0..pathLength.
     */
    private fun extractContourSegment(startDist: Float, length: Float, dst: Path) {
        dst.reset()
        if (pathLength <= 0f) return

        val s = (startDist % pathLength + pathLength) % pathLength
        val e = s + length

        if (e <= pathLength) {
            pathMeasure.getSegment(s, e, dst, true)
        } else {
            // First chunk to end of contour, second chunk from start of contour
            pathMeasure.getSegment(s, pathLength, dst, true)
            pathMeasure.getSegment(0f, e - pathLength, dst, true)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (isOnline) {
            // 1. Dark frosted slate background fill
            bgPaint.color = Color.parseColor("#131622")
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

            // 2. Base subtle dark border frame
            baseBorderPaint.color = if (isConnected) Color.parseColor("#162b3d") else Color.parseColor("#1a2232")
            canvas.drawPath(borderPath, baseBorderPaint)

            if (pathLength > 0f) {
                val glowLength = pathLength * 0.32f
                val startDist = glowProgress * pathLength

                // 3. Outer Frosted Ambient Halo Glow (with seamless 360-degree wrapping)
                extractContourSegment(startDist, glowLength, glowSegmentPath)
                haloGlowPaint.color = if (isConnected) Color.parseColor("#38bdf8") else Color.parseColor("#7dd3fc")
                haloGlowPaint.alpha = if (isConnected) 140 else 85
                canvas.drawPath(glowSegmentPath, haloGlowPaint)

                // 4. Sharp High-Contrast Frosted White Light Core (centered inside the glow beam)
                val coreLength = glowLength * 0.65f
                val coreStartDist = startDist + (glowLength - coreLength) / 2f
                extractContourSegment(coreStartDist, coreLength, coreSegmentPath)
                coreStrokePaint.color = if (isConnected) Color.parseColor("#ffffff") else Color.parseColor("#f8fafc")
                coreStrokePaint.alpha = 255
                canvas.drawPath(coreSegmentPath, coreStrokePaint)
            }
        } else {
            // Offline Card: Dark Obsidian muted stealth styling
            bgPaint.color = Color.parseColor("#090b10")
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)
            canvas.drawPath(borderPath, staticOfflineBorderPaint)
        }

        super.onDraw(canvas)
    }
}
