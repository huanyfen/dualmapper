package com.example.dualmapper.util

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement

object KeyExchangeHelper {
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private const val EC_CURVE = "secp256r1"

    data class KeyPairData(
        val publicKey: ByteArray,
        val privateKey: PrivateKey
    )

    fun generateKeyPair(): KeyPairData {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC")
        keyPairGenerator.initialize(ECGenParameterSpec(EC_CURVE), SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()
        return KeyPairData(
            publicKey = keyPair.public.encoded,
            privateKey = keyPair.private
        )
    }

    fun deriveSharedSecret(privateKey: PrivateKey, peerPublicKeyBytes: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        val peerPublicKey = keyFactory.generatePublic(
            java.security.spec.X509EncodedKeySpec(peerPublicKeyBytes)
        )
        val keyAgreement = KeyAgreement.getInstance("ECDH", "BC")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        return keyAgreement.generateSecret()
    }
}