package com.example.dualmapper.manager.preset

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.dualmapper.data.AppDatabase
import com.example.dualmapper.data.KeyCodeLibraryEntity
import com.example.dualmapper.data.KeyMappingEntity
import com.example.dualmapper.data.PresetLayoutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetManager @Inject constructor(
    private val context: Context,
    private val db: AppDatabase
) {
    @Volatile
    private var presetsLoaded = false

    companion object {
        private const val BACKUP_VERSION = 1
        private const val KEYCODE_LIB_VERSION = 1
        private const val PREF_KEYCODE_LIB_VERSION = "keycode_lib_version"
    }

    suspend fun loadPresets() = withContext(Dispatchers.IO) {
        if (presetsLoaded) return@withContext

        // 首次或升级时自动检查并更新按键库
        importKeyCodeLibrary()

        if (db.presetLayoutDao().getAll().isNotEmpty()) {
            presetsLoaded = true
            return@withContext
        }

        val fightingPreset = PresetLayoutEntity("preset_fighting", "格斗游戏布局", "经典六键布局")
        db.presetLayoutDao().insert(fightingPreset)
        val fightingKeys = listOf(
            KeyMappingEntity("f1", "上", 100, 600, 1f, "tap", 200f, 800f, 0f, 0f, 50, keyCode = 19, presetId = "preset_fighting"),
            KeyMappingEntity("f2", "下", 100, 700, 1f, "tap", 200f, 1000f, 0f, 0f, 50, keyCode = 20, presetId = "preset_fighting"),
            KeyMappingEntity("f3", "左", 50, 650, 1f, "tap", 100f, 900f, 0f, 0f, 50, keyCode = 21, presetId = "preset_fighting"),
            KeyMappingEntity("f4", "右", 150, 650, 1f, "tap", 300f, 900f, 0f, 0f, 50, keyCode = 22, presetId = "preset_fighting"),
            KeyMappingEntity("f5", "A", 250, 750, 1f, "tap", 400f, 1100f, 0f, 0f, 50, keyCode = 96, presetId = "preset_fighting"),
            KeyMappingEntity("f6", "B", 350, 750, 1f, "tap", 500f, 1100f, 0f, 0f, 50, keyCode = 97, presetId = "preset_fighting")
        )

        val shootingPreset = PresetLayoutEntity("preset_shooting", "射击游戏布局", "双摇杆+射击")
        db.presetLayoutDao().insert(shootingPreset)
        val shootingKeys = listOf(
            KeyMappingEntity("s1", "左", 80, 700, 1f, "tap", 300f, 1300f, 0f, 0f, 50, keyCode = 21, presetId = "preset_shooting"),
            KeyMappingEntity("s2", "右", 200, 700, 1f, "tap", 500f, 1300f, 0f, 0f, 50, keyCode = 22, presetId = "preset_shooting"),
            KeyMappingEntity("s3", "射击", 300, 750, 1f, "tap", 800f, 1400f, 0f, 0f, 50, keyCode = 96, presetId = "preset_shooting")
        )

        db.keyMappingDao().upsertAll(fightingKeys + shootingKeys)
        presetsLoaded = true
    }

    private fun importKeyCodeLibrary() {
        try {
            val prefs = context.getSharedPreferences("dualmapper_lib", Context.MODE_PRIVATE)
            val savedVersion = prefs.getInt(PREF_KEYCODE_LIB_VERSION, 0)
            if (savedVersion >= KEYCODE_LIB_VERSION) return

            val jsonStr = context.assets.open("keycode_library.json")
                .bufferedReader()
                .use { it.readText() }
            val array = JSONArray(jsonStr)
            val entities = mutableListOf<KeyCodeLibraryEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                entities.add(
                    KeyCodeLibraryEntity(
                        keyCode = obj.getInt("keyCode"),
                        vendorId = obj.optInt("vendorId", 0).takeIf { it != 0 },
                        productId = obj.optInt("productId", 0).takeIf { it != 0 },
                        label = obj.getString("label"),
                        category = obj.getString("category")
                    )
                )
            }
            if (entities.isNotEmpty()) {
                db.keyCodeLibraryDao().deleteAll()          // 清除旧版本
                db.keyCodeLibraryDao().insertAll(*entities.toTypedArray())
                prefs.edit().putInt(PREF_KEYCODE_LIB_VERSION, KEYCODE_LIB_VERSION).apply()
            }
        } catch (e: Exception) {
            Log.w("PresetManager", "Failed to import keycode library", e)
        }
    }

    suspend fun backupCurrent() = withContext(Dispatchers.IO) {
        val keys = db.keyMappingDao().getAll()
        val json = JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("keys", JSONArray().apply {
                keys.forEach { key ->
                    put(JSONObject().apply {
                        put("id", key.id)
                        put("label", key.label)
                        put("layoutX", key.layoutX)
                        put("layoutY", key.layoutY)
                        put("alpha", key.alpha.toDouble())
                        put("actionType", key.actionType)
                        put("targetX", key.targetX.toDouble())
                        put("targetY", key.targetY.toDouble())
                        put("endX", key.endX.toDouble())
                        put("endY", key.endY.toDouble())
                        put("durationMs", key.durationMs)
                        put("keyCode", key.keyCode)
                        put("playerIndex", key.playerIndex)
                        put("scaleX", key.scaleX.toDouble())
                        put("scaleY", key.scaleY.toDouble())
                        put("iconPath", key.iconPath ?: "")
                        put("designWidth", key.designWidth)
                        put("designHeight", key.designHeight)
                    })
                }
            })
        }
        val file = java.io.File(context.filesDir, "layout_backup.json")
        file.writeText(json.toString())
    }

    suspend fun restoreBackup() = withContext(Dispatchers.IO) {
        val file = java.io.File(context.filesDir, "layout_backup.json")
        if (!file.exists()) return@withContext
        val jsonStr = file.readText()
        val root = JSONObject(jsonStr)
        val version = root.optInt("version", 1)
        val keysArray = when (version) {
            1 -> root.getJSONArray("keys")
            else -> return@withContext
        }
        val entities = mutableListOf<KeyMappingEntity>()
        for (i in 0 until keysArray.length()) {
            KeyMappingEntity.fromJson(keysArray.getJSONObject(i))?.let { entity ->
                entities.add(entity)
            }
        }
        db.keyMappingDao().replaceAllWithTransaction(entities)
        context.sendBroadcast(Intent("ACTION_RELOAD_KEYS"))
    }

    suspend fun applyPreset(presetId: String, backupFirst: Boolean = true) = withContext(Dispatchers.IO) {
        if (backupFirst) backupCurrent()
        val keys = db.keyMappingDao().getByPresetId(presetId)
        db.keyMappingDao().replaceAllWithTransaction(keys)
        context.sendBroadcast(Intent("ACTION_RELOAD_KEYS"))
    }

    suspend fun getAvailablePresets(): List<PresetLayoutEntity> = db.presetLayoutDao().getAll()

    suspend fun getKeysByPresetId(presetId: String): List<KeyMappingEntity> = db.keyMappingDao().getByPresetId(presetId)

    suspend fun getKeyLabel(keyCode: Int): String? = db.keyCodeLibraryDao().getLabelForKeyCode(keyCode)
}