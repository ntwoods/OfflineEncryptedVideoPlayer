package com.ntwoods.offlineplayer.util

import android.content.Context
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecureStore {
    private const val FILE = "secure_prefs"
    private fun prefs(ctx: Context) : android.content.SharedPreferences {
        val spec = MasterKeys.AES256_GCM_SPEC
        val masterKey = MasterKeys.getOrCreate(spec)
        return EncryptedSharedPreferences.create(
            FILE, masterKey, ctx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveLicense(ctx: Context, key: String, exp: Long, email: String) {
        prefs(ctx).edit()
            .putString("license_key", key)
            .putLong("license_exp", exp)
            .putString("email", email)
            .apply()
    }

    fun getLicense(ctx: Context): Triple<String?, Long, String?> {
        val p = prefs(ctx)
        return Triple(p.getString("license_key", null), p.getLong("license_exp", 0L), p.getString("email", null))
    }

    fun clear(ctx: Context) = prefs(ctx).edit().clear().apply()

    fun deviceId(ctx: Context): String =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
}