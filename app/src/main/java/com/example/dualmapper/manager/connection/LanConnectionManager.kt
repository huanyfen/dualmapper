package com.example.dualmapper.manager.connection

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.ConcurrentHashMap

class LanConnectionManager(private val context: Context) : SecureSocketManager() {

    private val PORT = ConnectionConstants.LAN_PORT
    private val BROADCAST_PORT = ConnectionConstants.LAN_BROADCAST_PORT
    private val SERVICE_ID = ConnectionConstants.LAN_SERVICE_ID
    private val BROADCAST_INTERVAL = ConnectionConstants.LAN_BROADCAST_INTERVAL

    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var listenSocket: DatagramSocket? = null
    private var broadcastSocket: DatagramSocket? = null
    private var broadcastJob: Job? = null
    private var isDiscovering = false
    private val discoveredMap = ConcurrentHashMap<String, DeviceInfo>()
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Volatile
    private var isListening = false
    private val listeningLock = Any()

    override fun getInputStream(): InputStream? = clientSocket?.getInputStream()
    override fun getOutputStream(): OutputStream? = clientSocket?.getOutputStream()

    private fun ensureListening() {
        synchronized(listeningLock) {
            if (isListening) return
            isListening = true
        }
        startServer()
        startBroadcastListener()
    }

    private fun resetListening() {
        synchronized(listeningLock) {
            isListening = false
            try { serverSocket?.close() } catch (_: IOException) {}
            serverSocket = null
            try { listenSocket?.close() } catch (_: IOException) {}
            listenSocket = null
        }
    }

    private fun getLocalIpAddress(): String? {
        val ip = wifiManager.connectionInfo.ipAddress
        return if (ip != 0) {
            String.format("%d.%d.%d.%d", ip and 0xFF, ip shr 8 and 0xFF, ip shr 16 and 0xFF, ip shr 24 and 0xFF)
        } else null
    }

    private fun getBroadcastAddress(): InetAddress? {
        val dhcp = wifiManager.dhcpInfo
        val broadcast = dhcp.ipAddress and dhcp.netmask or dhcp.netmask.inv()
        val quads = ByteArray(4)
        for (k in 0..3) {
            quads[k] = (broadcast shr k * 8 and 0xFF).toByte()
        }
        return InetAddress.getByAddress(quads)
    }

    private fun startServer() {
        try { serverSocket?.close() } catch (_: IOException) {}
        serverSocket = null

        scope.launch {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(PORT))
                serverSocket = socket
                socket.soTimeout = ConnectionConstants.SERVER_ACCEPT_TIMEOUT.toInt()
                while (isActive) {
                    val client = try {
                        socket.accept()
                    } catch (e: SocketTimeoutException) {
                        if (!isActive) break
                        continue
                    } catch (e: IOException) {
                        break
                    }
                    if (client != null) {
                        manageConnection(client)
                    }
                }
            } catch (e: IOException) { /* ignore */ }
        }
    }

    private fun startBroadcastListener() {
        try { listenSocket?.close() } catch (_: IOException) {}
        listenSocket = null

        scope.launch {
            try {
                val socket = DatagramSocket(null)
                socket.reuseAddress = true
                socket.broadcast = true
                socket.bind(InetSocketAddress(BROADCAST_PORT))
                socket.soTimeout = ConnectionConstants.DEFAULT_SOCKET_TIMEOUT.toInt()
                listenSocket = socket
                val buffer = ByteArray(256)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    } catch (e: IOException) {
                        break
                    }
                    val message = String(packet.data, 0, packet.length)
                    if (message.startsWith(SERVICE_ID)) {
                        val parts = message.split("|")
                        val name = if (parts.size > 1) parts[1] else "未知"
                        val ip = packet.address.hostAddress ?: continue
                        if (ip == getLocalIpAddress()) continue
                        val device = DeviceInfo(ip, name, ConnectionType.LAN, ip)
                        discoveredMap[ip] = device
                        _discoveredDevices.value = discoveredMap.values.toList()
                    }
                }
            } catch (e: IOException) { /* ignore */ }
        }
    }

    override fun startDiscovery() {
        ensureListening()
        isDiscovering = true
        broadcastJob = scope.launch {
            while (isDiscovering) {
                sendBroadcast()
                delay(BROADCAST_INTERVAL)
            }
        }
    }

    private suspend fun sendBroadcast() {
        try {
            val localIp = getLocalIpAddress() ?: return
            val broadcastAddr = getBroadcastAddress() ?: return
            val message = "$SERVICE_ID|${Build.MODEL}|$localIp"
            val data = message.toByteArray()
            if (broadcastSocket == null || broadcastSocket?.isClosed == true) {
                broadcastSocket = DatagramSocket().apply {
                    reuseAddress = true
                    broadcast = true
                }
            }
            val socket = broadcastSocket ?: return
            repeat(3) {
                val packet = DatagramPacket(data, data.size, broadcastAddr, BROADCAST_PORT)
                socket.send(packet)
                delay(100)
            }
        } catch (e: Exception) { /* ignore */ }
    }

    override fun stopDiscovery() {
        isDiscovering = false
        broadcastJob?.cancel()
    }

    override fun connect(device: DeviceInfo) {
        ensureListening()
        currentDevice = device
        reconnectAttempts = 0
        totalReconnectCount = 0
        scope.launch {
            try {
                val socket = Socket()
                socket.soTimeout = ConnectionConstants.DEFAULT_SOCKET_TIMEOUT.toInt()
                socket.connect(InetSocketAddress(device.address, PORT), ConnectionConstants.DEFAULT_SOCKET_TIMEOUT.toInt())
                clientSocket = socket
                manageConnection(socket)
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                handleConnectionLost()
            }
        }
    }

    private suspend fun manageConnection(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                SecureConnectionHelper.performKeyExchange(socket.getInputStream(), socket.getOutputStream())
                clientSocket = socket
                _connectionState.value = ConnectionState.CONNECTED
                missedHeartbeats.set(0)
                startDataReceiver()
                startHeartbeat()
                // 连接成功后停止广播以节省电量
                stopDiscovery()
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                handleConnectionLost()
            }
        }
    }

    override fun handleConnectionLost() {
        super.handleConnectionLost()
        try { clientSocket?.close() } catch (_: IOException) {}
        clientSocket = null
        scheduleReconnect { attemptReconnect() }
    }

    private suspend fun attemptReconnect(): Boolean {
        val device = currentDevice ?: return false
        return try {
            withTimeout(ConnectionConstants.DEFAULT_SOCKET_TIMEOUT * 3) {
                val socket = Socket()
                socket.soTimeout = ConnectionConstants.DEFAULT_SOCKET_TIMEOUT.toInt()
                socket.connect(InetSocketAddress(device.address, PORT), ConnectionConstants.DEFAULT_SOCKET_TIMEOUT.toInt())
                clientSocket = socket
                manageConnection(socket)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun disconnect() {
        stopDiscovery()
        super.disconnect()
        try { clientSocket?.close() } catch (_: IOException) {}
        clientSocket = null
        resetListening()
    }

    override fun close() {
        stopDiscovery()
        super.disconnect()
        try { clientSocket?.close() } catch (_: IOException) {}
        clientSocket = null
        try { broadcastSocket?.close() } catch (_: IOException) {}
        broadcastSocket = null
        resetListening()
        scope.cancel()
    }
}