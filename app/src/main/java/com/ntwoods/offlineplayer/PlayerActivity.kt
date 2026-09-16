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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
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

        /*
         * Keep Media3's own codec ordering. Device vendors ship codec-specific
         * workarounds and preferred decoder ordering, so manually forcing a
         * particular hardware codec can make playback worse on some devices.
         * Decoder fallback remains enabled, and asynchronous MediaCodec queueing
         * reduces dropped-frame pressure on older Android versions.
         */
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .forceEnableMediaCodecAsynchronousQueueing()

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
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
             * The MP4 is a normal, unencrypted asset. SeekableAssetDataSource
             * opens the uncompressed APK asset by file descriptor and performs
             * direct random reads. There is no decryption, whole-file RAM load,
             * or temporary video copy before playback.
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
