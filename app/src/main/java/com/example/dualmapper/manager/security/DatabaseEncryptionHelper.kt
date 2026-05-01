package com.example.dualmapper.manager.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteDatabaseHook
import net.sqlcipher.database.SupportFactory
import java.util.UUID

object DatabaseEncryptionHelper {
    private lateinit var supportFactory: SupportFactory
    private var isInitialized = false

    private val hook = object : SQLiteDatabaseHook {
        override fun preKey(db: SQLiteDatabase) {
            db.execSQL("PRAGMA cipher_page_size = 4096")
            db.execSQL("PRAGMA kdf_iter = 64000")
        }
        override fun postKey(db: SQLiteDatabase) {}
    }

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context, "secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        var password = prefs.getString("db_password", null)
        if (password == null) {
            password = UUID.randomUUID().toString()
            prefs.edit().putString("db_password", password).apply()
        }
        supportFactory = SupportFactory(password.toByteArray(), hook, false)
        isInitialized = true
    }

    fun getSupportFactory(): SupportFactory = supportFactory
}