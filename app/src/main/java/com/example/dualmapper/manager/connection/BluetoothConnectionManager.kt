package com.example.dualmapper.manager.connection

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothConnectionManager(private val context: Context) : SecureSocketManager() {

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val MY_UUID = UUID.fromString(ConnectionConstants.BLUETOOTH_UUID_STRING)

    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices.asStateFlow()

    private val discoveredMap = linkedMapOf<String, DeviceInfo>()

    private var serverThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    private var isReceiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { addDevice(it) }
                }
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.setPairingConfirmation(true)
                    abortBroadcast()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _connectionState.value = ConnectionState.IDLE
                }
            }
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun addDevice(device: BluetoothDevice) {
        val name = try {
            device.name ?: "未知设备"
        } catch (e: SecurityException) {
            context.getString(R.string.bluetooth_permission_required)
        }
        val info = DeviceInfo(device.address, name, ConnectionType.BLUETOOTH, device.address)
        if (discoveredMap.put(device.address, info) == null) {
            _discoveredDevices.value = discoveredMap.values.toList()
        }
    }

    override fun getInputStream(): InputStream? = connectedThread?.input
    override fun getOutputStream(): OutputStream? = connectedThread?.output

    private fun startServer() {
        serverThread?.cancel()
        serverThread = AcceptThread().apply { start() }
    }

    private fun registerReceiverIfNeeded() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                priority = 100
            }
            context.registerReceiver(receiver, filter)
            isReceiverRegistered = true
        }
    }

    private fun unregisterReceiverIfNeeded() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // 可能已注销
            }
            isReceiverRegistered = false
        }
    }

    override fun startDiscovery() {
        if (!hasPermissions()) {
            _connectionState.value = ConnectionState.ERROR
            return
        }
        discoveredMap.clear()
        _discoveredDevices.value = emptyList()
        startServer()
        registerReceiverIfNeeded()
        try {
            bluetoothAdapter?.startDiscovery()
            _connectionState.value = ConnectionState.DISCOVERING
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.ERROR
            Log.e("Bluetooth", "Missing permission for discovery", e)
        }
    }

    override fun stopDiscovery() {
        bluetoothAdapter?.cancelDiscovery()
        unregisterReceiverIfNeeded()
    }

    override fun connect(device: DeviceInfo) {
        if (!hasConnectPermission()) {
            _connectionState.value = ConnectionState.ERROR
            Log.e("Bluetooth", "Missing BLUETOOTH_CONNECT permission")
            return
        }
        currentDevice = device
        reconnectAttempts = 0
        totalReconnectCount = 0
        val btDevice = bluetoothAdapter?.getRemoteDevice(device.address) ?: return
        connectThread?.cancel()
        connectThread = ConnectThread(btDevice).apply { start() }
    }

    override fun handleConnectionLost() {
        super.handleConnectionLost()
        connectedThread?.cancel()
        connectedThread = null
        scheduleReconnect { attemptReconnect() }
        startServer()
    }

    private suspend fun attemptReconnect(): Boolean {
        val found = withContext(Dispatchers.Main) {
            try {
                registerReceiverIfNeeded()
                startServer()
                bluetoothAdapter?.startDiscovery()
                withTimeoutOrNull(ConnectionConstants.DEFAULT_SOCKET_TIMEOUT) {
                    suspendCancellableCoroutine<Boolean> { cont ->
                        val targetAddress = currentDevice?.address
                        val scanReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                if (BluetoothDevice.ACTION_FOUND == intent.action) {
                                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                        intent.getParcelableExtra(
                                            BluetoothDevice.EXTRA_DEVICE,
                                            BluetoothDevice::class.java
                                        )
                                    else @Suppress("DEPRECATION")
                                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                    if (device?.address == targetAddress) {
                                        cont.resume(true, null)
                                    }
                                }
                            }
                        }
                        context.registerReceiver(scanReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
                        cont.invokeOnCancellation {
                            try {
                                context.unregisterReceiver(scanReceiver)
                            } catch (_: Exception) {
                            }
                            try {
                                bluetoothAdapter?.cancelDiscovery()
                            } catch (_: Exception) {
                            }
                        }
                    }
                } ?: false
            } catch (e: Exception) {
                false
            } finally {
                try {
                    bluetoothAdapter?.cancelDiscovery()
                } catch (_: Exception) {
                }
            }
        }
        if (!found) return false

        val device = currentDevice ?: return false
        return try {
            withTimeout(ConnectionConstants.DEFAULT_SOCKET_TIMEOUT * 3) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val btDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                    val thread = ConnectThread(btDevice!!)
                    connectThread = thread
                    thread.onConnected = { cont.resume(true, null) }
                    thread.onFailed = { cont.resume(false, null) }
                    thread.start()
                    cont.invokeOnCancellation {
                        try {
                            thread.cancel()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun disconnect() {
        super.disconnect()
    }

    override fun close() {
        stopDiscovery()
        super.disconnect()
        serverThread?.cancel()
        serverThread = null
        unregisterReceiverIfNeeded()
        scope.cancel()
    }

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? = try {
            bluetoothAdapter?.listenUsingRfcommWithServiceRecord("DualMapper", MY_UUID)
        } catch (e: IOException) { null }

        override fun run() {
            mmServerSocket?.let { serverSocket ->
                try {
                    val socket = serverSocket.accept()
                    manageConnectedSocket(socket)
                } catch (e: IOException) {
                } finally {
                    try { serverSocket.close() } catch (e: IOException) {}
                }
            }
        }

        fun cancel() {
            try { mmServerSocket?.close() } catch (e: IOException) {}
        }
    }

    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
        var onConnected: (() -> Unit)? = null
        var onFailed: (() -> Unit)? = null

        override fun run() {
            try {
                mmSocket.connect()
                manageConnectedSocket(mmSocket)
                onConnected?.invoke()
            } catch (e: IOException) {
                _connectionState.value = ConnectionState.ERROR
                onFailed?.invoke()
                try { mmSocket.close() } catch (_: IOException) {}
            }
        }

        fun cancel() {
            try { mmSocket.close() } catch (e: IOException) {}
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        val input: InputStream = socket.inputStream
        val output: OutputStream = socket.outputStream
        var isRunning = true

        override fun run() {
            try {
                socket.soTimeout = ConnectionConstants.DEFAULT_SOCKET_TIMEOUT.toInt()
                SecureConnectionHelper.performKeyExchange(input, output)
                _connectionState.value = ConnectionState.CONNECTED
                missedHeartbeats.set(0)
                startDataReceiver()
                startHeartbeat()
            } catch (e: IOException) {
                _connectionState.value = ConnectionState.ERROR
                handleConnectionLost()
            }
        }

        fun cancel() {
            isRunning = false
            try { socket.close() } catch (_: IOException) {}
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket) {
        connectedThread?.cancel()
        connectedThread = ConnectedThread(socket).apply { start() }
    }
}