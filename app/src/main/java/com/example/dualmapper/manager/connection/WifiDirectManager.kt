package com.example.dualmapper.manager.connection

import android.Manifest
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.*
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.dualmapper.receiver.WifiDirectReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Wi-Fi Direct 连接管理器，每个进程独立单例。
 * 若应用使用多进程，各进程需自行调用 getInstance() 获取本进程实例。
 */
class WifiDirectManager private constructor(private val context: Context) : SecureSocketManager() {

    private val p2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: WifiDirectReceiver? = null
    private var isRegistered = false

    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices.asStateFlow()

    private var dataSocket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private val PORT = ConnectionConstants.LAN_PORT

    private var connectionTimeoutJob: Job? = null

    override fun getInputStream(): InputStream? = dataSocket?.getInputStream()
    override fun getOutputStream(): OutputStream? = dataSocket?.getOutputStream()

    companion object {
        @Volatile
        private var instance: WifiDirectManager? = null
        fun getInstance(context: Context): WifiDirectManager {
            return instance ?: synchronized(this) {
                instance ?: WifiDirectManager(context.applicationContext).also { instance = it }
            }
        }
    }

    @Synchronized
    private fun ensureInitialized() {
        if (!isRegistered || channel == null) {
            initialize()
        }
    }

    fun initialize() {
        if (isRegistered && channel != null) return
        channel = p2pManager.initialize(context, context.mainLooper, null)
        if (!isRegistered) {
            receiver = WifiDirectReceiver()
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            context.registerReceiver(receiver, filter)
            isRegistered = true
        }
    }

    fun isLocationPermissionOk(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun discoverPeers(onPeersAvailable: (List<WifiP2pDevice>) -> Unit) {
        ensureInitialized()
        if (!isLocationPermissionOk()) return
        val ch = channel ?: return
        p2pManager.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                p2pManager.requestPeers(ch) { peers ->
                    scope.launch {
                        val devices = peers.deviceList.map { device ->
                            DeviceInfo(
                                device.deviceAddress,
                                device.deviceName ?: "未知设备",
                                ConnectionType.WIFI_DIRECT,
                                device.deviceAddress
                            )
                        }
                        _discoveredDevices.value = devices
                        onPeersAvailable(peers.deviceList.toList())
                    }
                }
            }
            override fun onFailure(reason: Int) {
                Log.e("WifiDirect", "Discover failed: $reason")
            }
        })
    }

    fun requestConnectionInfo(callback: (WifiP2pInfo) -> Unit) {
        ensureInitialized()
        channel?.let { ch ->
            p2pManager.requestConnectionInfo(ch) { info -> callback(info) }
        }
    }

    override fun startDiscovery() {
        ensureInitialized()
        discoverPeers { }
    }

    override fun stopDiscovery() {
        ensureInitialized()
        channel?.let { p2pManager.stopPeerDiscovery(it, null) }
    }

    override fun connect(device: DeviceInfo) {
        ensureInitialized()
        currentDevice = device
        reconnectAttempts = 0
        totalReconnectCount = 0
        internalConnect(device)
    }

    private fun internalConnect(device: DeviceInfo) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
            groupOwnerIntent = 0
        }
        channel?.let { ch ->
            p2pManager.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _connectionState.value = ConnectionState.CONNECTING
                    startConnectionTimeout()
                }
                override fun onFailure(reason: Int) {
                    _connectionState.value = ConnectionState.ERROR
                    scheduleReconnect { attemptWifiReconnect() }
                }
            })
        }
    }

    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(ConnectionConstants.SERVER_ACCEPT_TIMEOUT)
            if (_connectionState.value == ConnectionState.CONNECTING) {
                Log.e("WifiDirect", "Connection timed out")
                _connectionState.value = ConnectionState.ERROR
                disconnect()
                scheduleReconnect { attemptWifiReconnect() }
            }
        }
    }

    private fun cancelConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
    }

    override fun disconnect() {
        super.disconnect()
        cancelConnectionTimeout()
        dataSocket?.close()
        serverSocket?.close()
        channel?.let { p2pManager.removeGroup(it, null) }
        _connectionState.value = ConnectionState.IDLE
        currentDevice = null
        reconnectAttempts = 0
        totalReconnectCount = 0
    }

    fun handleConnectionInfo(info: WifiP2pInfo) {
        cancelConnectionTimeout()
        if (info.groupFormed) {
            scope.launch {
                try {
                    if (info.isGroupOwner) {
                        serverSocket = ServerSocket(PORT)
                        serverSocket?.soTimeout = ConnectionConstants.SERVER_ACCEPT_TIMEOUT.toInt()
                        dataSocket = serverSocket!!.accept()
                    } else {
                        dataSocket = Socket(info.groupOwnerAddress.hostAddress, PORT)
                    }
                    dataSocket?.soTimeout = ConnectionConstants.DEFAULT_SOCKET_TIMEOUT.toInt()
                    SecureConnectionHelper.performKeyExchange(
                        dataSocket!!.getInputStream(),
                        dataSocket!!.getOutputStream()
                    )
                    _connectionState.value = ConnectionState.CONNECTED
                    missedHeartbeats.set(0)
                    reconnectAttempts = 0
                    totalReconnectCount = 0
                    startDataReceiver()
                    startHeartbeat()
                } catch (e: IOException) {
                    Log.e("WifiDirect", "Socket connection failed", e)
                    _connectionState.value = ConnectionState.ERROR
                    scheduleReconnect { attemptWifiReconnect() }
                }
            }
        } else {
            Log.e("WifiDirect", "Group formation failed")
            _connectionState.value = ConnectionState.ERROR
            scheduleReconnect { attemptWifiReconnect() }
        }
    }

    fun onPeersChanged() {
        discoverPeers { }
    }

    override fun handleConnectionLost() {
        super.handleConnectionLost()
        dataSocket?.close()
        serverSocket?.close()
        scheduleReconnect { attemptWifiReconnect() }
    }

    private suspend fun attemptWifiReconnect(): Boolean {
        ensureInitialized()
        val device = currentDevice ?: return false
        return try {
            withTimeout(ConnectionConstants.SERVER_ACCEPT_TIMEOUT) {
                val found = suspendCancellableCoroutine<Boolean> { cont ->
                    discoverPeers { peers ->
                        cont.resume(peers.any { it.deviceAddress == device.address }, null)
                    }
                }
                if (!found) return@withTimeout false
                internalConnect(device)
                connectionState.first { it == ConnectionState.CONNECTED }
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun cleanup() {
        disconnect()
        if (isRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) { /* 已注销 */ }
            isRegistered = false
        }
        channel?.close()
        channel = null
        // 不再置空 instance，避免多线程空指针
    }

    override fun close() {
        cleanup()
        super.close()
        scope.cancel()
    }
}