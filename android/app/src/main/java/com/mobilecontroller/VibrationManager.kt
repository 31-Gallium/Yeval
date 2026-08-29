package com.mobilecontroller

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationManager {
    private var vibrator: Vibrator? = null
    
    // 0 = Low, 1 = Medium, 2 = High
    var hapticStrength: Int = 1
    var hapticEnabled: Boolean = true
    
    var rumbleStrength: Int = 1
    var rumbleEnabled: Boolean = true
    
    var analogTriggersEnabled: Boolean = false
    var continuousJoystickEnabled: Boolean = true
    var pixelPerfectHitboxesEnabled: Boolean = true

    // Right Stick Modes: "stick", "aim", "cursor"
    var rightStickMode: String = "stick"
    // Response Curve: "linear", "dynamic"
    var trackpadCurve: String = "linear"
    // Sensitivity: 10 levels (1 = 0.4x, 5 = 1.55x, 10 = 3.0x)
    var cursorSensitivityLevel: Int = 5
    var trackpadSensitivity: Float = 1.55f

    fun setCursorSensitivity(level: Int) {
        cursorSensitivityLevel = level.coerceIn(1, 10)
        trackpadSensitivity = 0.4f + (cursorSensitivityLevel - 1) * 0.288f
    }

    var currentLargeMotor: Int = 0
    var currentSmallMotor: Int = 0

    private var lastRumbleTimeMs: Long = 0L
    private var lastRumbleAmplitude: Int = -1

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        loadPrefs(context)
    }

    // Defaults configured in Settings
    var defaultRightStickMode: String = "stick"
    var defaultTrackpadCurve: String = "linear"
    var defaultAnalogTriggersEnabled: Boolean = false

    fun loadPrefs(context: Context) {
        val prefs = context.getSharedPreferences("vibration_settings", Context.MODE_PRIVATE)
        hapticEnabled = prefs.getBoolean("hapticEnabled", true)
        hapticStrength = prefs.getInt("hapticStrength", 1)
        rumbleEnabled = prefs.getBoolean("rumbleEnabled", true)
        rumbleStrength = prefs.getInt("rumbleStrength", 1)
        defaultAnalogTriggersEnabled = prefs.getBoolean("analogTriggersEnabled", false)
        analogTriggersEnabled = defaultAnalogTriggersEnabled
        
        val rawMode = prefs.getString("rightStickMode", "stick") ?: "stick"
        defaultRightStickMode = when(rawMode) {
            "standard", "stick" -> "stick"
            "camera_trackpad", "aim" -> "aim"
            "mouse_touchpad", "cursor" -> "cursor"
            else -> "stick"
        }
        rightStickMode = defaultRightStickMode

        val rawCurve = prefs.getString("trackpadCurve", "linear") ?: "linear"
        defaultTrackpadCurve = when(rawCurve) {
            "precision", "linear" -> "linear"
            "speed", "dynamic" -> "dynamic"
            else -> "linear"
        }
        trackpadCurve = defaultTrackpadCurve

        cursorSensitivityLevel = prefs.getInt("cursorSensitivityLevel", 5).coerceIn(1, 10)
        trackpadSensitivity = 0.4f + (cursorSensitivityLevel - 1) * 0.288f
    }

    fun resetToDefaults(context: Context) {
        loadPrefs(context)
        rightStickMode = defaultRightStickMode
        trackpadCurve = defaultTrackpadCurve
        analogTriggersEnabled = defaultAnalogTriggersEnabled
    }

    fun savePrefs(context: Context) {
        val prefs = context.getSharedPreferences("vibration_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("hapticEnabled", hapticEnabled)
            .putInt("hapticStrength", hapticStrength)
            .putBoolean("rumbleEnabled", rumbleEnabled)
            .putInt("rumbleStrength", rumbleStrength)
            .putBoolean("analogTriggersEnabled", defaultAnalogTriggersEnabled)
            .putString("rightStickMode", defaultRightStickMode)
            .putString("trackpadCurve", defaultTrackpadCurve)
            .putInt("cursorSensitivityLevel", cursorSensitivityLevel)
            .putFloat("trackpadSensitivity", trackpadSensitivity)
            .apply()
    }

    fun vibrateHaptic() {
        if (!hapticEnabled || vibrator == null || isRumbleActive) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effectId = when (hapticStrength) {
                0 -> VibrationEffect.EFFECT_TICK
                1 -> VibrationEffect.EFFECT_CLICK
                2 -> VibrationEffect.EFFECT_HEAVY_CLICK
                else -> VibrationEffect.EFFECT_CLICK
            }
            vibrator?.vibrate(VibrationEffect.createPredefined(effectId))
        } else {
            val duration = when (hapticStrength) {
                0 -> 30L
                1 -> 60L
                2 -> 100L
                else -> 60L
            }
            
            val amplitude = when (hapticStrength) {
                0 -> 64
                1 -> 128
                2 -> 255
                else -> 128
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vibrator?.hasAmplitudeControl() == true) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                } else {
                    vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        }
    }

    private var safetyTimeoutRunnable: Runnable? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isRumbleActive = false

    fun vibrateRumble(largeMotor: Int, smallMotor: Int) {
        currentLargeMotor = largeMotor
        currentSmallMotor = smallMotor

        if (!rumbleEnabled || vibrator == null || (largeMotor == 0 && smallMotor == 0)) {
            cancelRumble()
            return
        }

        // Combine motors and scale by rumbleStrength
        val maxMotor = maxOf(largeMotor, smallMotor)
        val strengthMultiplier = when(rumbleStrength) {
            0 -> 0.35f
            1 -> 0.70f
            2 -> 1.00f
            else -> 0.70f
        }
        val scaledAmplitude = (maxMotor.toFloat() * strengthMultiplier).toInt().coerceIn(1, 255)

        val ampDiff = Math.abs(scaledAmplitude - lastRumbleAmplitude)

        // If rumble is already active with the same amplitude, just extend the watchdog timer
        if (isRumbleActive && lastRumbleAmplitude != -1 && ampDiff < 15) {
            resetSafetyTimeout()
            return
        }

        lastRumbleTimeMs = System.currentTimeMillis()
        lastRumbleAmplitude = scaledAmplitude
        isRumbleActive = true

        // Continuous repeating waveform without any 0-amplitude cycle or gap
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(1000)
            val amplitudes = if (vibrator?.hasAmplitudeControl() == true) {
                intArrayOf(scaledAmplitude)
            } else {
                intArrayOf(VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0)) // 0 = repeat continuously with zero off-time
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 1000), 0) // repeat index 0 = loop forever
        }

        resetSafetyTimeout()
    }

    private fun resetSafetyTimeout() {
        safetyTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        val timeoutRunnable = Runnable {
            cancelRumble()
        }
        safetyTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, 1200L) // 1200ms safety watchdog
    }

    fun cancelRumble() {
        safetyTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        safetyTimeoutRunnable = null
        isRumbleActive = false
        lastRumbleAmplitude = -1
        lastRumbleTimeMs = 0L
        currentLargeMotor = 0
        currentSmallMotor = 0
        vibrator?.cancel()
    }

    fun vibrateRumblePreview() {
        if (!rumbleEnabled || vibrator == null) return
        val strengthMultiplier = when(rumbleStrength) {
            0 -> 0.35f
            1 -> 0.70f
            2 -> 1.00f
            else -> 0.70f
        }
        val scaledAmplitude = (255f * strengthMultiplier).toInt().coerceIn(1, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator?.hasAmplitudeControl() == true) {
                vibrator?.vibrate(VibrationEffect.createOneShot(500L, scaledAmplitude))
            } else {
                vibrator?.vibrate(VibrationEffect.createOneShot(500L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(500L)
        }
    }
    
    fun vibrateMaxPressure() {
        if (!hapticEnabled || vibrator == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(10L)
        }
    }
}
