package com.example.dualmapper.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.security.MessageDigest

object SignatureUtils {
    // 正式发布时替换为你的签名证书 SHA-256 指纹（大写，冒号分隔）
    private const val EXPECTED_SIGNATURE = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"

    fun verifySelfSignature(context: Context): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signatures = packageInfo.signingInfo.apkContentsSigners
            if (signatures.isEmpty()) return false
            val hash = computeSha256(signatures[0])
            hash == EXPECTED_SIGNATURE
        } catch (e: Exception) {
            false
        }
    }

    private fun computeSha256(signature: Signature): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(signature.toByteArray())
        return digest.joinToString(":") { "%02X".format(it) }
    }
}