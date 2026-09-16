package com.ntwoods.offlineplayer

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.min

/**
 * Read-only Media3 DataSource for large, uncompressed assets bundled in the APK.
 *
 * Unlike the generic asset:// path, this source opens the asset with openFd() and
 * seeks directly on the APK file descriptor. That keeps playback of very large
 * MP4 files off the heap, avoids copying the video to cache/internal storage and
 * gives Media3 efficient random access for MP4 parsing and user seeks.
 *
 * IMPORTANT: the asset extension must be listed in androidResources.noCompress.
 */
@UnstableApi
class SeekableAssetDataSource(context: Context) : BaseDataSource(false) {

    private val appContext = context.applicationContext

    private var uri: Uri? = null
    private var assetFileDescriptor: AssetFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var bytesRemaining: Long = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        try {
            val assetPath = dataSpec.uri.path
                ?.removePrefix("/")
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("Invalid asset URI: ${dataSpec.uri}")

            // openFd() is available only for assets that are stored uncompressed.
            // app/build.gradle.kts explicitly keeps MP4/PDF assets uncompressed.
            val afd = appContext.assets.openFd(assetPath)
            assetFileDescriptor = afd

            val assetLength = afd.length
            if (assetLength < 0L) {
                throw IOException(
                    "Asset length is unknown. Ensure .$assetPath is packaged uncompressed."
                )
            }

            val requestedPosition = dataSpec.position
            if (requestedPosition < 0L || requestedPosition > assetLength) {
                throw IOException(
                    "Requested position $requestedPosition is outside asset length $assetLength"
                )
            }

            val stream = FileInputStream(afd.fileDescriptor)
            inputStream = stream
            stream.channel.position(afd.startOffset + requestedPosition)

            val available = assetLength - requestedPosition
            bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                available
            } else {
                min(available, dataSpec.length)
            }

            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (t: Throwable) {
            closeInternal(notifyTransferEnd = false)
            if (t is IOException) throw t
            throw IOException("Unable to open local media asset", t)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val stream = inputStream ?: throw IOException("Data source is not open")
        val toRead = min(readLength.toLong(), bytesRemaining).toInt()
        val read = stream.read(buffer, offset, toRead)

        if (read == -1) {
            if (bytesRemaining > 0L) {
                throw IOException("Unexpected end of local media asset")
            }
            return C.RESULT_END_OF_INPUT
        }

        bytesRemaining -= read.toLong()
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closeInternal(notifyTransferEnd = opened)
    }

    private fun closeInternal(notifyTransferEnd: Boolean) {
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

        bytesRemaining = 0L
        uri = null

        if (notifyTransferEnd) {
            transferEnded()
        }
        opened = false
    }

    class Factory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource = SeekableAssetDataSource(context)
    }
}
