package com.smu.pocket

import com.smu.pocket.core.EncryptedPayload
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class EncryptedPayloadTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val purpose = "session".toByteArray()
    private val plain = "Cookie=secret; 成绩=90".toByteArray()

    @Test fun roundTripAndRandomIv() {
        val first = EncryptedPayload.encrypt(plain, key, purpose)
        val second = EncryptedPayload.encrypt(plain, key, purpose)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, EncryptedPayload.decrypt(first, key, purpose))
        assertFalse(String(first, Charsets.ISO_8859_1).contains("Cookie=secret"))
    }

    @Test fun roundTripsEmptyAndLargePersonalData() {
        for (data in listOf(byteArrayOf(), plain + ByteArray(256 * 1024) { (it % 251).toByte() })) {
            val encrypted = EncryptedPayload.encrypt(data, key, purpose)
            assertArrayEquals(data, EncryptedPayload.decrypt(encrypted, key, purpose))
        }
    }

    @Test fun rejectsTamperingWrongKeyWrongPurposeAndMalformedData() {
        val payload = EncryptedPayload.encrypt(plain, key, purpose)
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        fun rejects(block: () -> Unit) {
            try { block(); fail("Unauthenticated data accepted") } catch (expected: Exception) { }
        }
        for (index in listOf(0, 1, 13, payload.lastIndex)) {
            rejects { EncryptedPayload.decrypt(payload.copyOf().apply { this[index] = (this[index].toInt() xor 1).toByte() }, key, purpose) }
        }
        rejects { EncryptedPayload.decrypt(payload, otherKey, purpose) }
        rejects { EncryptedPayload.decrypt(payload, key, "snapshot".toByteArray()) }
        rejects { EncryptedPayload.decrypt(payload.copyOf(10), key, purpose) }
        rejects { EncryptedPayload.decrypt(plain, key, purpose) }
    }
}
