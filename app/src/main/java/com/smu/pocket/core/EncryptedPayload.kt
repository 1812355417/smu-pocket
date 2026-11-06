package com.smu.pocket.core

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Version + random 96-bit IV + ciphertext with 128-bit authentication tag. */
internal object EncryptedPayload {
    private const val VERSION: Byte = 1
    private const val IV_SIZE = 12

    fun encrypt(plain: ByteArray, key: SecretKey, purpose: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        require(cipher.iv.size == IV_SIZE)
        cipher.updateAAD(byteArrayOf(VERSION) + purpose)
        return byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(payload: ByteArray, key: SecretKey, purpose: ByteArray): ByteArray {
        require(payload.size >= 1 + IV_SIZE + 16 && payload[0] == VERSION) { "Invalid encrypted payload" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.copyOfRange(1, 1 + IV_SIZE)))
        cipher.updateAAD(byteArrayOf(VERSION) + purpose)
        return cipher.doFinal(payload.copyOfRange(1 + IV_SIZE, payload.size))
    }
}
