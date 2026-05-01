package com.example.dualmapper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PresetLayoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PresetLayoutEntity)

    @Query("SELECT * FROM preset_layouts")
    suspend fun getAll(): List<PresetLayoutEntity>

    @Query("DELETE FROM preset_layouts")
    suspend fun deleteAll()
}