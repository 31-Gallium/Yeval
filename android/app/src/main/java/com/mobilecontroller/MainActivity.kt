package com.mobilecontroller

import android.os.Bundle
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.BatteryManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicReference
import androidx.activity.OnBackPressedCallback

import android.content.Intent
import android.provider.MediaStore
import android.net.Uri

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var udpSender: UdpSender? = null
    private var adbSender: AdbTcpSender? = null
    private var reloadSocket: DatagramSocket? = null
    
    private var isGyroAimingEnabled = false
    private var gyroX = 0f
    private var gyroY = 0f
    private var gyroZ = 0f
    
    private var activeHudToast: android.widget.TextView? = null
    private var hudToastRunnable: Runnable? = null
    
    private val currentState = AtomicReference(ControllerState(0, 0, 0, 0, 0, 0, 0, 0))
    private val discoveredDevices = java.util.concurrent.CopyOnWriteArrayList<DiscoveredDevice>()
    private val devicePorts = java.util.concurrent.ConcurrentHashMap<String, Triple<Int, Int, Int>>() // ip -> Triple(udpPort, httpPort, tcpPort)

    data class ControllerState(
        val buttons: Short, val lt: Byte, val rt: Byte,
        val lx: Short, val ly: Short, val rx: Short, val ry: Short,
        val flags: Int = 0
    )

    private lateinit var rootContainer: android.widget.FrameLayout
    private var gridBackgroundView: GridBackgroundView? = null
    private var activeOverlay: android.view.View? = null
    private lateinit var navPill: android.view.View
    private lateinit var homeView: HomeView
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    
    private var deviceId: Int = 0
    private var activePcIp: String? = null

    private var pillTouchDownX = 0f
    private var pillTouchDownY = 0f
    private var isPillTracking = false

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        gridBackgroundView?.handleGlobalTouch(ev)

        if (::navPill.isInitialized && navPill.visibility == android.view.View.VISIBLE && navPill.width > 0) {
            val location = IntArray(2)
            navPill.getLocationOnScreen(location)
            val hitPadding = (24 * resources.displayMetrics.density).toInt()
            val pillRect = android.graphics.Rect(
                location[0] - hitPadding,
                location[1] - hitPadding,
                location[0] + navPill.width + hitPadding,
                location[1] + navPill.height + hitPadding
            )

            val rawX = ev.rawX
            val rawY = ev.rawY

            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    if (pillRect.contains(rawX.toInt(), rawY.toInt())) {
                        pillTouchDownX = rawX
                        pillTouchDownY = rawY
                        isPillTracking = true
                    } else {
                        isPillTracking = false
                    }
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (isPillTracking) {
                        val dx = rawX - pillTouchDownX
                        val dy = rawY - pillTouchDownY
                        val slop = 18 * resources.displayMetrics.density
                        if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                            if (dx < -slop && viewPager.currentItem < 2) {
                                viewPager.currentItem = viewPager.currentItem + 1
                                isPillTracking = false
                                ev.action = android.view.MotionEvent.ACTION_CANCEL
                            } else if (dx > slop && viewPager.currentItem > 0) {
                                viewPager.currentItem = viewPager.currentItem - 1
                                isPillTracking = false
                                ev.action = android.view.MotionEvent.ACTION_CANCEL
                            }
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    isPillTracking = false
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPrefs = getSharedPreferences("YevalPrefs", Context.MODE_PRIVATE)
        deviceId = sharedPrefs.getInt("deviceId", 0)
        if (deviceId == 0) {
            deviceId = java.util.UUID.randomUUID().hashCode()
            sharedPrefs.edit().putInt("deviceId", deviceId).apply()
        }

        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        VibrationManager.init(this)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyroSensor != null) {
            sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        
        isTransmitting = false
        udpSender?.close()
        udpSender = null

        gridBackgroundView = GridBackgroundView(this@MainActivity)

        rootContainer = android.widget.FrameLayout(this).apply {
            val bgGradient = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#12141c"), Color.parseColor("#090a0f"))
            ).apply {
                setDither(true)
            }
            background = bgGradient
            
            // Cyber Grid Background matching PC App with global interactive touch
            addView(gridBackgroundView, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        
        viewPager = androidx.viewpager2.widget.ViewPager2(this).apply {
            isUserInputEnabled = false // Bottom nav pill handles tab switching; ensures inner HomeView cards have 100% swipe control
        }
        
        homeView = HomeView(this, onLaunchGamepad = { ip, slot -> launchGamepad(ip, slot) })
        val profilesView = ProfilesView(this, onLaunchEditor = { slot -> launchEditor(slot) })
        val settingsView = SettingsView(this)
        
        val pages = listOf(homeView, profilesView, settingsView)
        
        viewPager.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val frame = android.widget.FrameLayout(parent.context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(frame) {}
            }
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val frame = holder.itemView as android.widget.FrameLayout
                frame.removeAllViews()
                val page = pages[position]
                if (page.parent != null) {
                    (page.parent as android.view.ViewGroup).removeView(page)
                }
                frame.addView(page)
            }
            override fun getItemCount() = pages.size
        }
        
        rootContainer.addView(viewPager)
        
        // Add Bottom Nav Pill
        navPill = android.widget.FrameLayout(this).apply {
            val shape = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 64f
                setColor(Color.parseColor("#e612141c")) // Frosted Nordic Slate glass
                setStroke(2, Color.parseColor("#242836"))
                setDither(true)
            }
            background = shape
            layoutParams = android.widget.FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.66).toInt(),
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 96) // Elevated higher
            }
            elevation = 16f
        }
        
        val navPillTabs = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 24, 32, 24)
        }
        

        val liquidIndicator = android.view.View(this).apply {
            val indShape = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 4f
                setColor(Color.parseColor("#e2e8f0")) // Pure Frost White indicator
            }
            background = indShape
            elevation = 8f
            layoutParams = android.widget.FrameLayout.LayoutParams(32, 8).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(0, 0, 0, 24)
            }
        }
        

        (navPill as android.widget.FrameLayout).addView(navPillTabs)
        (navPill as android.widget.FrameLayout).addView(liquidIndicator)
        
        fun createNavTab(iconRes: Int): Pair<android.widget.LinearLayout, android.widget.ImageView> {
            val container = android.widget.LinearLayout(this@MainActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setPadding(32, 24, 32, 24)
            }
            val iconView = android.widget.ImageView(this@MainActivity).apply { 
                setImageResource(iconRes)
                layoutParams = android.widget.LinearLayout.LayoutParams(72, 72)
            }
            container.addView(iconView)
            
            return container to iconView
        }
        
        val (homeTab, btnHome) = createNavTab(R.drawable.ic_home)
        val (profilesTab, btnProfiles) = createNavTab(R.drawable.ic_profile)
        val (settingsTab, btnSettings) = createNavTab(R.drawable.ic_setting)
        
        navPillTabs.addView(homeTab)
        navPillTabs.addView(profilesTab)
        navPillTabs.addView(settingsTab)
        
        val updateNavState = { pos: Int ->
            btnHome.imageTintList = android.content.res.ColorStateList.valueOf(if(pos==0) Color.parseColor("#f8fafc") else Color.parseColor("#64748b"))
            btnProfiles.imageTintList = android.content.res.ColorStateList.valueOf(if(pos==1) Color.parseColor("#f8fafc") else Color.parseColor("#64748b"))
            btnSettings.imageTintList = android.content.res.ColorStateList.valueOf(if(pos==2) Color.parseColor("#f8fafc") else Color.parseColor("#64748b"))
            
            val targetTab = when(pos) {
                0 -> homeTab
                1 -> profilesTab
                else -> settingsTab
            }
            
            targetTab.post {
                val targetX = targetTab.left + (targetTab.width / 2f) - (liquidIndicator.width / 2f) + navPillTabs.left
                liquidIndicator.animate()
                    .translationX(targetX)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.OvershootInterpolator())
                    .start()
            }
            
            // Re-fetch profiles when hitting the Profiles tab just in case
            if (pos == 1) profilesView.refreshProfiles()
        }
        
        homeTab.setOnClickListener { viewPager.currentItem = 0 }
        profilesTab.setOnClickListener { viewPager.currentItem = 1 }
        settingsTab.setOnClickListener { viewPager.currentItem = 2 }

        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavState(position)
            }
        })
        
        rootContainer.addView(navPill)
        
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(rootContainer)
        
        homeView.onRefresh = {
            triggerActiveScan(homeView)
        }

        startDiscoveryLoop(homeView)
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeOverlay != null) {
                    dismissOverlay()
                } else {
                    finish()
                }
            }
        })
    }

    private fun dismissOverlay() {
        val overlay = activeOverlay ?: return
        isTransmitting = false
        isTetheredSession = false
        
        val targetPcIp = activePcIp
        val currentDeviceId = deviceId
        val unsignedDevId = (deviceId.toLong() and 0xFFFFFFFFL).toString()
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val disconnectMsg = "YEVAL_DISCONNECT:$unsignedDevId"
                val bytes = disconnectMsg.toByteArray()
                val dSocket = DatagramSocket()
                dSocket.broadcast = true
                dSocket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), 14568))
                dSocket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), 14569))
                if (targetPcIp != null && targetPcIp != "USB") {
                    try {
                        val directAddr = InetAddress.getByName(targetPcIp)
                        dSocket.send(DatagramPacket(bytes, bytes.size, directAddr, 14568))
                        dSocket.send(DatagramPacket(bytes, bytes.size, directAddr, 14569))
                    } catch (e: Exception) {}
                }
                dSocket.close()
            } catch (e: Exception) {}
            
            if (targetPcIp != null && targetPcIp != "USB") {
                try {
                    val httpPort = devicePorts[targetPcIp]?.second ?: 8080
                    val url = java.net.URL("http://$targetPcIp:$httpPort/api/disconnect?deviceId=$unsignedDevId")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 400
                    conn.readTimeout = 400
                    conn.requestMethod = "GET"
                    conn.responseCode
                    conn.disconnect()
                } catch (e: Exception) {}
            }
        }

        udpSender?.sendDisconnect()
        adbSender?.sendDisconnect()
        udpSender?.close()
        udpSender = null
        adbSender?.close()
        adbSender = null
        try { reloadSocket?.close() } catch (e: Exception) {}
        reloadSocket = null
        activePcIp = null
        
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        overlay.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(300)
            .setInterpolator(android.view.animation.AccelerateInterpolator(1.5f))
            .withEndAction {
                rootContainer.removeView(overlay)
                activeOverlay = null
                homeView.disconnect()
                navPill.visibility = android.view.View.VISIBLE
                viewPager.visibility = android.view.View.VISIBLE
                viewPager.alpha = 0f
                viewPager.animate().alpha(1f).setDuration(150).start()
                triggerActiveScan(homeView)
            }.start()
    }

    override fun onResume() {
        super.onResume()
        if (::homeView.isInitialized) {
            triggerActiveScan(homeView)
        }
    }

    private fun showOverlay(view: android.view.View) {
        activeOverlay = view
        navPill.visibility = android.view.View.GONE
        viewPager.visibility = android.view.View.INVISIBLE
        rootContainer.addView(view)
        
        view.alpha = 0f
        view.scaleX = 0.95f
        view.scaleY = 0.95f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .start()
    }

    private fun getNetworkForIp(ip: String): android.net.Network? {
        if (ip.isEmpty() || ip == "127.0.0.1" || ip == "USB") return null
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return null
        val targetBytes = try { java.net.InetAddress.getByName(ip).address } catch (e: Exception) { return null }
        
        for (network in cm.allNetworks) {
            val linkProps = cm.getLinkProperties(network) ?: continue
            for (linkAddr in linkProps.linkAddresses) {
                val addr = linkAddr.address
                if (addr is java.net.Inet4Address) {
                    val b = addr.address
                    if (b[0] == targetBytes[0] && b[1] == targetBytes[1] && b[2] == targetBytes[2]) {
                        return network
                    }
                }
            }
        }
        return null
    }

    private fun launchGamepad(pcIp: String, preferredSlotIndex: Int = -1) {
        activePcIp = pcIp
        GlobalScope.launch(Dispatchers.IO) {
            var profile: ControllerProfile? = null
            try {
                val dev = discoveredDevices.find { it.ip == pcIp || it.wifiIp == pcIp || it.tetherIp == pcIp || it.serverId == pcIp }
                val now = System.currentTimeMillis()
                val isAdb = (pcIp == "USB") || (adbSender?.isConnected == true && adbSender?.targetIp == "127.0.0.1")
                val isTether = dev != null && dev.tetherIp != null && (now - dev.lastTetherSeenMs < 4000L)
                val targetIp = if (isAdb) "127.0.0.1" else if (isTether) dev!!.tetherIp!! else (dev?.wifiIp ?: if (pcIp != "USB") pcIp else "127.0.0.1")
                val httpPort = if (isAdb) 8080 else (devicePorts[targetIp]?.second ?: 8080)
                val boundNet = if (!isAdb) getNetworkForIp(targetIp) else null
                val clientQuery = if (isAdb) "USB" else targetIp
                val unsignedDeviceId = (deviceId.toLong() and 0xFFFFFFFFL).toString()

                var slotIdToFetch = "default-xbox"
                try {
                    val builder = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(800, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .readTimeout(800, java.util.concurrent.TimeUnit.MILLISECONDS)
                    try {
                        boundNet?.socketFactory?.let { sf -> builder.socketFactory(sf) }
                    } catch (e: Exception) {}
                    val client = builder.build()
                    val req = okhttp3.Request.Builder()
                        .url("http://$targetIp:$httpPort/api/request-reload?client=$clientQuery&deviceId=$unsignedDeviceId&t=${System.currentTimeMillis()}")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val jsonStr = resp.body?.string()
                            if (jsonStr != null && jsonStr.contains("\"slotId\"")) {
                                val sId = org.json.JSONObject(jsonStr).optString("slotId")
                                if (sId.isNotEmpty()) slotIdToFetch = sId
                            }
                        }
                    }
                } catch (e: Exception) {}

                profile = ProfileManager.fetchProfile(targetIp, profileId = slotIdToFetch, port = httpPort, boundNetwork = boundNet)
                if (profile != null) {
                    latestPcProfile = profile
                }
            } catch (e: Exception) {}
            
            if (profile == null) profile = ProfileManager.loadDefaultProfile()
            if (latestPcProfile == null) latestPcProfile = profile
            
            withContext(Dispatchers.Main) {
                VibrationManager.resetToDefaults(this@MainActivity)
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                val controllerView = ControllerView(this@MainActivity)
                
                val localProfiles = ProfileStorage.getLocalProfiles(this@MainActivity)
                var activeProfile = latestPcProfile ?: profile!!
                if (preferredSlotIndex in localProfiles.indices) {
                    isPcModeActive = false
                    activeProfile = localProfiles[preferredSlotIndex]
                } else {
                    isPcModeActive = true
                    activeProfile = latestPcProfile ?: profile!!
                }
                
                controllerView.setProfile(activeProfile)
                
                val container = android.widget.FrameLayout(this@MainActivity)
                container.setBackgroundColor(Color.parseColor("#0a0a0f"))
                
                container.addView(controllerView)
                
                controllerView.isServerFull = false
                controllerView.onMenuClicked = {
                    showProfileDialog(pcIp, controllerView, container)
                }
                controllerView.onExitClicked = {
                    dismissOverlay()
                }

                showOverlay(container)

                val dev = discoveredDevices.find { it.ip == pcIp || it.wifiIp == pcIp || it.tetherIp == pcIp || it.serverId == pcIp }
                val now = System.currentTimeMillis()
                val isWifiLive = dev != null && dev.wifiIp != null && (now - dev.lastWifiSeenMs < 4000L)
                val isTetherLive = dev != null && dev.tetherIp != null && (now - dev.lastTetherSeenMs < 4000L)
                val isAdbLive = (now - lastUsbSeenMs < 4000L) || pcIp == "USB"

                isTetheredSession = isTetherLive && !isWifiLive

                val activeWifiIp = if (isWifiLive) dev?.wifiIp else if (pcIp != "USB" && !isTetherLive) pcIp else null
                val activeTetherIp = if (isTetherLive) dev?.tetherIp else null
                val primaryUdpIp = activeWifiIp ?: activeTetherIp ?: if (pcIp != "USB") pcIp else null

                try {
                    if (primaryUdpIp != null) {
                        val ports = devicePorts[primaryUdpIp] ?: (activeWifiIp?.let { devicePorts[it] }) ?: (activeTetherIp?.let { devicePorts[it] }) ?: (dev?.let { devicePorts[it.ip] })
                        val udpPort = ports?.first ?: 14567
                        var localBindIp: String? = null
                        try {
                            val pcBytes = java.net.InetAddress.getByName(primaryUdpIp).address
                            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                            while (interfaces.hasMoreElements()) {
                                val iface = interfaces.nextElement()
                                if (iface.isLoopback || !iface.isUp) continue
                                for (ifAddr in iface.interfaceAddresses) {
                                    val localAddr = ifAddr.address
                                    if (localAddr is java.net.Inet4Address) {
                                        val localBytes = localAddr.address
                                        if (localBytes[0] == pcBytes[0] && localBytes[1] == pcBytes[1] && localBytes[2] == pcBytes[2]) {
                                            localBindIp = localAddr.hostAddress
                                            break
                                        }
                                    }
                                }
                                if (localBindIp != null) break
                            }
                        } catch (e: Exception) {}
                        
                        val boundNet = getNetworkForIp(primaryUdpIp ?: "")
                        udpSender = UdpSender(primaryUdpIp, udpPort, deviceId, localBindIp, boundNet) { getNetworkForIp(primaryUdpIp) }
                    } else {
                        udpSender = null
                    }
                } catch (e: Exception) {}

                val reloadCallback: (String) -> Unit = { slotId ->
                    val isAdb = (pcIp == "USB") || (adbSender?.isConnected == true && adbSender?.targetIp == "127.0.0.1")
                    val isTether = dev != null && dev.tetherIp != null && (System.currentTimeMillis() - dev.lastTetherSeenMs < 4000L)
                    val targetIp = if (isAdb) "127.0.0.1" else if (isTether) dev!!.tetherIp!! else (dev?.wifiIp ?: primaryUdpIp ?: if (pcIp != "USB") pcIp else "127.0.0.1")
                    val syncPort = if (isAdb) 8080 else (devicePorts[targetIp]?.second ?: 8080)
                    val boundNet = if (!isAdb) getNetworkForIp(targetIp) else null

                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val newProfile = ProfileManager.fetchProfile(targetIp, slotId, syncPort, boundNet)
                            if (newProfile != null) {
                                latestPcProfile = newProfile
                                val currentId = controllerView.getProfile()?.id
                                val isLocalSlot = currentId?.startsWith("slot") == true
                                val isCurrentlyInPcMode = isPcModeActive && !isLocalSlot
                                if (isCurrentlyInPcMode) {
                                    withContext(Dispatchers.Main) {
                                        controllerView.setProfile(newProfile)
                                        android.widget.Toast.makeText(this@MainActivity, "Profile synced!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }

                if (isTetherLive && activeTetherIp != null) {
                    val tcpTargetPort = devicePorts[activeTetherIp]?.third ?: 51230
                    val tetherNet = getNetworkForIp(activeTetherIp)
                    adbSender = AdbTcpSender(activeTetherIp, tcpTargetPort, deviceId, reloadCallback, tetherNet)
                    adbSender?.connect()
                } else if (isAdbLive || dev?.hasUsb == true || pcIp == "USB") {
                    adbSender = AdbTcpSender("127.0.0.1", 14569, deviceId, reloadCallback)
                    adbSender?.connect()
                } else {
                    adbSender = null
                }

                controllerView.onStateChanged = { buttons, lt, rt, lx, ly, rx, ry, flags ->
                    currentState.set(ControllerState(buttons, lt, rt, lx, ly, rx, ry, flags))
                }

                startTransmissionLoop(pcIp)
                startReloadListener(pcIp, controllerView)
                
                // Connection health monitor
                GlobalScope.launch(Dispatchers.Main) {
                    while (isTransmitting && activeOverlay != null) {
                        val prefs = getSharedPreferences("ybox_prefs", Context.MODE_PRIVATE)
                        val enableUsb = prefs.getBoolean("enable_usb", true)

                        // Discovery updates lastWifiDiscoveryMs for BOTH Wi-Fi and USB tethering
                        val now = System.currentTimeMillis()
                        val networkHealthy = (now - lastWifiDiscoveryMs) < 4500L
                        val usbHealthy = enableUsb && (adbSender?.isConnected == true || (now - lastUsbSeenMs < 4500L))
                        
                        val wasConnected = controllerView.isConnected
                        val isNowConnected = networkHealthy || usbHealthy
                        controllerView.isConnected = isNowConnected
                        if (wasConnected != isNowConnected) {
                            controllerView.invalidate()
                        }
                        kotlinx.coroutines.delay(400)
                    }
                }
            }
        }
    }

    private fun launchEditor(slotIndex: Int) {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controllerView = ControllerView(this@MainActivity)
        controllerView.isEditMode = true
        
        val localProfiles = ProfileStorage.getLocalProfiles(this)
        controllerView.setProfile(localProfiles[slotIndex])
        
        val editorOverlay = EditorOverlayView(
            context = this,
            controllerView = controllerView,
            activeSlot = slotIndex,
            onSave = { profile, slot -> 
                ProfileStorage.saveLocalProfile(this, profile, slot)
                dismissOverlay()
            },
            onCancel = {
                dismissOverlay()
            }
        )

        showOverlay(editorOverlay)
    }

    data class DiscoveredDevice(
        val serverId: String,
        val ip: String,
        val wifiIp: String? = null,
        val tetherIp: String? = null,
        val name: String,
        val battery: Int,
        val slotsText: String,
        val hasWifi: Boolean = true,
        val hasUsb: Boolean = false,
        val isUsbTethered: Boolean = false,
        val lastSeen: Long = System.currentTimeMillis(),
        val lastWifiSeenMs: Long = 0L,
        val lastTetherSeenMs: Long = 0L
    )

    private var isTransmitting = false
    @Volatile private var isTetheredSession = false
    @Volatile private var lastWifiDiscoveryMs: Long = 0L
    @Volatile private var lastUsbSeenMs: Long = 0L
    @Volatile private var cachedUsbHostname: String = ""
    @Volatile private var cachedUsbServerId: String = ""
    @Volatile private var cachedUsbBattery: Int = 100
    @Volatile private var cachedUsbSlots: String = "0/4"
    @Volatile private var latestPcProfile: ControllerProfile? = null
    @Volatile private var isPcModeActive: Boolean = true

    private fun saveCachedDevice(dev: DiscoveredDevice) {
        if (dev.serverId == "usb_local") return
        try {
            val prefs = getSharedPreferences("ybox_cached_devices", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("devices_json", "[]") ?: "[]"
            val arr = org.json.JSONArray(jsonStr)
            val newArr = org.json.JSONArray()
            
            val obj = org.json.JSONObject().apply {
                put("serverId", dev.serverId)
                put("ip", dev.ip)
                put("wifiIp", dev.wifiIp ?: "")
                put("tetherIp", dev.tetherIp ?: "")
                put("name", dev.name)
                put("battery", dev.battery)
                put("slotsText", dev.slotsText)
                put("hasWifi", dev.hasWifi)
                put("hasUsb", dev.hasUsb)
                put("isUsbTethered", dev.isUsbTethered)
            }
            newArr.put(obj)
            
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (item.optString("serverId") != dev.serverId && item.optString("ip") != dev.ip) {
                    newArr.put(item)
                }
            }
            prefs.edit().putString("devices_json", newArr.toString()).apply()
        } catch (e: Exception) {}
    }

    private fun loadCachedDevices(): List<DiscoveredDevice> {
        val list = mutableListOf<DiscoveredDevice>()
        try {
            val prefs = getSharedPreferences("ybox_cached_devices", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("devices_json", "[]") ?: "[]"
            val arr = org.json.JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val sId = item.optString("serverId", item.optString("ip"))
                val wIp = item.optString("wifiIp").takeIf { it.isNotEmpty() }
                val tIp = item.optString("tetherIp").takeIf { it.isNotEmpty() }
                list.add(DiscoveredDevice(
                    serverId = sId,
                    ip = item.getString("ip"),
                    wifiIp = wIp,
                    tetherIp = tIp,
                    name = item.optString("name", "Saved PC"),
                    battery = item.optInt("battery", 100),
                    slotsText = item.optString("slotsText", "0/4"),
                    hasWifi = false,
                    hasUsb = false,
                    isUsbTethered = false,
                    lastSeen = 0L,
                    lastWifiSeenMs = 0L,
                    lastTetherSeenMs = 0L
                ))
            }
        } catch (e: Exception) {}
        return list
    }

    private suspend fun handleServerResponse(senderIp: String, response: String, senderAddr: InetAddress, homeView: HomeView) {
        val parts = response.split(":")
        var hostname = "Unknown PC"
        var battery = 100
        var slotsText = "0/4"
        if (parts.size >= 2) hostname = parts[1]
        if (parts.size >= 3) battery = parts[2].toIntOrNull() ?: 100
        if (parts.size >= 4) slotsText = parts[3]
        val pUdp = if (parts.size >= 5) parts[4].toIntOrNull() ?: 14567 else 14567
        val pHttp = if (parts.size >= 6) parts[5].toIntOrNull() ?: 8080 else 8080
        val pTcp = if (parts.size >= 7) parts[6].toIntOrNull() ?: 51230 else 51230
        val serverId = if (parts.size >= 8) parts[7].trim() else hostname

        devicePorts[senderIp] = Triple(pUdp, pHttp, pTcp)
        val now = System.currentTimeMillis()

        var isUsbTether = false
        try {
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
            val senderBytes = senderAddr.address
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                for (addr in iface.interfaceAddresses) {
                    val local = addr.address
                    if (local is java.net.Inet4Address) {
                        val localBytes = local.address
                        if (localBytes[0] == senderBytes[0] && localBytes[1] == senderBytes[1] && localBytes[2] == senderBytes[2]) {
                            val ifName = iface.name.lowercase()
                            if (ifName.contains("rndis") || ifName.contains("usb") || ifName.contains("ncm") || (ifName.contains("eth") && !ifName.contains("wlan"))) {
                                isUsbTether = true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}

        val prefs = getSharedPreferences("ybox_prefs", Context.MODE_PRIVATE)
        val route = prefs.getString("connection_route", "auto") ?: "auto"
        val enableWifi = (route == "auto" || route == "wifi_only")
        val enableUsb = (route == "auto" || route == "usb_only")

        if (!isUsbTether && !enableWifi) {
            return // Silence Wi-Fi beacons if Wi-Fi route is disabled
        }
        if (isUsbTether && !enableUsb) {
            return // Silence USB tether beacons if USB route is disabled
        }

        if (!isUsbTether) {
            lastWifiDiscoveryMs = now
        }

        // Match existing device by persistent serverId (or hostname)
        val existing = discoveredDevices.find { it.serverId == serverId || it.name == hostname }
        val finalWifiIp = if (!isUsbTether) senderIp else existing?.wifiIp
        val finalTetherIp = if (isUsbTether) senderIp else existing?.tetherIp
        val finalLastWifiSeen = if (!isUsbTether) now else (existing?.lastWifiSeenMs ?: 0L)
        val finalLastTetherSeen = if (isUsbTether) now else (existing?.lastTetherSeenMs ?: 0L)
        
        val finalHasWifi = (now - finalLastWifiSeen) < 3000L
        val finalIsTether = (now - finalLastTetherSeen) < 3000L
        val finalHasUsb = finalIsTether || (existing?.hasUsb == true && !existing.isUsbTethered)

        val dev = DiscoveredDevice(
            serverId = serverId,
            ip = if (finalHasWifi && finalWifiIp != null) finalWifiIp else (finalTetherIp ?: senderIp),
            wifiIp = finalWifiIp,
            tetherIp = finalTetherIp,
            name = hostname,
            battery = battery,
            slotsText = slotsText,
            hasWifi = finalHasWifi,
            hasUsb = finalHasUsb,
            isUsbTethered = finalIsTether,
            lastSeen = now,
            lastWifiSeenMs = finalLastWifiSeen,
            lastTetherSeenMs = finalLastTetherSeen
        )

        if (existing == null) {
            // Remove any placeholder USB device if matched
            discoveredDevices.removeAll { it.serverId == "usb_local" && it.name.startsWith(hostname) }
            discoveredDevices.add(dev)
        } else {
            val index = discoveredDevices.indexOf(existing)
            discoveredDevices[index] = dev
        }

        saveCachedDevice(dev)

        withContext(Dispatchers.Main) {
            homeView.updateDevices(discoveredDevices.toList())
        }
    }

    private fun startDiscoveryLoop(homeView: HomeView) {
        // 1. Immediately load and render cached devices for 0ms initial load
        val cached = loadCachedDevices()
        for (c in cached) {
            if (discoveredDevices.none { it.ip == c.ip }) {
                discoveredDevices.add(c)
            }
        }
        if (cached.isNotEmpty()) {
            GlobalScope.launch(Dispatchers.Main) {
                homeView.updateDevices(discoveredDevices.toList())
            }
        }

        // 2. Always-on passive UDP beacon listener (listens for PC heartbeats on 14568)
        GlobalScope.launch(Dispatchers.IO) {
            var listenSocket: DatagramSocket? = null
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)

            while (true) {
                if (listenSocket == null || listenSocket?.isClosed == true) {
                    try {
                        listenSocket = DatagramSocket(null).apply {
                            reuseAddress = true
                            broadcast = true
                            bind(InetSocketAddress("0.0.0.0", 14568))
                        }
                    } catch (e: Exception) {
                        try { listenSocket = DatagramSocket(14568) } catch (e2: Exception) {}
                    }
                }

                try {
                    listenSocket?.receive(packet)
                    val response = String(packet.data, 0, packet.length)
                    if (response.startsWith("YEVAL_SERVER")) {
                        val senderIp = packet.address.hostAddress ?: continue
                        handleServerResponse(senderIp, response, packet.address, homeView)
                    } else if (response.startsWith("YEVAL_SHUTDOWN")) {
                        val shutdownServerId = response.substringAfter("YEVAL_SHUTDOWN:").trim()
                        val matching = discoveredDevices.find { it.serverId == shutdownServerId }
                        if (matching != null) {
                            val idx = discoveredDevices.indexOf(matching)
                            discoveredDevices[idx] = matching.copy(hasWifi = false, hasUsb = false, lastSeen = 0L)
                            withContext(Dispatchers.Main) {
                                homeView.updateDevices(discoveredDevices.toList())
                            }
                        }
                        lastWifiDiscoveryMs = 0L
                        lastUsbSeenMs = 0L
                    }
                } catch (e: Exception) {
                    try { listenSocket?.close() } catch (e2: Exception) {}
                    listenSocket = null
                    kotlinx.coroutines.delay(800)
                }
            }
        }

        // 3. Fast Targeted Ping Loop (Sends 1 ping to cached hosts + 1 broadcast)
        GlobalScope.launch(Dispatchers.IO) {
            var sendSocket: DatagramSocket? = null

            while (true) {
                if (sendSocket == null || sendSocket?.isClosed == true) {
                    try {
                        sendSocket = DatagramSocket().apply { broadcast = true; reuseAddress = true }
                    } catch (e: Exception) {}
                }
                val prefs = getSharedPreferences("ybox_prefs", Context.MODE_PRIVATE)
                val route = prefs.getString("connection_route", "auto") ?: "auto"
                val enableWifi = (route == "auto" || route == "wifi_only")
                val enableUsb = (route == "auto" || route == "usb_only")

                // Quick ADB probe via HTTP discovery (only when not actively transmitting)
                if (enableUsb) {
                    if (isTransmitting && adbSender?.isConnected == true) {
                        lastUsbSeenMs = System.currentTimeMillis()
                    } else {
                        try {
                            val url = java.net.URL("http://127.0.0.1:8080/discovery")
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 300
                            conn.readTimeout = 300
                            val response = conn.inputStream.bufferedReader().readText()
                            val json = org.json.JSONObject(response)
                            cachedUsbHostname = json.optString("hostname", "")
                            cachedUsbServerId = json.optString("serverId", "")
                            cachedUsbBattery = json.optInt("battery", 100)
                            cachedUsbSlots = json.optString("slotsText", "0/4")
                            lastUsbSeenMs = System.currentTimeMillis()
                        } catch (e: Exception) {}
                    }
                }

                val usbAvailable = enableUsb && (System.currentTimeMillis() - lastUsbSeenMs < 3000L)
                val usbHostname = cachedUsbHostname
                val usbServerId = cachedUsbServerId
                val usbBattery = cachedUsbBattery
                val usbSlots = cachedUsbSlots

                // Send fast lightweight active pings
                try {
                    val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val currentBattery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val requestData = "YEVAL_DISCOVER:$currentBattery".toByteArray()

                    if (sendSocket != null) {
                        if (enableWifi) {
                            // A) Ping standard broadcast
                            try {
                                val bcastPacket = DatagramPacket(requestData, requestData.size, InetAddress.getByName("255.255.255.255"), 14568)
                                sendSocket.send(bcastPacket)
                            } catch (e: Exception) {}
                        }

                        // B) Ping subnet broadcasts of eligible network interfaces
                        val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
                        while (ifaces.hasMoreElements()) {
                            val iface = ifaces.nextElement()
                            if (iface.isLoopback || !iface.isUp) continue
                            val ifName = iface.name.lowercase()
                            val isTetherIface = ifName.contains("rndis") || ifName.contains("usb") || ifName.contains("ncm") || (ifName.contains("eth") && !ifName.contains("wlan"))
                            
                            // Check eligibility per connection route
                            if (isTetherIface && !enableUsb) continue
                            if (!isTetherIface && !enableWifi) continue

                            for (addr in iface.interfaceAddresses) {
                                val bcast = addr.broadcast
                                if (bcast != null) {
                                    try {
                                        sendSocket.send(DatagramPacket(requestData, requestData.size, bcast, 14568))
                                    } catch (e: Exception) {}
                                }
                            }
                        }

                        // C) Ping discovered device IPs if route is active
                        if (enableWifi) {
                            for (dev in discoveredDevices) {
                                if (dev.ip != "USB" && dev.wifiIp != null) {
                                    try {
                                        val targetAddr = InetAddress.getByName(dev.wifiIp)
                                        sendSocket.send(DatagramPacket(requestData, requestData.size, targetAddr, 14568))
                                    } catch (e: Exception) {}
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}

                // Update timeouts and fallback USB entry
                var changed = false
                val now = System.currentTimeMillis()

                val iterator = discoveredDevices.iterator()
                while (iterator.hasNext()) {
                    val dev = iterator.next()
                    if (dev.ip != "USB") {
                        if (dev.lastSeen > 0L && now - dev.lastSeen > 2500) {
                            if (dev.hasWifi || dev.isUsbTethered) {
                                val index = discoveredDevices.indexOf(dev)
                                if (index != -1) {
                                    discoveredDevices[index] = dev.copy(hasWifi = false, isUsbTethered = false, lastSeen = 0L)
                                    changed = true
                                }
                            }
                        }
                    } else {
                        if (!usbAvailable) {
                            discoveredDevices.remove(dev)
                            changed = true
                        }
                    }
                }

                if (usbAvailable) {
                    val matchingWifi = discoveredDevices.find {
                        (usbServerId.isNotEmpty() && it.serverId == usbServerId) ||
                        (usbHostname.isNotEmpty() && (it.name == usbHostname || it.name.startsWith(usbHostname)))
                    } ?: if (discoveredDevices.size == 1 && discoveredDevices[0].ip != "USB") discoveredDevices[0] else null

                    if (matchingWifi != null) {
                        val idx = discoveredDevices.indexOf(matchingWifi)
                        discoveredDevices[idx] = matchingWifi.copy(
                            hasUsb = true,
                            battery = usbBattery,
                            slotsText = usbSlots,
                            name = if (usbHostname.isNotEmpty()) usbHostname else matchingWifi.name,
                            serverId = if (usbServerId.isNotEmpty()) usbServerId else matchingWifi.serverId
                        )
                        val rem = discoveredDevices.removeAll { it.serverId == "usb_local" || (it.ip == "USB" && it != matchingWifi) }
                        if (rem || !matchingWifi.hasUsb) changed = true
                    } else {
                        val existingUsb = discoveredDevices.find { it.serverId == "usb_local" || it.ip == "USB" }
                        val usbName = if (usbHostname.isNotEmpty()) "$usbHostname (USB)" else "USB PC"
                        val usbDev = DiscoveredDevice(
                            serverId = if (usbServerId.isNotEmpty()) usbServerId else "usb_local",
                            ip = "USB",
                            wifiIp = null,
                            name = usbName,
                            battery = usbBattery,
                            slotsText = usbSlots,
                            hasWifi = false,
                            hasUsb = true,
                            isUsbTethered = false,
                            lastSeen = System.currentTimeMillis()
                        )
                        if (existingUsb == null) {
                            discoveredDevices.add(0, usbDev)
                            changed = true
                        } else if (existingUsb.name != usbName || existingUsb.battery != usbBattery || existingUsb.slotsText != usbSlots) {
                            val idx = discoveredDevices.indexOf(existingUsb)
                            discoveredDevices[idx] = usbDev
                            changed = true
                        }
                    }
                } else {
                    for (i in 0 until discoveredDevices.size) {
                        val d = discoveredDevices[i]
                        if (d.hasUsb && !d.isUsbTethered) {
                            discoveredDevices[i] = d.copy(hasUsb = false)
                            changed = true
                        }
                    }
                    val removed = discoveredDevices.removeAll { it.serverId == "usb_local" || it.ip == "USB" }
                    if (removed) changed = true
                }

                if (changed) {
                    withContext(Dispatchers.Main) {
                        homeView.updateDevices(discoveredDevices.toList())
                    }
                }

                kotlinx.coroutines.delay(800L)
            }
        }
    }

    private fun triggerActiveScan(homeView: HomeView) {
        GlobalScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                homeView.setRefreshing(true)
            }

            val sendSocket = try {
                DatagramSocket().apply { broadcast = true }
            } catch (e: Exception) { null }

            try {
                val prefs = getSharedPreferences("ybox_prefs", Context.MODE_PRIVATE)
                val route = prefs.getString("connection_route", "auto") ?: "auto"
                val enableWifi = (route == "auto" || route == "wifi_only")
                val enableUsb = (route == "auto" || route == "usb_only")

                val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val currentBattery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val requestData = "YEVAL_DISCOVER:$currentBattery".toByteArray()

                if (sendSocket != null) {
                    if (enableWifi) {
                        try {
                            sendSocket.send(DatagramPacket(requestData, requestData.size, InetAddress.getByName("255.255.255.255"), 14568))
                        } catch (e: Exception) {}
                    }

                    val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
                    while (ifaces.hasMoreElements()) {
                        val iface = ifaces.nextElement()
                        if (iface.isLoopback || !iface.isUp) continue
                        val ifName = iface.name.lowercase()
                        val isTetherIface = ifName.contains("rndis") || ifName.contains("usb") || ifName.contains("ncm") || (ifName.contains("eth") && !ifName.contains("wlan"))
                        
                        if (isTetherIface && !enableUsb) continue
                        if (!isTetherIface && !enableWifi) continue

                        for (addr in iface.interfaceAddresses) {
                            val bcast = addr.broadcast
                            if (bcast != null) {
                                try {
                                    sendSocket.send(DatagramPacket(requestData, requestData.size, bcast, 14568))
                                } catch (e: Exception) {}
                            }
                        }
                    }

                    if (enableWifi) {
                        for (dev in discoveredDevices) {
                            if (dev.ip != "USB" && dev.wifiIp != null) {
                                try {
                                    sendSocket.send(DatagramPacket(requestData, requestData.size, InetAddress.getByName(dev.wifiIp), 14568))
                                } catch (e: Exception) {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {}

            kotlinx.coroutines.delay(1000L)

            try { sendSocket?.close() } catch (e: Exception) {}

            withContext(Dispatchers.Main) {
                homeView.updateDevices(discoveredDevices.toList())
                homeView.setRefreshing(false)
            }
        }
    }

    private var isProfileMenuOpen = false

    private fun showProfileDialog(pcIp: String, controllerView: ControllerView, container: android.widget.FrameLayout) {
        if (isProfileMenuOpen) return
        isProfileMenuOpen = true

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var hideRunnable: Runnable? = null

        // Display Cutout (Selfie Camera Notch/Punch-hole) Safe Insets
        val rootInsets = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
        val displayCutout = rootInsets?.displayCutout
        val safeCutoutLeft = displayCutout?.safeInsetLeft ?: 0
        val safeCutoutRight = displayCutout?.safeInsetRight ?: 0
        val safeCutoutTop = displayCutout?.safeInsetTop ?: 0

        // 1. Top Dynamic Island Pill (Profile Slots) with Dynamic Horizontal Scrolling capped to 7 slots
        val maxSlotsVisible = 7
        val slotBtnSize = 92 // width of each slot button
        val slotBtnMargin = 12 // total horizontal margins (6 on left, 6 on right)
        val pillPadding = 32 // total horizontal container padding (16 on left, 16 on right)
        val maxPillWidth = (slotBtnSize + slotBtnMargin) * maxSlotsVisible + pillPadding

        val dynamicIsland = android.widget.FrameLayout(this).apply {
            val shape = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 48f
                setColor(android.graphics.Color.parseColor("#e612141c")) // Frosted Nordic Slate glass
                setStroke(2, android.graphics.Color.parseColor("#242836"))
                setDither(true)
            }
            background = shape
            elevation = 24f
            
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, 
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, maxOf(64, safeCutoutTop + 24), 0, 0)
            }
        }

        val islandScrollView = object : android.widget.HorizontalScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val mode = MeasureSpec.getMode(widthMeasureSpec)
                val size = MeasureSpec.getSize(widthMeasureSpec)
                val constrainedWidthSpec = if (mode == MeasureSpec.UNSPECIFIED || size > maxPillWidth) {
                    MeasureSpec.makeMeasureSpec(maxPillWidth, MeasureSpec.AT_MOST)
                } else {
                    widthMeasureSpec
                }
                super.onMeasure(constrainedWidthSpec, heightMeasureSpec)
            }
        }.apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(28)
        }

        val islandTabs = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        islandScrollView.addView(islandTabs)
        dynamicIsland.addView(islandScrollView)

        // 2. Right Vertical Quick Settings Pill (RS Mode, Curve, Triggers)
        val quickPill = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val shape = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 40f
                setColor(android.graphics.Color.parseColor("#e612141c"))
                setStroke(2, android.graphics.Color.parseColor("#242836"))
                setDither(true)
            }
            background = shape
            setPadding(16, 20, 16, 20)
            elevation = 24f
            isClickable = true
            isFocusable = true
            
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, 
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
                setMargins(0, 0, maxOf(36, safeCutoutRight + 24), 0)
            }
        }

        // 3. Left Vertical Sensitivity Pill (10 Level Slabs for Cursor Mode in Zone layout)
        val sensitivityPill = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val shape = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24f
                setColor(android.graphics.Color.parseColor("#e612141c")) // Frosted Nordic Slate glass
                setStroke(2, android.graphics.Color.parseColor("#242836"))
                setDither(true)
            }
            background = shape
            setPadding(14, 16, 14, 16)
            elevation = 24f
            isClickable = true
            isFocusable = true
            
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, 
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                setMargins(maxOf(36, safeCutoutLeft + 24), 0, 0, 0)
            }
        }

        val sensSlabs = mutableListOf<Pair<Int, android.view.View>>()
        val slotButtons = mutableListOf<Pair<String, TextView>>()

        var rsModeBtn: android.widget.ImageView? = null
        var curveBtn: android.widget.ImageView? = null
        var triggersBtn: android.widget.ImageView? = null

        val cancelHide = {
            hideRunnable?.let { handler.removeCallbacks(it) }
        }

        val scheduleHide = {
            cancelHide()
            val r = Runnable {
                if (dynamicIsland.parent != null) {
                    android.animation.ObjectAnimator.ofFloat(dynamicIsland, "translationY", -300f).setDuration(300).apply {
                        interpolator = android.view.animation.AnticipateInterpolator(1.2f)
                        addListener(object: android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                container.removeView(dynamicIsland)
                            }
                        })
                        start()
                    }
                }
                if (quickPill.parent != null) {
                    android.animation.ObjectAnimator.ofFloat(quickPill, "translationX", 300f).setDuration(300).apply {
                        interpolator = android.view.animation.AnticipateInterpolator(1.2f)
                        addListener(object: android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                container.removeView(quickPill)
                                isProfileMenuOpen = false
                            }
                        })
                        start()
                    }
                }
                if (sensitivityPill.parent != null) {
                    android.animation.ObjectAnimator.ofFloat(sensitivityPill, "translationX", -300f).setDuration(300).apply {
                        interpolator = android.view.animation.AnticipateInterpolator(1.2f)
                        addListener(object: android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                container.removeView(sensitivityPill)
                            }
                        })
                        start()
                    }
                }
            }
            hideRunnable = r
            handler.postDelayed(r, 4500L) // 4.5 seconds auto-hide
        }

        // Prevent Dynamic Island from closing when user is scrolling profile slots
        islandScrollView.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                    cancelHide()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    scheduleHide()
                }
            }
            false
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            islandScrollView.setOnScrollChangeListener { _, _, _, _, _ ->
                scheduleHide() // Resets 4.5s countdown on every scroll delta
            }
        }

        fun showHudToast(msg: String) {
            hudToastRunnable?.let { handler.removeCallbacks(it) }
            activeHudToast?.let { container.removeView(it) }
            
            val toastTv = TextView(this@MainActivity).apply {
                text = msg
                textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#f8fafc"))
                gravity = android.view.Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 32f
                    setColor(android.graphics.Color.parseColor("#e612141c")) // Frosted Nordic Slate glass
                    setStroke(2, android.graphics.Color.parseColor("#242836"))
                }
                background = shape
                setPadding(40, 16, 40, 16)
                elevation = 32f
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    setMargins(0, 0, 0, 96)
                }
            }
            container.addView(toastTv)
            activeHudToast = toastTv
            
            val r = Runnable {
                toastTv.animate().alpha(0f).setDuration(200).withEndAction {
                    container.removeView(toastTv)
                    if (activeHudToast == toastTv) activeHudToast = null
                }.start()
            }
            hudToastRunnable = r
            handler.postDelayed(r, 1500L) // 1.5 seconds
        }

        fun refreshSensitivityHighlights() {
            val currentLvl = VibrationManager.cursorSensitivityLevel
            for ((lvl, slab) in sensSlabs) {
                val isActive = (lvl <= currentLvl)
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 6f
                    if (isActive) {
                        val ratio = (lvl - 1).toFloat() / 9f
                        val r = (0x38 + ((0xf8 - 0x38) * ratio)).toInt()
                        val g = (0xbd + ((0xfa - 0xbd) * ratio)).toInt()
                        val b = (0xf8 + ((0xfc - 0xf8) * ratio)).toInt()
                        setColor(android.graphics.Color.rgb(r, g, b))
                    } else {
                        setColor(android.graphics.Color.parseColor("#1c2230"))
                        setStroke(2, android.graphics.Color.parseColor("#2a3345"))
                    }
                }
                slab.background = shape
                slab.alpha = if (isActive) 1.0f else 0.45f
            }
        }

        fun getRsModeIcon(): Int = when (VibrationManager.rightStickMode) {
            "aim" -> R.drawable.ic_aim
            "cursor" -> R.drawable.ic_cursor
            else -> R.drawable.ic_stick
        }
        fun getRsModeName(): String = when (VibrationManager.rightStickMode) {
            "aim" -> "Aim"
            "cursor" -> "Cursor"
            else -> "Stick"
        }

        fun getCurveIcon(): Int = if (VibrationManager.trackpadCurve == "dynamic") R.drawable.ic_dynamic else R.drawable.ic_linear
        fun getCurveName(): String = if (VibrationManager.trackpadCurve == "dynamic") "Dynamic" else "Linear"

        fun getTriggerIcon(): Int = if (VibrationManager.analogTriggersEnabled) R.drawable.ic_analog_trigger else R.drawable.ic_digital_trigger
        fun getTriggerName(): String = if (VibrationManager.analogTriggersEnabled) "Analog" else "Digital"

        fun updateCurveState() {
            if (curveBtn == null) return
            val isStick = VibrationManager.rightStickMode == "stick"
            curveBtn?.alpha = if (isStick) 0.35f else 1.0f
            curveBtn?.isEnabled = !isStick
            curveBtn?.setImageResource(getCurveIcon())
        }

        fun refreshProfileHighlights() {
            val curActiveId = controllerView.getProfile()?.id
            val isLocal = curActiveId?.startsWith("slot") == true
            val isPc = isPcModeActive || !isLocal

            for ((key, btn) in slotButtons) {
                val selected = if (key == "PC") isPc else (!isPc && curActiveId == key)
                val btnShape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 24f
                    setColor(if (selected) android.graphics.Color.parseColor("#1e293b") else android.graphics.Color.TRANSPARENT)
                    if (selected) setStroke(2, android.graphics.Color.parseColor("#38bdf8"))
                }
                btn.background = btnShape
                btn.setTextColor(if (selected) android.graphics.Color.parseColor("#f8fafc") else android.graphics.Color.parseColor("#94a3b8"))
            }
        }

        fun refreshModePill() {
            val isZone = controllerView.getProfile()?.layoutMode == "zone"
            val isCursor = isZone && (VibrationManager.rightStickMode == "cursor")
            
            rsModeBtn?.visibility = if (isZone) android.view.View.VISIBLE else android.view.View.GONE
            curveBtn?.visibility = if (isZone) android.view.View.VISIBLE else android.view.View.GONE
            triggersBtn?.visibility = android.view.View.VISIBLE

            sensitivityPill.visibility = if (isCursor) android.view.View.VISIBLE else android.view.View.GONE
            refreshSensitivityHighlights()
        }

        // Fill sensitivity slabs (10 visual slabs from top down)
        for (lvl in 10 downTo 1) {
            val slab = android.view.View(this@MainActivity).apply {
                isClickable = false
                isFocusable = false
                layoutParams = android.widget.LinearLayout.LayoutParams(52, 18).apply {
                    setMargins(2, 3, 2, 3)
                }
            }
            sensSlabs.add(lvl to slab)
            sensitivityPill.addView(slab)
        }

        // Allow instant, flawless touch tap and drag / sliding across sensitivity power slabs
        val handleSensTouch = { y: Float ->
            val h = if (sensitivityPill.height > 0) sensitivityPill.height.toFloat() else (10 * 24f * resources.displayMetrics.density + 32f * resources.displayMetrics.density)
            val padY = 16f * resources.displayMetrics.density
            val usableHeight = h - (padY * 2f)
            if (usableHeight > 0) {
                val fraction = ((y - padY) / usableHeight).coerceIn(0f, 0.999f)
                val lvl = (10 - (fraction * 10).toInt()).coerceIn(1, 10)
                if (lvl != VibrationManager.cursorSensitivityLevel) {
                    VibrationManager.setCursorSensitivity(lvl)
                    VibrationManager.vibrateHaptic()
                    refreshSensitivityHighlights()
                    val mult = String.format(java.util.Locale.US, "%.1fx", VibrationManager.trackpadSensitivity)
                    showHudToast("Cursor Sensitivity: Level $lvl ($mult)")
                }
            }
        }

        sensitivityPill.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    cancelHide()
                    handleSensTouch(event.y)
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    cancelHide()
                    handleSensTouch(event.y)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    handleSensTouch(event.y)
                    VibrationManager.savePrefs(this@MainActivity)
                    val lvl = VibrationManager.cursorSensitivityLevel
                    val mult = String.format(java.util.Locale.US, "%.1fx", VibrationManager.trackpadSensitivity)
                    showHudToast("Cursor Sensitivity: Level $lvl ($mult)")
                    scheduleHide()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    scheduleHide()
                    true
                }
                else -> false
            }
        }

        fun addSlotBtn(key: String, label: String, onClick: () -> Unit) {
            val btn = TextView(this@MainActivity).apply {
                text = label
                textSize = 16f
                setTextColor(android.graphics.Color.parseColor("#94a3b8"))
                gravity = android.view.Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                isClickable = true
                isFocusable = true
                layoutParams = android.widget.LinearLayout.LayoutParams(92, 92).apply { setMargins(6, 0, 6, 0) }
                
                setOnClickListener {
                    onClick()
                    scheduleHide()
                }
            }
            slotButtons.add(key to btn)
            islandTabs.addView(btn)
        }

        // Add PC button
        addSlotBtn("PC", "PC") {
            isPcModeActive = true
            if (latestPcProfile != null) {
                controllerView.setProfile(latestPcProfile!!)
            }
            refreshProfileHighlights()
            refreshModePill()

            GlobalScope.launch(Dispatchers.IO) {
                val dev = discoveredDevices.find { it.ip == pcIp || it.wifiIp == pcIp || it.tetherIp == pcIp || it.serverId == pcIp }
                val now = System.currentTimeMillis()
                val isAdb = (pcIp == "USB") || (adbSender?.isConnected == true && adbSender?.targetIp == "127.0.0.1")
                val isTether = dev != null && dev.tetherIp != null && (now - dev.lastTetherSeenMs < 4000L)
                val targetIp = if (isAdb) "127.0.0.1" else if (isTether) dev!!.tetherIp!! else (dev?.wifiIp ?: if (pcIp != "USB") pcIp else "127.0.0.1")
                val httpPort = if (isAdb) 8080 else (devicePorts[targetIp]?.second ?: 8080)
                val boundNet = if (!isAdb) getNetworkForIp(targetIp) else null
                
                val clientQuery = if (isAdb) "USB" else targetIp
                val unsignedDeviceId = (deviceId.toLong() and 0xFFFFFFFFL).toString()
                
                try {
                    val builder = okhttp3.OkHttpClient.Builder()
                    try {
                        boundNet?.socketFactory?.let { sf -> builder.socketFactory(sf) }
                    } catch (e: Exception) {}
                    val client = builder.build()

                    val request = okhttp3.Request.Builder()
                        .url("http://$targetIp:$httpPort/api/request-reload?client=$clientQuery&deviceId=$unsignedDeviceId&t=${System.currentTimeMillis()}")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val json = response.body?.string()
                            if (json != null && json.contains("\"slotId\"")) {
                                val slotId = org.json.JSONObject(json).optString("slotId")
                                if (slotId.isNotEmpty()) {
                                    val newProfile = ProfileManager.fetchProfile(targetIp, slotId, httpPort, boundNet)
                                    if (newProfile != null) {
                                        latestPcProfile = newProfile
                                        withContext(Dispatchers.Main) {
                                            controllerView.setProfile(newProfile)
                                            refreshProfileHighlights()
                                            refreshModePill()
                                            android.widget.Toast.makeText(this@MainActivity, "Profile synced!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        // Add Dynamic Profile Slots (1 for Alpha, 2 for Beta, 3 for Gamma, etc.)
        val localProfiles = ProfileStorage.getLocalProfiles(this@MainActivity)
        localProfiles.forEachIndexed { i, p ->
            val slotKey = "slot${i + 1}"
            val badgeLabel = "${i + 1}" // 1 is Alpha, 2 is Beta, 3 is Gamma, etc.
            addSlotBtn(slotKey, badgeLabel) {
                isPcModeActive = false
                controllerView.setProfile(p)
                refreshProfileHighlights()
                refreshModePill()
            }
        }

        // Fill Vertical Quick Settings Pill with Grayscale Frost Icons
        fun createQuickIconBtn(resId: Int, onClick: (android.widget.ImageView) -> Unit): android.widget.ImageView {
            return android.widget.ImageView(this@MainActivity).apply {
                setImageResource(resId)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 24f
                    setColor(android.graphics.Color.parseColor("#151922")) // Frosted Nordic Slate button background
                    setStroke(2, android.graphics.Color.parseColor("#2a3346")) // Subtle frost border
                }
                background = shape
                setPadding(18, 18, 18, 18)
                layoutParams = android.widget.LinearLayout.LayoutParams(80, 80).apply {
                    setMargins(0, 8, 0, 8)
                }

                setOnClickListener {
                    onClick(this)
                    scheduleHide()
                }
            }
        }

        // 1. RS Mode Button (Stick -> Aim -> Cursor) - Shown in Zone layout mode
        rsModeBtn = createQuickIconBtn(getRsModeIcon()) { btn ->
            VibrationManager.rightStickMode = when (VibrationManager.rightStickMode) {
                "stick" -> "aim"
                "aim" -> "cursor"
                else -> "stick"
            }
            VibrationManager.savePrefs(this@MainActivity)
            VibrationManager.vibrateHaptic()
            btn.setImageResource(getRsModeIcon())
            updateCurveState()
            refreshModePill()
            showHudToast(getRsModeName())
        }
        quickPill.addView(rsModeBtn)

        // 2. Curve Button (Linear -> Dynamic) - Shown in Zone layout mode
        curveBtn = createQuickIconBtn(getCurveIcon()) { btn ->
            VibrationManager.trackpadCurve = if (VibrationManager.trackpadCurve == "dynamic") "linear" else "dynamic"
            VibrationManager.savePrefs(this@MainActivity)
            VibrationManager.vibrateHaptic()
            btn.setImageResource(getCurveIcon())
            showHudToast(getCurveName())
        }
        quickPill.addView(curveBtn)

        // 3. Triggers Button (Digital -> Analog) - Shown in Both Layout Modes
        triggersBtn = createQuickIconBtn(getTriggerIcon()) { btn ->
            VibrationManager.analogTriggersEnabled = !VibrationManager.analogTriggersEnabled
            VibrationManager.savePrefs(this@MainActivity)
            VibrationManager.vibrateHaptic()
            btn.setImageResource(getTriggerIcon())
            showHudToast(getTriggerName())
        }
        quickPill.addView(triggersBtn)

        // Initial live refresh
        refreshProfileHighlights()
        refreshModePill()

        container.addView(dynamicIsland)
        container.addView(quickPill)
        container.addView(sensitivityPill)

        // Animate all in simultaneously
        dynamicIsland.translationY = -300f
        quickPill.translationX = 300f
        sensitivityPill.translationX = -300f

        android.animation.ObjectAnimator.ofFloat(dynamicIsland, "translationY", 0f).apply {
            duration = 350
            interpolator = android.view.animation.OvershootInterpolator(1.2f)
            start()
        }
        android.animation.ObjectAnimator.ofFloat(quickPill, "translationX", 0f).apply {
            duration = 350
            interpolator = android.view.animation.OvershootInterpolator(1.2f)
            start()
        }
        android.animation.ObjectAnimator.ofFloat(sensitivityPill, "translationX", 0f).apply {
            duration = 350
            interpolator = android.view.animation.OvershootInterpolator(1.2f)
            start()
        }

        scheduleHide()
    }

    private fun startReloadListener(pcIp: String, controllerView: ControllerView) {
        if (pcIp == "USB") return
        try { reloadSocket?.close() } catch (e: Exception) {}
        thread(start = true) {
            try {
                val socket = DatagramSocket(14569)
                reloadSocket = socket
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isTransmitting) {
                    socket.receive(packet)
                    val msg = String(packet.data, 0, packet.length).substringBefore("\u0000")
                    if (msg.startsWith("YEVAL_RELOAD:")) {
                        val slotId = msg.substringAfter("YEVAL_RELOAD:").trim()
                        
                        runOnUiThread {
                            controllerView.isServerFull = false
                            controllerView.invalidate()
                        }
                        
                        val dev = discoveredDevices.find { it.ip == pcIp || it.wifiIp == pcIp || it.tetherIp == pcIp || it.serverId == pcIp }
                        val isTether = dev != null && dev.tetherIp != null && (System.currentTimeMillis() - dev.lastTetherSeenMs < 4000L)
                        val targetIp = if (isTether) dev!!.tetherIp!! else (dev?.wifiIp ?: if (pcIp != "USB") pcIp else "127.0.0.1")
                        val httpPort = devicePorts[targetIp]?.second ?: 8080
                        val boundNet = getNetworkForIp(targetIp)

                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                val newProfile = ProfileManager.fetchProfile(targetIp, slotId, httpPort, boundNet)
                                if (newProfile != null) {
                                    latestPcProfile = newProfile
                                    val currentId = controllerView.getProfile()?.id
                                    val isLocalSlot = currentId?.startsWith("slot") == true
                                    val isCurrentlyInPcMode = isPcModeActive && !isLocalSlot
                                    if (isCurrentlyInPcMode) {
                                        withContext(Dispatchers.Main) {
                                            controllerView.setProfile(newProfile)
                                            android.widget.Toast.makeText(this@MainActivity, "Profile synced!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    } else if (msg.startsWith("YEVAL_SERVER_FULL")) {
                        runOnUiThread {
                            controllerView.isServerFull = true
                            controllerView.invalidate()
                        }
                    } else if (msg == "YEVAL_KICK" || msg.startsWith("YEVAL_SHUTDOWN")) {
                        runOnUiThread {
                            controllerView.isServerFull = false
                            dismissOverlay()
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore socket closed exceptions
            }
        }
    }

    private fun startTransmissionLoop(pcIp: String) {
        isTransmitting = true
        
        val batteryManager = getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
        
        thread(start = true) {
            var batteryLevel = 100
            var loopCount = 0
            var idleTickCount = 0
            
            while (isTransmitting) {
                try {
                    // Update battery level once a second
                    if (loopCount % 60 == 0) {
                        batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    }
                    loopCount++
                    
                    val state = currentState.get()
                    var finalRx = state.rx.toInt()
                    var finalRy = state.ry.toInt()
                    
                    // Mouse event is an instantaneous delta: consume once and clear rx, ry and click flags
                    if ((state.flags and 0x00010000) != 0) {
                        currentState.compareAndSet(
                            state,
                            state.copy(
                                rx = 0,
                                ry = 0,
                                flags = state.flags and 0x00010000.inv() and 0x00020000.inv() and 0x00040000.inv()
                            )
                        )
                    }
                    
                    if (isGyroAimingEnabled) {
                        val gyroInputX = (gyroX * 8000f).toInt()
                        val gyroInputY = (gyroY * 8000f).toInt()
                        
                        finalRx = (finalRx + gyroInputX).coerceIn(-32768, 32767)
                        finalRy = (finalRy + gyroInputY).coerceIn(-32768, 32767)
                    }

                    val prefs = getSharedPreferences("ybox_prefs", Context.MODE_PRIVATE)
                    val route = prefs.getString("connection_route", "auto") ?: "auto"
                    val enableWifi = (route == "auto" || route == "wifi_only")
                    val enableUsb = (route == "auto" || route == "usb_only")

                    val nowMs = System.currentTimeMillis()
                    if (loopCount % 30 == 0) {
                        val targetDev = discoveredDevices.find { it.ip == pcIp || it.wifiIp == pcIp || it.tetherIp == pcIp || it.serverId == pcIp } ?: discoveredDevices.firstOrNull()
                        val isWifiLive = targetDev != null && targetDev.wifiIp != null && (nowMs - targetDev.lastWifiSeenMs < 4000L)
                        val isTetherLive = targetDev != null && targetDev.tetherIp != null && (nowMs - targetDev.lastTetherSeenMs < 4000L)
                        val isAdbLive = (nowMs - lastUsbSeenMs < 4000L)

                        if (enableUsb) {
                            if (isTetherLive && targetDev?.tetherIp != null) {
                                val targetTetherIp = targetDev.tetherIp!!
                                if (adbSender == null || adbSender!!.targetIp != targetTetherIp || !adbSender!!.isConnected) {
                                    try {
                                        adbSender?.close()
                                        val tcpPort = devicePorts[targetTetherIp]?.third ?: 51230
                                        val tetherNet = getNetworkForIp(targetTetherIp)
                                        val sdr = AdbTcpSender(targetTetherIp, tcpPort, deviceId, { }, tetherNet)
                                        sdr.connect()
                                        adbSender = sdr
                                    } catch (e: Exception) {}
                                }
                            } else if (isAdbLive) {
                                if (adbSender == null || adbSender!!.targetIp != "127.0.0.1" || !adbSender!!.isConnected) {
                                    try {
                                        adbSender?.close()
                                        val sdr = AdbTcpSender("127.0.0.1", 14569, deviceId, { })
                                        sdr.connect()
                                        adbSender = sdr
                                    } catch (e: Exception) {}
                                }
                            }
                        }

                        if (enableWifi && (isWifiLive || pcIp != "USB")) {
                            val targetWifiIp = targetDev?.wifiIp ?: if (pcIp != "USB") pcIp else null
                            if (targetWifiIp != null) {
                                val udpPort = devicePorts[targetWifiIp]?.first ?: (targetDev?.let { devicePorts[it.ip]?.first }) ?: 14567
                                val currentNet = getNetworkForIp(targetWifiIp)
                                if (udpSender == null || udpSender!!.targetIp != targetWifiIp || udpSender!!.targetPort != udpPort || !udpSender!!.isHealthy || udpSender!!.boundNetwork != currentNet) {
                                    try {
                                        udpSender?.close()
                                        udpSender = UdpSender(targetWifiIp, udpPort, deviceId, null, currentNet) { getNetworkForIp(targetWifiIp) }
                                    } catch (e: Exception) {}
                                }
                            }
                        } else if (!isWifiLive && !isTetheredSession && pcIp == "USB") {
                            if (udpSender != null) {
                                udpSender?.close()
                                udpSender = null
                            }
                        }
                    }

                    val transports = mutableListOf<Pair<String, Any>>()

                    if (enableUsb) {
                        if (adbSender?.isConnected == true) {
                            transports.add("usb" to adbSender!!)
                        }
                    }
                    if (enableWifi || isTetheredSession) {
                        if (udpSender != null) {
                            transports.add("udp" to udpSender!!)
                        }
                    }

                    if (transports.isEmpty()) {
                        if (enableUsb) adbSender?.takeIf { it.isConnected }?.let { transports.add("usb" to it) }
                        if (enableWifi || isTetheredSession) udpSender?.let { transports.add("udp" to it) }
                    }

                    val primary = transports.firstOrNull()
                    val backup = transports.getOrNull(1)

                    val totalFlags = (state.flags and 0xFFFFFF00.toInt()) or (batteryLevel and 0xFF)
                    val stateToSend = { sender: Any ->
                        if (sender is AdbTcpSender) {
                            sender.sendState(
                                state.buttons, state.lt, state.rt, state.lx, state.ly, finalRx.toShort(), finalRy.toShort(),
                                gyroX, gyroY, gyroZ, 0f, 0f, 0f, totalFlags
                            )
                        } else if (sender is UdpSender) {
                            sender.sendState(
                                state.buttons, state.lt, state.rt, state.lx, state.ly, finalRx.toShort(), finalRy.toShort(),
                                gyroX, gyroY, gyroZ, 0f, 0f, 0f, totalFlags
                            )
                        }
                    }

                    // Always send on primary
                    primary?.second?.let { stateToSend(it) }

                    // In dual-transport mode, probe backup every ~80ms for instant seamless failover
                    if (backup != null && loopCount % 10 == 5) {
                        stateToSend(backup.second)
                    }

                    // Every 120th loop (~1 second), probe disconnected senders to trigger auto-reconnect
                    if (loopCount % 120 == 0) {
                        if (adbSender?.isConnected == false) stateToSend(adbSender!!)
                    }
                } catch (e: Exception) {
                    // Ignore socket exceptions during shutdown
                }
                
                // Adaptive idle rate
                val state = currentState.get()
                val isIdle = state.flags == 0 && state.buttons == 0.toShort() && state.lt == 0.toByte() && state.rt == 0.toByte() &&
                             kotlin.math.abs(state.lx.toInt()) < 1000 && kotlin.math.abs(state.ly.toInt()) < 1000 &&
                             kotlin.math.abs(state.rx.toInt()) < 1000 && kotlin.math.abs(state.ry.toInt()) < 1000
                             
                if (isIdle) {
                    idleTickCount++
                } else {
                    idleTickCount = 0
                }
                
                val sleepMs = if (idleTickCount > 62) 33L else 8L
                Thread.sleep(sleepMs)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isTransmitting = false
        udpSender?.close()
        adbSender?.close()
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(this)
    }

    companion object {
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            gyroX = event.values[0]
            gyroY = event.values[1]
            gyroZ = event.values[2]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
