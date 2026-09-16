package com.ntwoods.offlineplayer

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class PdfActivity : AppCompatActivity() {
    private var renderer: PdfRenderer? = null
    private var current: PdfRenderer.Page? = null
    private var tempPdfFile: File? = null

    private lateinit var iv: ImageView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf)

        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        val title = intent.getStringExtra("title") ?: "Document"
        val assetPath = intent.getStringExtra("assetPath").orEmpty()
        supportActionBar?.title = title

        iv = findViewById(R.id.pdfImage)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)

        if (assetPath.isBlank() || !assetPath.endsWith(".pdf", ignoreCase = true)) {
            Toast.makeText(this, "Invalid PDF asset", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            // PdfRenderer needs a seekable ParcelFileDescriptor. Assets are
            // therefore streamed to a temporary plain PDF file; no encryption,
            // decryption or full-file byte array is used.
            val tmp = File.createTempFile("doc_", ".pdf", cacheDir)
            assets.open(assetPath, AssetManager.ACCESS_STREAMING).use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
            tempPdfFile = tmp

            val fd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)

            showPage(0)

            btnPrev.setOnClickListener {
                current?.let { showPage((it.index - 1).coerceAtLeast(0)) }
            }
            btnNext.setOnClickListener {
                current?.let { showPage((it.index + 1).coerceAtMost(renderer!!.pageCount - 1)) }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "PDF open failed: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun showPage(index: Int) {
        val r = renderer ?: return
        current?.close()
        current = r.openPage(index)
        val page = current!!

        val scale = resources.displayMetrics.density
        val w = (page.width * scale).toInt()
        val h = (page.height * scale).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        iv.setImageBitmap(bmp)

        btnPrev.isEnabled = index > 0
        btnNext.isEnabled = index < r.pageCount - 1
    }

    override fun onDestroy() {
        current?.close()
        renderer?.close()
        tempPdfFile?.delete()
        super.onDestroy()
    }
}
