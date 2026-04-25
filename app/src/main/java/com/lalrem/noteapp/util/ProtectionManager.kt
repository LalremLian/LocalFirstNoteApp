package com.lalrem.noteapp.util

import android.app.Application
import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionManager @Inject constructor(
    app: Application
) {
    private val prefs = app.getSharedPreferences("protection_prefs", Context.MODE_PRIVATE)
    private val salt = "note_app_global_salt_v1".toByteArray()

    private fun getPassword(): String {
        return prefs.getString("workspace_password", "default_sync_key") ?: "default_sync_key"
    }

    fun setPassword(password: String) {
        prefs.edit().putString("workspace_password", password).apply()
    }

    private var cachedKey: SecretKeySpec? = null
    private var lastPassword: String? = null

    private fun getSecretKey(): SecretKeySpec {
        val currentPassword = getPassword()
        if (cachedKey != null && currentPassword == lastPassword) {
            return cachedKey!!
        }
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(currentPassword.toCharArray(), salt, 10000, 256)
        val tmp = factory.generateSecret(spec)
        val key = SecretKeySpec(tmp.encoded, "AES")
        
        cachedKey = key
        lastPassword = currentPassword
        return key
    }

    fun encrypt(text: String): String {
        if (text.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
            
            val encrypted = cipher.doFinal(text.toByteArray())
            
            // Combine IV and Encrypted bytes
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            text
        }
    }

    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < 12) return encryptedBase64
            
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
            
            val decryptedBytes = cipher.doFinal(encrypted)
            String(decryptedBytes)
        } catch (e: Exception) {
            encryptedBase64
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
