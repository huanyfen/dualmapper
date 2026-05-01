package com.example.dualmapper.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object IconManager {
    private const val ICON_FILENAME = "floating_icon.png"
    private const val KEY_ICON_DIR = "key_icons"

    fun getIconFile(context: Context): File = File(context.filesDir, ICON_FILENAME)

    fun saveIconFromUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(getIconFile(context)).use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) { false }
    }

    fun deleteIcon(context: Context): Boolean = getIconFile(context).delete()

    private fun getKeyIconDir(context: Context): File {
        val dir = File(context.filesDir, KEY_ICON_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getKeyIconFile(context: Context, keyId: String): File = File(getKeyIconDir(context), "${keyId}.png")

    fun saveKeyIconFromUri(context: Context, keyId: String, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(getKeyIconFile(context, keyId)).use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) { false }
    }

    fun deleteKeyIcon(context: Context, keyId: String): Boolean = getKeyIconFile(context, keyId).delete()

    fun hasKeyCustomIcon(context: Context, keyId: String): Boolean = getKeyIconFile(context, keyId).exists()
}