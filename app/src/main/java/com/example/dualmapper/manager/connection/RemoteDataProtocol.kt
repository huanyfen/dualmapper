package com.example.dualmapper.manager.connection

import org.json.JSONObject

object RemoteDataProtocol {
    const val TYPE_SYNC_LAYOUT = "sync_layout"
    const val TYPE_EXECUTE_MAPPING = "execute_mapping"
    const val TYPE_PING = "ping"
    const val TYPE_PONG = "pong"

    fun createSyncLayoutPacket(layoutJson: String): ByteArray {
        val json = JSONObject().apply { put("type", TYPE_SYNC_LAYOUT); put("data", layoutJson) }.toString()
        return json.toByteArray()
    }

    fun createExecuteMappingPacket(keyCode: Int): ByteArray {
        val json = JSONObject().apply {
            put("type", TYPE_EXECUTE_MAPPING)
            put("keyCode", keyCode)
        }.toString()
        return json.toByteArray()
    }

    fun parseExecuteMappingKeyCode(data: ByteArray): Int? {
        return try {
            val json = JSONObject(String(data))
            if (json.optString("type") == TYPE_EXECUTE_MAPPING) {
                json.optInt("keyCode", -1).takeIf { it >= 0 }
            } else null
        } catch (e: Exception) { null }
    }

    fun createPingPacket(): ByteArray {
        val json = JSONObject().put("type", TYPE_PING).toString()
        return json.toByteArray()
    }

    fun createPongPacket(): ByteArray {
        val json = JSONObject().put("type", TYPE_PONG).toString()
        return json.toByteArray()
    }

    fun isPing(data: ByteArray): Boolean {
        return try {
            val json = JSONObject(String(data))
            json.optString("type") == TYPE_PING
        } catch (e: Exception) { false }
    }

    fun isPong(data: ByteArray): Boolean {
        return try {
            val json = JSONObject(String(data))
            json.optString("type") == TYPE_PONG
        } catch (e: Exception) { false }
    }
}