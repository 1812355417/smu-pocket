package com.smu.pocket.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Only ciphertext enters AtomicFile, including its temporary/backup files. No plaintext fallback. */
internal class EncryptedStore(context: Context, private val name: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "$name.enc"))

    @Synchronized fun read(): String? = try {
        String(EncryptedPayload.decrypt(file.readFully(), key(), name.toByteArray()), Charsets.UTF_8)
    } catch (e: Exception) {
        // Missing/invalidated key or authentication failure: discard unusable data.
        file.delete()
        null
    }

    @Synchronized fun write(value: String) {
        val encrypted = EncryptedPayload.encrypt(value.toByteArray(Charsets.UTF_8), key(), name.toByteArray())
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    @Synchronized fun clear() = file.delete()

    private fun key(): SecretKey = synchronized(KEY_LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    private companion object {
        val KEY_LOCK = Any()
        const val KEY_ALIAS = "smu_private_data_v1"
    }
}
