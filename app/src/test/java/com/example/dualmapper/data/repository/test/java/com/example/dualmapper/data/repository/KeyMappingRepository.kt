package com.example.dualmapper.data.repository

import com.example.dualmapper.data.KeyMappingEntity
import kotlinx.coroutines.flow.Flow

interface KeyMappingRepository {
    suspend fun getAll(): List<KeyMappingEntity>
    suspend fun getByKeyCode(keyCode: Int): KeyMappingEntity?
    suspend fun getById(id: String): KeyMappingEntity?
    suspend fun insertOrUpdate(entity: KeyMappingEntity)
    suspend fun deleteById(id: String)
    suspend fun replaceAll(entities: List<KeyMappingEntity>)
    fun observeAll(): Flow<List<KeyMappingEntity>>
}