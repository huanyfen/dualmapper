package com.example.dualmapper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.view.KeyEvent
import com.example.dualmapper.BuildConfig
import com.example.dualmapper.data.AppDatabase
import com.example.dualmapper.manager.connection.ConnectionManagerRegistry
import com.example.dualmapper.manager.connection.RemoteDataProtocol
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MappedAccessibilityService : AccessibilityService() {

    @Inject lateinit var db: AppDatabase

    companion object {
        var instance: MappedAccessibilityService? = null
        private const val MAX_DEBOUNCE_SIZE = 150
        private const val DEBOUNCE_MS = 150L
    }

    data class KeyMappingAction(
        val actionType: String,
        val targetX: Float,
        val targetY: Float,
        val endX: Float,
        val endY: Float,
        val durationMs: Long,
        val playerIndex: Int = 1
    )

    private var isKeyMappingEnabled = true
    private var isRemoteMappingMode = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _keyMappingCache = MutableStateFlow<Map<Int, KeyMappingAction>>(emptyMap())
    val keyMappingCache: StateFlow<Map<Int, KeyMappingAction>> = _keyMappingCache.asStateFlow()

    private val debounceLock = Any()
    private val lastExecuteTime = LinkedHashMap<Int, Long>() // LRU order

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "ACTION_KEY_MAPPING_TOGGLE" -> {
                    isKeyMappingEnabled = intent.getBooleanExtra("enabled", true)
                }
                "ACTION_REMOTE_MAPPING_TOGGLE" -> {
                    isRemoteMappingMode = intent.getBooleanExtra("enabled", false)
                }
            }
        }
    }

    private val simulateKeyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.dualmapper.SIMULATE_KEY") {
                val keyCode = intent.getIntExtra("keyCode", 0)
                if (keyCode != 0) {
                    val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                    val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                    onKeyEvent(downEvent)
                    onKeyEvent(upEvent)
                }
            }
        }
    }

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                loadKeyMappings()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        loadKeyMappings()
        registerReceiver(toggleReceiver, IntentFilter().apply {
            addAction("ACTION_KEY_MAPPING_TOGGLE")
            addAction("ACTION_REMOTE_MAPPING_TOGGLE")
        })
        registerReceiver(configReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        if (BuildConfig.DEBUG) {
            registerReceiver(simulateKeyReceiver, IntentFilter("com.example.dualmapper.SIMULATE_KEY"))
        }
    }

    private fun loadKeyMappings() {
        serviceScope.launch {
            val mappings = db.keyMappingDao().getAll()
            val metrics = resources.displayMetrics
            val currentWidth = metrics.widthPixels.toFloat()
            val currentHeight = metrics.heightPixels.toFloat()

            val newCache = mappings.filter { it.keyCode != 0 }.associate {
                val designW = if (it.designWidth > 0) it.designWidth.toFloat() else currentWidth
                val designH = if (it.designHeight > 0) it.designHeight.toFloat() else currentHeight
                val scaleX = currentWidth / designW
                val scaleY = currentHeight / designH

                it.keyCode to KeyMappingAction(
                    it.actionType,
                    (it.targetX * scaleX).coerceIn(0f, currentWidth),
                    (it.targetY * scaleY).coerceIn(0f, currentHeight),
                    (it.endX * scaleX).coerceIn(0f, currentWidth),
                    (it.endY * scaleY).coerceIn(0f, currentHeight),
                    it.durationMs,
                    it.playerIndex
                )
            }
            _keyMappingCache.value = newCache

            synchronized(debounceLock) {
                lastExecuteTime.keys.retainAll(newCache.keys)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.onKeyEvent(event)

        if (isRemoteMappingMode) {
            val manager = ConnectionManagerRegistry.getCurrent()
            if (manager != null && manager.isConnected) {
                val packet = RemoteDataProtocol.createExecuteMappingPacket(event.keyCode)
                manager.send(packet)
                return true
            }
        }

        if (isKeyMappingEnabled) {
            val action = _keyMappingCache.value[event.keyCode] ?: return super.onKeyEvent(event)
            val now = System.currentTimeMillis()
            synchronized(debounceLock) {
                val last = lastExecuteTime[event.keyCode]
                if (last == null || (now - last) >= DEBOUNCE_MS) {
                    lastExecuteTime.remove(event.keyCode) // refresh LRU order
                    lastExecuteTime[event.keyCode] = now
                    if (lastExecuteTime.size > MAX_DEBOUNCE_SIZE) {
                        val iter = lastExecuteTime.entries.iterator()
                        var removed = 0
                        while (iter.hasNext() && removed < lastExecuteTime.size - MAX_DEBOUNCE_SIZE / 2) {
                            iter.next()
                            iter.remove()
                            removed++
                        }
                    }
                    executeMapping(action)
                }
            }
            return true
        }
        return super.onKeyEvent(event)
    }

    private fun executeMapping(action: KeyMappingAction) {
        when (action.actionType) {
            "tap" -> performTap(action.targetX, action.targetY, action.durationMs)
            "longpress" -> performTap(
                action.targetX,
                action.targetY,
                action.durationMs.coerceAtLeast(500)
            )
            "swipe" -> performSwipe(
                action.targetX, action.targetY,
                action.endX, action.endY,
                action.durationMs
            )
        }
    }

    fun onRemoteMappingReceived(keyCode: Int) {
        val action: KeyMappingAction? = _keyMappingCache.value[keyCode]
        if (action != null) {
            val handler = android.os.Handler(mainLooper)
            handler.post { executeMapping(action) }
        }
    }

    fun performTap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun performSwipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun refreshMappings() {
        loadKeyMappings()
    }

    fun refreshSingleMapping(keyCode: Int) {
        serviceScope.launch {
            val mapping = db.keyMappingDao().getByKeyCode(keyCode)
            val current = _keyMappingCache.value.toMutableMap()
            if (mapping != null && mapping.keyCode != 0) {
                val metrics = resources.displayMetrics
                val currentWidth = metrics.widthPixels.toFloat()
                val currentHeight = metrics.heightPixels.toFloat()
                val designW =
                    if (mapping.designWidth > 0) mapping.designWidth.toFloat() else currentWidth
                val designH =
                    if (mapping.designHeight > 0) mapping.designHeight.toFloat() else currentHeight
                val scaleX = currentWidth / designW
                val scaleY = currentHeight / designH
                current[keyCode] = KeyMappingAction(
                    mapping.actionType,
                    (mapping.targetX * scaleX).coerceIn(0f, currentWidth),
                    (mapping.targetY * scaleY).coerceIn(0f, currentHeight),
                    (mapping.endX * scaleX).coerceIn(0f, currentWidth),
                    (mapping.endY * scaleY).coerceIn(0f, currentHeight),
                    mapping.durationMs,
                    mapping.playerIndex
                )
            } else {
                current.remove(keyCode)
            }
            _keyMappingCache.value = current

            synchronized(debounceLock) {
                lastExecuteTime.remove(keyCode)
            }
        }
    }

    override fun onDestroy() {
        instance = null
        unregisterReceiver(toggleReceiver)
        unregisterReceiver(configReceiver)
        if (BuildConfig.DEBUG) unregisterReceiver(simulateKeyReceiver)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: android.accessibilityservice.AccessibilityEvent?) {}
    override fun onInterrupt() {}
}