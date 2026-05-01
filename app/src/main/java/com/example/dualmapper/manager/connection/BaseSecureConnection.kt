package com.example.dualmapper.manager.connection

import com.example.dualmapper.util.AesUtils
import com.example.dualmapper.util.KeyExchangeHelper
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

object SecureConnectionHelper {

    fun performKeyExchange(input: InputStream, output: OutputStream) {
        val localKeyPair = KeyExchangeHelper.generateKeyPair()
        val localPub = localKeyPair.publicKey
        val lenBytes = ByteBuffer.allocate(4).putInt(localPub.size).array()
        output.write(lenBytes)
        output.write(localPub)
        output.flush()

        val lenBuf = ByteArray(4)
        readFully(input, lenBuf)
        val peerKeyLen = ByteBuffer.wrap(lenBuf).int
        if (peerKeyLen <= 0 || peerKeyLen > 1024) throw IllegalArgumentException("Invalid public key length")
        val peerPub = ByteArray(peerKeyLen)
        readFully(input, peerPub)

        val sharedSecret = KeyExchangeHelper.deriveSharedSecret(localKeyPair.privateKey, peerPub)
        AesUtils.setSessionKey(sharedSecret)
    }

    fun sendEncrypted(output: OutputStream, data: ByteArray) {
        if (!AesUtils.isSessionKeySet()) throw IllegalStateException("Session key not set, perform key exchange first")
        val encrypted = AesUtils.encrypt(data)
        val lengthPrefix = ByteBuffer.allocate(4).putInt(encrypted.size).array()
        output.write(lengthPrefix)
        output.write(encrypted)
        output.flush()
    }

    fun receiveDecrypted(input: InputStream): ByteArray? {
        val lenBuf = ByteArray(4)
        try {
            readFully(input, lenBuf)
        } catch (e: IOException) {
            return null
        }
        val encryptedSize = ByteBuffer.wrap(lenBuf).int
        if (encryptedSize <= 0 || encryptedSize > 1024 * 1024) { // 限制最大1MB
            return null
        }
        val encryptedData = ByteArray(encryptedSize)
        try {
            readFully(input, encryptedData)
        } catch (e: IOException) {
            return null
        }
        return try {
            AesUtils.decrypt(encryptedData)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 带超时的解密接收，适用于长时间等待可能卡死的场景
     * @param input 输入流
     * @param timeoutMs 超时毫秒
     * @return 解密后的数据，超时或异常返回 null
     */
    suspend fun receiveDecryptedWithTimeout(input: InputStream, timeoutMs: Long = 5000): ByteArray? {
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                receiveDecrypted(input)
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) throw IOException("Unexpected end of stream")
            offset += read
        }
    }
}