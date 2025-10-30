package com.ntwoods.offlineplayer

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.ntwoods.offlineplayer.crypto.DecryptedAssetDataSource
import com.ntwoods.offlineplayer.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra("title") ?: "Video"
        val assetPath = intent.getStringExtra("assetPath") ?: ""
        supportActionBar?.title = title

        player = ExoPlayer.Builder(this).build()
        findViewById<PlayerView>(R.id.playerView).player = player

        player?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Playback error: ${error.cause?.message ?: error.message}",
                    Toast.LENGTH_LONG
                ).show()
                error.printStackTrace()
            }
        })

        try {
            val uri = Uri.parse("asset:///$assetPath")
            val factory = DecryptedAssetDataSource.Factory(this)
            val mediaItem = MediaItem.fromUri(uri)
            val mediaSource = ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
            player?.setMediaSource(mediaSource)
            player?.prepare()
            player?.playWhenReady = true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Play failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}
