package com.example.dualmapper.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.dualmapper.KeySettingsDialogActivity
import com.example.dualmapper.R
import com.example.dualmapper.RemapActivity
import com.example.dualmapper.data.AppDatabase
import com.example.dualmapper.data.KeyMappingEntity
import com.example.dualmapper.manager.edit.EditModeManager
import com.example.dualmapper.util.DeviceTypeHelper
import com.example.dualmapper.util.IconManager
import com.example.dualmapper.view.MappedKeyView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class FloatingService : Service() {

    @Inject
    lateinit var db: AppDatabase

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var iconImageView: ImageView
    private lateinit var closeBtn: ImageView
    private lateinit var editBtn: ImageView
    private lateinit var addBtn: ImageView
    private var editModeManager: EditModeManager? = null
    private var isEditMode = false
    private var isTvMode = false
    private var isViewAdded = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val displayMetrics = android.util.DisplayMetrics()

    private var keyContainer: FrameLayout? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val saveDebounceJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var positionSaveDebounceJob: Job? = null

    private data class KeySnapshot(
        val keyId: String, val label: String,
        val layoutX: Int, val layoutY: Int,
        val alpha: Float,
        val actionType: String,
        val targetX: Float, val targetY: Float,
        val endX: Float, val endY: Float,
        val duration: Long,
        val keyCode: Int,
        val playerIndex: Int,
        val scaleX: Float, val scaleY: Float,
        val rotation: Float,
        val iconPath: String?
    )

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "ACTION_RELOAD_KEYS") loadKeysFromDb()
        }
    }
    private val iconUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "ACTION_UPDATE_FLOATING_ICON") updateIcon()
        }
    }
    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_RESET_POSITION) resetFloatingPosition()
        }
    }
    private val changeKeyIconReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val keyId = intent.getStringExtra("keyId") ?: return
            val uriString = intent.getStringExtra("uri")
            if (uriString != null) {
                val uri = Uri.parse(uriString)
                serviceScope.launch {
                    if (IconManager.saveKeyIconFromUri(context, keyId, uri)) {
                        editModeManager?.getAllKeyViews()?.find { it.keyId == keyId }?.let { view ->
                            view.iconPath = "key_icons/${keyId}.png"
                            view.applyIcon()
                        }
                    }
                }
            }
        }
    }
    private val keyActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "ACTION_KEY_DELETED" -> {
                    val keyId = intent.getStringExtra("key_id") ?: return
                    val view = editModeManager?.getAllKeyViews()?.find { it.keyId == keyId }
                    view?.let {
                        editModeManager?.removeKeyView(it)
                        serviceScope.launch { saveKeysToDb() }
                    }
                }
                "ACTION_KEY_ALPHA_CHANGED" -> {
                    val keyId = intent.getStringExtra("key_id") ?: return
                    val alpha = intent.getFloatExtra("alpha", 1f)
                    editModeManager?.getAllKeyViews()?.find { it.keyId == keyId }?.setOpacity(
                        (alpha * 255).toInt()
                    )
                    debounceSaveAlpha(keyId)
                }
            }
        }
    }

    companion object {
        var instance: FloatingService? = null
        const val ACTION_RESET_POSITION = "com.example.dualmapper.RESET_FLOATING_POSITION"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isTvMode = DeviceTypeHelper.isTelevision(this)
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        if (!isTvMode) {
            createAndShowFloatingView()
        }
        registerReceiver(iconUpdateReceiver, IntentFilter("ACTION_UPDATE_FLOATING_ICON"))
        registerReceiver(reloadReceiver, IntentFilter("ACTION_RELOAD_KEYS"))
        registerReceiver(resetReceiver, IntentFilter(ACTION_RESET_POSITION))
        registerReceiver(changeKeyIconReceiver, IntentFilter("ACTION_KEY_ICON_SELECTED"))
        registerReceiver(keyActionReceiver, IntentFilter().apply {
            addAction("ACTION_KEY_DELETED")
            addAction("ACTION_KEY_ALPHA_CHANGED")
        })
        loadKeysFromDb()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "floating_channel",
                getString(R.string.floating_service_running),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingService::class.java).apply { action = "STOP_FLOATING" }
        val pendingStopIntent =
            PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "floating_channel")
            .setContentTitle(getString(R.string.floating_service_running))
            .setContentText(getString(R.string.floating_service_desc))
            .setSmallIcon(R.drawable.ic_notification)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop),
                pendingStopIntent
            )
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_FLOATING") {
            serviceScope.launch {
                saveKeysToDb()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createAndShowFloatingView() {
        if (isViewAdded) return
        createFloatingView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(floatingView, params)
        isViewAdded = true
        updateThemeColors()
    }

    private fun hideAndDestroyFloatingView() {
        if (isViewAdded && ::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
            isViewAdded = false
        }
        editModeManager = null
        keyContainer = null
    }

    private fun createFloatingView() {
        val mainContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        keyContainer = FrameLayout(this).apply { id = R.id.key_container }

        iconImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(56), dpToPx(56)).apply {
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        updateIcon()

        closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            layoutParams = FrameLayout.LayoutParams(dpToPx(24), dpToPx(24)).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, dpToPx(4), dpToPx(4), 0)
            }
            setOnClickListener {
                serviceScope.launch {
                    saveKeysToDb()
                    stopSelf()
                }
            }
        }

        editBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_settings)
            layoutParams = FrameLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dpToPx(8), dpToPx(8))
            }
            setOnClickListener {
                isEditMode = !isEditMode
                editModeManager?.setEditMode(isEditMode)
                updateEditButtonTint()
                if (!isEditMode) {
                    serviceScope.launch { saveKeysToDb() }
                }
            }
        }

        addBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_add)
            layoutParams = FrameLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(dpToPx(8), 0, 0, dpToPx(8))
            }
            setOnClickListener { if (isEditMode) createNewKey() }
        }

        keyContainer!!.addView(iconImageView)
        keyContainer!!.addView(closeBtn)
        keyContainer!!.addView(editBtn)
        keyContainer!!.addView(addBtn)
        mainContainer.addView(keyContainer)
        editModeManager = EditModeManager(keyContainer!!)

        keyContainer!!.setOnTouchListener { view, event ->
            if (isEditMode && editModeManager?.isLayoutLocked != true) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = view.rootView.layoutParams as WindowManager.LayoutParams
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = view.rootView.layoutParams as WindowManager.LayoutParams
                    val newX = (initialX + (event.rawX - initialTouchX)).toInt()
                        .coerceIn(0, displayMetrics.widthPixels - view.width)
                    val newY = (initialY + (event.rawY - initialTouchY)).toInt()
                        .coerceIn(0, displayMetrics.heightPixels - view.height)
                    params.x = newX
                    params.y = newY
                    windowManager.updateViewLayout(view.rootView, params)
                    true
                }
                else -> false
            }
        }
        floatingView = mainContainer
    }

    private fun createNewKey() {
        val newId = "key_${System.currentTimeMillis()}"
        val container = keyContainer ?: return
        serviceScope.launch {
            val metrics = resources.displayMetrics
            val tempEntity = KeyMappingEntity(
                id = newId,
                label = getString(R.string.new_key),
                layoutX = 0,
                layoutY = 0,
                alpha = 1f,
                actionType = "tap",
                targetX = 540f,
                targetY = 1200f,
                endX = 0f,
                endY = 0f,
                durationMs = 50L,
                keyCode = 0,
                playerIndex = 1,
                scaleX = 1f,
                scaleY = 1f,
                iconPath = null,
                designWidth = metrics.widthPixels,
                designHeight = metrics.heightPixels
            )
            db.keyMappingDao().insert(tempEntity)

            withContext(Dispatchers.Main) {
                val centerX = (container.width / 2) - dpToPx(28)
                val centerY = (container.height / 2) - dpToPx(28)
                val keyView = createMappedKeyView(
                    newId,
                    getString(R.string.new_key),
                    centerX, centerY,
                    1f, "tap", 540f, 1200f
                )
                editModeManager?.addKeyView(keyView, centerX, centerY)

                keyView.post {
                    val params = keyView.layoutParams as FrameLayout.LayoutParams
                    serviceScope.launch {
                        val entity = db.keyMappingDao().getById(newId)
                        if (entity != null) {
                            val updated = entity.copy(
                                layoutX = params.leftMargin.coerceAtLeast(0),
                                layoutY = params.topMargin.coerceAtLeast(0)
                            )
                            db.keyMappingDao().insert(updated)
                        }
                    }
                }

                startActivity(Intent(this@FloatingService, RemapActivity::class.java).putExtra("key_id", newId))
            }
        }
    }

    private fun createMappedKeyView(
        id: String, label: String, x: Int, y: Int, alpha: Float,
        actionType: String, targetX: Float, targetY: Float,
        endX: Float = 0f, endY: Float = 0f, duration: Long = 50,
        playerIndex: Int = 1, scaleX: Float = 1f, scaleY: Float = 1f,
        iconPath: String? = null
    ): MappedKeyView {
        return MappedKeyView(this).apply {
            keyId = id
            setLabel(label)
            this.iconPath = iconPath
            applyIcon()
            setOpacity((alpha * 255).toInt())
            this.actionType = actionType
            this.targetX = targetX
            this.targetY = targetY
            this.endX = endX
            this.endY = endY
            this.duration = duration
            this.playerIndex = playerIndex
            this.scaleX = scaleX
            this.scaleY = scaleY
            applyScale()
            setOnLongPressListener {
                if (isEditMode) {
                    val intent =
                        Intent(this@FloatingService, KeySettingsDialogActivity::class.java).apply {
                            putExtra("key_id", keyId)
                            putExtra("alpha", currentAlpha)
                        }
                    startActivity(intent)
                }
            }
            setOnPositionChangedListener { _, _ ->
                debounceSavePosition()
            }
        }
    }

    private fun debounceSavePosition() {
        positionSaveDebounceJob?.cancel()
        positionSaveDebounceJob = serviceScope.launch {
            delay(500L)
            saveKeysToDb()
        }
    }

    private fun loadKeysFromDb() {
        val manager = editModeManager ?: return
        serviceScope.launch {
            val keys = db.keyMappingDao().getAll()
            val currentWidth = displayMetrics.widthPixels.toFloat()
            val currentHeight = displayMetrics.heightPixels.toFloat()
            withContext(Dispatchers.Main) {
                val existingMap = manager.getAllKeyViews().associateBy { it.keyId }
                val newIds = keys.map { it.id }.toSet()
                existingMap.keys.filter { it !in newIds }.forEach { id ->
                    existingMap[id]?.let { manager.removeKeyView(it) }
                }
                keys.forEach { entity ->
                    val designW = if (entity.designWidth > 0) entity.designWidth.toFloat() else currentWidth
                    val designH = if (entity.designHeight > 0) entity.designHeight.toFloat() else currentHeight
                    val scaleX = currentWidth / designW
                    val scaleY = currentHeight / designH
                    val scaledX = (entity.layoutX * scaleX).toInt()
                    val scaledY = (entity.layoutY * scaleY).toInt()
                    val view = existingMap[entity.id]
                    if (view == null) {
                        val keyView = createMappedKeyView(
                            entity.id, entity.label, scaledX, scaledY, entity.alpha,
                            entity.actionType, entity.targetX, entity.targetY,
                            entity.endX, entity.endY,
                            entity.durationMs, entity.playerIndex,
                            entity.scaleX, entity.scaleY, entity.iconPath
                        )
                        manager.addKeyView(keyView, scaledX, scaledY)
                    } else {
                        val params = view.layoutParams as FrameLayout.LayoutParams
                        if (params.leftMargin != scaledX || params.topMargin != scaledY) {
                            params.leftMargin = scaledX
                            params.topMargin = scaledY
                            view.layoutParams = params
                        }
                        view.currentAlpha = entity.alpha
                        view.scaleX = entity.scaleX
                        view.scaleY = entity.scaleY
                        view.applyScale()
                        view.label = entity.label
                        view.playerIndex = entity.playerIndex
                        if (view.iconPath != entity.iconPath) {
                            view.iconPath = entity.iconPath
                            view.applyIcon()
                        }
                    }
                }
                updateThemeColors()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        loadKeysFromDb()
    }

    private fun debounceSaveAlpha(keyId: String) {
        saveDebounceJobs[keyId]?.cancel()
        saveDebounceJobs[keyId] = serviceScope.launch {
            delay(500L)
            saveKeysToDb()
            saveDebounceJobs.remove(keyId)
        }
    }

    private suspend fun saveKeysToDb() {
        val manager = editModeManager ?: return
        val designWidth = resources.displayMetrics.widthPixels
        val designHeight = resources.displayMetrics.heightPixels
        val snapshots = manager.getAllKeyViews().map { view ->
            val params = view.layoutParams as FrameLayout.LayoutParams
            KeySnapshot(
                keyId = view.keyId,
                label = view.label,
                layoutX = params.leftMargin.coerceAtLeast(0),
                layoutY = params.topMargin.coerceAtLeast(0),
                alpha = view.currentAlpha,
                actionType = view.actionType,
                targetX = view.targetX,
                targetY = view.targetY,
                endX = view.endX,
                endY = view.endY,
                duration = view.duration,
                keyCode = 0,
                playerIndex = view.playerIndex,
                scaleX = view.scaleX,
                scaleY = view.scaleY,
                rotation = view.rotation,
                iconPath = view.iconPath
            )
        }
        saveKeysToDbInternal(manager, db, designWidth, designHeight, snapshots)
    }

    private suspend fun saveKeysToDbInternal(
        manager: EditModeManager,
        database: AppDatabase,
        defaultDesignWidth: Int,
        defaultDesignHeight: Int,
        snapshots: List<KeySnapshot>
    ) = withContext(Dispatchers.IO) {
        val existingMap = database.keyMappingDao().getAll().associateBy { it.id }
        val entities = snapshots.map { snap ->
            val existing = existingMap[snap.keyId]
            KeyMappingEntity(
                id = snap.keyId,
                label = snap.label,
                layoutX = snap.layoutX,
                layoutY = snap.layoutY,
                alpha = snap.alpha,
                actionType = snap.actionType,
                targetX = snap.targetX,
                targetY = snap.targetY,
                endX = snap.endX,
                endY = snap.endY,
                durationMs = snap.duration,
                keyCode = existing?.keyCode ?: snap.keyCode,
                presetId = existing?.presetId,
                playerIndex = snap.playerIndex,
                scaleX = snap.scaleX,
                scaleY = snap.scaleY,
                rotation = snap.rotation,
                iconPath = snap.iconPath,
                designWidth = existing?.designWidth?.takeIf { it > 0 } ?: defaultDesignWidth,
                designHeight = existing?.designHeight?.takeIf { it > 0 } ?: defaultDesignHeight
            )
        }
        database.keyMappingDao().upsertAll(entities)
        val currentIds = entities.map { it.id }.toSet()
        if (currentIds.isNotEmpty()) {
            database.keyMappingDao().deleteAllExcept(currentIds)
        }
    }

    fun setGlobalAlpha(alpha: Int) {
        editModeManager?.getAllKeyViews()?.forEach { it.setOpacity(alpha) }
        serviceScope.launch { saveKeysToDb() }
    }

    private fun updateIcon() {
        val iconFile = IconManager.getIconFile(this)
        Glide.with(this)
            .load(if (iconFile.exists()) iconFile else R.drawable.ic_default_floating)
            .override(128, 128)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .into(iconImageView)
    }

    private fun resetFloatingPosition() {
        if (!::floatingView.isInitialized || !isViewAdded) {
            Toast.makeText(this, R.string.floating_not_shown, Toast.LENGTH_SHORT).show()
            return
        }
        val params = floatingView.layoutParams as WindowManager.LayoutParams
        params.x = ((displayMetrics.widthPixels - floatingView.width) / 2).coerceAtLeast(0)
        params.y = ((displayMetrics.heightPixels - floatingView.height) / 2).coerceAtLeast(0)
        windowManager.updateViewLayout(floatingView, params)
        Toast.makeText(this, R.string.position_reset, Toast.LENGTH_SHORT).show()
    }

    private fun updateThemeColors() {
        val nightMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightMode == Configuration.UI_MODE_NIGHT_YES
        val backgroundColor = ContextCompat.getColor(
            this,
            if (isDark) R.color.dark_surface else R.color.light_surface
        )
        val textColor = ContextCompat.getColor(
            this,
            if (isDark) R.color.text_primary else R.color.light_text_primary
        )
        keyContainer?.setBackgroundColor(backgroundColor)
        editModeManager?.getAllKeyViews()?.forEach { view ->
            view.setLabelTextColor(textColor)
            view.applyTheme(isDark)
        }
        closeBtn.setColorFilter(textColor)
        addBtn.setColorFilter(textColor)
        updateEditButtonTint()
    }

    private fun updateEditButtonTint() {
        if (isEditMode) {
            editBtn.setColorFilter(ContextCompat.getColor(this, R.color.text_error))
        } else {
            val nightMode =
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val isDark = nightMode == Configuration.UI_MODE_NIGHT_YES
            val normalColor = ContextCompat.getColor(
                this,
                if (isDark) R.color.text_primary else R.color.light_text_primary
            )
            editBtn.setColorFilter(normalColor)
        }
    }

    fun enterEditModeForTv() {
        if (!isTvMode) return
        createAndShowFloatingView()
        isEditMode = true
        editModeManager?.setEditMode(true)
        editModeManager?.setFocusedView(editModeManager?.getAllKeyViews()?.firstOrNull())
    }

    fun exitEditModeForTv() {
        if (!isTvMode) return
        isEditMode = false
        editModeManager?.setEditMode(false)
        editModeManager?.setFocusedView(null)
        serviceScope.launch {
            saveKeysToDb()
            hideAndDestroyFloatingView()
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        instance = null
        saveDebounceJobs.values.forEach { it.cancel() }
        saveDebounceJobs.clear()
        positionSaveDebounceJob?.cancel()
        positionSaveDebounceJob = null

        // 异步保存，不阻塞主线程，超时放弃
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(500L) {
                    val manager = editModeManager
                    val database = db
                    if (manager != null) {
                        saveKeysToDbInternal(
                            manager, database,
                            resources.displayMetrics.widthPixels,
                            resources.displayMetrics.heightPixels,
                            manager.getAllKeyViews().map { view ->
                                val params = view.layoutParams as FrameLayout.LayoutParams
                                KeySnapshot(
                                    keyId = view.keyId,
                                    label = view.label,
                                    layoutX = params.leftMargin.coerceAtLeast(0),
                                    layoutY = params.topMargin.coerceAtLeast(0),
                                    alpha = view.currentAlpha,
                                    actionType = view.actionType,
                                    targetX = view.targetX,
                                    targetY = view.targetY,
                                    endX = view.endX,
                                    endY = view.endY,
                                    duration = view.duration,
                                    keyCode = 0,
                                    playerIndex = view.playerIndex,
                                    scaleX = view.scaleX,
                                    scaleY = view.scaleY,
                                    rotation = view.rotation,
                                    iconPath = view.iconPath
                                )
                            }
                        )
                    }
                }
            } catch (_: Exception) { /* give up */ }
        }

        serviceScope.cancel()
        Glide.with(this).onDestroy()
        unregisterReceiver(iconUpdateReceiver)
        unregisterReceiver(reloadReceiver)
        unregisterReceiver(resetReceiver)
        unregisterReceiver(changeKeyIconReceiver)
        unregisterReceiver(keyActionReceiver)
        hideAndDestroyFloatingView()
        keyContainer = null
        editModeManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}