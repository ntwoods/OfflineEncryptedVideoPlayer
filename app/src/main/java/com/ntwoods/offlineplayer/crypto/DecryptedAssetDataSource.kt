package com.ntwoods.offlineplayer.crypto

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.ntwoods.offlineplayer.BuildConfig
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import kotlin.math.min

@UnstableApi
class DecryptedAssetDataSource(private val context: Context) : BaseDataSource(false) {
    private var uri: Uri? = null
    private var stream: ByteArrayInputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false
    private lateinit var decrypted: ByteArray

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        try {
            val path = (uri?.path ?: error("Invalid asset uri")).removePrefix("/")
            val am: AssetManager = context.assets
            val all = am.open(path).use { it.readBytes() }
            if (all.size < 28) throw IOException("Corrupt .enc file: too small")

            val iv = all.copyOfRange(0, 12)
            val cipherPlusTag = all.copyOfRange(12, all.size)

            val keyBytes = Base64.decode(BuildConfig.AES_KEY_B64, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))

            decrypted = try { cipher.doFinal(cipherPlusTag) }
            catch (bad: AEADBadTagException) {
                throw IOException("Decryption failed (bad key or corrupted file). Check AES_KEY_B64 & re-encrypt.", bad)
            }

            val start = dataSpec.position.toInt().coerceAtMost(decrypted.size)
            val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) decrypted.size
            else min(start + dataSpec.length.toInt(), decrypted.size)

            stream = ByteArrayInputStream(decrypted.copyOfRange(start, end))
            bytesRemaining = (end - start).toLong()

            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) readLength
        else min(readLength.toLong(), bytesRemaining).toInt()
        val r = stream?.read(buffer, offset, toRead) ?: -1
        if (r == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= r
        bytesTransferred(r)
        return r
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) {
            try { stream?.close() } catch (_: Exception) {}
            stream = null
            opened = false
            transferEnded()
        }
    }

    class Factory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource = DecryptedAssetDataSource(context)
    }
}
