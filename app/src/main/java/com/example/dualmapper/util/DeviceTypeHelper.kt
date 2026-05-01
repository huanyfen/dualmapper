package com.example.dualmapper.util

import android.content.Context
import android.content.pm.PackageManager

object DeviceTypeHelper {
    fun isTelevision(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}