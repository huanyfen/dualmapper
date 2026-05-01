package com.example.dualmapper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "keycode_library")
data class KeyCodeLibraryEntity(
    @PrimaryKey val keyCode: Int,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val label: String,
    val category: String
)