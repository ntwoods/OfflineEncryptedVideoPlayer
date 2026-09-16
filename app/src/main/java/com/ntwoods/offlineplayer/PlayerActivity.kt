package com.ntwoods.offlineplayer

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.ntwoods.offlineplayer.databinding.ActivityPlayerBinding

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

        // Keep decoder fallback enabled so devices with a problematic hardware
        // AVC decoder can automatically try another compatible decoder.
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory).build()
        player = exoPlayer
        binding.playerView.player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Playback error (${error.errorCodeName}): ${error.cause?.message ?: error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        try {
            // The MP4 is stored as a normal, unencrypted asset. Media3 handles
            // asset:// URIs directly; no custom DataSource, decryption or temp
            // video copy is involved.
            val encodedPath = Uri.encode(assetPath, "/")
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse("asset:///$encodedPath"))
                .setMimeType(MimeTypes.VIDEO_MP4)
                .build()

            exoPlayer.setMediaItem(mediaItem)
            if (playbackPosition > 0L) {
                exoPlayer.seekTo(playbackPosition)
            }
            exoPlayer.playWhenReady = playWhenReady
            exoPlayer.prepare()
        } catch (e: Exception) {
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
        private const val STATE_POSITION = "playback_position"
        private const val STATE_PLAY_WHEN_READY = "play_when_ready"
    }
}
