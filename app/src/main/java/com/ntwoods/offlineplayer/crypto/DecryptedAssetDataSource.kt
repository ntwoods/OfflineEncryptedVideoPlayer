package com.ntwoods.offlineplayer.crypto

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.ntwoods.offlineplayer.BuildConfig
import java.io.FileInputStream
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

@UnstableApi
class DecryptedAssetDataSource(private val context: Context) : BaseDataSource(false) {

    companion object {
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_SIZE = 16
        private const val AES_BLOCK_SIZE = 16
        private const val READ_BUFFER_SIZE = 64 * 1024
    }

    private var uri: Uri? = null
    private var assetFileDescriptor: AssetFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var cipher: Cipher? = null
    private var bytesRemaining: Long = 0L
    private var opened = false
    private val encryptedBuffer = ByteArray(READ_BUFFER_SIZE)

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        try {
            val path = (uri?.path ?: throw IOException("Invalid asset uri")).removePrefix("/")

            // .enc assets are packaged uncompressed (see app/build.gradle.kts), so openFd()
            // gives us efficient random access inside the APK without loading the file into RAM.
            val afd = context.assets.openFd(path)
            assetFileDescriptor = afd

            val encryptedLength = afd.length
            if (encryptedLength < GCM_IV_SIZE + GCM_TAG_SIZE) {
                throw IOException("Corrupt .enc file: too small")
            }

            val plaintextLength = encryptedLength - GCM_IV_SIZE - GCM_TAG_SIZE
            val requestedPosition = dataSpec.position
            if (requestedPosition < 0L || requestedPosition > plaintextLength) {
                throw IOException(
                    "Requested position $requestedPosition is outside video length $plaintextLength"
                )
            }

            val stream = FileInputStream(afd.fileDescriptor)
            inputStream = stream
            val channel = stream.channel

            // Read the 96-bit GCM IV from the beginning of this asset.
            channel.position(afd.startOffset)
            val iv = ByteArray(GCM_IV_SIZE)
            var ivRead = 0
            while (ivRead < iv.size) {
                val r = stream.read(iv, ivRead, iv.size - ivRead)
                if (r < 0) throw IOException("Corrupt .enc file: missing IV")
                ivRead += r
            }

            /*
             * The payload produced by AES-GCM is encrypted with GCTR (AES-CTR).
             * For a 96-bit IV, GCM defines J0 = IV || 0x00000001 and the first
             * payload block uses inc32(J0), i.e. IV || 0x00000002.
             *
             * Android Conscrypt buffers an entire AES-GCM message until doFinal(),
             * which is exactly what caused the ~574 MB allocation/OOM in Logcat.
             * For playback we therefore stream the ciphertext payload with AES-CTR
             * and exclude the trailing 16-byte GCM tag. This keeps memory usage
             * essentially constant and supports ExoPlayer random seeks.
             */
            val blockIndex = requestedPosition / AES_BLOCK_SIZE
            val offsetInsideBlock = (requestedPosition % AES_BLOCK_SIZE).toInt()
            val counterValue = (2L + blockIndex) and 0xFFFF_FFFFL

            val counterBlock = ByteArray(AES_BLOCK_SIZE)
            System.arraycopy(iv, 0, counterBlock, 0, iv.size)
            counterBlock[12] = (counterValue ushr 24).toByte()
            counterBlock[13] = (counterValue ushr 16).toByte()
            counterBlock[14] = (counterValue ushr 8).toByte()
            counterBlock[15] = counterValue.toByte()

            val keyBytes = Base64.decode(BuildConfig.AES_KEY_B64, Base64.DEFAULT)
            val ctrCipher = Cipher.getInstance("AES/CTR/NoPadding")
            ctrCipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                IvParameterSpec(counterBlock)
            )

            // If ExoPlayer seeks into the middle of an AES block, advance the CTR
            // keystream by the same number of bytes before decrypting real data.
            if (offsetInsideBlock > 0) {
                ctrCipher.update(ByteArray(offsetInsideBlock))
            }
            cipher = ctrCipher

            // Seek directly to the requested ciphertext byte. The final GCM tag is
            // not part of the MP4 payload and is excluded via plaintextLength.
            channel.position(afd.startOffset + GCM_IV_SIZE + requestedPosition)

            val available = plaintextLength - requestedPosition
            bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                available
            } else {
                min(available, dataSpec.length)
            }

            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (t: Throwable) {
            closeResources(notifyTransferEnd = false)
            if (t is IOException) throw t
            throw IOException("Unable to open encrypted video", t)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val stream = inputStream ?: throw IOException("Data source is not open")
        val activeCipher = cipher ?: throw IOException("Decrypt cipher is not initialized")

        val toRead = min(
            readLength.toLong(),
            min(bytesRemaining, encryptedBuffer.size.toLong())
        ).toInt()

        val read = stream.read(encryptedBuffer, 0, toRead)
        if (read < 0) {
            throw IOException("Unexpected end of encrypted video")
        }

        val written = try {
            activeCipher.update(encryptedBuffer, 0, read, buffer, offset)
        } catch (t: Throwable) {
            throw IOException("Video decryption failed", t)
        }

        if (written != read) {
            throw IOException("Unexpected AES-CTR output size: read=$read written=$written")
        }

        bytesRemaining -= written.toLong()
        bytesTransferred(written)
        return written
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closeResources(notifyTransferEnd = opened)
    }

    private fun closeResources(notifyTransferEnd: Boolean) {
        try {
            inputStream?.close()
        } catch (_: Exception) {
        }
        inputStream = null

        try {
            assetFileDescriptor?.close()
        } catch (_: Exception) {
        }
        assetFileDescriptor = null

        cipher = null
        bytesRemaining = 0L
        uri = null

        if (notifyTransferEnd) {
            transferEnded()
        }
        opened = false
    }

    class Factory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource = DecryptedAssetDataSource(context)
    }
}
