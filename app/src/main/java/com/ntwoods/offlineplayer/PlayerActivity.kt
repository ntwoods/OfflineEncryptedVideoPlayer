package com.ntwoods.offlineplayer

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ntwoods.offlineplayer.databinding.ActivityPlayerBinding

@UnstableApi
class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var assetPath: String

    private var player: ExoPlayer? = null
    private var playbackPosition = 0L
    private var playWhenReady = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.playerView.keepScreenOn = true

        val title = intent.getStringExtra("title") ?: "Video"
        assetPath = intent.getStringExtra("assetPath").orEmpty()
        supportActionBar?.title = title

        if (assetPath.isBlank() || !assetPath.endsWith(".mp4", ignoreCase = true)) {
            Toast.makeText(this, "Invalid MP4 asset", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        playbackPosition = savedInstanceState?.getLong(STATE_POSITION) ?: 0L
        playWhenReady = savedInstanceState?.getBoolean(STATE_PLAY_WHEN_READY) ?: true
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    private fun initializePlayer() {
        if (player != null || isFinishing) return

        logAvailableAvcDecoders()

        /*
         * The bundled production video is 3840x2160 H.264 High@5.1 at 30 fps
         * and ~28 Mbps. On tablets this should stay on the vendor hardware
         * MediaCodec path. We preserve decoder fallback, but explicitly order
         * hardware/vendor codecs ahead of software codecs so a software AVC
         * decoder is not chosen before a capable hardware decoder.
         */
        val hardwareFirstCodecSelector = MediaCodecSelector {
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder ->
            val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )

            if (!mimeType.startsWith("video/", ignoreCase = true)) {
                decoders
            } else {
                decoders.sortedWith(
                    compareByDescending<MediaCodecInfo> { it.hardwareAccelerated }
                        .thenBy { it.softwareOnly }
                        .thenByDescending { it.vendor }
                )
            }
        }

        val renderersFactory = DefaultRenderersFactory(this)
            .setMediaCodecSelector(hardwareFirstCodecSelector)
            .setEnableDecoderFallback(true)
            .forceEnableMediaCodecAsynchronousQueueing()

        /*
         * A 28 Mbps local file consumes roughly 3.5 MB/s. Keep a healthy
         * time-based read-ahead window so APK/flash-storage scheduling cannot
         * starve the decoder during short I/O stalls. The buffer is still far
         * smaller than the media file and the video is never loaded as one blob.
         */
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                8_000,   // min buffer
                30_000,  // max buffer
                1_500,   // start/resume after seek
                3_000    // resume after an actual rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setVideoChangeFrameRateStrategy(
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            )
            .build()

        player = exoPlayer
        binding.playerView.player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    TAG,
                    "Playback error ${error.errorCodeName}: ${error.cause?.message ?: error.message}",
                    error
                )
                Toast.makeText(
                    this@PlayerActivity,
                    "Playback error (${error.errorCodeName}): ${error.cause?.message ?: error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                Log.i(
                    TAG,
                    "VIDEO_DECODER=$decoderName init=${initializationDurationMs}ms " +
                        "device=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL} " +
                        "sdk=${android.os.Build.VERSION.SDK_INT}"
                )
            }

            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                Log.w(
                    TAG,
                    "DROPPED_FRAMES=$droppedFrames over=${elapsedMs}ms " +
                        "position=${eventTime.currentPlaybackPositionMs}ms"
                )
            }

            override fun onVideoCodecError(
                eventTime: AnalyticsListener.EventTime,
                videoCodecError: Exception
            ) {
                Log.e(TAG, "VIDEO_CODEC_ERROR=${videoCodecError.message}", videoCodecError)
            }
        })

        try {
            /*
             * Normal unencrypted MP4, read directly through openFd()/FileChannel.
             * There is no decryption, no whole-file RAM allocation and no temp
             * video copy. This is the fastest seekable path for a bundled asset.
             */
            val encodedPath = Uri.encode(assetPath, "/")
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse("asset:///$encodedPath"))
                .setMimeType(MimeTypes.VIDEO_MP4)
                .build()

            val mediaSource = ProgressiveMediaSource.Factory(
                SeekableAssetDataSource.Factory(applicationContext)
            ).createMediaSource(mediaItem)

            exoPlayer.setMediaSource(mediaSource)
            if (playbackPosition > 0L) {
                exoPlayer.seekTo(playbackPosition)
            }
            exoPlayer.playWhenReady = playWhenReady
            exoPlayer.prepare()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to prepare local MP4", e)
            Toast.makeText(this, "Play failed: ${e.message}", Toast.LENGTH_LONG).show()
            releasePlayer()
        }
    }

    private fun logAvailableAvcDecoders() {
        runCatching {
            MediaCodecSelector.DEFAULT
                .getDecoderInfos(MimeTypes.VIDEO_H264, false, false)
                .forEachIndexed { index, codec ->
                    Log.i(
                        TAG,
                        "AVC_CODEC[$index]=${codec.name} " +
                            "hardware=${codec.hardwareAccelerated} " +
                            "software=${codec.softwareOnly} vendor=${codec.vendor}"
                    )
                }
        }.onFailure {
            Log.w(TAG, "Unable to enumerate AVC decoders", it)
        }
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            playWhenReady = exoPlayer.playWhenReady
            binding.playerView.player = null
            exoPlayer.release()
        }
        player = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_POSITION, player?.currentPosition ?: playbackPosition)
        outState.putBoolean(STATE_PLAY_WHEN_READY, player?.playWhenReady ?: playWhenReady)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    companion object {
        private const val TAG = "NTWoodsPlayer"
        private const val STATE_POSITION = "playback_position"
        private const val STATE_PLAY_WHEN_READY = "play_when_ready"
    }
}
