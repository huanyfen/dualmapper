package com.example.dualmapper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KeyCodeLibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg entities: KeyCodeLibraryEntity)

    @Query("SELECT label FROM keycode_library WHERE keyCode = :keyCode LIMIT 1")
    suspend fun getLabelForKeyCode(keyCode: Int): String?

    @Query("SELECT * FROM keycode_library ORDER BY category, label")
    suspend fun getAll(): List<KeyCodeLibraryEntity>

    @Query("DELETE FROM keycode_library")
    suspend fun deleteAll()
}