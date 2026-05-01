package com.example.dualmapper.manager.connection

object ConnectionManagerRegistry {
    private var current: ConnectionManager? = null

    fun register(manager: ConnectionManager) {
        current?.disconnect()
        current?.close()
        current = manager
    }

    fun getCurrent(): ConnectionManager? = current

    fun cleanup() {
        current?.disconnect()
        current?.close()
        current = null
    }
}