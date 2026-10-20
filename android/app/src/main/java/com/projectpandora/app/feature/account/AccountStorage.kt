package com.projectpandora.app.feature.account

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

interface TokenStorage {
    fun read(): StoredTokens?
    fun write(tokens: StoredTokens)
    fun updateAccess(access: String, refresh: String? = null)
    fun clear()
}

/** Stores the token pair as AES-GCM ciphertext, using an AES key held by Android Keystore. */
class AndroidTokenStorage(context: Context) : TokenStorage {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    override fun read(): StoredTokens? {
        val encrypted = preferences.getString(CIPHERTEXT, null) ?: return null
        val encodedIv = preferences.getString(IV, null) ?: return invalidateStoredValue()
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(128, decode(encodedIv)))
            val plain = cipher.doFinal(decode(encrypted)).toString(StandardCharsets.UTF_8)
            val separator = plain.indexOf('\n')
            if (separator <= 0 || separator == plain.lastIndex) invalidateStoredValue()
            else StoredTokens(plain.substring(0, separator), plain.substring(separator + 1))
        } catch (_: GeneralSecurityException) {
            invalidateStoredValue()
        } catch (_: IllegalArgumentException) {
            invalidateStoredValue()
        }
    }

    @Synchronized
    override fun write(tokens: StoredTokens) {
        require(tokens.access.isNotBlank() && tokens.refresh.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val plain = "${tokens.access}\n${tokens.refresh}".toByteArray(StandardCharsets.UTF_8)
        preferences.edit()
            .putString(IV, encode(cipher.iv))
            .putString(CIPHERTEXT, encode(cipher.doFinal(plain)))
            .apply()
    }

    @Synchronized
    override fun updateAccess(access: String, refresh: String?) {
        val existing = read() ?: return
        write(StoredTokens(access = access, refresh = refresh?.takeIf(String::isNotBlank) ?: existing.refresh))
    }

    @Synchronized
    override fun clear() {
        // commit makes logout durable before the UI leaves the account flow.
        preferences.edit().remove(CIPHERTEXT).remove(IV).commit()
    }

    private fun invalidateStoredValue(): StoredTokens? {
        clear()
        return null
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.projectpandora.app.session-token-key"
        const val PREFERENCES = "pandora_secure_session"
        const val CIPHERTEXT = "token_ciphertext"
        const val IV = "token_iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/** Notification preferences are non-sensitive and remain separate from encrypted credentials. */
class AccountPreferences(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences("pandora_preferences", Context.MODE_PRIVATE)

    fun notification(key: String): Boolean = preferences.getBoolean("notification_$key", true)

    fun setNotification(key: String, enabled: Boolean) {
        preferences.edit().putBoolean("notification_$key", enabled).apply()
    }
}
