package com.example.dualmapper.di

import android.content.Context
import androidx.room.Room
import com.example.dualmapper.data.AppDatabase
import com.example.dualmapper.manager.security.DatabaseEncryptionHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        DatabaseEncryptionHelper.init(context)
        return Room.databaseBuilder(context, AppDatabase::class.java, "mapping.db")
            .openHelperFactory(DatabaseEncryptionHelper.getSupportFactory())
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8
            )
            // fallbackToDestructiveMigration removed to protect user data
            .build()
    }
}