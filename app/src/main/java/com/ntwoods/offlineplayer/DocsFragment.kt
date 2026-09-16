package com.ntwoods.offlineplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager

class DocsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_docs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvDocs)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val items = loadDocs()
        rv.adapter = VideoAdapter(items) { item ->
            val i = Intent(requireContext(), PdfActivity::class.java)
            i.putExtra("title", item.title)
            i.putExtra("assetPath", item.assetPath)
            startActivity(i)
        }
    }

    private fun loadDocs(): List<VideoItem> =
        (requireContext().assets.list("docs") ?: emptyArray())
            .filter { it.endsWith(".pdf", ignoreCase = true) }
            .sorted()
            .map { fileName ->
                VideoItem(
                    title = fileName.substringBeforeLast('.'),
                    assetPath = "docs/$fileName"
                )
            }
}
