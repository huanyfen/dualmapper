package com.example.dualmapper.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONObject

@Entity(
    tableName = "key_mappings",
    indices = [
        Index(value = ["keyCode"]),
        Index(value = ["presetId"])
    ]
)
data class KeyMappingEntity(
    @PrimaryKey val id: String,
    val label: String,
    val layoutX: Int,
    val layoutY: Int,
    val alpha: Float,
    val actionType: String,
    val targetX: Float,
    val targetY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long,
    val keyCode: Int = 0,
    val presetId: String? = null,
    val playerIndex: Int = 1,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val iconPath: String? = null,
    val designWidth: Int = 0,   // 新增：保存时的屏幕宽度
    val designHeight: Int = 0   // 新增：保存时的屏幕高度
) {
    companion object {
        fun fromJson(obj: JSONObject): KeyMappingEntity? {
            return try {
                KeyMappingEntity(
                    id = obj.getString("id"),
                    label = obj.getString("label"),
                    layoutX = obj.getInt("layoutX"),
                    layoutY = obj.getInt("layoutY"),
                    alpha = obj.getDouble("alpha").toFloat(),
                    actionType = obj.getString("actionType"),
                    targetX = obj.getDouble("targetX").toFloat(),
                    targetY = obj.getDouble("targetY").toFloat(),
                    endX = obj.getDouble("endX").toFloat(),
                    endY = obj.getDouble("endY").toFloat(),
                    durationMs = obj.getLong("durationMs"),
                    keyCode = obj.optInt("keyCode", 0),
                    presetId = obj.optString("presetId", null).takeIf { it.isNotEmpty() },
                    playerIndex = obj.optInt("playerIndex", 1),
                    scaleX = obj.optDouble("scaleX", 1.0).toFloat(),
                    scaleY = obj.optDouble("scaleY", 1.0).toFloat(),
                    rotation = obj.optDouble("rotation", 0.0).toFloat(),
                    iconPath = obj.optString("iconPath", null).takeIf { it.isNotEmpty() },
                    designWidth = obj.optInt("designWidth", 0),
                    designHeight = obj.optInt("designHeight", 0)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}