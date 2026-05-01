package com.example.dualmapper.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.dualmapper.data.AppDatabase
import com.example.dualmapper.data.KeyMappingEntity
import com.example.dualmapper.manager.preset.PresetManager
import com.example.dualmapper.service.MappedAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LayoutFileHelper {
    private const val FILE_PROVIDER_AUTHORITY = "com.example.dualmapper.fileprovider"
    private const val MAX_COORD = 10000f

    suspend fun exportLayoutToFile(context: Context, database: AppDatabase): Uri? = withContext(Dispatchers.IO) {
        try {
            val keys = database.keyMappingDao().getAll()
            val jsonArray = JSONArray()
            keys.forEach { key ->
                jsonArray.put(JSONObject().apply {
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
                    put("rotation", key.rotation.toDouble())
                    put("iconPath", key.iconPath ?: "")
                    put("presetId", key.presetId ?: "")
                    put("designWidth", key.designWidth)
                    put("designHeight", key.designHeight)
                })
            }
            val fileName = "dualmapper_layout_${System.currentTimeMillis()}.json"
            val file = File(context.cacheDir, fileName)
            file.writeText(jsonArray.toString())
            FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun importLayoutFromUri(context: Context, uri: Uri, database: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: return@withContext false
            if (!isValidLayoutJson(jsonString)) return@withContext false
            val jsonArray = JSONArray(jsonString)
            val presetManager = PresetManager(context, database)
            presetManager.backupCurrent()
            val entities = mutableListOf<KeyMappingEntity>()
            for (i in 0 until jsonArray.length()) {
                KeyMappingEntity.fromJson(jsonArray.getJSONObject(i))?.let { entity ->
                    entities.add(entity)
                }
            }
            database.keyMappingDao().deleteAll()
            database.keyMappingDao().upsertAll(entities)
            MappedAccessibilityService.instance?.refreshMappings()
            context.sendBroadcast(Intent("ACTION_RELOAD_KEYS"))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidLayoutJson(json: String): Boolean {
        return try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                // mandatory strings
                if (!obj.has("id") || obj.optString("id", "").isEmpty()) return false
                if (!obj.has("label") || obj.optString("label", "").isEmpty()) return false

                // mandatory integers with range check
                val layoutX = obj.getInt("layoutX").also { if (it < 0 || it > MAX_COORD) return false }
                val layoutY = obj.getInt("layoutY").also { if (it < 0 || it > MAX_COORD) return false }
                val durationMs = obj.getLong("durationMs").also { if (it < 0) return false }
                val keyCode = obj.getInt("keyCode").also { if (it < 0 || it > 65535) return false }
                val playerIndex = obj.getInt("playerIndex").also { if (it !in 1..4) return false }

                // mandatory floats with range check
                val alpha = obj.getDouble("alpha").toFloat().also { if (it < 0f || it > 1f) return false }
                val targetX = obj.getDouble("targetX").toFloat().also { if (it < 0f || it > MAX_COORD) return false }
                val targetY = obj.getDouble("targetY").toFloat().also { if (it < 0f || it > MAX_COORD) return false }
                val endX = obj.getDouble("endX").toFloat().also { if (it < -MAX_COORD || it > MAX_COORD) return false }
                val endY = obj.getDouble("endY").toFloat().also { if (it < -MAX_COORD || it > MAX_COORD) return false }
                val scaleX = obj.getDouble("scaleX").toFloat().also { if (it <= 0f || it > 10f) return false }
                val scaleY = obj.getDouble("scaleY").toFloat().also { if (it <= 0f || it > 10f) return false }
                obj.getDouble("rotation").toFloat() // just verify it's a number

                // actionType must be valid
                val actionType = obj.getString("actionType")
                if (actionType !in listOf("tap", "swipe", "longpress")) return false

                // optional fields type check
                if (obj.has("designWidth")) {
                    val dw = obj.getInt("designWidth"); if (dw < 0 || dw > MAX_COORD) return false
                }
                if (obj.has("designHeight")) {
                    val dh = obj.getInt("designHeight"); if (dh < 0 || dh > MAX_COORD) return false
                }
                if (obj.has("iconPath") && obj.opt("iconPath") !is String) return false
                if (obj.has("presetId") && obj.opt("presetId") !is String) return false
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}