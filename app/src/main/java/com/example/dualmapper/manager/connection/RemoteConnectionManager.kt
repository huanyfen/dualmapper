package com.example.dualmapper.manager.connection

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bitlet.weupnp.GatewayDevice
import org.bitlet.weupnp.GatewayDiscover
import java.io.IOException
import java.net.*

class RemoteConnectionManager(private val context: Context) : SecureSocketManager() {

    private val PORT = ConnectionConstants.REMOTE_PORT

    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var gateway: GatewayDevice? = null
    private var isPortMapped = false

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "remote_auth_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    @Volatile
    private var authToken: String = securePrefs.getString("auth_token", null) ?: run {
        val newToken = java.util.UUID.randomUUID().toString()
        securePrefs.edit().putString("auth_token", newToken).apply()
        newToken
    }

    override fun getInputStream(): java.io.InputStream? = clientSocket?.getInputStream()
    override fun getOutputStream(): java.io.OutputStream? = clientSocket?.getOutputStream()

    fun getAuthToken(): String = authToken

    fun resetAuthToken() {
        val newToken = java.util.UUID.randomUUID().toString()
        securePrefs.edit().putString("auth_token", newToken).apply()
        authToken = newToken
        disconnect()
        Log.d("Remote", "Auth token has been reset")
    }

    override fun startDiscovery() {}
    override fun stopDiscovery() {}

    suspend fun startHostMode(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
                soTimeout = ConnectionConstants.SERVER_ACCEPT_TIMEOUT.toInt()
            }
            gateway = withTimeoutOrNull(ConnectionConstants.DEFAULT_SOCKET_TIMEOUT) {
                GatewayDiscover().discover().firstOrNull()
            }
            isPortMapped = gateway?.addPortMapping(PORT, PORT, InetAddress.getLocalHost().hostAddress, "DualMapper", "TCP") ?: false
            val localIp = getLocalIpAddress()
            val info = if (isPortMapped) {
                val publicIp = getPublicIp() ?: localIp
                "公网地址: $publicIp:$PORT\n配对码: $authToken"
            } else {
                "UPnP 失败，请手动映射端口 $PORT，或使用局域网地址 $localIp:$PORT\n配对码: $authToken"
            }
            acceptClients()
            Pair(true, info)
        } catch (e: Exception) {
            Pair(false, "启动失败: ${e.message}")
        }
    }

    private fun acceptClients() {
        scope.launch {
            while (serverSocket != null && !serverSocket!!.isClosed) {
                val socket = try {
                    withTimeout(ConnectionConstants.DEFAULT_SOCKET_TIMEOUT) { serverSocket?.accept() }
                } catch (e: TimeoutCancellationException) {
                    continue
                } catch (e: IOException) {
                    break
                }
                if (socket == null) continue

                scope.launch {
                    try {
                        socket.soTimeout = ConnectionConstants.DEFAULT_SOCKET_TIMEOUT.toInt()
                        SecureConnectionHelper.performKeyExchange(socket.getInputStream(), socket.getOutputStream())
                        if (authenticateClientEncrypted(socket)) {
                            clientSocket = socket
                            _connectionState.value = ConnectionState.CONNECTED
                            missedHeartbeats.set(0)          // 使用 set 而非直接赋值
                            startDataReceiver()
                            startHeartbeat()
                            serverSocket?.close()
                            serverSocket = null
                        } else {
                            Log.e("Remote", "Token authentication failed")
                            socket.close()
                        }
                    } catch (e: IOException) {
                        Log.e("Remote", "Key exchange or auth failed", e)
                        try { socket.close() } catch (_: IOException) {}
                    }
                }
            }
        }
    }

    private suspend fun authenticateClientEncrypted(socket: Socket): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeout(ConnectionConstants.DEFAULT_SOCKET_TIMEOUT) {
                val received = SecureConnectionHelper.receiveDecrypted(socket.getInputStream())
                received != null && String(received) == authToken
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun connect(device: DeviceInfo) {
        scope.launch {
            try {
                withTimeout(ConnectionConstants.DEFAULT_SOCKET_TIMEOUT * 3) {
                    val addressParts = device.address.split(":")
                    val ip = addressParts[0]
                    val port = if (addressParts.size > 1) addressParts[1].toInt() else PORT
                    val socket = Socket()
                    socket.soTimeout = ConnectionConstants.DEFAULT_SOCKET_TIMEOUT.toInt()
                    socket.connect(InetSocketAddress(ip, port), ConnectionConstants.DEFAULT_SOCKET_TIMEOUT.toInt())
                    clientSocket = socket

                    SecureConnectionHelper.performKeyExchange(
                        socket.getInputStream(),
                        socket.getOutputStream()
                    )
                    SecureConnectionHelper.sendEncrypted(
                        socket.getOutputStream(),
                        authToken.toByteArray()
                    )

                    _connectionState.value = ConnectionState.CONNECTED
                    missedHeartbeats.set(0)          // 使用 set 而非直接赋值
                    startDataReceiver()
                    startHeartbeat()
                }
            } catch (e: TimeoutCancellationException) {
                _connectionState.value = ConnectionState.ERROR
                Log.e("Remote", "Connection timed out")
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                Log.e("Remote", "Connection failed", e)
            }
        }
    }

    override fun disconnect() {
        super.disconnect()
        try { clientSocket?.close() } catch (_: IOException) {}
        clientSocket = null
        try { serverSocket?.close() } catch (_: IOException) {}
        serverSocket = null
        if (isPortMapped && gateway != null) gateway?.deletePortMapping(PORT, "TCP")
        _connectionState.value = ConnectionState.IDLE
    }

    private fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress
        } catch (e: Exception) { null }
    }

    private suspend fun getPublicIp(): String? = withContext(Dispatchers.IO) {
        try {
            java.net.URL("https://api.ipify.org").openStream().bufferedReader().readText()
        } catch (e: Exception) { null }
    }

    override fun close() {
        disconnect()
        super.close()
    }
}