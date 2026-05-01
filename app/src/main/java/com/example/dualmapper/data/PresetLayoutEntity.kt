package com.example.dualmapper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preset_layouts")
data class PresetLayoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null
)