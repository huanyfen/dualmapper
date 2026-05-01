package com.example.dualmapper.domain.usecase

import com.example.dualmapper.data.repository.KeyMappingRepository
import com.example.dualmapper.manager.preset.PresetManager
import javax.inject.Inject

class ApplyPresetUseCase @Inject constructor(
    private val presetManager: PresetManager,
    private val mappingRepository: KeyMappingRepository
) {
    suspend operator fun invoke(presetId: String, backupFirst: Boolean = true) {
        if (backupFirst) {
            presetManager.backupCurrent()
        }
        val keys = presetManager.getKeysByPresetId(presetId)
        mappingRepository.replaceAll(keys)
    }
}