package com.example.dualmapper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: KeyMappingEntity)

    @Update
    suspend fun update(entity: KeyMappingEntity)

    @Query("DELETE FROM key_mappings WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM key_mappings")
    suspend fun getAll(): List<KeyMappingEntity>

    @Query("SELECT * FROM key_mappings")
    fun observeAll(): Flow<List<KeyMappingEntity>>

    @Query("SELECT * FROM key_mappings WHERE keyCode = :keyCode LIMIT 1")
    suspend fun getByKeyCode(keyCode: Int): KeyMappingEntity?

    @Query("SELECT * FROM key_mappings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KeyMappingEntity?

    @Query("SELECT * FROM key_mappings WHERE presetId = :presetId")
    suspend fun getByPresetId(presetId: String): List<KeyMappingEntity>

    @Query("DELETE FROM key_mappings")
    suspend fun deleteAll()

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<KeyMappingEntity>)

    /**
     * 在事务中替换所有映射记录，确保原子性。
     */
    @Transaction
    suspend fun replaceAllWithTransaction(entities: List<KeyMappingEntity>) {
        deleteAll()
        upsertAll(entities)
    }

    /**
     * 删除所有 ID 不在给定集合中的映射记录。
     * 用于批量更新后清理已移除的按键。
     * 当 ids 为空集合时，相当于删除全部（为安全起见，调用方需确保 ids 不为空）。
     */
    @Query("DELETE FROM key_mappings WHERE id NOT IN (:ids)")
    suspend fun deleteAllExcept(ids: Set<String>)
}