package com.mobilecontroller

import org.json.JSONObject

data class StickConfig(val id: String, var x: Float, var y: Float, var scale: Float = 1f)
data class TriggerConfig(val id: String, var x: Float, var y: Float, var scale: Float = 1f)
data class BumperConfig(val id: String, var x: Float, var y: Float, var scale: Float = 1f)
data class DpadConfig(var x: Float, var y: Float, var scale: Float = 1f)
data class FaceButtonConfig(val id: String, var x: Float, var y: Float, var scale: Float = 1f)
data class MetaButtonConfig(val id: String, var x: Float, var y: Float, var scale: Float = 1f)
data class MenuButtonConfig(var x: Float, var y: Float, var scale: Float = 1f)

data class ZoneVertexConfig(val x: Float, val y: Float)
data class ZoneConfig(val buttonId: String, val vertices: List<ZoneVertexConfig>)

data class ControllerProfile(
    var id: String,
    var name: String,
    var layoutMode: String = "button",
    var curveZones: Boolean = true,
    var zones: List<ZoneConfig> = emptyList(),
    val leftStick: StickConfig,
    val rightStick: StickConfig,
    val triggers: List<TriggerConfig>,
    val bumpers: List<BumperConfig>,
    val dpad: DpadConfig,
    val faceButtons: List<FaceButtonConfig>,
    val metaButtons: List<MetaButtonConfig>,
    val menuButton: MenuButtonConfig? = null
)

object ProfileManager {
    
    // In a real Android app, this would be loaded from res/raw or assets.
    // We provide a hard-coded fallback just for the PoC skeleton to avoid I/O boilerplate.
    fun loadDefaultProfile(): ControllerProfile {
        return ControllerProfile(
            id = "default-xbox",
            name = "Default",
            layoutMode = "button",
            curveZones = true,
            zones = listOf(
                ZoneConfig("LT", listOf(ZoneVertexConfig(0f, 0f), ZoneVertexConfig(200f, 0f), ZoneVertexConfig(200f, 150f), ZoneVertexConfig(0f, 75f))),
                ZoneConfig("LB", listOf(ZoneVertexConfig(200f, 0f), ZoneVertexConfig(425f, 0f), ZoneVertexConfig(400f, 75f), ZoneVertexConfig(200f, 150f))),
                ZoneConfig("START", listOf(ZoneVertexConfig(425f, 0f), ZoneVertexConfig(400f, 75f), ZoneVertexConfig(500f, 75f), ZoneVertexConfig(500f, 0f))),
                ZoneConfig("BACK", listOf(ZoneVertexConfig(500f, 0f), ZoneVertexConfig(500f, 75f), ZoneVertexConfig(600f, 75f), ZoneVertexConfig(575f, 0f))),
                ZoneConfig("MENU", listOf(ZoneVertexConfig(400f, 75f), ZoneVertexConfig(425f, 125f), ZoneVertexConfig(500f, 125f), ZoneVertexConfig(500f, 75f))),
                ZoneConfig("GUIDE", listOf(ZoneVertexConfig(500f, 75f), ZoneVertexConfig(600f, 75f), ZoneVertexConfig(575f, 125f), ZoneVertexConfig(500f, 125f))),
                ZoneConfig("RB", listOf(ZoneVertexConfig(575f, 0f), ZoneVertexConfig(800f, 0f), ZoneVertexConfig(800f, 150f), ZoneVertexConfig(600f, 75f))),
                ZoneConfig("RT", listOf(ZoneVertexConfig(800f, 0f), ZoneVertexConfig(1000f, 0f), ZoneVertexConfig(1000f, 75f), ZoneVertexConfig(800f, 150f))),
                ZoneConfig("DPAD", listOf(ZoneVertexConfig(250f, 175f), ZoneVertexConfig(0f, 175f), ZoneVertexConfig(0f, 425f), ZoneVertexConfig(250f, 425f))),
                ZoneConfig("LS", listOf(ZoneVertexConfig(475f, 175f), ZoneVertexConfig(500f, 200f), ZoneVertexConfig(500f, 400f), ZoneVertexConfig(475f, 425f), ZoneVertexConfig(275f, 425f), ZoneVertexConfig(250f, 400f), ZoneVertexConfig(250f, 200f), ZoneVertexConfig(275f, 175f))),
                ZoneConfig("RS", listOf(ZoneVertexConfig(500f, 200f), ZoneVertexConfig(525f, 175f), ZoneVertexConfig(725f, 175f), ZoneVertexConfig(750f, 200f), ZoneVertexConfig(750f, 400f), ZoneVertexConfig(725f, 425f), ZoneVertexConfig(525f, 425f), ZoneVertexConfig(500f, 400f))),
                ZoneConfig("X", listOf(ZoneVertexConfig(875f, 300f), ZoneVertexConfig(750f, 175f), ZoneVertexConfig(750f, 425f))),
                ZoneConfig("Y", listOf(ZoneVertexConfig(1000f, 175f), ZoneVertexConfig(950f, 200f), ZoneVertexConfig(900f, 250f), ZoneVertexConfig(875f, 300f), ZoneVertexConfig(750f, 175f))),
                ZoneConfig("A", listOf(ZoneVertexConfig(1000f, 425f), ZoneVertexConfig(950f, 400f), ZoneVertexConfig(900f, 350f), ZoneVertexConfig(875f, 300f), ZoneVertexConfig(750f, 425f))),
                ZoneConfig("B", listOf(ZoneVertexConfig(1000f, 175f), ZoneVertexConfig(950f, 200f), ZoneVertexConfig(900f, 250f), ZoneVertexConfig(875f, 300f), ZoneVertexConfig(900f, 350f), ZoneVertexConfig(950f, 400f), ZoneVertexConfig(1000f, 425f)))
            ),
            leftStick = StickConfig("LS", 0.125f, 0.6667f),
            rightStick = StickConfig("RS", 0.625f, 0.7778f),
            triggers = listOf(
                TriggerConfig("LT", 0.125f, 0.1111f),
                TriggerConfig("RT", 0.875f, 0.1111f)
            ),
            bumpers = listOf(
                BumperConfig("LB", 0.175f, 0.3333f),
                BumperConfig("RB", 0.825f, 0.3333f)
            ),
            dpad = DpadConfig(0.375f, 0.7778f),
            faceButtons = listOf(
                FaceButtonConfig("Y", 0.875f, 0.5556f),
                FaceButtonConfig("X", 0.800f, 0.7222f),
                FaceButtonConfig("B", 0.950f, 0.7222f),
                FaceButtonConfig("A", 0.875f, 0.8889f)
            ),
            metaButtons = listOf(
                MetaButtonConfig("BACK", 0.425f, 0.1667f),
                MetaButtonConfig("GUIDE", 0.500f, 0.2778f),
                MetaButtonConfig("START", 0.575f, 0.1667f)
            ),
            menuButton = MenuButtonConfig(0.500f, 0.1111f)
        )
    }

    suspend fun fetchProfile(
        ip: String,
        profileId: String = "default-xbox",
        port: Int = 8080,
        boundNetwork: android.net.Network? = null
    ): ControllerProfile? = kotlinx.coroutines.Dispatchers.IO.let {
        return kotlinx.coroutines.withContext(it) {
            try {
                val builder = okhttp3.OkHttpClient.Builder()
                try {
                    boundNetwork?.socketFactory?.let { sf -> builder.socketFactory(sf) }
                } catch (e: Exception) {}
                val client = builder.build()

                val request = okhttp3.Request.Builder()
                    .url("http://$ip:$port/profiles/$profileId.json?t=${System.currentTimeMillis()}")
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val json = response.body?.string() ?: return@withContext null
                    return@withContext parseProfile(json)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    fun parseProfile(jsonString: String): ControllerProfile? {
        try {
            val root = JSONObject(jsonString)
            
            val layoutMode = if (root.has("layoutMode")) root.getString("layoutMode") else "button"
            val curveZones = if (root.has("curveZones")) root.getBoolean("curveZones") else true
            
            val zonesList = mutableListOf<ZoneConfig>()
            val zonesArray = if (root.has("rawZones")) root.getJSONArray("rawZones") else if (root.has("zones")) root.getJSONArray("zones") else null
            if (zonesArray != null) {
                for (i in 0 until zonesArray.length()) {
                    val zObj = zonesArray.getJSONObject(i)
                    val bId = zObj.getString("buttonId")
                    val vArray = zObj.getJSONArray("vertices")
                    val vList = mutableListOf<ZoneVertexConfig>()
                    for (j in 0 until vArray.length()) {
                        val vObj = vArray.getJSONObject(j)
                        vList.add(ZoneVertexConfig(vObj.getDouble("x").toFloat(), vObj.getDouble("y").toFloat()))
                    }
                    zonesList.add(ZoneConfig(bId, vList))
                }
            }
            
            val sticks = root.getJSONObject("sticks")
            val left = sticks.getJSONObject("left")
            val right = sticks.getJSONObject("right")
            val leftStick = StickConfig(left.getString("id"), left.getDouble("x").toFloat(), left.getDouble("y").toFloat(), left.optDouble("scale", 1.0).toFloat())
            val rightStick = StickConfig(right.getString("id"), right.getDouble("x").toFloat(), right.getDouble("y").toFloat(), right.optDouble("scale", 1.0).toFloat())

            val triggersArray = root.getJSONArray("triggers")
            val triggersList = mutableListOf<TriggerConfig>()
            for (i in 0 until triggersArray.length()) {
                val o = triggersArray.getJSONObject(i)
                triggersList.add(TriggerConfig(o.getString("id"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.optDouble("scale", 1.0).toFloat()))
            }

            val bumpersArray = root.getJSONArray("bumpers")
            val bumpersList = mutableListOf<BumperConfig>()
            for (i in 0 until bumpersArray.length()) {
                val o = bumpersArray.getJSONObject(i)
                bumpersList.add(BumperConfig(o.getString("id"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.optDouble("scale", 1.0).toFloat()))
            }

            val dpadObj = root.getJSONObject("dpad")
            val dpad = DpadConfig(dpadObj.getDouble("x").toFloat(), dpadObj.getDouble("y").toFloat(), dpadObj.optDouble("scale", 1.0).toFloat())

            val faceArray = root.getJSONArray("faceButtons")
            val faceList = mutableListOf<FaceButtonConfig>()
            for (i in 0 until faceArray.length()) {
                val o = faceArray.getJSONObject(i)
                faceList.add(FaceButtonConfig(o.getString("id"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.optDouble("scale", 1.0).toFloat()))
            }

            val metaArray = root.getJSONArray("metaButtons")
            val metaList = mutableListOf<MetaButtonConfig>()
            for (i in 0 until metaArray.length()) {
                val o = metaArray.getJSONObject(i)
                metaList.add(MetaButtonConfig(o.getString("id"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.optDouble("scale", 1.0).toFloat()))
            }

            var menuBtn: MenuButtonConfig? = null
            if (root.has("menuButton")) {
                val mb = root.getJSONObject("menuButton")
                menuBtn = MenuButtonConfig(mb.getDouble("x").toFloat(), mb.getDouble("y").toFloat(), mb.optDouble("scale", 1.0).toFloat())
            }
            
            return ControllerProfile(
                id = root.getString("id"),
                name = root.getString("name"),
                layoutMode = layoutMode,
                curveZones = curveZones,
                zones = zonesList,
                leftStick = leftStick,
                rightStick = rightStick,
                triggers = triggersList,
                bumpers = bumpersList,
                dpad = dpad,
                faceButtons = faceList,
                metaButtons = metaList,
                menuButton = menuBtn
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
