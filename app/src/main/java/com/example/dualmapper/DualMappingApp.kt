package com.example.dualmapper

import android.app.Application
import com.example.dualmapper.util.SignatureUtils
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DualMappingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 验证 APK 签名，防止重打包
        if (!SignatureUtils.verifySelfSignature(this)) {
            throw SecurityException("App integrity check failed. Unauthorized modification detected.")
        }
    }
}