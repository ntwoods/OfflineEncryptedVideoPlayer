package com.ntwoods.offlineplayer.crypto

import android.content.Context
import android.util.Base64
import com.ntwoods.offlineplayer.BuildConfig
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Decryptor {
    /** Reads asset .enc → returns decrypted bytes (AES-GCM, IV=first 12 bytes) */
    fun decryptAssetToBytes(context: Context, assetPath: String): ByteArray {
        val all = context.assets.open(assetPath).use { it.readBytes() }
        require(all.size >= 28) { "Encrypted file too small" }

        val iv = all.copyOfRange(0, 12)
        val cipherPlusTag = all.copyOfRange(12, all.size)

        val key = SecretKeySpec(
            Base64.decode(BuildConfig.AES_KEY_B64, Base64.DEFAULT),
            "AES"
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherPlusTag) // throws if key/tag invalid
    }
}
