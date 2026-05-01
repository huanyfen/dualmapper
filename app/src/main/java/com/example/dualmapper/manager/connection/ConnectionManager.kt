package com.example.dualmapper.manager.connection

import kotlinx.coroutines.flow.StateFlow

data class DeviceInfo(
    val id: String,
    val name: String,
    val type: ConnectionType,
    val address: String = ""
)

enum class ConnectionType { BLUETOOTH, WIFI_DIRECT, LAN, REMOTE }

enum class ConnectionState {
    IDLE, DISCOVERING, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED, ERROR
}

interface ConnectionManager : AutoCloseable {
    val discoveredDevices: StateFlow<List<DeviceInfo>>
    val connectionState: StateFlow<ConnectionState>
    val isConnected: Boolean

    fun startDiscovery()
    fun stopDiscovery()
    fun connect(device: DeviceInfo)
    fun disconnect()
    fun send(data: ByteArray)
    fun setOnDataReceivedListener(listener: ((ByteArray) -> Unit)?)
}