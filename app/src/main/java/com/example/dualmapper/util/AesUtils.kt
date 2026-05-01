package com.example.dualmapper.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_LENGTH = 128

    @Volatile
    private var sessionKey: ByteArray? = null

    fun setSessionKey(key: ByteArray) {
        sessionKey = key
    }

    fun isSessionKeySet(): Boolean = sessionKey != null

    fun encrypt(plainData: ByteArray): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("Session key not set")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM), GCMParameterSpec(TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(plainData)
        return iv + encrypted
    }

    fun decrypt(encryptedData: ByteArray): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("Session key not set")
        val iv = encryptedData.copyOfRange(0, IV_SIZE)
        val encrypted = encryptedData.copyOfRange(IV_SIZE, encryptedData.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, ALGORITHM), GCMParameterSpec(TAG_LENGTH, iv))
        return cipher.doFinal(encrypted)
    }
}