package com.example.dualmapper.manager.connection

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

abstract class SecureSocketManager : ConnectionManager {

    protected val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    protected val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override val isConnected: Boolean
        get() = _connectionState.value == ConnectionState.CONNECTED

    protected var dataReceivedListener: ((ByteArray) -> Unit)? = null
    private var receiveJob: Job? = null
    private var heartbeatJob: Job? = null

    protected val missedHeartbeats = AtomicInteger(0)

    protected var currentDevice: DeviceInfo? = null
    protected var reconnectAttempts = 0
    protected open val maxReconnectAttempts: Int = ConnectionConstants.MAX_RECONNECT_ATTEMPTS
    protected var totalReconnectCount = 0
    protected open val maxTotalReconnect: Int = ConnectionConstants.MAX_TOTAL_RECONNECT
    private var reconnectJob: Job? = null
    private val reconnectLock = Any()

    protected abstract fun getInputStream(): InputStream?
    protected abstract fun getOutputStream(): OutputStream?

    override fun setOnDataReceivedListener(listener: ((ByteArray) -> Unit)?) {
        dataReceivedListener = listener
    }

    protected fun startDataReceiver() {
        receiveJob = scope.launch {
            val input = getInputStream() ?: return@launch
            try {
                while (isActive) {
                    val data = SecureConnectionHelper.receiveDecrypted(input) ?: break
                    missedHeartbeats.set(0)
                    when {
                        RemoteDataProtocol.isPing(data) -> sendPong()
                        RemoteDataProtocol.isPong(data) -> { /* heartbeat response */ }
                        else -> dataReceivedListener?.invoke(data)
                    }
                }
            } catch (e: IOException) {
                // disconnection
            } finally {
                handleConnectionLost()
            }
        }
    }

    protected fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(ConnectionConstants.HEARTBEAT_INTERVAL)
                try {
                    sendPing()
                    if (missedHeartbeats.incrementAndGet() >= ConnectionConstants.MISSED_HEARTBEAT_THRESHOLD) {
                        handleConnectionLost()
                        break
                    }
                } catch (e: IOException) {
                    handleConnectionLost()
                    break
                }
            }
        }
    }

    override fun send(data: ByteArray) {
        scope.launch {
            try {
                getOutputStream()?.let { SecureConnectionHelper.sendEncrypted(it, data) }
            } catch (e: IOException) { /* silent */ }
        }
    }

    protected fun sendPing() {
        getOutputStream()?.let { SecureConnectionHelper.sendEncrypted(it, RemoteDataProtocol.createPingPacket()) }
    }

    protected fun sendPong() {
        getOutputStream()?.let { SecureConnectionHelper.sendEncrypted(it, RemoteDataProtocol.createPongPacket()) }
    }

    protected open fun handleConnectionLost() {
        if (_connectionState.value == ConnectionState.DISCONNECTED) return
        _connectionState.value = ConnectionState.DISCONNECTED
        receiveJob?.cancel()
        heartbeatJob?.cancel()
    }

    protected fun scheduleReconnect(connectAction: suspend () -> Boolean) {
        synchronized(reconnectLock) {
            if (reconnectJob?.isActive == true) return
            reconnectJob = scope.launch {
                val device = currentDevice ?: return@launch
                if (reconnectAttempts >= maxReconnectAttempts || totalReconnectCount >= maxTotalReconnect) {
                    _connectionState.value = ConnectionState.ERROR
                    return@launch
                }
                _connectionState.value = ConnectionState.RECONNECTING
                val delayMs = ConnectionConstants.RECONNECT_BASE_DELAY * (reconnectAttempts + 1)
                delay(delayMs)
                reconnectAttempts++
                totalReconnectCount++
                val success = try {
                    withTimeoutOrNull(ConnectionConstants.DEFAULT_SOCKET_TIMEOUT * 3) {
                        connectAction()
                    } ?: false
                } catch (e: Exception) {
                    false
                }
                if (success) {
                    reconnectAttempts = 0
                    totalReconnectCount = 0
                } else {
                    scheduleReconnect(connectAction)
                }
            }
        }
    }

    override fun disconnect() {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        receiveJob?.cancel()
        _connectionState.value = ConnectionState.IDLE
    }

    override fun close() {
        disconnect()
        // Subclasses should call scope.cancel() after own cleanup
    }
}