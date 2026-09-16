package com.ntwoods.offlineplayer

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ntwoods.offlineplayer.databinding.ItemVideoBinding

class VideoAdapter(
    private val items: List<VideoItem>,
    private val onPlay: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    inner class VH(val vb: ItemVideoBinding) : RecyclerView.ViewHolder(vb.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vb = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(vb)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.vb.root.context
        val am: AssetManager = ctx.assets
        val isDoc = item.assetPath.startsWith("docs/", ignoreCase = true)

        holder.vb.title.text = item.title

        // Encryption/decryption is no longer part of the app. Keep the card
        // metadata accurate and neutral for both plain PDFs and plain videos.
        runCatching {
            holder.vb.subtext.text = if (isDoc) "PDF • Available offline" else "Video • Available offline"
        }

        if (isDoc) {
            holder.vb.btnPlay.text = "Open"

            val thumb = loadFirstBitmap(
                am,
                listOf(
                    "docs/thumbs/${item.title}.jpg",
                    "docs/thumbs/${item.title}.png"
                )
            )
            if (thumb != null) {
                holder.vb.thumb.setImageBitmap(thumb)
            } else {
                runCatching { holder.vb.thumb.setImageResource(R.drawable.ic_picture_as_pdf) }
            }
        } else {
            holder.vb.btnPlay.text = "Play"

            val thumb = loadFirstBitmap(
                am,
                listOf(
                    "videos/thumbs/${item.title}.jpg",
                    "videos/thumbs/${item.title}.png"
                )
            )
            if (thumb != null) {
                holder.vb.thumb.setImageBitmap(thumb)
            } else {
                runCatching { holder.vb.thumb.setImageResource(R.drawable.ic_video_placeholder) }
            }
        }

        holder.vb.root.setOnClickListener { onPlay(item) }
        holder.vb.btnPlay.setOnClickListener { onPlay(item) }

        holder.vb.thumb.contentDescription =
            if (isDoc) "Document thumbnail for ${item.title}" else "Video thumbnail for ${item.title}"
    }

    private fun loadFirstBitmap(am: AssetManager, candidates: List<String>): Bitmap? {
        candidates.forEach { path ->
            try {
                am.open(path).use { stream ->
                    return BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                // Try the next thumbnail format.
            }
        }
        return null
    }
}
