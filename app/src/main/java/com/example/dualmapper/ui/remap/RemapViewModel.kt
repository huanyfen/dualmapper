package com.example.dualmapper.ui.remap

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dualmapper.data.KeyMappingEntity
import com.example.dualmapper.data.repository.KeyMappingRepository
import com.example.dualmapper.manager.preset.PresetManager
import com.example.dualmapper.service.MappedAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RemapUiState(
    val actionType: String = "tap",
    val instruction: String = "",
    val currentTouchX: Int = 0,
    val currentTouchY: Int = 0,
    val startPoint: Offset? = null,
    val endPoint: Offset? = null,
    val recordedKeyLabel: String? = null,
    val recordedKeyCode: Int = 0,
    val canSave: Boolean = false,
    val durationMs: Long = 50,
    val playerIndex: Int = 1
)

@HiltViewModel
class RemapViewModel @Inject constructor(
    private val repo: KeyMappingRepository,
    private val presetManager: PresetManager,
    private val savedStateHandle: SavedStateHandle,
    private val application: Application
) : ViewModel() {

    private val keyId: String? = savedStateHandle["key_id"]
    private val recordKeyOnly: Boolean = savedStateHandle["record_key_only"] ?: false

    private val _uiState = MutableStateFlow(RemapUiState())
    val uiState: StateFlow<RemapUiState> = _uiState.asStateFlow()

    private var downTime = 0L

    init {
        if (keyId != null) {
            viewModelScope.launch {
                val entity = withContext(Dispatchers.IO) { repo.getById(keyId) }
                entity?.let {
                    _uiState.update { state ->
                        state.copy(
                            actionType = it.actionType,
                            startPoint = Offset(it.targetX, it.targetY),
                            endPoint = if (it.endX != 0f || it.endY != 0f) Offset(it.endX, it.endY) else null,
                            recordedKeyCode = it.keyCode,
                            durationMs = it.durationMs,
                            playerIndex = it.playerIndex,
                            canSave = true
                        )
                    }
                    updateKeyLabel(it.keyCode)
                }
            }
        }
        if (recordKeyOnly) {
            _uiState.update { it.copy(instruction = "仅录制按键模式") }
        }
    }

    fun setActionType(type: String) {
        _uiState.update { it.copy(actionType = type, startPoint = null, endPoint = null, canSave = false) }
    }

    fun setPlayerIndex(index: Int) {
        _uiState.update { it.copy(playerIndex = index) }
    }

    fun onTouchEvent(screenOffset: Offset) {
        _uiState.update { it.copy(currentTouchX = screenOffset.x.toInt(), currentTouchY = screenOffset.y.toInt()) }
        when (uiState.value.actionType) {
            "tap" -> _uiState.update { it.copy(startPoint = screenOffset, endPoint = null, canSave = true) }
            "longpress" -> { /* handled by drag flow */ }
        }
    }

    fun onDragStart(screenOffset: Offset) {
        downTime = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                startPoint = screenOffset,
                endPoint = null,
                currentTouchX = screenOffset.x.toInt(),
                currentTouchY = screenOffset.y.toInt()
            )
        }
    }

    fun onDrag(screenOffset: Offset) {
        _uiState.update {
            it.copy(
                endPoint = screenOffset,
                currentTouchX = screenOffset.x.toInt(),
                currentTouchY = screenOffset.y.toInt()
            )
        }
    }

    fun onDragEnd() {
        val upTime = System.currentTimeMillis()
        val duration = upTime - downTime
        _uiState.update {
            it.copy(
                durationMs = duration.coerceAtLeast(50),
                canSave = it.startPoint != null
            )
        }
    }

    fun startKeyRecord() {
        _uiState.update { it.copy(instruction = "请按下外接设备按键") }
    }

    fun onKeyEvent(keyCode: Int) {
        _uiState.update { it.copy(recordedKeyCode = keyCode, canSave = true) }
        updateKeyLabel(keyCode)
    }

    private fun updateKeyLabel(keyCode: Int) {
        viewModelScope.launch {
            val label = withContext(Dispatchers.IO) { presetManager.getKeyLabel(keyCode) } ?: "码:$keyCode"
            _uiState.update { it.copy(recordedKeyLabel = label) }
        }
    }

    fun saveMapping() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.recordedKeyCode == 0) return@launch
            if (state.actionType != "swipe" && state.startPoint == null) return@launch
            if (state.actionType == "swipe" && (state.startPoint == null || state.endPoint == null)) return@launch

            withContext(Dispatchers.IO) {
                val existing = keyId?.let { repo.getById(keyId) }
                val metrics = application.resources.displayMetrics
                // 优先使用已有设计尺寸，否则使用当前屏幕尺寸（至少为1）
                val designWidth = existing?.designWidth?.takeIf { it > 0 } ?: metrics.widthPixels
                val designHeight = existing?.designHeight?.takeIf { it > 0 } ?: metrics.heightPixels
                val safeDesignWidth = if (designWidth > 0) designWidth else 1
                val safeDesignHeight = if (designHeight > 0) designHeight else 1

                val entity = KeyMappingEntity(
                    id = keyId ?: java.util.UUID.randomUUID().toString(),
                    label = state.recordedKeyLabel ?: "按键 ${state.recordedKeyCode}",
                    layoutX = existing?.layoutX ?: 0,
                    layoutY = existing?.layoutY ?: 0,
                    alpha = existing?.alpha ?: 1f,
                    actionType = state.actionType,
                    targetX = state.startPoint?.x ?: 0f,
                    targetY = state.startPoint?.y ?: 0f,
                    endX = state.endPoint?.x ?: 0f,
                    endY = state.endPoint?.y ?: 0f,
                    durationMs = state.durationMs,
                    keyCode = state.recordedKeyCode,
                    playerIndex = state.playerIndex,
                    scaleX = existing?.scaleX ?: 1f,
                    scaleY = existing?.scaleY ?: 1f,
                    rotation = existing?.rotation ?: 0f,
                    iconPath = existing?.iconPath,
                    designWidth = safeDesignWidth,
                    designHeight = safeDesignHeight
                )
                repo.insertOrUpdate(entity)
            }
            MappedAccessibilityService.instance?.refreshSingleMapping(state.recordedKeyCode)
        }
    }
}