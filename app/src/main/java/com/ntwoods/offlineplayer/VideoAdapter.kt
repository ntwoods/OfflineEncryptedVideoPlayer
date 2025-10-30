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

        // Title
        holder.vb.title.text = item.title

        // Default subtext if your layout has it (ignore if not present)
        runCatching { holder.vb.subtext.text = "Offline encrypted" }

        // Decide by path
        val isDoc = item.assetPath.startsWith("docs/", ignoreCase = true)
        if (isDoc) {
            // Button label
            holder.vb.btnPlay.text = "Open"

            // Try docs thumbnail from assets/docs/thumbs/<title>.jpg|png, else pdf icon
            val thumb =
                loadFirstBitmap(am, listOf(
                    "docs/thumbs/${item.title}.jpg",
                    "docs/thumbs/${item.title}.png"
                ))
            if (thumb != null) {
                holder.vb.thumb.setImageBitmap(thumb)
            } else {
                // fallback icon (add a vector: ic_picture_as_pdf)
                runCatching { holder.vb.thumb.setImageResource(R.drawable.ic_picture_as_pdf) }
            }
        } else {
            // Video case
            holder.vb.btnPlay.text = "Play"

            // Try videos thumbnail from assets/videos/thumbs/<title>.jpg|png
            val thumb =
                loadFirstBitmap(am, listOf(
                    "videos/thumbs/${item.title}.jpg",
                    "videos/thumbs/${item.title}.png"
                ))
            if (thumb != null) {
                holder.vb.thumb.setImageBitmap(thumb)
            } else {
                // fallback icon (add a vector: ic_video_placeholder) or keep blank bg
                runCatching { holder.vb.thumb.setImageResource(R.drawable.ic_video_placeholder) }
            }
        }

        // Clicks: whole card + button
        holder.vb.root.setOnClickListener { onPlay(item) }
        holder.vb.btnPlay.setOnClickListener { onPlay(item) }

        // Accessibility
        holder.vb.thumb.contentDescription =
            if (isDoc) "Document thumbnail for ${item.title}" else "Video thumbnail for ${item.title}"
    }

    // --- helpers ---

    private fun loadFirstBitmap(am: AssetManager, candidates: List<String>): Bitmap? {
        candidates.forEach { path ->
            try {
                am.open(path).use { stream ->
                    return BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                // try next
            }
        }
        return null
    }
}
