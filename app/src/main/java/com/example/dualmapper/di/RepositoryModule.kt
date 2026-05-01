package com.example.dualmapper.di

import com.example.dualmapper.data.AppDatabase
import com.example.dualmapper.data.repository.KeyMappingRepository
import com.example.dualmapper.data.repository.KeyMappingRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideKeyMappingRepository(db: AppDatabase): KeyMappingRepository {
        return KeyMappingRepositoryImpl(db)
    }
}