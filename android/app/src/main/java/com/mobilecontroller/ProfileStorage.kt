package com.mobilecontroller

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ProfileStorage {
    const val MAX_PROFILES = 24
    const val MAX_NAME_LENGTH = 16
    private const val PREF_NAME = "controller_profiles"
    private const val KEY_PROFILE_COUNT = "profile_count"
    private const val CURRENT_VERSION = 8
    private const val KEY_PROFILE_VERSION = "profiles_version_v8"
    private val GREEK_ALPHABET = listOf(
        "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", 
        "Iota", "Kappa", "Lambda", "Mu", "Nu", "Xi", "Omicron", "Pi", "Rho", 
        "Sigma", "Tau", "Upsilon", "Phi", "Chi", "Psi", "Omega"
    )

    fun getProfileCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PROFILE_COUNT, 5)
    }

    private fun setProfileCount(context: Context, count: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_PROFILE_COUNT, count).apply()
    }
    
    fun saveLocalProfile(context: Context, profile: ControllerProfile, slotIndex: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        profile.id = "slot${slotIndex + 1}"
        if (profile.name.isBlank()) {
            profile.name = GREEK_ALPHABET.getOrElse(slotIndex) { "Profile ${slotIndex + 1}" }
        }
        val jsonStr = serializeProfile(profile)
        prefs.edit().putString(profile.id, jsonStr).apply()
    }

    fun getLocalProfiles(context: Context): List<ControllerProfile> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentVer = prefs.getInt(KEY_PROFILE_VERSION, 0)
        
        // Automatic migration to initialize Alpha, Beta, Gamma, Delta, Epsilon
        if (currentVer < CURRENT_VERSION) {
            val editor = prefs.edit()
            editor.clear()
            val initialNames = listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon")
            for (i in initialNames.indices) {
                val slotId = "slot${i + 1}"
                val p = createDefaultSlot(slotId, initialNames[i])
                editor.putString(slotId, serializeProfile(p))
            }
            editor.putInt(KEY_PROFILE_COUNT, 5)
            editor.putInt(KEY_PROFILE_VERSION, CURRENT_VERSION)
            editor.apply()
        }

        val count = getProfileCount(context)
        val list = mutableListOf<ControllerProfile>()

        for (i in 0 until count) {
            val slotId = "slot${i + 1}"
            val defaultName = GREEK_ALPHABET.getOrElse(i) { "Profile ${i + 1}" }
            val jsonStr = prefs.getString(slotId, null)

            if (jsonStr != null) {
                try {
                    val p = ProfileManager.parseProfile(jsonStr)
                    if (p != null) {
                        p.id = slotId
                        if (p.name.isBlank() || p.name == "null") {
                            p.name = defaultName
                        }
                        list.add(p)
                    } else {
                        list.add(createDefaultSlot(slotId, defaultName))
                    }
                } catch (e: Exception) {
                    list.add(createDefaultSlot(slotId, defaultName))
                }
            } else {
                list.add(createDefaultSlot(slotId, defaultName))
            }
        }

        // Safety fallback: if list is somehow still empty, create Alpha profile immediately
        if (list.isEmpty()) {
            val pDefault = createDefaultSlot("slot1", "Alpha")
            list.add(pDefault)
            val editor = prefs.edit()
            editor.putString("slot1", serializeProfile(pDefault))
            editor.putInt(KEY_PROFILE_COUNT, 1)
            editor.putInt(KEY_PROFILE_VERSION, CURRENT_VERSION)
            editor.apply()
        }

        return list
    }

    private fun createDefaultSlot(id: String, name: String): ControllerProfile {
        val p = ProfileManager.loadDefaultProfile()
        p.id = id
        p.name = name
        return p
    }

    fun swapProfiles(context: Context, fromIndex: Int, toIndex: Int) {
        val profiles = getLocalProfiles(context).toMutableList()
        if (fromIndex !in profiles.indices || toIndex !in profiles.indices) return
        val movedItem = profiles.removeAt(fromIndex)
        profiles.add(toIndex, movedItem)
        
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        for (i in profiles.indices) {
            val p = profiles[i]
            p.id = "slot${i + 1}"
            prefs.putString(p.id, serializeProfile(p))
        }
        prefs.apply()
    }

    fun resetProfile(context: Context, slotIndex: Int) {
        val defaultName = GREEK_ALPHABET.getOrElse(slotIndex) { "Profile ${slotIndex + 1}" }
        val p = createDefaultSlot("slot${slotIndex + 1}", defaultName)
        saveLocalProfile(context, p, slotIndex)
    }

    fun addProfile(context: Context) {
        val count = getProfileCount(context)
        if (count >= MAX_PROFILES) return
        
        val existingProfiles = getLocalProfiles(context)
        
        // Pick first unused Greek letter from the alphabet
        val availableGreek = GREEK_ALPHABET.firstOrNull { greek ->
            existingProfiles.none { it.name.equals(greek, ignoreCase = true) }
        }
        val rawBaseName = availableGreek ?: GREEK_ALPHABET.getOrElse(count) { "Profile ${count + 1}" }
        
        var nameToUse = rawBaseName
        var suffix = 2
        while (existingProfiles.any { it.name.equals(nameToUse, ignoreCase = true) }) {
            val maxBaseLen = MAX_NAME_LENGTH - 3
            val base = if (rawBaseName.length > maxBaseLen) rawBaseName.substring(0, maxBaseLen).trim() else rawBaseName
            nameToUse = "$base $suffix"
            suffix++
        }
        
        val nextIndex = count
        val p = createDefaultSlot("slot${nextIndex + 1}", nameToUse)
        saveLocalProfile(context, p, nextIndex)
        setProfileCount(context, count + 1)
    }

    fun deleteProfile(context: Context, slotIndex: Int) {
        val profiles = getLocalProfiles(context).toMutableList()
        if (profiles.size <= 1 || slotIndex !in profiles.indices) return
        
        profiles.removeAt(slotIndex)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        prefs.clear()
        
        for (i in profiles.indices) {
            val p = profiles[i]
            p.id = "slot${i + 1}"
            prefs.putString(p.id, serializeProfile(p))
        }
        prefs.putInt(KEY_PROFILE_COUNT, profiles.size)
        prefs.putInt(KEY_PROFILE_VERSION, CURRENT_VERSION)
        prefs.apply()
    }

    fun renameProfile(context: Context, slotIndex: Int, newName: String) {
        val profiles = getLocalProfiles(context)
        if (slotIndex !in profiles.indices) return

        var nameToUse = newName.trim()
        if (nameToUse.isBlank()) {
            val availableGreek = GREEK_ALPHABET.firstOrNull { greek ->
                profiles.none { it.name.equals(greek, ignoreCase = true) }
            }
            nameToUse = availableGreek ?: GREEK_ALPHABET.getOrElse(slotIndex) { "Profile ${slotIndex + 1}" }
        }

        if (nameToUse.length > MAX_NAME_LENGTH) {
            nameToUse = nameToUse.substring(0, MAX_NAME_LENGTH).trim()
        }

        // Check for duplicate name among other profiles and auto-append suffix (e.g. Beta 2, Beta 3)
        val otherProfiles = profiles.filterIndexed { index, _ -> index != slotIndex }
        var finalName = nameToUse
        var suffix = 2
        while (otherProfiles.any { it.name.equals(finalName, ignoreCase = true) }) {
            val maxBaseLen = MAX_NAME_LENGTH - 3
            val base = if (nameToUse.length > maxBaseLen) nameToUse.substring(0, maxBaseLen).trim() else nameToUse
            finalName = "$base $suffix"
            suffix++
        }

        val p = profiles[slotIndex]
        p.name = finalName
        saveLocalProfile(context, p, slotIndex)
    }

    fun serializeProfile(profile: ControllerProfile): String {
        val root = JSONObject()
        root.put("id", profile.id)
        root.put("name", profile.name)
        root.put("layoutMode", profile.layoutMode)
        root.put("curveZones", profile.curveZones)

        val zonesArr = JSONArray()
        for (z in profile.zones) {
            val zObj = JSONObject()
            zObj.put("buttonId", z.buttonId)
            val vArr = JSONArray()
            for (v in z.vertices) {
                val vObj = JSONObject()
                vObj.put("x", v.x.toDouble())
                vObj.put("y", v.y.toDouble())
                vArr.put(vObj)
            }
            zObj.put("vertices", vArr)
            zonesArr.put(zObj)
        }
        root.put("rawZones", zonesArr)
        root.put("zones", zonesArr)

        val sticksObj = JSONObject()
        val leftObj = JSONObject()
        leftObj.put("id", profile.leftStick.id)
        leftObj.put("x", profile.leftStick.x.toDouble())
        leftObj.put("y", profile.leftStick.y.toDouble())
        leftObj.put("scale", profile.leftStick.scale.toDouble())
        sticksObj.put("left", leftObj)

        val rightObj = JSONObject()
        rightObj.put("id", profile.rightStick.id)
        rightObj.put("x", profile.rightStick.x.toDouble())
        rightObj.put("y", profile.rightStick.y.toDouble())
        rightObj.put("scale", profile.rightStick.scale.toDouble())
        sticksObj.put("right", rightObj)
        root.put("sticks", sticksObj)

        val triggersArr = JSONArray()
        for (t in profile.triggers) {
            val tObj = JSONObject()
            tObj.put("id", t.id)
            tObj.put("x", t.x.toDouble())
            tObj.put("y", t.y.toDouble())
            tObj.put("scale", t.scale.toDouble())
            triggersArr.put(tObj)
        }
        root.put("triggers", triggersArr)

        val bumpersArr = JSONArray()
        for (b in profile.bumpers) {
            val bObj = JSONObject()
            bObj.put("id", b.id)
            bObj.put("x", b.x.toDouble())
            bObj.put("y", b.y.toDouble())
            bObj.put("scale", b.scale.toDouble())
            bumpersArr.put(bObj)
        }
        root.put("bumpers", bumpersArr)

        val dpadObj = JSONObject()
        dpadObj.put("x", profile.dpad.x.toDouble())
        dpadObj.put("y", profile.dpad.y.toDouble())
        dpadObj.put("scale", profile.dpad.scale.toDouble())
        root.put("dpad", dpadObj)

        val faceArr = JSONArray()
        for (f in profile.faceButtons) {
            val fObj = JSONObject()
            fObj.put("id", f.id)
            fObj.put("x", f.x.toDouble())
            fObj.put("y", f.y.toDouble())
            fObj.put("scale", f.scale.toDouble())
            faceArr.put(fObj)
        }
        root.put("faceButtons", faceArr)

        val metaArr = JSONArray()
        for (m in profile.metaButtons) {
            val mObj = JSONObject()
            mObj.put("id", m.id)
            mObj.put("x", m.x.toDouble())
            mObj.put("y", m.y.toDouble())
            mObj.put("scale", m.scale.toDouble())
            metaArr.put(mObj)
        }
        root.put("metaButtons", metaArr)

        if (profile.menuButton != null) {
            val mbObj = JSONObject()
            mbObj.put("x", profile.menuButton.x.toDouble())
            mbObj.put("y", profile.menuButton.y.toDouble())
            mbObj.put("scale", profile.menuButton.scale.toDouble())
            root.put("menuButton", mbObj)
        }

        return root.toString()
    }
}
