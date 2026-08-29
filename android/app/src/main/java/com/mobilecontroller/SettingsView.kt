package com.mobilecontroller

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

class SettingsView(context: Context) : FrameLayout(context) {

    init {
        setBackgroundColor(Color.TRANSPARENT)

        val scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(100)
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding(64, 128, 64, 320)
        }

        val title = TextView(context).apply {
            text = "Settings"
            textSize = 36f
            setTextColor(Color.WHITE)
            gravity = Gravity.START
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 0, 0, 32) }
        }
        mainLayout.addView(title)

        // --- Section: FEEDBACK & HAPTICS ---
        mainLayout.addView(createSectionTitle(context, "FEEDBACK & HAPTICS"))

        // Rumble Card
        val rumbleSwitch = SwitchCompat(context).apply {
            isChecked = VibrationManager.rumbleEnabled
            setOnCheckedChangeListener { _, isChecked ->
                VibrationManager.rumbleEnabled = isChecked
                VibrationManager.savePrefs(context)
            }
        }
        val rumbleStrengthLayout = createStrengthSelector(context, VibrationManager.rumbleStrength) { strength ->
            VibrationManager.rumbleStrength = strength
            VibrationManager.savePrefs(context)
            VibrationManager.vibrateRumblePreview()
        }
        mainLayout.addView(createSettingsCard(context, "Game Rumble", "Motor vibrations triggered by in-game events", rumbleSwitch, rumbleStrengthLayout))

        // Haptics Card
        val hapticSwitch = SwitchCompat(context).apply {
            isChecked = VibrationManager.hapticEnabled
            setOnCheckedChangeListener { _, isChecked ->
                VibrationManager.hapticEnabled = isChecked
                VibrationManager.savePrefs(context)
            }
        }
        val hapticStrengthLayout = createStrengthSelector(context, VibrationManager.hapticStrength) { strength ->
            VibrationManager.hapticStrength = strength
            VibrationManager.savePrefs(context)
            VibrationManager.vibrateHaptic()
        }
        mainLayout.addView(createSettingsCard(context, "Button Haptics", "Tactile pulse feedback on button tap", hapticSwitch, hapticStrengthLayout))


        // --- Section: ADVANCED CONTROLS ---
        mainLayout.addView(createSectionTitle(context, "ADVANCED CONTROLS"))

        // Right Stick Mode Card (Default Mode)
        val rsModeLabels = arrayOf("Stick", "Aim", "Cursor")
        val rsModeValues = arrayOf("stick", "aim", "cursor")
        val rsModeDescriptions = mapOf(
            "stick" to "Default virtual stick (holding maintains continuous deflection)",
            "aim" to "Swipe-to-aim for games (no sticky continuous spinning)",
            "cursor" to "Controls actual Windows desktop mouse cursor via SendInput"
        )

        val rsModeBtn = createDropdownAnchorButton(context, rsModeLabels[rsModeValues.indexOf(VibrationManager.defaultRightStickMode).coerceAtLeast(0)], "#e2e8f0")
        var rsModeCard: LinearLayout? = null
        rsModeBtn.setOnClickListener {
            val selectedIdx = rsModeValues.indexOf(VibrationManager.defaultRightStickMode).coerceAtLeast(0)
            showDropdownMenu(rsModeBtn, rsModeLabels, selectedIdx, "#e2e8f0") { which ->
                val chosen = rsModeValues[which]
                VibrationManager.defaultRightStickMode = chosen
                VibrationManager.savePrefs(context)
                val labelTv = rsModeBtn.getChildAt(0) as? TextView
                labelTv?.text = rsModeLabels[which]
                val descTv = rsModeCard?.findViewById<TextView>(2001)
                descTv?.text = rsModeDescriptions[chosen]
            }
        }

        rsModeCard = createSettingsCard(context, "Right Stick Mode", rsModeDescriptions[VibrationManager.defaultRightStickMode] ?: "Select default right stick touch mode", rsModeBtn, null).apply {
            val descTv = getChildAt(0) as? LinearLayout
            val textLayout = descTv?.getChildAt(0) as? LinearLayout
            val descView = textLayout?.getChildAt(1) as? TextView
            descView?.id = 2001
        }
        mainLayout.addView(rsModeCard)

        // Cursor Sensitivity Card (10-level Power Bar)
        val sensitivityPowerBar = createSensitivityPowerBar(context)
        val sensitivityCard = createSettingsCard(
            context,
            "Cursor Sensitivity",
            "Adjust trackpad swipe response multiplier (0.4x to 3.0x)",
            null,
            sensitivityPowerBar
        )
        mainLayout.addView(sensitivityCard)

        // Response Curve Card (Default Curve)
        val curveLabels = arrayOf("Linear", "Dynamic")
        val curveValues = arrayOf("linear", "dynamic")
        val curveDescriptions = mapOf(
            "linear" to "Movement is strictly proportional to swipe distance",
            "dynamic" to "Fast swipes turn far; slow swipes allow micro-aiming"
        )

        val curveBtn = createDropdownAnchorButton(context, curveLabels[curveValues.indexOf(VibrationManager.defaultTrackpadCurve).coerceAtLeast(0)], "#e2e8f0")
        var curveCard: LinearLayout? = null
        curveBtn.setOnClickListener {
            val selectedIdx = curveValues.indexOf(VibrationManager.defaultTrackpadCurve).coerceAtLeast(0)
            showDropdownMenu(curveBtn, curveLabels, selectedIdx, "#e2e8f0") { which ->
                val chosen = curveValues[which]
                VibrationManager.defaultTrackpadCurve = chosen
                VibrationManager.savePrefs(context)
                val labelTv = curveBtn.getChildAt(0) as? TextView
                labelTv?.text = curveLabels[which]
                val descTv = curveCard?.findViewById<TextView>(2002)
                descTv?.text = curveDescriptions[chosen]
            }
        }

        curveCard = createSettingsCard(context, "Response Curve", curveDescriptions[VibrationManager.defaultTrackpadCurve] ?: "Select default pointer response curve", curveBtn, null).apply {
            val descTv = getChildAt(0) as? LinearLayout
            val textLayout = descTv?.getChildAt(0) as? LinearLayout
            val descView = textLayout?.getChildAt(1) as? TextView
            descView?.id = 2002
        }
        mainLayout.addView(curveCard)

        // Trigger Mode Card (Default Digital / Analog)
        val triggerLabels = arrayOf("Digital", "Analog")
        val triggerDescriptions = mapOf(
            false to "Instant click actuation (0 or 255)",
            true to "Progressive pressure slide (13 to 255)"
        )

        val triggerBtn = createDropdownAnchorButton(context, if (VibrationManager.defaultAnalogTriggersEnabled) "Analog" else "Digital", "#e2e8f0")
        var triggerCard: LinearLayout? = null
        triggerBtn.setOnClickListener {
            val selectedIdx = if (VibrationManager.defaultAnalogTriggersEnabled) 1 else 0
            showDropdownMenu(triggerBtn, triggerLabels, selectedIdx, "#e2e8f0") { which ->
                val isAnalog = (which == 1)
                VibrationManager.defaultAnalogTriggersEnabled = isAnalog
                VibrationManager.savePrefs(context)
                val labelTv = triggerBtn.getChildAt(0) as? TextView
                labelTv?.text = triggerLabels[which]
                val descTv = triggerCard?.findViewById<TextView>(2003)
                descTv?.text = triggerDescriptions[isAnalog]
            }
        }

        triggerCard = createSettingsCard(context, "Trigger Mode", triggerDescriptions[VibrationManager.defaultAnalogTriggersEnabled] ?: "Select trigger actuation mode", triggerBtn, null).apply {
            val descTv = getChildAt(0) as? LinearLayout
            val textLayout = descTv?.getChildAt(0) as? LinearLayout
            val descView = textLayout?.getChildAt(1) as? TextView
            descView?.id = 2003
        }
        mainLayout.addView(triggerCard)


        // --- Section: CONNECTIVITY ---
        mainLayout.addView(createSectionTitle(context, "CONNECTIVITY"))

        val prefs = context.getSharedPreferences("ybox_prefs", Context.MODE_PRIVATE)
        val currentRoute = prefs.getString("connection_route", "auto") ?: "auto"

        val routeLabels = arrayOf("Auto (Seamless Failover)", "Wired Only (USB)", "Wireless Only (Wi-Fi)")
        val routeValues = arrayOf("auto", "usb_only", "wifi_only")
        val routeDescriptions = mapOf(
            "auto" to "Connects via USB & Wi-Fi with dual failover",
            "usb_only" to "Connects via direct USB cable or tethering only",
            "wifi_only" to "Connects over local Wi-Fi network only"
        )

        val routeBtn = createDropdownAnchorButton(context, routeLabels[routeValues.indexOf(currentRoute).coerceAtLeast(0)], "#e2e8f0")
        var routeCard: LinearLayout? = null
        routeBtn.setOnClickListener {
            val selectedIdx = routeValues.indexOf(prefs.getString("connection_route", "auto") ?: "auto").coerceAtLeast(0)
            showDropdownMenu(routeBtn, routeLabels, selectedIdx, "#e2e8f0") { which ->
                val chosen = routeValues[which]
                prefs.edit()
                    .putString("connection_route", chosen)
                    .putBoolean("enable_wifi", chosen == "auto" || chosen == "wifi_only")
                    .putBoolean("enable_usb", chosen == "auto" || chosen == "usb_only")
                    .apply()
                val labelTv = routeBtn.getChildAt(0) as? TextView
                labelTv?.text = routeLabels[which]
                val descTv = routeCard?.findViewById<TextView>(1001)
                descTv?.text = routeDescriptions[chosen]
            }
        }

        routeCard = createSettingsCard(context, "Connection Route", routeDescriptions[currentRoute] ?: "Select connection routing", routeBtn, null).apply {
            val descTv = getChildAt(0) as? LinearLayout
            val textLayout = descTv?.getChildAt(0) as? LinearLayout
            val descView = textLayout?.getChildAt(1) as? TextView
            descView?.id = 1001
        }
        mainLayout.addView(routeCard)

        scrollView.addView(mainLayout)
        addView(scrollView)
    }

    private fun createDropdownAnchorButton(context: Context, textStr: String, accentHex: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val shape = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#141722"))
                setStroke(2, Color.parseColor("#252c3c"))
            }
            background = shape
            setPadding(28, 14, 28, 14)
            
            val label = TextView(context).apply {
                text = textStr
                textSize = 12f
                setTextColor(Color.parseColor(accentHex))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            addView(label)

            val arrow = TextView(context).apply {
                text = "▾"
                textSize = 14f
                setTextColor(Color.parseColor("#8c96a5"))
                setPadding(12, 0, 0, 0)
            }
            addView(arrow)
        }
    }

    private fun showDropdownMenu(
        anchorView: View,
        items: Array<String>,
        selectedIndex: Int,
        accentHex: String,
        onSelect: (Int) -> Unit
    ) {
        val popupView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val shape = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(Color.parseColor("#121620")) // Frosted Nordic Slate
                setStroke(2, Color.parseColor("#2a3345"))
            }
            background = shape
            setPadding(12, 12, 12, 12)
            elevation = 32f
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        var popupWindow: PopupWindow? = null

        items.forEachIndexed { idx, label ->
            val isSelected = (idx == selectedIndex)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 18, 24, 18)
                val rowShape = GradientDrawable().apply {
                    cornerRadius = 18f
                    setColor(if (isSelected) Color.parseColor("#1c2536") else Color.TRANSPARENT)
                    if (isSelected) setStroke(1, Color.parseColor("#2a3345"))
                }
                background = rowShape
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 4) }

                val labelTv = TextView(context).apply {
                    text = label
                    setTextColor(if (isSelected) Color.parseColor("#f8fafc") else Color.parseColor("#8c96a5"))
                    textSize = 13f
                    isSingleLine = true
                    setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(labelTv)

                setOnClickListener {
                    onSelect(idx)
                    popupWindow?.dismiss()
                }
            }
            popupView.addView(row)
        }

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView.measuredWidth.coerceAtLeast(anchorView.width)

        popupWindow = PopupWindow(
            popupView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = 32f
        }

        val xOffset = anchorView.width - popupWidth
        popupWindow.showAsDropDown(anchorView, xOffset, 8)
    }

    private fun createSectionTitle(context: Context, title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 13f
            setTextColor(Color.parseColor("#94a3b8"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(16, 48, 0, 16)
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createSettingsCard(context: Context, titleStr: String, descStr: String, controlView: View?, bottomView: View?): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            
            val shape = GradientDrawable().apply {
                cornerRadius = 36f
                setColor(Color.parseColor("#151922")) // Nordic Slate Frosted
                setStroke(2, Color.parseColor("#2a3345"))
            }
            background = shape
            setPadding(44, 44, 44, 44)
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(context).apply {
            text = titleStr
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        textContainer.addView(title)

        val desc = TextView(context).apply {
            text = descStr
            textSize = 13f
            setTextColor(Color.parseColor("#94a3b8"))
            setPadding(0, 6, 0, 0)
        }
        textContainer.addView(desc)

        topRow.addView(textContainer)
        if (controlView != null) {
            topRow.addView(controlView)
        }

        card.addView(topRow)

        if (bottomView != null) {
            val divider = View(context).apply {
                setBackgroundColor(Color.parseColor("#2a3345"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 2
                ).apply { setMargins(0, 28, 0, 28) }
            }
            card.addView(divider)
            card.addView(bottomView)
        }

        return card
    }

    private fun createStrengthSelector(context: Context, currentStrength: Int, onSelect: (Int) -> Unit): LinearLayout {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val buttons = mutableListOf<Button>()

        val options = listOf("Low", "Medium", "High")
        for (i in options.indices) {
            val btn = Button(context).apply {
                text = options[i]
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(6, 0, 6, 0)
                }
            }
            buttons.add(btn)
            layout.addView(btn)
        }

        val updateButtonStyles = { selectedIdx: Int ->
            for (i in buttons.indices) {
                val btn = buttons[i]
                val shape = GradientDrawable().apply {
                    cornerRadius = 20f
                    if (i == selectedIdx) {
                        setColor(Color.parseColor("#e2e8f0"))
                        setStroke(2, Color.parseColor("#e2e8f0"))
                    } else {
                        setColor(Color.parseColor("#141722"))
                        setStroke(2, Color.parseColor("#242836"))
                    }
                }
                btn.background = shape
                btn.setTextColor(if (i == selectedIdx) Color.parseColor("#090a0f") else Color.parseColor("#8c96a5"))
            }
        }

        for (i in buttons.indices) {
            buttons[i].setOnClickListener {
                updateButtonStyles(i)
                onSelect(i)
            }
        }

        updateButtonStyles(currentStrength)

        return layout
    }

    private fun createSensitivityPowerBar(context: Context): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        val minLabel = TextView(context).apply {
            text = "LOW"
            textSize = 10f
            setTextColor(Color.parseColor("#64748b"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val levelLabel = TextView(context).apply {
            val lvl = VibrationManager.cursorSensitivityLevel
            val mult = String.format(java.util.Locale.US, "%.1fx", VibrationManager.trackpadSensitivity)
            text = "Level $lvl ($mult)"
            textSize = 12f
            setTextColor(Color.parseColor("#38bdf8"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val maxLabel = TextView(context).apply {
            text = "HIGH"
            textSize = 10f
            gravity = Gravity.END
            setTextColor(Color.parseColor("#64748b"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        headerRow.addView(minLabel)
        headerRow.addView(levelLabel)
        headerRow.addView(maxLabel)
        container.addView(headerRow)

        val barRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val segmentViews = mutableListOf<View>()

        fun updateSegments(selectedLevel: Int) {
            val mult = String.format(java.util.Locale.US, "%.1fx", VibrationManager.trackpadSensitivity)
            levelLabel.text = "Level $selectedLevel ($mult)"
            
            for (i in 0 until 10) {
                val seg = segmentViews[i]
                val isActive = (i < selectedLevel)
                val shape = GradientDrawable().apply {
                    cornerRadius = 8f
                    if (isActive) {
                        val ratio = i.toFloat() / 9f
                        val r = (0x38 + ((0xf8 - 0x38) * ratio)).toInt()
                        val g = (0xbd + ((0xfa - 0xbd) * ratio)).toInt()
                        val b = (0xf8 + ((0xfc - 0xf8) * ratio)).toInt()
                        setColor(Color.rgb(r, g, b))
                    } else {
                        setColor(Color.parseColor("#1c2230"))
                        setStroke(2, Color.parseColor("#2a3345"))
                    }
                }
                seg.background = shape
                seg.alpha = if (isActive) 1.0f else 0.45f
            }
        }

        val handleBarTouch = { x: Float ->
            val w = barRow.width.toFloat()
            if (w > 0) {
                val fraction = (x / w).coerceIn(0f, 0.999f)
                val lvl = (fraction * 10).toInt() + 1
                if (lvl != VibrationManager.cursorSensitivityLevel) {
                    VibrationManager.setCursorSensitivity(lvl)
                    VibrationManager.savePrefs(context)
                    VibrationManager.vibrateHaptic()
                    updateSegments(lvl)
                }
            }
        }

        barRow.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    handleBarTouch(event.x)
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    handleBarTouch(event.x)
                    true
                }
                else -> false
            }
        }

        for (i in 1..10) {
            val seg = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 36, 1f).apply {
                    setMargins(4, 0, 4, 0)
                }
                setOnClickListener {
                    VibrationManager.setCursorSensitivity(i)
                    VibrationManager.savePrefs(context)
                    VibrationManager.vibrateHaptic()
                    updateSegments(i)
                }
            }
            segmentViews.add(seg)
            barRow.addView(seg)
        }

        updateSegments(VibrationManager.cursorSensitivityLevel)
        container.addView(barRow)

        return container
    }
}
