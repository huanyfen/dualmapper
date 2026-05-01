package com.example.dualmapper.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsDataStore {
    private val KEY_MAPPING_ENABLED = booleanPreferencesKey("key_mapping_enabled")
    private val REMOTE_MAPPING_ENABLED = booleanPreferencesKey("remote_mapping_enabled")

    fun isKeyMappingEnabled(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { it[KEY_MAPPING_ENABLED] ?: true }
    }

    suspend fun setKeyMappingEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_MAPPING_ENABLED] = enabled }
    }

    fun isRemoteMappingEnabled(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { it[REMOTE_MAPPING_ENABLED] ?: false }
    }

    suspend fun setRemoteMappingEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[REMOTE_MAPPING_ENABLED] = enabled }
    }
}