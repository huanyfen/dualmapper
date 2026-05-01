package com.example.dualmapper.util

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object AccessibilityHelper {

    fun isServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val serviceName = "${context.packageName}/${serviceClass.name}"
        return try {
            val accessibilityManager =
                context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.split(":").any { it.equals(serviceName, ignoreCase = true) }
        } catch (e: Exception) {
            false
        }
    }
}