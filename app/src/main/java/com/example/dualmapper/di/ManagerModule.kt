package com.example.dualmapper.di

import android.content.Context
import com.example.dualmapper.data.AppDatabase
import com.example.dualmapper.manager.preset.PresetManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {

    @Provides
    @Singleton
    fun providePresetManager(
        @ApplicationContext context: Context,
        db: AppDatabase
    ): PresetManager {
        return PresetManager(context, db)
    }
}