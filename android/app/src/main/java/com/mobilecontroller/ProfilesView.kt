package com.mobilecontroller

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ProfilesView(context: Context, val onLaunchEditor: (Int) -> Unit) : FrameLayout(context) {

    private val recyclerView: RecyclerView
    private val adapter: ProfilesAdapter
    private var profiles = mutableListOf<ControllerProfile>()
    private val btnAdd: ImageButton

    init {
        setBackgroundColor(Color.TRANSPARENT)

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setPadding(64, 128, 64, 64)
        }

        val topLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, 32)
        }
        val title = TextView(context).apply {
            text = "Layout Profiles"
            textSize = 36f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topLayout.addView(title)

        btnAdd = ImageButton(context).apply {
            setImageResource(R.drawable.ic_add)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#e2e8f0"))
            setPadding(24, 24, 24, 24)
            setOnClickListener {
                if (profiles.size < ProfileStorage.MAX_PROFILES) {
                    ProfileStorage.addProfile(context)
                    refreshProfiles()
                }
            }
        }
        topLayout.addView(btnAdd)
        mainLayout.addView(topLayout)

        adapter = ProfilesAdapter()
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@ProfilesView.adapter
            clipToPadding = false
            setPadding(0, 0, 0, 300)
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(150)
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Hold to Drag & Drop Reordering
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION &&
                    fromPos < profiles.size && toPos < profiles.size && fromPos != toPos) {
                    ProfileStorage.swapProfiles(context, fromPos, toPos)
                    val item = profiles.removeAt(fromPos)
                    profiles.add(toPos, item)
                    adapter.notifyItemMoved(fromPos, toPos)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = true

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.animate()?.scaleX(1.03f)?.scaleY(1.03f)?.translationZ(24f)?.setDuration(150)?.start()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.translationZ(0f)?.setDuration(150)?.start()
                adapter.notifyDataSetChanged()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        mainLayout.addView(recyclerView)
        addView(mainLayout)

        refreshProfiles()
    }

    fun refreshProfiles() {
        profiles.clear()
        profiles.addAll(ProfileStorage.getLocalProfiles(context))
        val isMax = profiles.size >= ProfileStorage.MAX_PROFILES
        btnAdd.isEnabled = !isMax
        btnAdd.alpha = if (isMax) 0.3f else 1.0f
        adapter.notifyDataSetChanged()
    }

    private fun showRenameDialog(position: Int, currentName: String) {
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val shape = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(Color.parseColor("#12141c"))
                setStroke(2, Color.parseColor("#242836"))
            }
            background = shape
            setPadding(56, 44, 56, 44)
        }

        val title = TextView(context).apply {
            text = "Rename Profile"
            textSize = 20f
            setTextColor(Color.parseColor("#f8fafc"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 28)
        }
        dialogView.addView(title)

        val input = android.widget.EditText(context).apply {
            setText(currentName)
            setSelection(text.length)
            setTextColor(Color.parseColor("#f8fafc"))
            setHintTextColor(Color.parseColor("#64748b"))
            hint = "Max ${ProfileStorage.MAX_NAME_LENGTH} characters"
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(ProfileStorage.MAX_NAME_LENGTH))
            val inputShape = GradientDrawable().apply {
                cornerRadius = 18f
                setColor(Color.parseColor("#1a1e2a"))
                setStroke(2, Color.parseColor("#38bdf8"))
            }
            background = inputShape
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 36)
            }
        }
        dialogView.addView(input)

        val btnContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        var dialog: android.app.Dialog? = null

        val btnCancel = TextView(context).apply {
            text = "Cancel"
            textSize = 15f
            setTextColor(Color.parseColor("#94a3b8"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(32, 16, 32, 16)
            setOnClickListener { dialog?.dismiss() }
        }
        btnContainer.addView(btnCancel)

        val btnSave = TextView(context).apply {
            text = "Save"
            textSize = 15f
            setTextColor(Color.parseColor("#090a0f"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            val saveShape = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#38bdf8")) // Frost Cyan
            }
            background = saveShape
            setPadding(40, 18, 40, 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(20, 0, 0, 0)
            }
            setOnClickListener {
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    ProfileStorage.renameProfile(context, position, newName)
                    refreshProfiles()
                }
                dialog?.dismiss()
            }
        }
        btnContainer.addView(btnSave)
        dialogView.addView(btnContainer)

        dialog = android.app.Dialog(context).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(dialogView)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            show()
        }
        input.requestFocus()
    }

    private fun createIconButton(iconRes: Int, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            setImageResource(iconRes)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#94a3b8"))
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
        }
    }

    inner class ProfilesAdapter : RecyclerView.Adapter<ProfilesAdapter.ViewHolder>() {

        inner class ViewHolder(val view: LinearLayout) : RecyclerView.ViewHolder(view) {
            val leftLayout = if (view.childCount > 0) view.getChildAt(0) as LinearLayout else null
            val badge = leftLayout?.getChildAt(0) as? TextView
            val aliasText = leftLayout?.getChildAt(1) as? TextView
            val actionsLayout = if (view.childCount > 1) view.getChildAt(1) as LinearLayout else null
        }

        override fun getItemCount() = profiles.size + 1 // +1 for bottom spacer

        override fun getItemViewType(position: Int): Int {
            return if (position == profiles.size) 1 else 0
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            if (viewType == 1) { // Spacer
                val spacer = LinearLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        300
                    )
                }
                return ViewHolder(spacer)
            }
            
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val marginParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                marginParams.setMargins(0, 0, 0, 24)
                layoutParams = marginParams

                val shape = GradientDrawable().apply {
                    cornerRadius = 36f
                    setColor(Color.parseColor("#12141c")) // Frosted Nordic Slate
                    setStroke(2, Color.parseColor("#242836"))
                }
                background = shape
                setPadding(44, 44, 44, 44)
            }

            val leftLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val badge = TextView(context).apply {
                textSize = 16f
                setTextColor(Color.parseColor("#090a0f"))
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                val badgeShape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#e2e8f0")) // Grayscale Frost White
                }
                background = badgeShape
                layoutParams = LinearLayout.LayoutParams(76, 76).apply { setMargins(0, 0, 28, 0) }
            }
            leftLayout.addView(badge)

            val aliasText = TextView(context).apply {
                textSize = 19f
                setTextColor(Color.parseColor("#f8fafc"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            leftLayout.addView(aliasText)
            
            card.addView(leftLayout)

            val actionsLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            card.addView(actionsLayout)

            return ViewHolder(card)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (getItemViewType(position) == 1) return // Do nothing for spacer
            
            val profile = profiles[position]
            holder.badge?.text = "${position + 1}"
            holder.aliasText?.text = profile.name

            holder.actionsLayout?.removeAllViews()
            
            // Rename icon button
            holder.actionsLayout?.addView(createIconButton(R.drawable.ic_edit) {
                showRenameDialog(position, profile.name)
            })

            // Delete icon button
            if (profiles.size > 1) {
                holder.actionsLayout?.addView(createIconButton(R.drawable.ic_delete) {
                    ProfileStorage.deleteProfile(context, position)
                    refreshProfiles()
                })
            }

            holder.view.setOnClickListener {
                onLaunchEditor(position)
            }
        }
    }
}
