package com.mobilecontroller

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class HomeView(context: Context, val onLaunchGamepad: (String, Int) -> Unit) : FrameLayout(context) {

    val swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private val viewPager: ViewPager2
    private val dotsIndicatorLayout: LinearLayout
    private val adapter: DeviceAdapter
    private var devices: List<MainActivity.DiscoveredDevice> = emptyList()
    
    var onRefresh: (() -> Unit)? = null
    private var connectedIp: String? = null
    private val statusText: TextView

    private val textUpdateRunnable = object : Runnable {
        override fun run() {
            updateStatusText()
            postDelayed(this, 3000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(textUpdateRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(textUpdateRunnable)
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setPadding(64, 128, 64, 280)
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 32) }
        }

        val iconSize = (56 * context.resources.displayMetrics.density).toInt()
        val title = android.widget.ImageView(context).apply {
            setImageResource(R.drawable.ic_yeval)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        }
        topRow.addView(title)
        
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        topRow.addView(spacer)

        val batteryIcon = BatteryGamepadView(context).apply {
            layoutParams = LinearLayout.LayoutParams(160, 160)
        }
        topRow.addView(batteryIcon)
        
        mainLayout.addView(topRow)
        
        statusText = TextView(context).apply {
            text = "Searching for devices..."
            textSize = 14f
            setTextColor(Color.parseColor("#8c96a5"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        mainLayout.addView(statusText)

        val density = context.resources.displayMetrics.density

        // --- In-App Update Notification Banner ---
        val updateBannerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val shape = GradientDrawable().apply {
                cornerRadius = 16f * density
                setColor(Color.parseColor("#151d30"))
                setStroke(1, Color.parseColor("#38bdf8"))
            }
            background = shape
            setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (16 * density).toInt()) }
        }
        mainLayout.addView(updateBannerLayout)

        UpdateManager.checkForUpdate(context) { updateInfo ->
            if (updateInfo != null && updateInfo.hasUpdate) {
                updateBannerLayout.removeAllViews()
                val textLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val bannerTitle = TextView(context).apply {
                    text = "Update ${updateInfo.latestVersion} Available"
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                val bannerDesc = TextView(context).apply {
                    text = "Tap to update Yeval client"
                    textSize = 11f
                    setTextColor(Color.parseColor("#94a3b8"))
                }
                textLayout.addView(bannerTitle)
                textLayout.addView(bannerDesc)
                updateBannerLayout.addView(textLayout)

                val updateBtn = TextView(context).apply {
                    text = "Update"
                    textSize = 12f
                    setTextColor(Color.parseColor("#0f172a"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    val btnBg = GradientDrawable().apply {
                        cornerRadius = 12f * density
                        setColor(Color.parseColor("#38bdf8"))
                    }
                    background = btnBg
                    setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
                    setOnClickListener {
                        text = "..."
                        isEnabled = false
                        UpdateManager.downloadAndInstall(context, updateInfo.apkDownloadUrl, { progress ->
                            text = "$progress%"
                        }, {
                            isEnabled = true
                            text = "Retry"
                        })
                    }
                }
                updateBannerLayout.addView(updateBtn)
                updateBannerLayout.visibility = View.VISIBLE
            }
        }

        adapter = DeviceAdapter()

        viewPager = ViewPager2(context).apply {
            this.adapter = this@HomeView.adapter
            clipToPadding = true
            clipChildren = true
            offscreenPageLimit = 1

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateCarouselIndicators(position)
                }
            })

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }



        val nestedHost = NestedScrollableHost(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins((20 * density).toInt(), 0, (20 * density).toInt(), (12 * density).toInt()) }
            addView(viewPager)
        }

        mainLayout.addView(nestedHost)

        dotsIndicatorLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (16 * density).toInt()
            ).apply { setMargins(0, 0, 0, (48 * density).toInt()) }
            visibility = View.GONE
        }
        mainLayout.addView(dotsIndicatorLayout)

        mainLayout.addView(createGuideSection(context))

        val scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(100)
            addView(mainLayout)
        }

        swipeRefreshLayout = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(context).apply {
            setColorSchemeColors(Color.parseColor("#e2e8f0"))
            setProgressBackgroundColorSchemeColor(Color.parseColor("#12141c"))
            setOnRefreshListener {
                onRefresh?.invoke()
            }
            addView(scrollView)
        }
        addView(swipeRefreshLayout)
    }

    private fun createGuideSection(context: Context): LinearLayout {
        val guideContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 32, 0, 0) }
        }

        val title = TextView(context).apply {
            text = "Welcome to Yeval"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 0, 16, 24) }
        }
        guideContainer.addView(title)

        fun addCard(iconRes: Int, cardTitle: String, htmlText: String) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val shape = GradientDrawable().apply {
                    cornerRadius = 36f
                    setColor(Color.parseColor("#151922"))
                    setStroke(2, Color.parseColor("#2a3345"))
                }
                background = shape
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 24) }
            }

            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(44, 44, 44, 44)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val icon = android.widget.ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#38bdf8"))
                layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(0, 0, 32, 0) }
            }
            headerRow.addView(icon)

            val titleText = TextView(context).apply {
                text = cardTitle
                textSize = 17f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            headerRow.addView(titleText)

            val chevron = TextView(context).apply {
                text = "▾"
                textSize = 20f
                setTextColor(Color.parseColor("#8c96a5"))
            }
            headerRow.addView(chevron)

            val contentContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(44, 0, 44, 44)
                visibility = View.GONE
                
                val desc = TextView(context).apply {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        text = android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_COMPACT)
                    } else {
                        @Suppress("DEPRECATION")
                        text = android.text.Html.fromHtml(htmlText)
                    }
                    textSize = 14f
                    setTextColor(Color.parseColor("#94a3b8"))
                    setLineSpacing(12f, 1.2f)
                }
                addView(desc)
            }

            card.addView(headerRow)
            card.addView(contentContainer)

            var isExpanded = false
            card.setOnClickListener {
                isExpanded = !isExpanded
                contentContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
                chevron.animate().rotation(if (isExpanded) -180f else 0f).setDuration(200).start()
            }
            guideContainer.addView(card)
        }

        addCard(R.drawable.ic_wifi, "1. How to Connect", 
            "<b>Network (Wi-Fi / Hotspot):</b><br>Connect your PC and Phone to the same Wi-Fi network, or use a Mobile Hotspot.<br><br><b>USB (Tethering / Debugging):</b><br>For the absolute lowest latency, connect a USB cable and enable USB Tethering or USB Debugging in your Android phone settings.<br><br><b>Connecting:</b><br>Any discovered PCs will appear as cards on the Home screen. Swipe to switch between them, and tap the card to instantly connect."
        )
        
        addCard(R.drawable.ic_layout, "2. Layout Modes: Button vs Zone", 
            "<b>Button Mode:</b><br>The classic console controller layout with standard floating joysticks and circular face buttons.<br><br><b>Zone Mode:</b><br>Replaces all floating buttons with a seamless, invisible geometric grid covering the entire screen. Instead of aiming for a small circular 'A' button, the entire bottom-right area of your screen becomes the 'A' zone. The left and right stick areas become massive trackpads. Designed for playing entirely by muscle memory.<br><br><b>Important:</b><br>The layout mode is tied to your profile, but it <b>can only be changed using the Yeval Dashboard on your PC</b>. The mobile app editor does not have a toggle to change it."
        )
        
        addCard(R.drawable.ic_view, "3. In-Game Menus (The Pills)", 
            "<b>Top Island (Profile Slots):</b><br>A scrolling horizontal pill that shows your active profiles. Tap a slot to instantly swap your button layout on the fly.<br><br><b>Right Pill (Quick Settings):</b><br>Your on-the-fly toggles. Change your Right Stick mode, switch your Trackpad Curve, and toggle your Triggers.<br><i>Note: The Right Stick Mode and Trackpad Curve buttons only appear in Zone Mode. Furthermore, the Curve button is only activated when you are in Aim or Cursor mode (disabled in Stick mode).</i><br><br><b>Left Pill (Sensitivity Slabs):</b><br>A vertical 10-level power bar that only appears when using <b>Cursor mode</b>. Slide your finger up and down to instantly adjust your mouse speed."
        )
        
        addCard(R.drawable.ic_profile, "4. Profiles & Customization", 
            "<b>Creating & Editing:</b><br>Go to the Profiles tab and tap the <b>+</b> button to create a new layout. Tap on any existing profile card to open the <b>Layout Editor</b>, where you can drag, drop, and resize your buttons.<br><br><b>Renaming & Removing:</b><br>Each profile card has a rename icon and a delete icon. You can also hold and drag profiles to reorder them in your list.<br><br><b>Preferred Profile:</b><br>To assign a default profile to a specific PC, go to the Home tab and tap the dropdown pill on that PC's card (it defaults to saying 'PC Layout'). Select your preferred profile, and it will load automatically."
        )

        addCard(R.drawable.ic_setting, "5. Settings Explained", 
            "<b>Right Stick Mode (Zone Mode only):</b><br>• <i>Stick:</i> Default virtual stick. Holding it at the edge maintains continuous spin.<br>• <i>Aim:</i> Swipe-to-aim for games (no sticky continuous spinning).<br>• <i>Cursor:</i> Controls your actual Windows desktop mouse cursor.<br><br><b>Response Curve (Zone Mode — Aim & Cursor only):</b><br>• <i>Linear:</i> Raw 1:1 input with no acceleration.<br>• <i>Dynamic:</i> Speed-accelerated input (a faster swipe throws further).<br><i>Note: The curve is only active when Right Stick Mode is set to Aim or Cursor.</i><br><br><b>Trigger Mode:</b><br>• <i>Digital:</i> Instant click actuation.<br>• <i>Analog:</i> Progressive pressure slide simulating a real physical trigger."
        )

        return guideContainer
    }

    fun setRefreshing(refreshing: Boolean) {
        swipeRefreshLayout.isRefreshing = refreshing
    }

    fun updateDevices(newDevices: List<MainActivity.DiscoveredDevice>) {
        devices = newDevices
        adapter.notifyDataSetChanged()
        updateStatusText()
        updateCarouselIndicators(viewPager.currentItem)
    }

    private fun updateCarouselIndicators(currentPos: Int) {
        val density = context.resources.displayMetrics.density
        dotsIndicatorLayout.removeAllViews()

        if (devices.size <= 1) {
            dotsIndicatorLayout.visibility = View.GONE
            return
        }

        dotsIndicatorLayout.visibility = View.VISIBLE
        for (i in 0 until devices.size) {
            val isSelected = i == currentPos
            val dot = View(context).apply {
                val dotWidth = if (isSelected) (24 * density).toInt() else (8 * density).toInt()
                val dotHeight = (8 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(dotWidth, dotHeight).apply {
                    setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                }
                val dotShape = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(if (isSelected) Color.parseColor("#e2e8f0") else Color.parseColor("#242836"))
                }
                background = dotShape
            }
            dotsIndicatorLayout.addView(dot)
        }
    }

    private fun updateStatusText() {
        if (devices.isEmpty()) {
            statusText.text = "No devices found. (Pull down to scan)"
            return
        }
        
        val onlineCount = devices.count { it.lastSeen > 0L && (System.currentTimeMillis() - it.lastSeen < 15000) || it.ip == "USB" }
        if (devices.size > 1) {
            statusText.alpha = 1f
            statusText.text = "Swipe to switch PCs (${devices.size} available)"
        } else if (onlineCount > 0) {
            statusText.alpha = 1f
            statusText.text = "Tap card to connect"
        } else {
            statusText.alpha = 1f
            statusText.text = "Devices offline. Pull down to scan."
        }
    }

    fun disconnect() {
        connectedIp = null
        adapter.notifyDataSetChanged()
        updateStatusText()
    }

    private fun getDeviceKey(device: MainActivity.DiscoveredDevice): String {
        return if (device.serverId.isNotBlank()) device.serverId else device.ip
    }

    private fun getPreferredProfileForDevice(device: MainActivity.DiscoveredDevice): Int {
        val prefs = context.getSharedPreferences("home_device_prefs", Context.MODE_PRIVATE)
        val savedPref = prefs.getInt("pref_profile_${getDeviceKey(device)}", -1)
        if (savedPref == -1) return -1
        val localProfiles = ProfileStorage.getLocalProfiles(context)
        if (savedPref !in localProfiles.indices) {
            prefs.edit().putInt("pref_profile_${getDeviceKey(device)}", -1).apply()
            return -1
        }
        return savedPref
    }

    private fun setPreferredProfileForDevice(device: MainActivity.DiscoveredDevice, profileIndex: Int) {
        val prefs = context.getSharedPreferences("home_device_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("pref_profile_${getDeviceKey(device)}", profileIndex).apply()
    }

    private fun getProfileNameForIndex(index: Int): String {
        if (index == -1) return "PC Layout"
        val localProfiles = ProfileStorage.getLocalProfiles(context)
        return if (index in localProfiles.indices) localProfiles[index].name else "PC Layout"
    }

    private fun showDeviceProfileDropdown(anchorView: View, device: MainActivity.DiscoveredDevice, labelView: TextView) {
        val currentSelected = getPreferredProfileForDevice(device)
        val localProfiles = ProfileStorage.getLocalProfiles(context)
        
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

        data class ProfileOption(val index: Int, val badge: String, val title: String)
        val options = mutableListOf<ProfileOption>()
        options.add(ProfileOption(-1, "PC", "PC Layout"))
        localProfiles.forEachIndexed { idx, p ->
            options.add(ProfileOption(idx, "${idx + 1}", p.name))
        }

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = true
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(30)
        }
        
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        var popupWindow: PopupWindow? = null

        options.forEach { opt ->
            val isSelected = (opt.index == currentSelected)
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
                
                val badgeView = TextView(context).apply {
                    text = opt.badge
                    setTextColor(Color.parseColor("#090a0f"))
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    val badgeShape = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (isSelected) Color.parseColor("#e2e8f0") else Color.parseColor("#8c96a5"))
                    }
                    background = badgeShape
                    layoutParams = LinearLayout.LayoutParams(48, 48).apply { setMargins(0, 0, 20, 0) }
                }
                addView(badgeView)
                
                val titleView = TextView(context).apply {
                    text = opt.title
                    setTextColor(if (isSelected) Color.parseColor("#f8fafc") else Color.parseColor("#8c96a5"))
                    textSize = 13f
                    isSingleLine = true
                    setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(0, 0, 24, 0)
                    }
                }
                addView(titleView)
                
                setOnClickListener {
                    setPreferredProfileForDevice(device, opt.index)
                    labelView.text = opt.title
                    popupWindow?.dismiss()
                }
            }
            listContainer.addView(row)
        }

        scrollView.addView(listContainer)
        popupView.addView(scrollView)

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

    inner class DeviceAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        private val VIEW_TYPE_EMPTY = 0
        private val VIEW_TYPE_DEVICE = 1

        inner class EmptyViewHolder(val view: LinearLayout) : RecyclerView.ViewHolder(view)
        
        inner class DeviceViewHolder(
            val card: FrostDeviceCardView,
            val laptopView: BatteryLaptopView,
            val nameText: TextView,
            val ipText: TextView,
            val connectionIcons: LinearLayout,
            val slotsLabel: TextView,
            val profileBtn: LinearLayout,
            val profileBtnText: TextView
        ) : RecyclerView.ViewHolder(card)

        override fun getItemViewType(position: Int): Int {
            return if (devices.isEmpty()) VIEW_TYPE_EMPTY else VIEW_TYPE_DEVICE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == VIEW_TYPE_EMPTY) {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val shape = GradientDrawable().apply {
                        cornerRadius = 64f
                        setColor(Color.parseColor("#151922"))
                        setStroke(3, Color.parseColor("#2a3345"), 16f, 16f)
                    }
                    background = shape
                    setPadding(64, 96, 64, 96)
                }
                
                val titleContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 48) }
                }
                
                val emptyTitle = TextView(context).apply {
                    text = "How to Connect"
                    textSize = 24f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.START
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                titleContainer.addView(emptyTitle)
                card.addView(titleContainer)

                fun addInstruction(iconRes: Int, titleText: String, descText: String) {
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, 0, 32) }
                    }
                    val instructionIcon = android.widget.ImageView(context).apply {
                        setImageResource(iconRes)
                        imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#38bdf8"))
                        layoutParams = LinearLayout.LayoutParams(96, 96).apply { setMargins(0, 0, 32, 0) }
                    }
                    row.addView(instructionIcon)
                    
                    val textCol = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val t = TextView(context).apply {
                        text = titleText
                        textSize = 16f
                        setTextColor(Color.WHITE)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    val d = TextView(context).apply {
                        text = descText
                        textSize = 14f
                        setTextColor(Color.parseColor("#94a3b8"))
                        setPadding(0, 8, 0, 0)
                    }
                    textCol.addView(t)
                    textCol.addView(d)
                    row.addView(textCol)
                    card.addView(row)
                }
                
                addInstruction(R.drawable.ic_wifi, "Network (Wi-Fi / Hotspot)", "Connect your PC and Phone to the same Wi-Fi network, or use Mobile Hotspot.")
                addInstruction(R.drawable.ic_usb, "USB (Tethering / Debugging)", "Connect via USB and enable USB Tethering or USB Debugging in your Phone settings for ultra-low latency.")

                val subText = TextView(context).apply {
                    text = "Searching for PC on the network..."
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748b"))
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 32, 0, 0) }
                }
                card.addView(subText)
                return EmptyViewHolder(card)
            }

            val card = FrostDeviceCardView(context).apply {
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            // Top Row for Title, Laptop Icon, and Subtitle
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            val laptopView = BatteryLaptopView(context).apply {
                layoutParams = LinearLayout.LayoutParams(180, 180).apply {
                    setMargins(0, 0, 0, 20)
                }
            }
            topRow.addView(laptopView)
            
            val nameText = TextView(context).apply {
                textSize = 26f
                setTextColor(Color.parseColor("#f8fafc"))
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 6) }
            }
            topRow.addView(nameText)

            val ipText = TextView(context).apply {
                textSize = 14f
                setTextColor(Color.parseColor("#8c96a5"))
                gravity = Gravity.CENTER
            }
            topRow.addView(ipText)
            card.addView(topRow)

            // Transport Badges Row (Capsules)
            val statsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 28, 0, 28) }
            }

            val wifiBadge = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val badgeShape = GradientDrawable().apply {
                    cornerRadius = 32f
                    setColor(Color.parseColor("#141722"))
                    setStroke(2, Color.parseColor("#252c3c"))
                }
                background = badgeShape
                setPadding(28, 14, 28, 14)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 20, 0) }

                val icon = android.widget.ImageView(context).apply {
                    setImageResource(R.drawable.ic_wifi)
                    imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#e2e8f0"))
                    layoutParams = LinearLayout.LayoutParams(36, 36).apply { setMargins(0, 0, 12, 0) }
                }
                addView(icon)

                val label = TextView(context).apply {
                    text = "Wi-Fi"
                    textSize = 12f
                    setTextColor(Color.parseColor("#e2e8f0"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                addView(label)
            }
            statsRow.addView(wifiBadge)

            val usbBadge = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val badgeShape = GradientDrawable().apply {
                    cornerRadius = 32f
                    setColor(Color.parseColor("#141722"))
                    setStroke(2, Color.parseColor("#252c3c"))
                }
                background = badgeShape
                setPadding(28, 14, 28, 14)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                val icon = android.widget.ImageView(context).apply {
                    setImageResource(R.drawable.ic_usb)
                    imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#e2e8f0"))
                    layoutParams = LinearLayout.LayoutParams(36, 36).apply { setMargins(0, 0, 12, 0) }
                }
                addView(icon)

                val label = TextView(context).apply {
                    text = "USB"
                    textSize = 12f
                    setTextColor(Color.parseColor("#e2e8f0"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                addView(label)
            }
            statsRow.addView(usbBadge)

            card.addView(statsRow)

            val actionsLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val slotsLabel = TextView(context).apply {
                textSize = 13f
                setTextColor(Color.parseColor("#8c96a5"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
            }
            actionsLayout.addView(slotsLabel)

            // Preferred Profile Dropdown Pill (PC-like dropdown anchor)
            val profileBtn = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val shape = GradientDrawable().apply {
                    cornerRadius = 48f
                    setColor(Color.parseColor("#141722"))
                    setStroke(2, Color.parseColor("#252c3c"))
                }
                background = shape
                setPadding(36, 22, 36, 22)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val profileIcon = android.widget.ImageView(context).apply {
                setImageResource(R.drawable.ic_profile)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#e2e8f0"))
                layoutParams = LinearLayout.LayoutParams(36, 36).apply { setMargins(0, 0, 16, 0) }
            }
            profileBtn.addView(profileIcon)

            val profileBtnText = TextView(context).apply {
                text = "PC Layout"
                textSize = 14f
                setTextColor(Color.parseColor("#f8fafc"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            profileBtn.addView(profileBtnText)

            val dropdownArrow = TextView(context).apply {
                text = "▾"
                textSize = 16f
                setTextColor(Color.parseColor("#8c96a5"))
                setPadding(8, 0, 0, 0)
            }
            profileBtn.addView(dropdownArrow)

            actionsLayout.addView(profileBtn)
            card.addView(actionsLayout)
            
            return DeviceViewHolder(card, laptopView, nameText, ipText, statsRow, slotsLabel, profileBtn, profileBtnText)
        }
            
        override fun getItemCount() = if (devices.isEmpty()) 1 else devices.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is DeviceViewHolder) {
                val device = devices[position]
                val now = System.currentTimeMillis()
                val wifiLive = device.hasWifi && device.wifiIp != null && (now - device.lastWifiSeenMs < 3000)
                val usbLive = if (device.isUsbTethered) {
                    device.tetherIp != null && (now - device.lastTetherSeenMs < 3000)
                } else {
                    device.hasUsb
                }

                val showWifi = wifiLive
                val showUsb = usbLive
                val isOnline = showWifi || showUsb

                val usbLabelText = if (device.isUsbTethered) "USB Tether" else "USB Debug"

                val subtitle = when {
                    showWifi && showUsb -> "${device.wifiIp ?: device.ip} • $usbLabelText Ready"
                    showUsb -> if (device.isUsbTethered) "USB Tethering Active" else "USB Debug Active"
                    showWifi -> device.wifiIp ?: device.ip
                    else -> "Offline (Saved PC)"
                }
                holder.ipText.visibility = View.VISIBLE
                holder.ipText.text = subtitle
                
                val isConnected = connectedIp == device.ip
                holder.laptopView.isOnline = isOnline
                holder.laptopView.isConnected = isConnected
                holder.laptopView.batteryLevel = device.battery / 100f
                holder.slotsLabel.text = "Active Players: ${device.slotsText}"
                
                holder.card.isOnline = isOnline
                holder.card.isConnected = isConnected
                
                // Update Preferred Profile text & PC-like popup trigger
                val currentPrefIndex = getPreferredProfileForDevice(device)
                holder.profileBtnText.text = getProfileNameForIndex(currentPrefIndex)
                holder.profileBtn.setOnClickListener {
                    showDeviceProfileDropdown(holder.profileBtn, device, holder.profileBtnText)
                }

                // Update Wi-Fi capsule
                val wifiBadge = holder.connectionIcons.getChildAt(0) as? LinearLayout
                if (wifiBadge != null) {
                    val shape = wifiBadge.background as GradientDrawable
                    val icon = wifiBadge.getChildAt(0) as? android.widget.ImageView
                    val label = wifiBadge.getChildAt(1) as? TextView
                    if (showWifi) {
                        wifiBadge.visibility = View.VISIBLE
                        shape.setColor(Color.parseColor("#152233"))
                        shape.setStroke(2, Color.parseColor("#38bdf8"))
                        icon?.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#38bdf8"))
                        label?.setTextColor(Color.parseColor("#38bdf8"))
                    } else {
                        shape.setColor(Color.parseColor("#11141c"))
                        shape.setStroke(2, Color.parseColor("#1e2433"))
                        icon?.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#555f73"))
                        label?.setTextColor(Color.parseColor("#555f73"))
                    }
                }

                // Update USB capsule
                val usbBadge = holder.connectionIcons.getChildAt(1) as? LinearLayout
                if (usbBadge != null) {
                    val shape = usbBadge.background as GradientDrawable
                    val icon = usbBadge.getChildAt(0) as? android.widget.ImageView
                    val label = usbBadge.getChildAt(1) as? TextView
                    label?.text = usbLabelText
                    if (showUsb) {
                        usbBadge.visibility = View.VISIBLE
                        shape.setColor(Color.parseColor("#142622"))
                        shape.setStroke(2, Color.parseColor("#50fa7b"))
                        icon?.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#50fa7b"))
                        label?.setTextColor(Color.parseColor("#50fa7b"))
                    } else {
                        shape.setColor(Color.parseColor("#11141c"))
                        shape.setStroke(2, Color.parseColor("#1e2433"))
                        icon?.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#555f73"))
                        label?.setTextColor(Color.parseColor("#555f73"))
                    }
                }
                
                val isFull = device.slotsText.startsWith("4") || device.slotsText == "4/4"
                
                if (isOnline) {
                    holder.card.alpha = 1.0f
                    if (isFull) {
                        holder.card.setOnClickListener(null)
                    } else {
                        val launchAction = {
                            holder.card.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
                                holder.card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                                onLaunchGamepad(device.ip, getPreferredProfileForDevice(device))
                            }.start()
                        }
                        holder.card.setOnClickListener { launchAction() }
                    }
                } else {
                    holder.card.alpha = 0.55f
                    holder.card.setOnClickListener(null)
                }
            }
        }
    }
}
