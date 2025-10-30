package com.ntwoods.offlineplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager

class VideosFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_videos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvVideos)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val items = loadVideoList()
        rv.adapter = VideoAdapter(items) { item ->
            val i = Intent(requireContext(), PlayerActivity::class.java)
            i.putExtra("title", item.title)
            i.putExtra("assetPath", item.assetPath)
            startActivity(i)
        }
    }

    private fun loadVideoList(): List<VideoItem> {
        val am = requireContext().assets
        val names = am.list("videos")
            ?.filter { it.lowercase().endsWith(".enc") }
            ?.sorted()
            ?: emptyList()
        return names.map { f ->
            VideoItem(f.removeSuffix(".enc").removeSuffix(".ENC"), "videos/$f")
        }
    }
}
