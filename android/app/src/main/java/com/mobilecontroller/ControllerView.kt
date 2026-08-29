package com.mobilecontroller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import android.graphics.drawable.Drawable
import kotlin.math.hypot

class ControllerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // SVG Button VectorDrawables
    private val ltIdle: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_lt_idle)?.mutate()
    private val ltActive: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_lt_active)?.mutate()
    private val ltHalf: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_lt_half)?.mutate()

    private val rtIdle: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_rt_idle)?.mutate()
    private val rtActive: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_rt_active)?.mutate()
    private val rtHalf: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_rt_half)?.mutate()

    private val lbIdle: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_lb_idle)?.mutate()
    private val lbActive: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_lb_active)?.mutate()

    private val rbIdle: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_rb_idle)?.mutate()
    private val rbActive: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_rb_active)?.mutate()

    private val dpadIdle: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_dpad_idle)?.mutate()
    private val dpadActive: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_dpad_active)?.mutate()
    private val dpadUp: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_dpad_up)?.mutate()
    private val dpadDown: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_dpad_down)?.mutate()
    private val dpadLeft: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_dpad_left)?.mutate()
    private val dpadRight: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_dpad_right)?.mutate()
    private val dpadUpLeft: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_dpad_up_left)?.mutate()
    private val dpadUpRight: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_dpad_up_right)?.mutate()
    private val dpadDownLeft: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_dpad_down_left)?.mutate()
    private val dpadDownRight: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_dpad_down_right)?.mutate()

    private val btnAIdle: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_a_idle)?.mutate()
    private val btnAActive: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_a_active)?.mutate()
    private val btnBIdle: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_b_idle)?.mutate()
    private val btnBActive: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_b_active)?.mutate()
    private val btnXIdle: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_x_idle)?.mutate()
    private val btnXActive: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_x_active)?.mutate()
    private val btnYIdle: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_y_idle)?.mutate()
    private val btnYActive: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_y_active)?.mutate()

    private val stickBaseIdle: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_stick_base_idle)?.mutate()
    private val stickBaseActive: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_stick_base_active)?.mutate()
    private val stickKnobIdle: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_stick_knob_idle)?.mutate()
    private val stickKnobActive: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_btn_stick_knob_active)?.mutate()

    // Mini Menu Drawables
    private val xboxDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_xbox)?.mutate()
    private val playDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_play)?.mutate()
    private val viewDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_view)?.mutate()
    private val yevalDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_yeval)?.mutate()

    private var profile: ControllerProfile? = null
    var onStateChanged: ((buttons: Short, lt: Byte, rt: Byte, lx: Short, ly: Short, rx: Short, ry: Short, flags: Int) -> Unit)? = null
    var onMenuClicked: (() -> Unit)? = null
    var onCanvasTouch: (() -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDragComplete: (() -> Unit)? = null

    var isEditMode = false
    var isConnected = true
    var isServerFull = false
    var onExitClicked: (() -> Unit)? = null
    private val exitBtnRect = RectF()

    // Minimalist Premium Nordic Frost Colors
    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1f222c"); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val paintDarkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.parseColor("#1f222c")
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 4f, Color.parseColor("#40000000"))
    }
    private val paintDarkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#13151c"); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val paintActiveFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.parseColor("#0ea5e9")
        style = Paint.Style.FILL 
        setShadowLayer(16f, 0f, 8f, Color.parseColor("#60000000"))
    }
    private val paintActiveStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38bdf8"); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 24f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private val paintTextActive = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 24f; typeface = android.graphics.Typeface.DEFAULT_BOLD }

    // Mini button paints matching Nordic Frost
    private val paintMiniIdleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#151720"); style = Paint.Style.FILL }
    private val paintMiniIdleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4a4d5a"); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val paintMiniActiveFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0ea5e9"); style = Paint.Style.FILL }
    private val paintMiniActiveStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38bdf8"); style = Paint.Style.STROKE; strokeWidth = 2.5f }

    // Zone Color Palette (Tasteful, Less-Neon, Xbox Face Buttons)
    private val zoneColors = mapOf(
        "A" to (Color.parseColor("#3810b981") to Color.parseColor("#10b981")),
        "B" to (Color.parseColor("#38ef4444") to Color.parseColor("#ef4444")),
        "X" to (Color.parseColor("#383b82f6") to Color.parseColor("#3b82f6")),
        "Y" to (Color.parseColor("#38f59e0b") to Color.parseColor("#f59e0b")),
        "LT" to (Color.parseColor("#2e06b6d4") to Color.parseColor("#06b6d4")),
        "RT" to (Color.parseColor("#2ef97316") to Color.parseColor("#f97316")),
        "LB" to (Color.parseColor("#2e6366f1") to Color.parseColor("#6366f1")),
        "RB" to (Color.parseColor("#2eec4899") to Color.parseColor("#ec4899")),
        "DPAD" to (Color.parseColor("#2e14b8a6") to Color.parseColor("#14b8a6")),
        "LS" to (Color.parseColor("#2e818cf8") to Color.parseColor("#818cf8")),
        "RS" to (Color.parseColor("#2ea855f7") to Color.parseColor("#a855f7")),
        "START" to (Color.parseColor("#2e84cc16") to Color.parseColor("#84cc16")),
        "BACK" to (Color.parseColor("#2ed946ef") to Color.parseColor("#d946ef")),
        "MENU" to (Color.parseColor("#2e94a3b8") to Color.parseColor("#94a3b8")),
        "GUIDE" to (Color.parseColor("#2eeab308") to Color.parseColor("#eab308"))
    )
    private val paintZoneDynamicFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintZoneDynamicStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val paintEditMode = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.parseColor("#40ffffff")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private var draggedButtonId: String? = null
    
    private var leftThumbDx = 0f
    private var leftThumbDy = 0f
    private var rightThumbDx = 0f
    private var rightThumbDy = 0f

    // Trackpad & Mouse state
    private var rsLastX = 0f
    private var rsLastY = 0f
    private var rsLastMoveTimeMs = 0L
    private var rsMouseDx = 0
    private var rsMouseDy = 0
    private var rsMouseLeftClick = false
    private var rsMouseRightClick = false
    private val pressedButtons = mutableSetOf<String>()
    private val previousButtons = mutableSetOf<String>()

    private val zonePaths = mutableMapOf<String, android.graphics.Path>()
    private val curvedZonePolygons = mutableMapOf<String, List<ZoneVertexConfig>>()
    private var lastProfileId: String? = null
    private var lastScale = -1f
    private var lastCurveZones = true

    private var ltPressure = 0
    private var rtPressure = 0
    private var ltAnchorX = 0f
    private var ltAnchorY = 0f
    private var rtAnchorX = 0f
    private var rtAnchorY = 0f
    private var ltMaxHit = false
    private var rtMaxHit = false
    private var ltPointerId = -1
    private var rtPointerId = -1
    private var lsPointerId = -1
    private var rsPointerId = -1
    private var dpadPointerId = -1
    private var lsAnchorX = 0f
    private var lsAnchorY = 0f
    private var rsAnchorX = 0f
    private var rsAnchorY = 0f
    private var dpadAnchorX = 0f
    private var dpadAnchorY = 0f

    // Stick Tap Detection (Tap stick cap to trigger L3 / R3)
    private var lsDownTime: Long = 0L
    private var lsStartX: Float = 0f
    private var lsStartY: Float = 0f
    private var lsHasMoved: Boolean = false

    private var rsDownTime: Long = 0L
    private var rsStartX: Float = 0f
    private var rsStartY: Float = 0f
    private var rsHasMoved: Boolean = false

    private var l3TapUntilMs: Long = 0L
    private var r3TapUntilMs: Long = 0L

    fun setProfile(newProfile: ControllerProfile) {
        this.profile = newProfile
        zonePaths.clear()
        invalidate()
    }

    fun getProfile(): ControllerProfile? = profile

    private fun getScale(): Float = width / 1000f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = profile ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val s = getScale()
        paintText.textSize = 12f * s
        paintTextActive.textSize = 12f * s

        // Background is now handled by the parent container for wallpaper support

        if (isEditMode) {
            val xStep = w / 20f
            val yStep = h / 20f
            for (x in 0..20) {
                canvas.drawLine(x * xStep, 0f, x * xStep, h, paintGrid)
            }
            for (y in 0..20) {
                canvas.drawLine(0f, y * yStep, w, y * yStep, paintGrid)
            }
        }

        if (p.layoutMode == "zone") {
            drawZones(canvas, p, w, h, s)
        } else {
            // Draw Triggers (Scaled 230x92 using exact SVGs)
            p.triggers.forEach { t ->
                val trigW = 230f * s * t.scale
                val trigH = 92f * s * t.scale
                val cx = t.x * w
                val cy = t.y * h
                val isActive = pressedButtons.contains(t.id) || draggedButtonId == t.id
                val isLt = t.id == "LT"
                val pressure = if (isLt) ltPressure else rtPressure
                val d = when {
                    isActive -> if (isLt) ltActive else rtActive
                    pressure > 128 -> if (isLt) ltActive else rtActive
                    pressure > 25 -> if (isLt) ltHalf else rtHalf
                    else -> if (isLt) ltIdle else rtIdle
                }
                drawSvg(canvas, d, cx, cy, trigW, trigH)
                if (isEditMode) {
                    val rect = RectF(cx - trigW/2, cy - trigH/2, cx + trigW/2, cy + trigH/2)
                    canvas.drawRoundRect(rect, 16f * s, 16f * s, paintEditMode)
                }
            }

            // Draw Bumpers (Scaled 230x92 using exact SVGs)
            p.bumpers.forEach { b ->
                val bumpW = 230f * s * b.scale
                val bumpH = 92f * s * b.scale
                val cx = b.x * w
                val cy = b.y * h
                val isActive = pressedButtons.contains(b.id) || draggedButtonId == b.id
                val d = if (b.id == "LB") {
                    if (isActive) lbActive else lbIdle
                } else {
                    if (isActive) rbActive else rbIdle
                }
                drawSvg(canvas, d, cx, cy, bumpW, bumpH)
                if (isEditMode) {
                    val rect = RectF(cx - bumpW/2, cy - bumpH/2, cx + bumpW/2, cy + bumpH/2)
                    canvas.drawRoundRect(rect, 16f * s, 16f * s, paintEditMode)
                }
            }

            // Draw D-Pad (Scaled 168x168)
            val dpad = p.dpad
            val dpSize = 168f * s * dpad.scale
            val cxDp = dpad.x * w
            val cyDp = dpad.y * h
            val isUp = pressedButtons.contains("Up")
            val isDown = pressedButtons.contains("Down")
            val isLeft = pressedButtons.contains("Left")
            val isRight = pressedButtons.contains("Right")
            val isDragged = draggedButtonId == "DPAD"

            val dpadDrawable = when {
                isDragged -> dpadActive
                isUp && isLeft -> dpadUpLeft
                isUp && isRight -> dpadUpRight
                isDown && isLeft -> dpadDownLeft
                isDown && isRight -> dpadDownRight
                isUp -> dpadUp
                isDown -> dpadDown
                isLeft -> dpadLeft
                isRight -> dpadRight
                else -> dpadIdle
            }
            drawSvg(canvas, dpadDrawable, cxDp, cyDp, dpSize, dpSize)
            if (isEditMode) canvas.drawCircle(cxDp, cyDp, dpSize / 2f, paintEditMode)

            // Draw Face Buttons (Scaled 92x92)
            p.faceButtons.forEach { fb ->
                val size = 92f * s * fb.scale
                val cx = fb.x * w
                val cy = fb.y * h
                val isActive = pressedButtons.contains(fb.id) || draggedButtonId == fb.id
                val d = when(fb.id) {
                    "A" -> if (isActive) btnAActive else btnAIdle
                    "B" -> if (isActive) btnBActive else btnBIdle
                    "X" -> if (isActive) btnXActive else btnXIdle
                    "Y" -> if (isActive) btnYActive else btnYIdle
                    else -> if (isActive) btnAActive else btnAIdle
                }
                drawSvg(canvas, d, cx, cy, size, size)
                if (isEditMode) canvas.drawCircle(cx, cy, size / 2f, paintEditMode)
            }

            // Draw Meta Buttons (Scaled 28px radius / 56px diameter)
            p.metaButtons.forEach { m ->
                val metaR = 28f * s * m.scale
                val cx = m.x * w
                val cy = m.y * h
                val isActive = pressedButtons.contains(m.id) || draggedButtonId == m.id
                
                canvas.drawCircle(cx, cy, metaR, if (isActive) paintMiniActiveFill else paintMiniIdleFill)
                canvas.drawCircle(cx, cy, metaR, if (isActive) paintMiniActiveStroke else paintMiniIdleStroke)
                
                val iconDrawable = when(m.id) {
                    "START" -> playDrawable
                    "BACK" -> viewDrawable
                    "GUIDE" -> xboxDrawable
                    else -> null
                }
                
                if (iconDrawable != null) {
                    val iconSize = (metaR * (if (m.id == "GUIDE") 1.3f else 1.15f)).toInt()
                    iconDrawable.setBounds((cx - iconSize/2).toInt(), (cy - iconSize/2).toInt(), (cx + iconSize/2).toInt(), (cy + iconSize/2).toInt())
                    iconDrawable.draw(canvas)
                }
                
                if (isEditMode) canvas.drawCircle(cx, cy, metaR, paintEditMode)
            }

            // Draw Menu Button (Scaled 28px radius / 56px diameter)
            p.menuButton?.let { mb ->
                val cx = mb.x * w
                val cy = mb.y * h
                val isActive = draggedButtonId == "MENU"
                val r = 28f * s * (p.menuButton?.scale ?: 1f)
                
                canvas.drawCircle(cx, cy, r, if (isActive) paintMiniActiveFill else paintMiniIdleFill)
                canvas.drawCircle(cx, cy, r, if (isActive) paintMiniActiveStroke else paintMiniIdleStroke)
                
                if (yevalDrawable != null) {
                    val iconSize = (r * 1.3f).toInt()
                    yevalDrawable.setBounds((cx - iconSize/2).toInt(), (cy - iconSize/2).toInt(), (cx + iconSize/2).toInt(), (cy + iconSize/2).toInt())
                    yevalDrawable.draw(canvas)
                }
                if (isEditMode) canvas.drawCircle(cx, cy, r, paintEditMode)
            }

            // Draw Sticks (Scaled 208px base, 125px knob)
            val lsStickR = 104f * s * p.leftStick.scale
            val lsX = p.leftStick.x * w
            val lsY = p.leftStick.y * h
            val lsActive = lsPointerId >= 0 || draggedButtonId == "LS"
            drawSvg(canvas, if (lsActive) stickBaseActive else stickBaseIdle, lsX, lsY, lsStickR * 2, lsStickR * 2)
            val knobSize = 125f * s * p.leftStick.scale
            val kx = lsX + leftThumbDx
            val ky = lsY + leftThumbDy
            drawSvg(canvas, if (lsActive) stickKnobActive else stickKnobIdle, kx, ky, knobSize, knobSize)
            canvas.drawText("LS", kx, ky + (paintText.textSize / 3), paintText)
            if (isEditMode) canvas.drawCircle(lsX, lsY, lsStickR, paintEditMode)

            val rsStickR = 104f * s * p.rightStick.scale
            val rsX = p.rightStick.x * w
            val rsY = p.rightStick.y * h
            val rsActive = rsPointerId >= 0 || draggedButtonId == "RS"
            drawSvg(canvas, if (rsActive) stickBaseActive else stickBaseIdle, rsX, rsY, rsStickR * 2, rsStickR * 2)
            val rkx = rsX + rightThumbDx
            val rky = rsY + rightThumbDy
            drawSvg(canvas, if (rsActive) stickKnobActive else stickKnobIdle, rkx, rky, knobSize, knobSize)
            canvas.drawText("RS", rkx, rky + (paintText.textSize / 3), paintText)
            if (isEditMode) canvas.drawCircle(rsX, rsY, rsStickR, paintEditMode)
        }

        if ((!isConnected || isServerFull) && !isEditMode) {
            val overlayPaint = Paint().apply {
                color = Color.parseColor("#E608080C")
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, w, h, overlayPaint)
            
            // Title
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isServerFull) Color.parseColor("#FFAA33") else Color.parseColor("#FF5555")
                textSize = 34f * s
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            val titleText = if (isServerFull) "Server is Full (4/4)" else "Disconnected from PC"
            canvas.drawText(titleText, w / 2f, h / 2f - 40f * s, titlePaint)

            // Subtitle
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#888899")
                textSize = 15f * s
                textAlign = Paint.Align.CENTER
            }
            val subText = if (isServerFull) "All player slots are occupied. Waiting for an open slot..." else "Attempting to reconnect..."
            canvas.drawText(subText, w / 2f, h / 2f - 5f * s, subPaint)
            
            // Exit Button
            val btnW = 160f * s
            val btnH = 44f * s
            val btnL = w / 2f - btnW / 2f
            val btnT = h / 2f + 25f * s
            val btnR = w / 2f + btnW / 2f
            val btnB = btnT + btnH
            exitBtnRect.set(btnL, btnT, btnR, btnB)

            val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#222230")
                style = Paint.Style.FILL
            }
            val btnStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#40ffffff")
                style = Paint.Style.STROKE
                strokeWidth = 2f * s
            }
            canvas.drawRoundRect(exitBtnRect, btnH / 2f, btnH / 2f, btnBgPaint)
            canvas.drawRoundRect(exitBtnRect, btnH / 2f, btnH / 2f, btnStrokePaint)

            val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 15f * s
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Exit to Menu", w / 2f, btnT + (btnH / 2f) + (btnTextPaint.textSize / 3f), btnTextPaint)
        }
    }

    private fun drawSvg(canvas: Canvas, drawable: Drawable?, cx: Float, cy: Float, w: Float, h: Float) {
        if (drawable == null) return
        val hw = w / 2f
        val hh = h / 2f
        drawable.setBounds((cx - hw).toInt(), (cy - hh).toInt(), (cx + hw).toInt(), (cy + hh).toInt())
        drawable.draw(canvas)
    }

    private fun getTriggerPath(cx: Float, cy: Float, w: Float, h: Float): Path {
        val path = Path()
        val hw = w / 2
        val hh = h / 2
        val r = h * 0.3f
        path.moveTo(cx - hw + r, cy + hh)
        path.lineTo(cx + hw - r, cy + hh)
        path.quadTo(cx + hw, cy + hh, cx + hw - r * 0.5f, cy + hh - r)
        path.lineTo(cx + hw * 0.7f, cy - hh + r)
        path.quadTo(cx + hw * 0.65f, cy - hh, cx + hw * 0.5f, cy - hh)
        path.lineTo(cx - hw * 0.5f, cy - hh)
        path.quadTo(cx - hw * 0.65f, cy - hh, cx - hw * 0.7f, cy - hh + r)
        path.lineTo(cx - hw + r * 0.5f, cy + hh - r)
        path.quadTo(cx - hw, cy + hh, cx - hw + r, cy + hh)
        path.close()
        return path
    }

    private fun drawTrigger(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, active: Boolean) {
        val path = getTriggerPath(cx, cy, w, h)
        canvas.drawPath(path, if (active) paintActiveFill else paintDarkFill)
        canvas.drawPath(path, if (active) paintActiveStroke else paintDarkStroke)
    }

    private fun getDpadPath(cx: Float, cy: Float, arm: Float, scale: Float): Path {
        val path = Path()
        val thick = arm * 0.75f
        val r = 4f * getScale() * scale
        val gap = arm * 0.25f
        val slant = arm * 0.2f
        
        fun addButton(dx: Float, dy: Float, isHorizontal: Boolean) {
            path.moveTo(cx + dx * gap, cy + dy * gap)
            if (isHorizontal) {
                val sign = dx
                path.lineTo(cx + sign * (gap + slant), cy - thick/2)
                path.lineTo(cx + sign * arm - sign * r, cy - thick/2)
                path.quadTo(cx + sign * arm, cy - thick/2, cx + sign * arm, cy - thick/2 + r)
                path.lineTo(cx + sign * arm, cy + thick/2 - r)
                path.quadTo(cx + sign * arm, cy + thick/2, cx + sign * arm - sign * r, cy + thick/2)
                path.lineTo(cx + sign * (gap + slant), cy + thick/2)
            } else {
                val sign = dy
                path.lineTo(cx - thick/2, cy + sign * (gap + slant))
                path.lineTo(cx - thick/2, cy + sign * arm - sign * r)
                path.quadTo(cx - thick/2, cy + sign * arm, cx - thick/2 + r, cy + sign * arm)
                path.lineTo(cx + thick/2 - r, cy + sign * arm)
                path.quadTo(cx + thick/2, cy + sign * arm, cx + thick/2, cy + sign * arm - sign * r)
                path.lineTo(cx + thick/2, cy + sign * (gap + slant))
            }
            path.close()
        }
        
        addButton(1f, 0f, true)   // Right
        addButton(-1f, 0f, true)  // Left
        addButton(0f, 1f, false)  // Down
        addButton(0f, -1f, false) // Up
        
        return path
    }

    private fun drawDpadCross(canvas: Canvas, cx: Float, cy: Float, arm: Float, up: Boolean, down: Boolean, left: Boolean, right: Boolean, scale: Float = 1f) {
        val dishRadius = arm * 1.35f * scale
        val dishPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#15151e")
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 4f, Color.parseColor("#80000000"))
        }
        val dishRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2a2a35")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(cx, cy, dishRadius, dishPaint)
        canvas.drawCircle(cx, cy, dishRadius, dishRingPaint)

        // 2. Draw the base cross shape
        val path = getDpadPath(cx, cy, arm, scale)
        canvas.drawPath(path, paintDarkFill)
        
        // 3. Draw active states inside the cross
        canvas.save()
        canvas.clipPath(path)
        if (up) {
            val p = Path().apply { moveTo(cx, cy); lineTo(cx - arm, cy - arm); lineTo(cx + arm, cy - arm); close() }
            canvas.drawPath(p, paintActiveFill)
        }
        if (down) {
            val p = Path().apply { moveTo(cx, cy); lineTo(cx - arm, cy + arm); lineTo(cx + arm, cy + arm); close() }
            canvas.drawPath(p, paintActiveFill)
        }
        if (left) {
            val p = Path().apply { moveTo(cx, cy); lineTo(cx - arm, cy - arm); lineTo(cx - arm, cy + arm); close() }
            canvas.drawPath(p, paintActiveFill)
        }
        if (right) {
            val p = Path().apply { moveTo(cx, cy); lineTo(cx + arm, cy - arm); lineTo(cx + arm, cy + arm); close() }
            canvas.drawPath(p, paintActiveFill)
        }
        canvas.restore()
        
        // 4. Draw chevron arrows on each button
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3a3a45")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val activeArrowPaint = Paint(arrowPaint).apply { color = Color.parseColor("#0a0a0f") }
        val aw = arm * 0.18f * scale
        val ah = arm * 0.12f * scale
        
        fun drawArrow(x: Float, y: Float, dir: String, isActive: Boolean) {
            val p = if (isActive) activeArrowPaint else arrowPaint
            val aPath = Path()
            when(dir) {
                "UP" -> { aPath.moveTo(x - aw, y + ah); aPath.lineTo(x, y); aPath.lineTo(x + aw, y + ah) }
                "DOWN" -> { aPath.moveTo(x - aw, y - ah); aPath.lineTo(x, y); aPath.lineTo(x + aw, y - ah) }
                "LEFT" -> { aPath.moveTo(x + ah, y - aw); aPath.lineTo(x, y); aPath.lineTo(x + ah, y + aw) }
                "RIGHT" -> { aPath.moveTo(x - ah, y - aw); aPath.lineTo(x, y); aPath.lineTo(x - ah, y + aw) }
            }
            canvas.drawPath(aPath, p)
        }

        val offset = arm * 0.75f * scale
        drawArrow(cx, cy - offset, "UP", up)
        drawArrow(cx, cy + offset, "DOWN", down)
        drawArrow(cx - offset, cy, "LEFT", left)
        drawArrow(cx + offset, cy, "RIGHT", right)
        
        val stroke = if (up || down || left || right) paintActiveStroke else paintDarkStroke
        canvas.drawPath(path, stroke)
    }

    private fun isHitTrigger(t: TriggerConfig, px: Float, py: Float, w: Float, h: Float, s: Float): Boolean {
        val tw = 230f * s * t.scale
        val th = 92f * s * t.scale
        if (VibrationManager.pixelPerfectHitboxesEnabled) {
            val path = getTriggerPath(t.x * w, t.y * h, tw, th)
            val rectF = RectF()
            path.computeBounds(rectF, true)
            val region = Region()
            region.setPath(path, Region(rectF.left.toInt(), rectF.top.toInt(), rectF.right.toInt(), rectF.bottom.toInt()))
            return region.contains(px.toInt(), py.toInt())
        } else {
            val dx = Math.abs(px - t.x * w)
            val dy = Math.abs(py - t.y * h)
            return dx <= tw / 2 && dy <= th / 2
        }
    }

    private fun isHitBumper(b: BumperConfig, px: Float, py: Float, w: Float, h: Float, s: Float): Boolean {
        val cx = b.x * w
        val cy = b.y * h
        val bumpW = 230f * s * b.scale
        val bumpH = 92f * s * b.scale
        val dx = Math.abs(px - cx)
        val dy = Math.abs(py - cy)
        return dx <= bumpW / 2 && dy <= bumpH / 2
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val p = profile ?: return false
        val w = width.toFloat()
        val h = height.toFloat()
        val s = getScale()
        
        val action = event.actionMasked
        val actionIndex = event.actionIndex

        if ((!isConnected || isServerFull) && !isEditMode) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
                val px = event.getX(0)
                val py = event.getY(0)
                if (exitBtnRect.contains(px, py)) {
                    if (action == MotionEvent.ACTION_UP) {
                        onExitClicked?.invoke()
                    }
                    return true
                }
            }
            return true
        }

        leftThumbDx = 0f
        leftThumbDy = 0f
        rightThumbDx = 0f
        rightThumbDy = 0f
        
        previousButtons.clear()
        previousButtons.addAll(pressedButtons)
        pressedButtons.clear()

        if (isEditMode) {
            if (action == MotionEvent.ACTION_DOWN) {
                val px = event.getX(0)
                val py = event.getY(0)
                var closestId: String? = null
                var minDist = Float.MAX_VALUE
                
                fun check(id: String, itemX: Float, itemY: Float, hitR: Float) {
                    val dist = hypot(px - itemX, py - itemY)
                    if (dist < minDist && dist < hitR * 1.5f) {
                        minDist = dist
                        closestId = id
                    }
                }
                
                fun checkRect(id: String, itemX: Float, itemY: Float, hw: Float, hh: Float) {
                    val dx = Math.abs(px - itemX)
                    val dy = Math.abs(py - itemY)
                    if (dx < hw * 1.5f && dy < hh * 1.5f) {
                        val dist = hypot(dx, dy)
                        if (dist < minDist) {
                            minDist = dist
                            closestId = id
                        }
                    }
                }
                
                check("LS", p.leftStick.x * w, p.leftStick.y * h, 104f * s * p.leftStick.scale)
                check("RS", p.rightStick.x * w, p.rightStick.y * h, 104f * s * p.rightStick.scale)
                check("DPAD", p.dpad.x * w, p.dpad.y * h, 84f * s * p.dpad.scale)
                
                // Triggers (230x92 scaled) => half-width 115, half-height 46
                p.triggers.forEach { checkRect(it.id, it.x * w, it.y * h, 115f * s * it.scale, 46f * s * it.scale) }
                // Bumpers (230x92 scaled) => half-width 115, half-height 46
                p.bumpers.forEach { checkRect(it.id, it.x * w, it.y * h, 115f * s * it.scale, 46f * s * it.scale) }
                
                p.faceButtons.forEach { check(it.id, it.x * w, it.y * h, 46f * s * it.scale) }
                p.metaButtons.forEach { check(it.id, it.x * w, it.y * h, 28f * s * it.scale) }
                p.menuButton?.let { check("MENU", it.x * w, it.y * h, 28f * s * (it.scale ?: 1f)) }

                if (closestId != null) {
                    draggedButtonId = closestId
                    onDragStart?.invoke()
                } else {
                    onCanvasTouch?.invoke()
                }
            } else if (action == MotionEvent.ACTION_MOVE && draggedButtonId != null) {
                val px = event.getX(0)
                val py = event.getY(0)
                val normX = px / w
                val normY = py / h
                
                when (draggedButtonId) {
                    "LS" -> { p.leftStick.x = normX; p.leftStick.y = normY }
                    "RS" -> { p.rightStick.x = normX; p.rightStick.y = normY }
                    "DPAD" -> { p.dpad.x = normX; p.dpad.y = normY }
                    "MENU" -> { p.menuButton?.let { it.x = normX; it.y = normY } }
                    else -> {
                        p.triggers.find { it.id == draggedButtonId }?.let { it.x = normX; it.y = normY }
                        p.bumpers.find { it.id == draggedButtonId }?.let { it.x = normX; it.y = normY }
                        p.faceButtons.find { it.id == draggedButtonId }?.let { it.x = normX; it.y = normY }
                        p.metaButtons.find { it.id == draggedButtonId }?.let { it.x = normX; it.y = normY }
                    }
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (draggedButtonId != null) {
                    fun snapX(v: Float): Float = Math.round(v * 20f) / 20f
                    fun snapY(v: Float): Float = Math.round(v * 20f) / 20f
                    when (draggedButtonId) {
                        "LS" -> { p.leftStick.x = snapX(p.leftStick.x); p.leftStick.y = snapY(p.leftStick.y) }
                        "RS" -> { p.rightStick.x = snapX(p.rightStick.x); p.rightStick.y = snapY(p.rightStick.y) }
                        "DPAD" -> { p.dpad.x = snapX(p.dpad.x); p.dpad.y = snapY(p.dpad.y) }
                        "MENU" -> { p.menuButton?.let { it.x = snapX(it.x); it.y = snapY(it.y) } }
                        else -> {
                            p.triggers.find { it.id == draggedButtonId }?.let { it.x = snapX(it.x); it.y = snapY(it.y) }
                            p.bumpers.find { it.id == draggedButtonId }?.let { it.x = snapX(it.x); it.y = snapY(it.y) }
                            p.faceButtons.find { it.id == draggedButtonId }?.let { it.x = snapX(it.x); it.y = snapY(it.y) }
                            p.metaButtons.find { it.id == draggedButtonId }?.let { it.x = snapX(it.x); it.y = snapY(it.y) }
                        }
                    }
                    onDragComplete?.invoke()
                }
                draggedButtonId = null
            }
            invalidate()
            return true
        }

        // Collect pointer IDs that are reserved for analog triggers — these must not interact with other buttons
        val reservedPointerIds = mutableSetOf<Int>()
        if (VibrationManager.analogTriggersEnabled) {
            if (ltPointerId >= 0) reservedPointerIds.add(ltPointerId)
            if (rtPointerId >= 0) reservedPointerIds.add(rtPointerId)
        }
        if (VibrationManager.continuousJoystickEnabled) {
            if (lsPointerId >= 0) reservedPointerIds.add(lsPointerId)
            if (rsPointerId >= 0) reservedPointerIds.add(rsPointerId)
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // all fingers lifted
        } else {
            for (i in 0 until event.pointerCount) {
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && i == actionIndex) continue

                val px = event.getX(i)
                val py = event.getY(i)
                val currentPointerId = event.getPointerId(i)
                
                if (p.layoutMode == "zone") {
                    val hitZones = mutableListOf<String>()
                    p.zones.forEach { zone ->
                        val verts = if (p.curveZones) (curvedZonePolygons[zone.buttonId] ?: zone.vertices) else zone.vertices
                        if (isPointInPolygon(px, py, verts, w, h)) {
                            hitZones.add(zone.buttonId)
                            
                            if (VibrationManager.analogTriggersEnabled && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && i == actionIndex) {
                                if (zone.buttonId == "LT" && ltPointerId == -1) {
                                    ltPointerId = currentPointerId; ltAnchorX = px; ltAnchorY = py; ltPressure = 0; ltMaxHit = false
                                } else if (zone.buttonId == "RT" && rtPointerId == -1) {
                                    rtPointerId = currentPointerId; rtAnchorX = px; rtAnchorY = py; rtPressure = 0; rtMaxHit = false
                                }
                            }
                            
                            if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && i == actionIndex) {
                                if (zone.buttonId == "LS" && lsPointerId == -1) {
                                    lsPointerId = currentPointerId; lsAnchorX = px; lsAnchorY = py
                                    lsDownTime = System.currentTimeMillis(); lsStartX = px; lsStartY = py; lsHasMoved = false
                                } else if (zone.buttonId == "RS" && rsPointerId == -1) {
                                    rsPointerId = currentPointerId; rsAnchorX = px; rsAnchorY = py
                                    rsDownTime = System.currentTimeMillis(); rsStartX = px; rsStartY = py; rsHasMoved = false
                                    rsLastX = px; rsLastY = py; rsLastMoveTimeMs = System.currentTimeMillis()
                                } else if (zone.buttonId == "DPAD" && dpadPointerId == -1) {
                                    dpadPointerId = currentPointerId; dpadAnchorX = px; dpadAnchorY = py
                                } else if (zone.buttonId == "MENU") {
                                    onMenuClicked?.invoke()
                                }
                            }
                        }
                    }
                    
                    if (ltPointerId == currentPointerId || rtPointerId == currentPointerId || lsPointerId == currentPointerId || rsPointerId == currentPointerId || dpadPointerId == currentPointerId) {
                        continue 
                    }
                    
                    pressedButtons.addAll(hitZones)
                    continue
                }

                // --- Analog trigger initial hit detection (runs for ALL pointers including fresh ones) ---
                if (VibrationManager.analogTriggersEnabled) {
                    if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && i == actionIndex) {
                        p.triggers.forEach { t ->
                            if (isHitTrigger(t, px, py, w, h, s)) {
                                if (t.id == "LT" && ltPointerId == -1) {
                                    ltPointerId = currentPointerId
                                    ltAnchorX = px
                                    ltAnchorY = py
                                    ltPressure = 0
                                    ltMaxHit = false
                                    reservedPointerIds.add(currentPointerId)
                                } else if (t.id == "RT" && rtPointerId == -1) {
                                    rtPointerId = currentPointerId
                                    rtAnchorX = px
                                    rtAnchorY = py
                                    rtPressure = 0
                                    rtMaxHit = false
                                    reservedPointerIds.add(currentPointerId)
                                }
                            }
                        }
                    }
                }

                if (VibrationManager.continuousJoystickEnabled) {
                    if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && i == actionIndex) {
                        val stickR = 104f * s * p.leftStick.scale
                        val lsX = p.leftStick.x * w
                        val lsY = p.leftStick.y * h
                        if (hypot(px - lsX, py - lsY) < stickR * 1.35f && lsPointerId == -1) {
                            lsPointerId = currentPointerId
                            lsDownTime = System.currentTimeMillis(); lsStartX = px; lsStartY = py; lsHasMoved = false
                            reservedPointerIds.add(currentPointerId)
                        }
                        val rsX = p.rightStick.x * w
                        val rsY = p.rightStick.y * h
                        if (hypot(px - rsX, py - rsY) < stickR * 1.35f && rsPointerId == -1) {
                            rsPointerId = currentPointerId
                            rsDownTime = System.currentTimeMillis(); rsStartX = px; rsStartY = py; rsHasMoved = false
                            rsLastX = px; rsLastY = py; rsLastMoveTimeMs = System.currentTimeMillis()
                            reservedPointerIds.add(currentPointerId)
                        }
                    }
                }

                // --- Skip this pointer entirely if it's reserved for an analog trigger ---
                if (reservedPointerIds.contains(currentPointerId)) continue

                // Menu button (28px radius)
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                    p.menuButton?.let { mb ->
                        if (hypot(px - mb.x * w, py - mb.y * h) <= 28f * s * 1.35f * (mb.scale ?: 1f)) {
                            onMenuClicked?.invoke()
                            return@let
                        }
                    }
                }

                // Sticks (Legacy mode)
                if (!VibrationManager.continuousJoystickEnabled) {
                    val lsX = p.leftStick.x * w
                    val lsY = p.leftStick.y * h
                    val stickR = 104f * s * p.leftStick.scale
                    val distL = hypot(px - lsX, py - lsY)
                    if (distL < stickR * 1.5f) {
                        leftThumbDx = px - lsX
                        leftThumbDy = py - lsY
                        if (distL > stickR) {
                            leftThumbDx = (leftThumbDx / distL) * stickR
                            leftThumbDy = (leftThumbDy / distL) * stickR
                        }
                    }

                    val rsX = p.rightStick.x * w
                    val rsY = p.rightStick.y * h
                    val distR = hypot(px - rsX, py - rsY)
                    if (distR < stickR * 1.5f) {
                        rightThumbDx = px - rsX
                        rightThumbDy = py - rsY
                        if (distR > stickR) {
                            rightThumbDx = (rightThumbDx / distR) * stickR
                            rightThumbDy = (rightThumbDy / distR) * stickR
                        }
                    }
                }

                // Dpad - 8-way directional sector angle test matching 84px outer radius
                val dpX = p.dpad.x * w
                val dpY = p.dpad.y * h
                val dpR = 84f * s * p.dpad.scale
                val distDp = hypot(px - dpX, py - dpY)
                if (distDp <= dpR * 1.15f && distDp >= 10f * s) {
                    val angleDeg = Math.toDegrees(Math.atan2((py - dpY).toDouble(), (px - dpX).toDouble())).let { if (it < 0) it + 360.0 else it }
                    when {
                        angleDeg >= 337.5 || angleDeg < 22.5 -> pressedButtons.add("Right")
                        angleDeg in 22.5..67.5 -> { pressedButtons.add("Right"); pressedButtons.add("Down") }
                        angleDeg in 67.5..112.5 -> pressedButtons.add("Down")
                        angleDeg in 112.5..157.5 -> { pressedButtons.add("Left"); pressedButtons.add("Down") }
                        angleDeg in 157.5..202.5 -> pressedButtons.add("Left")
                        angleDeg in 202.5..247.5 -> { pressedButtons.add("Left"); pressedButtons.add("Up") }
                        angleDeg in 247.5..292.5 -> pressedButtons.add("Up")
                        angleDeg in 292.5..337.5 -> { pressedButtons.add("Right"); pressedButtons.add("Up") }
                    }
                }

                // Triggers (digital mode only — analog is handled separately above)
                if (!VibrationManager.analogTriggersEnabled) {
                    p.triggers.forEach { t ->
                        if (isHitTrigger(t, px, py, w, h, s)) pressedButtons.add(t.id)
                    }
                }
                
                // Bumpers (230x92 pixel-perfect hitbox)
                p.bumpers.forEach { b -> 
                    if (isHitBumper(b, px, py, w, h, s)) pressedButtons.add(b.id) 
                }
                
                // Face (46px radius) & Meta (28px radius)
                p.faceButtons.forEach { if (hypot(px - it.x * w, py - it.y * h) <= 46f * s * 1.15f * it.scale) pressedButtons.add(it.id) }
                p.metaButtons.forEach { if (hypot(px - it.x * w, py - it.y * h) <= 28f * s * 1.3f * it.scale) pressedButtons.add(it.id) }
            }
        }
        
        // Continuous Joystick tracking
        if (VibrationManager.continuousJoystickEnabled) {
            val lsX = if (p.layoutMode == "zone") lsAnchorX else p.leftStick.x * w
            val lsY = if (p.layoutMode == "zone") lsAnchorY else p.leftStick.y * h
            val rsX = if (p.layoutMode == "zone") rsAnchorX else p.rightStick.x * w
            val rsY = if (p.layoutMode == "zone") rsAnchorY else p.rightStick.y * h
            val stickR = 104f * s * p.leftStick.scale
            
            if (lsPointerId >= 0) {
                val idx = try { event.findPointerIndex(lsPointerId) } catch (_: Exception) { -1 }
                if (idx < 0 || ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && idx == actionIndex)) {
                    val elapsed = System.currentTimeMillis() - lsDownTime
                    val px = try { event.getX(if (idx >= 0) idx else actionIndex) } catch (_: Exception) { lsStartX }
                    val py = try { event.getY(if (idx >= 0) idx else actionIndex) } catch (_: Exception) { lsStartY }
                    val dragDist = hypot(px - lsStartX, py - lsStartY)
                    
                    // Quick tap (< 250ms) with minimal drag (< 12dp) -> Trigger L3 click
                    if (elapsed < 250L && dragDist < 12f * s) {
                        l3TapUntilMs = System.currentTimeMillis() + 150L
                        pressedButtons.add("L3")
                        VibrationManager.vibrateHaptic()
                        postDelayed({
                            pressedButtons.remove("L3")
                            profile?.let { prof -> dispatchState(prof, width.toFloat(), height.toFloat()) }
                            invalidate()
                        }, 150L)
                    }
                    lsPointerId = -1
                    leftThumbDx = 0f
                    leftThumbDy = 0f
                } else {
                    val px = event.getX(idx)
                    val py = event.getY(idx)
                    val distL = hypot(px - lsX, py - lsY)
                    leftThumbDx = px - lsX
                    leftThumbDy = py - lsY
                    if (distL > stickR) {
                        leftThumbDx = (leftThumbDx / distL) * stickR
                        leftThumbDy = (leftThumbDy / distL) * stickR
                    }
                }
            }
            if (rsPointerId >= 0) {
                val idx = try { event.findPointerIndex(rsPointerId) } catch (_: Exception) { -1 }
                if (idx < 0 || ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && idx == actionIndex)) {
                    val elapsed = System.currentTimeMillis() - rsDownTime
                    val px = try { event.getX(if (idx >= 0) idx else actionIndex) } catch (_: Exception) { rsStartX }
                    val py = try { event.getY(if (idx >= 0) idx else actionIndex) } catch (_: Exception) { rsStartY }
                    val dragDist = hypot(px - rsStartX, py - rsStartY)
                    val isCursorMode = (p.layoutMode == "zone") && (VibrationManager.rightStickMode == "cursor" || VibrationManager.rightStickMode == "mouse_touchpad")
                    if (isCursorMode) {
                        if (elapsed < 250L && dragDist < 12f * s) {
                            // Quick tap = Left Click
                            rsMouseLeftClick = true
                            VibrationManager.vibrateHaptic()
                            profile?.let { prof -> dispatchState(prof, width.toFloat(), height.toFloat()) }
                            postDelayed({
                                rsMouseLeftClick = false
                                profile?.let { prof -> dispatchState(prof, width.toFloat(), height.toFloat()) }
                            }, 100L)
                        }
                    } else {
                        // Quick tap (< 250ms) with minimal drag (< 12dp) -> Trigger R3 click
                        if (elapsed < 250L && dragDist < 12f * s) {
                            r3TapUntilMs = System.currentTimeMillis() + 150L
                            pressedButtons.add("R3")
                            VibrationManager.vibrateHaptic()
                            postDelayed({
                                pressedButtons.remove("R3")
                                profile?.let { prof -> dispatchState(prof, width.toFloat(), height.toFloat()) }
                                invalidate()
                            }, 150L)
                        }
                    }
                    rsPointerId = -1
                    rightThumbDx = 0f
                    rightThumbDy = 0f
                    rsMouseDx = 0
                    rsMouseDy = 0
                } else {
                    val px = event.getX(idx)
                    val py = event.getY(idx)
                    
                    val isAim = (p.layoutMode == "zone") && (VibrationManager.rightStickMode == "aim" || VibrationManager.rightStickMode == "camera_trackpad")
                    val isCursor = (p.layoutMode == "zone") && (VibrationManager.rightStickMode == "cursor" || VibrationManager.rightStickMode == "mouse_touchpad")
                    val isDynamic = VibrationManager.trackpadCurve == "dynamic" || VibrationManager.trackpadCurve == "speed"

                    if (isAim) {
                        val now = System.currentTimeMillis()
                        val dt = (now - rsLastMoveTimeMs).coerceIn(8L, 50L)
                        val deltaX = px - rsLastX
                        val deltaY = py - rsLastY
                        rsLastX = px
                        rsLastY = py
                        rsLastMoveTimeMs = now
                        
                        val sens = VibrationManager.trackpadSensitivity
                        val accel = if (isDynamic) {
                            val speed = hypot(deltaX, deltaY) / dt.toFloat()
                            (1.0f + (speed * 0.8f)).coerceIn(1.0f, 4.0f)
                        } else {
                            1.0f
                        }
                        
                        // Transient stick deflection proportional to swipe velocity/distance
                        val swipeScale = 3.5f * sens * accel
                        rightThumbDx = (deltaX * swipeScale * s).coerceIn(-stickR, stickR)
                        rightThumbDy = (deltaY * swipeScale * s).coerceIn(-stickR, stickR)
                    } else if (isCursor) {
                        val now = System.currentTimeMillis()
                        val dt = (now - rsLastMoveTimeMs).coerceIn(8L, 50L)
                        val deltaX = px - rsLastX
                        val deltaY = py - rsLastY
                        rsLastX = px
                        rsLastY = py
                        rsLastMoveTimeMs = now
                        
                        val sens = VibrationManager.trackpadSensitivity
                        val accel = if (isDynamic) {
                            val speed = hypot(deltaX, deltaY) / dt.toFloat()
                            (1.0f + (speed * 0.7f)).coerceIn(1.0f, 3.5f)
                        } else {
                            1.0f
                        }
                        
                        rsMouseDx = (deltaX * 1.8f * sens * accel).toInt()
                        rsMouseDy = (deltaY * 1.8f * sens * accel).toInt()
                        rightThumbDx = 0f
                        rightThumbDy = 0f
                    } else { // "stick" (default standard analog thumbstick)
                        val distR = hypot(px - rsX, py - rsY)
                        rightThumbDx = px - rsX
                        rightThumbDy = py - rsY
                        if (distR > stickR) {
                            rightThumbDx = (rightThumbDx / distR) * stickR
                            rightThumbDy = (rightThumbDy / distR) * stickR
                        }
                    }
                }
            }
            if (dpadPointerId >= 0) {
                val idx = try { event.findPointerIndex(dpadPointerId) } catch (_: Exception) { -1 }
                if (idx < 0 || ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && idx == actionIndex)) {
                    dpadPointerId = -1
                } else {
                    val px = event.getX(idx)
                    val py = event.getY(idx)
                    val dx = px - dpadAnchorX
                    val dy = py - dpadAnchorY
                    val absDx = Math.abs(dx)
                    val absDy = Math.abs(dy)
                    // Only trigger if we moved past a small deadzone
                    if (absDx > 10f * s || absDy > 10f * s) {
                        if (absDx > absDy * 2f) {
                            if (dx < 0) pressedButtons.add("Left") else pressedButtons.add("Right")
                        } else if (absDy > absDx * 2f) {
                            if (dy < 0) pressedButtons.add("Up") else pressedButtons.add("Down")
                        } else {
                            if (dx < 0) pressedButtons.add("Left") else pressedButtons.add("Right")
                            if (dy < 0) pressedButtons.add("Up") else pressedButtons.add("Down")
                        }
                    }
                }
            }
        }

        // Analog trigger pointer tracking: compute pressure from distance
        // Design: sliding AWAY from anchor = pressing trigger DEEPER = MORE pressure (like pulling a physical trigger)
        // Within deadzone = 0 pressure (finger resting, not pressing yet)
        // Outside deadzone = pressure scales up with distance (further = harder press)
        if (VibrationManager.analogTriggersEnabled) {
            val deadzone = 20f * s
            val modulateDist = 80f * s
            
            fun updateAnalogTrigger(pointerId: Int, anchorX: Float, anchorY: Float, currentPressure: Int, maxHit: Boolean): Triple<Int, Boolean, Boolean> {
                val idx = if (pointerId >= 0) {
                    try { event.findPointerIndex(pointerId) } catch (_: Exception) { -1 }
                } else -1
                
                if (idx < 0) return Triple(0, false, false)
                
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && idx == actionIndex) {
                    return Triple(0, false, false)
                }
                
                val px = event.getX(idx)
                val py = event.getY(idx)
                val diff = hypot(px - anchorX, py - anchorY)
                
                val newPressure: Int
                if (diff <= deadzone) {
                    // Within deadzone: finger is resting on trigger, output 5% (13/255)
                    newPressure = 13
                } else {
                    // Outside deadzone: the further you slide, the harder you press
                    val slide = (diff - deadzone).coerceIn(0f, modulateDist)
                    newPressure = 13 + ((slide / modulateDist) * 242).toInt() // Scale from 5% to 100%
                }
                
                // Light actuation tick on crossing initial pressure threshold
                if (newPressure > 25 && currentPressure <= 25) {
                    VibrationManager.vibrateHaptic()
                }

                var newMaxHit = maxHit
                if (newPressure >= 255 && !maxHit) {
                    newMaxHit = true
                    VibrationManager.vibrateMaxPressure()
                } else if (newPressure < 255) {
                    newMaxHit = false
                }
                return Triple(newPressure, newMaxHit, true)
            }
            
            if (ltPointerId >= 0) {
                val (pressure, maxHit, active) = updateAnalogTrigger(ltPointerId, ltAnchorX, ltAnchorY, ltPressure, ltMaxHit)
                if (active) {
                    ltPressure = pressure
                    ltMaxHit = maxHit
                    pressedButtons.add("LT")
                } else {
                    ltPressure = 0
                    ltMaxHit = false
                    ltPointerId = -1
                }
            }
            if (rtPointerId >= 0) {
                val (pressure, maxHit, active) = updateAnalogTrigger(rtPointerId, rtAnchorX, rtAnchorY, rtPressure, rtMaxHit)
                if (active) {
                    rtPressure = pressure
                    rtMaxHit = maxHit
                    pressedButtons.add("RT")
                } else {
                    rtPressure = 0
                    rtMaxHit = false
                    rtPointerId = -1
                }
            }
        }

        // Trigger crisp micro-haptics strictly on newly pressed buttons (down-transition)
        val newlyPressed = pressedButtons - previousButtons
        if (newlyPressed.isNotEmpty()) {
            VibrationManager.vibrateHaptic()
        }

        profile?.let { prof -> dispatchState(prof, w, h) }
        invalidate()
        return true
    }

    private fun dispatchState(p: ControllerProfile, w: Float, h: Float) {
        var buttonsMask: Short = 0
        if (pressedButtons.contains("A")) buttonsMask = (buttonsMask.toInt() or 0x1000).toShort()
        if (pressedButtons.contains("B")) buttonsMask = (buttonsMask.toInt() or 0x2000).toShort()
        if (pressedButtons.contains("X")) buttonsMask = (buttonsMask.toInt() or 0x4000).toShort()
        if (pressedButtons.contains("Y")) buttonsMask = (buttonsMask.toInt() or 0x8000).toShort()
        if (pressedButtons.contains("Up")) buttonsMask = (buttonsMask.toInt() or 0x0001).toShort()
        if (pressedButtons.contains("Down")) buttonsMask = (buttonsMask.toInt() or 0x0002).toShort()
        if (pressedButtons.contains("Left")) buttonsMask = (buttonsMask.toInt() or 0x0004).toShort()
        if (pressedButtons.contains("Right")) buttonsMask = (buttonsMask.toInt() or 0x0008).toShort()
        if (pressedButtons.contains("START")) buttonsMask = (buttonsMask.toInt() or 0x0010).toShort()
        if (pressedButtons.contains("BACK")) buttonsMask = (buttonsMask.toInt() or 0x0020).toShort()
        if (pressedButtons.contains("GUIDE")) buttonsMask = (buttonsMask.toInt() or 0x0400).toShort()
        if (pressedButtons.contains("L3") || System.currentTimeMillis() < l3TapUntilMs) buttonsMask = (buttonsMask.toInt() or 0x0040).toShort()
        if (pressedButtons.contains("R3") || System.currentTimeMillis() < r3TapUntilMs) buttonsMask = (buttonsMask.toInt() or 0x0080).toShort()
        if (pressedButtons.contains("LB")) buttonsMask = (buttonsMask.toInt() or 0x0100).toShort()
        if (pressedButtons.contains("RB")) buttonsMask = (buttonsMask.toInt() or 0x0200).toShort()
        
        val s = getScale()
        val stickR = 104f * s * p.leftStick.scale
        val lxNormalized = leftThumbDx / stickR
        val lyNormalized = -(leftThumbDy / stickR)
        val rxNormalized = rightThumbDx / stickR
        val ryNormalized = -(rightThumbDy / stickR)

        val isAim = (p.layoutMode == "zone") && (VibrationManager.rightStickMode == "aim" || VibrationManager.rightStickMode == "camera_trackpad")
        val isCursor = (p.layoutMode == "zone") && (VibrationManager.rightStickMode == "cursor" || VibrationManager.rightStickMode == "mouse_touchpad")

        // If in aim trackpad mode and finger has stopped moving, decay stick to neutral
        if (isAim && rsPointerId >= 0) {
            if (System.currentTimeMillis() - rsLastMoveTimeMs > 40L) {
                rightThumbDx = 0f
                rightThumbDy = 0f
            }
        }

        var flags = 0
        val outRx: Short
        val outRy: Short
        
        if (isCursor) {
            if (rsMouseDx != 0 || rsMouseDy != 0 || rsMouseLeftClick || rsMouseRightClick) {
                flags = flags or 0x00010000 // FLAG_MOUSE_EVENT
                if (rsMouseLeftClick) flags = flags or 0x00020000
                if (rsMouseRightClick) flags = flags or 0x00040000
                outRx = rsMouseDx.toShort()
                outRy = rsMouseDy.toShort()
                rsMouseDx = 0
                rsMouseDy = 0
            } else {
                outRx = 0
                outRy = 0
            }
        } else {
            outRx = (rxNormalized * 32767).toInt().toShort()
            outRy = (ryNormalized * 32767).toInt().toShort()
        }

        onStateChanged?.invoke(
            buttonsMask,
            (if (VibrationManager.analogTriggersEnabled) (if (pressedButtons.contains("LT")) ltPressure else 0) else (if (pressedButtons.contains("LT")) 255 else 0)).toByte(),
            (if (VibrationManager.analogTriggersEnabled) (if (pressedButtons.contains("RT")) rtPressure else 0) else (if (pressedButtons.contains("RT")) 255 else 0)).toByte(),
            (lxNormalized * 32767).toInt().toShort(),
            (lyNormalized * 32767).toInt().toShort(),
            outRx,
            outRy,
            flags
        )
    }

    private fun getCentroid(poly: List<ZoneVertexConfig>): ZoneVertexConfig {
        var cx = 0f
        var cy = 0f
        var area = 0f
        var j = poly.size - 1
        for (i in poly.indices) {
            val cross = poly[i].x * poly[j].y - poly[j].x * poly[i].y
            cx += (poly[i].x + poly[j].x) * cross
            cy += (poly[i].y + poly[j].y) * cross
            area += cross
            j = i
        }
        area *= 0.5f
        if (Math.abs(area) > 0.1f) {
            return ZoneVertexConfig(cx / (6f * area), cy / (6f * area))
        }
        var sx = 0f
        var sy = 0f
        poly.forEach { sx += it.x; sy += it.y }
        return ZoneVertexConfig(sx / poly.size, sy / poly.size)
    }

    private fun generateCurvedZonePoints(poly: List<ZoneVertexConfig>): List<ZoneVertexConfig> {
        val n = poly.size
        if (n < 3) return poly

        val centroid = getCentroid(poly)
        val margin = 4.0f
        val insetPoly = poly.map { p ->
            val dx = centroid.x - p.x
            val dy = centroid.y - p.y
            val dist = hypot(dx, dy)
            if (dist < 1f) {
                ZoneVertexConfig(p.x, p.y)
            } else {
                val ratio = minOf(margin / dist, 0.22f)
                ZoneVertexConfig(p.x + dx * ratio, p.y + dy * ratio)
            }
        }

        val smoothedPoints = mutableListOf<ZoneVertexConfig>()
        for (i in 0 until n) {
            val p1 = insetPoly[i]
            val p2 = insetPoly[(i + 1) % n]
            val p0 = insetPoly[(i - 1 + n) % n]

            val dPrev = hypot(p1.x - p0.x, p1.y - p0.y)
            val dNext = hypot(p2.x - p1.x, p2.y - p1.y)
            val r = minOf(dPrev * 0.5f, dNext * 0.5f)

            if (r < 1f) {
                smoothedPoints.add(p1)
                continue
            }

            val startX = p1.x + (p0.x - p1.x) * (r / dPrev)
            val startY = p1.y + (p0.y - p1.y) * (r / dPrev)
            val endX = p1.x + (p2.x - p1.x) * (r / dNext)
            val endY = p1.y + (p2.y - p1.y) * (r / dNext)

            val steps = 8
            for (t in 0..steps) {
                val u = t / steps.toFloat()
                val invU = 1f - u
                val qx = invU * invU * startX + 2f * invU * u * p1.x + u * u * endX
                val qy = invU * invU * startY + 2f * invU * u * p1.y + u * u * endY
                smoothedPoints.add(ZoneVertexConfig(qx, qy))
            }
        }
        return smoothedPoints
    }

    private fun drawZones(canvas: Canvas, p: ControllerProfile, w: Float, h: Float, s: Float) {
        if (zonePaths.isEmpty() || p.id != lastProfileId || s != lastScale || p.curveZones != lastCurveZones) {
            zonePaths.clear()
            curvedZonePolygons.clear()

            val sx = w / 1000f
            val sy = h / 450f

            p.zones.forEach { zone ->
                if (zone.vertices.isNotEmpty()) {
                    val path = Path()
                    if (p.curveZones) {
                        val curvedVerts = generateCurvedZonePoints(zone.vertices)
                        curvedZonePolygons[zone.buttonId] = curvedVerts
                        if (curvedVerts.isNotEmpty()) {
                            path.moveTo(curvedVerts[0].x * sx, curvedVerts[0].y * sy)
                            for (i in 1 until curvedVerts.size) {
                                path.lineTo(curvedVerts[i].x * sx, curvedVerts[i].y * sy)
                            }
                            path.close()
                        }
                    } else {
                        path.moveTo(zone.vertices[0].x * sx, zone.vertices[0].y * sy)
                        for (i in 1 until zone.vertices.size) {
                            path.lineTo(zone.vertices[i].x * sx, zone.vertices[i].y * sy)
                        }
                        path.close()
                    }
                    zonePaths[zone.buttonId] = path
                }
            }
            lastProfileId = p.id
            lastScale = s
            lastCurveZones = p.curveZones
        }

        p.zones.forEach { zone ->
            val isActive = pressedButtons.contains(zone.buttonId) || 
                           (zone.buttonId == "LS" && lsPointerId >= 0) || 
                           (zone.buttonId == "RS" && rsPointerId >= 0) ||
                           (zone.buttonId == "DPAD" && dpadPointerId >= 0)
                           
            val path = zonePaths[zone.buttonId]
            if (path != null) {
                val pair = zoneColors[zone.buttonId] ?: (Color.parseColor("#33ffffff") to Color.parseColor("#ffffff"))
                val fillCol = if (isActive) Color.argb(120, Color.red(pair.second), Color.green(pair.second), Color.blue(pair.second)) else pair.first
                val strokeCol = if (isActive) Color.WHITE else pair.second
                paintZoneDynamicFill.color = fillCol
                paintZoneDynamicStroke.color = strokeCol
                paintZoneDynamicStroke.strokeWidth = if (isActive) 2.5f else 1.5f

                canvas.drawPath(path, paintZoneDynamicFill)
                canvas.drawPath(path, paintZoneDynamicStroke)
                
                // Draw label or icon at geometric centroid
                val c = getCentroid(zone.vertices)
                val cx = c.x * (w / 1000f)
                val cy = c.y * (h / 450f)
                
                val miniDrawable = when (zone.buttonId) {
                    "GUIDE" -> xboxDrawable
                    "MENU" -> yevalDrawable
                    "START" -> playDrawable
                    "BACK" -> viewDrawable
                    else -> null
                }

                if (miniDrawable != null) {
                    drawSvg(canvas, miniDrawable, cx, cy, 26f * s, 26f * s)
                } else {
                    canvas.drawText(zone.buttonId, cx, cy + (paintText.textSize/3), if (isActive) paintTextActive else paintText)
                }
            }
        }
        
        // Draw floating joystick caps
        if (lsPointerId >= 0) {
            val lsStickR = 76f * s * p.leftStick.scale
            canvas.drawCircle(lsAnchorX + leftThumbDx, lsAnchorY + leftThumbDy, lsStickR * 0.6f, paintActiveFill)
            canvas.drawCircle(lsAnchorX + leftThumbDx, lsAnchorY + leftThumbDy, lsStickR * 0.6f, paintActiveStroke)
        }
        if (rsPointerId >= 0) {
            val isAimOrCursor = (VibrationManager.rightStickMode == "aim" || VibrationManager.rightStickMode == "camera_trackpad" || VibrationManager.rightStickMode == "cursor" || VibrationManager.rightStickMode == "mouse_touchpad")
            if (!isAimOrCursor) {
                val rsStickR = 76f * s * p.rightStick.scale
                canvas.drawCircle(rsAnchorX + rightThumbDx, rsAnchorY + rightThumbDy, rsStickR * 0.6f, paintActiveFill)
                canvas.drawCircle(rsAnchorX + rightThumbDx, rsAnchorY + rightThumbDy, rsStickR * 0.6f, paintActiveStroke)
            }
        }
    }

    private fun isPointInPolygon(px: Float, py: Float, vertices: List<ZoneVertexConfig>, w: Float, h: Float): Boolean {
        var inside = false
        var j = vertices.size - 1
        for (i in vertices.indices) {
            val viX = vertices[i].x * (w / 1000f)
            val viY = vertices[i].y * (h / 450f)
            val vjX = vertices[j].x * (w / 1000f)
            val vjY = vertices[j].y * (h / 450f)
            if (((viY > py) != (vjY > py)) && (px < (vjX - viX) * (py - viY) / (vjY - viY) + viX)) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
