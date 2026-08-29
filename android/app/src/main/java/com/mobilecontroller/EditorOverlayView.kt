package com.mobilecontroller

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

class EditorOverlayView(
    context: Context,
    val controllerView: ControllerView,
    val activeSlot: Int,
    val onSave: (ControllerProfile, Int) -> Unit,
    val onCancel: () -> Unit
) : FrameLayout(context) {

    private val dynamicIsland: LinearLayout
    private lateinit var toolsPill: LinearLayout
    private lateinit var toolsPillContainer: android.widget.HorizontalScrollView
    
    private val handler = Handler(Looper.getMainLooper())
    private var isUiVisible = false

    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    
    private var btnUndo: ImageButton? = null
    private var btnRedo: ImageButton? = null

    private fun pushSnapshot() {
        val p = controllerView.getProfile()
        if (p != null) {
            val json = ProfileStorage.serializeProfile(p)
            if (undoStack.isEmpty() || undoStack.last() != json) {
                undoStack.add(json)
                redoStack.clear()
                updateUndoRedoButtons()
            }
        }
    }

    private fun updateUndoRedoButtons() {
        val canUndo = undoStack.size > 1
        btnUndo?.isEnabled = canUndo
        btnUndo?.alpha = if (canUndo) 1.0f else 0.35f

        val canRedo = redoStack.isNotEmpty()
        btnRedo?.isEnabled = canRedo
        btnRedo?.alpha = if (canRedo) 1.0f else 0.35f
    }

    init {
        setBackgroundColor(Color.parseColor("#090a0f"))
        addView(controllerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Capture initial snapshot
        val initialProfile = controllerView.getProfile()
        if (initialProfile != null) {
            undoStack.add(ProfileStorage.serializeProfile(initialProfile))
        }

        dynamicIsland = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val shape = GradientDrawable().apply {
                cornerRadius = 48f
                setColor(Color.parseColor("#e60f1118")) // Semi-transparent Nordic Frost glass
                setStroke(2, Color.parseColor("#242836"))
            }
            background = shape
            setPadding(40, 20, 40, 20)
            
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setMargins(0, 64, 0, 0)
            }
            translationY = -300f
        }
        
        val profileNameText = TextView(context).apply {
            text = controllerView.getProfile()?.name ?: "Layout Editor"
            textSize = 18f
            setTextColor(Color.parseColor("#f8fafc"))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(16, 0, 16, 0)
        }
        dynamicIsland.addView(profileNameText)
        addView(dynamicIsland)

        toolsPillContainer = android.widget.HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 32)
            }
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            translationY = 300f
            
            toolsPill = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val shape = GradientDrawable().apply {
                    cornerRadius = 48f
                    setColor(Color.parseColor("#e60f1118")) // Semi-transparent Nordic Frost glass
                    setStroke(2, Color.parseColor("#242836"))
                }
                background = shape
                setPadding(32, 16, 32, 16)
                
                val undoBtn = createToolButton(R.drawable.ic_undo) { 
                    if (undoStack.size > 1) {
                        val currentJson = undoStack.removeAt(undoStack.size - 1)
                        redoStack.add(currentJson)
                        
                        val targetJson = undoStack.last()
                        val targetProfile = ProfileManager.parseProfile(targetJson)
                        if (targetProfile != null) controllerView.setProfile(targetProfile)
                        updateUndoRedoButtons()
                    }
                }
                btnUndo = undoBtn
                addView(undoBtn)

                val redoBtn = createToolButton(R.drawable.ic_redo) { 
                    if (redoStack.isNotEmpty()) {
                        val nextJson = redoStack.removeAt(redoStack.size - 1)
                        undoStack.add(nextJson)
                        
                        val nextProfile = ProfileManager.parseProfile(nextJson)
                        if (nextProfile != null) controllerView.setProfile(nextProfile)
                        updateUndoRedoButtons()
                    }
                }
                btnRedo = redoBtn
                addView(redoBtn)

                addView(createToolButton(R.drawable.ic_reset) {
                    val currentP = controllerView.getProfile()
                    val p = ProfileManager.loadDefaultProfile()
                    p.id = currentP?.id ?: "slot${activeSlot + 1}"
                    p.name = currentP?.name ?: ProfileStorage.getLocalProfiles(context).getOrNull(activeSlot)?.name ?: "Alpha"
                    controllerView.setProfile(p)
                    pushSnapshot()
                })
                addView(createToolButton(R.drawable.ic_cancel) { onCancel() })
                addView(createToolButton(R.drawable.ic_save) { 
                    val p = controllerView.getProfile()
                    if (p != null) onSave(p, activeSlot)
                })
            }
            addView(toolsPill)
        }
        addView(toolsPillContainer)

        updateUndoRedoButtons()
        showUi()
        
        controllerView.onCanvasTouch = { 
            if (isUiVisible) {
                hideUi()
                handler.removeCallbacks(hideRunnable)
            } else {
                showUi()
            }
        }
        controllerView.onDragComplete = { pushSnapshot() }
    }

    // Called by MainActivity to forward touch events for auto-hiding
    fun onCanvasTouch() {
        showUi()
    }

    private fun createToolButton(iconRes: Int, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            setImageResource(iconRes)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setPadding(16, 32, 16, 32)
            setOnClickListener {
                onClick()
                showUi()
            }
        }
    }

    private val hideRunnable = Runnable { hideUi() }

    private fun showUi() {
        if (!isUiVisible) {
            isUiVisible = true
            ObjectAnimator.ofFloat(dynamicIsland, "translationY", 0f).apply {
                duration = 300
                interpolator = android.view.animation.DecelerateInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(toolsPillContainer, "translationY", 0f).apply {
                duration = 300
                interpolator = android.view.animation.DecelerateInterpolator()
                start()
            }
        }
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 4000)
    }

    private fun hideUi() {
        if (isUiVisible) {
            isUiVisible = false
            ObjectAnimator.ofFloat(dynamicIsland, "translationY", -300f).apply {
                duration = 300
                interpolator = android.view.animation.AccelerateInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(toolsPillContainer, "translationY", 300f).apply {
                duration = 300
                interpolator = android.view.animation.AccelerateInterpolator()
                start()
            }
        }
    }
}
