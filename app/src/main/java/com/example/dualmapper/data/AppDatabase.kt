package com.example.dualmapper.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        KeyMappingEntity::class,
        PresetLayoutEntity::class,
        KeyCodeLibraryEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun keyMappingDao(): KeyMappingDao
    abstract fun presetLayoutDao(): PresetLayoutDao
    abstract fun keyCodeLibraryDao(): KeyCodeLibraryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE key_mappings ADD COLUMN keyCode INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE key_mappings ADD COLUMN playerIndex INTEGER NOT NULL DEFAULT 1")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS keycode_library (
                        keyCode INTEGER PRIMARY KEY NOT NULL,
                        vendorId INTEGER,
                        productId INTEGER,
                        label TEXT NOT NULL,
                        category TEXT NOT NULL
                    )
                """)
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE key_mappings ADD COLUMN scaleX REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE key_mappings ADD COLUMN scaleY REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE key_mappings ADD COLUMN rotation REAL NOT NULL DEFAULT 0.0")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_key_mappings_keyCode ON key_mappings(keyCode)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_key_mappings_presetId ON key_mappings(presetId)")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE key_mappings ADD COLUMN iconPath TEXT")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE key_mappings ADD COLUMN designWidth INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE key_mappings ADD COLUMN designHeight INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}