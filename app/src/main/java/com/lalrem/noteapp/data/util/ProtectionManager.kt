package com.lalrem.noteapp.data.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionManager @Inject constructor() {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private fun getKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun getEncryptCipher(): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getKey())
        }
    }

    private fun getDecryptCipherForIv(iv: ByteArray): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        }
    }

    fun encrypt(text: String): String {
        if (text.isEmpty()) return ""
        val bytes = text.toByteArray()
        val cipher = getEncryptCipher()
        val iv = cipher.iv
        val encrypted = cipher.doFinal(bytes)
        
        // Combine IV and Encrypted bytes
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            
            val iv = combined.copyOfRange(0, 12) // GCM default IV size
            val encrypted = combined.copyOfRange(12, combined.size)
            
            val cipher = getDecryptCipherForIv(iv)
            val decryptedBytes = cipher.doFinal(encrypted)
            String(decryptedBytes)
        } catch (e: Exception) {
            // If decryption fails (e.g. data was not encrypted), return the original
            // This is a safety measure for transitioning existing data
            encryptedBase64
        }
    }

    companion object {
        private const val KEY_ALIAS = "note_app_secret_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
