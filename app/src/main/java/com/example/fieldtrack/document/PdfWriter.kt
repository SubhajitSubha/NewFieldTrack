package com.example.fieldtrack.document

import android.content.Context
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PdfLayoutCallback
import android.print.PdfWriteCallback
import android.print.PrintDocumentAdapter
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Something went wrong turning a document into a PDF. */
class PdfError(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Turns a [Document] into a PDF file on disk.
 *
 * Goes through a WebView and the platform's own print pipeline rather than drawing to
 * a Canvas. The documents are tables with conditional rows and a letterhead that
 * changes shape depending on what the business filled in; laying that out by hand in
 * Canvas calls would be hundreds of lines of measuring text, and it would drift from
 * the design the first time a row was added.
 *
 * Everything here runs offline. The page pulls its typeface out of the app's own
 * resources and makes no network request, because these get made in a field.
 */
object PdfWriter {

    /** A4 at 72dpi in the print system's units (thousandths of an inch). */
    private val A4 = PrintAttributes.MediaSize.ISO_A4

    /**
     * Writes [document] to a PDF in the app's cache and returns the file.
     *
     * Must be called from a coroutine; the WebView parts are moved to the main thread
     * internally, since a WebView may only be touched there.
     */
    suspend fun write(context: Context, document: Document): File {
        val html = DocumentHtml.render(document)
        val target = documentFile(context, document.fileName)

        val webView = withContext(Dispatchers.Main) {
            loadOffscreen(context.applicationContext, html)
        }
        try {
            val adapter = withContext(Dispatchers.Main) {
                webView.createPrintDocumentAdapter(document.fileName)
            }
            writeAdapterTo(adapter, target)
        } finally {
            withContext(Dispatchers.Main) { webView.destroy() }
        }
        return target
    }

    /**
     * The same document, handed to the system print dialog instead of to a file.
     *
     * "Save as PDF" is a printer as far as Android is concerned, so this covers both
     * paper and a copy saved to the phone's own storage.
     *
     * The WebView has to outlive this call — the print dialog drives the adapter
     * later, on its own schedule — so it is held until the next print replaces it.
     * It is built on the application context, so holding it cannot pin an Activity.
     */
    suspend fun printAdapter(context: Context, document: Document): PrintDocumentAdapter {
        val html = DocumentHtml.render(document)
        val webView = withContext(Dispatchers.Main) {
            printing?.destroy()
            loadOffscreen(context.applicationContext, html).also { printing = it }
        }
        return withContext(Dispatchers.Main) {
            webView.createPrintDocumentAdapter(document.fileName)
        }
    }

    /** Kept alive for the system print dialog. See [printAdapter]. */
    private var printing: WebView? = null

    /** Where a generated document lives. Cache: a sent bill can always be remade. */
    fun documentFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "documents").apply { mkdirs() }
        return File(dir, "$name.pdf")
    }

    /**
     * Renders the HTML in a detached WebView and waits for it to finish.
     *
     * The WebView is never attached to the window. It still lays out, because the
     * print adapter drives layout itself from the print attributes rather than from
     * the view's measured size.
     */
    private suspend fun loadOffscreen(context: Context, html: String): WebView =
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(context)
            webView.settings.apply {
                // The page is ours and entirely local: it needs to read the bundled
                // font out of android_res and nothing else at all.
                allowFileAccess = true
                javaScriptEnabled = false
                blockNetworkLoads = true
            }
            webView.webViewClient = object : WebViewClient() {
                private var done = false
                override fun onPageFinished(view: WebView, url: String) {
                    if (done) return
                    done = true
                    if (continuation.isActive) continuation.resume(webView)
                }
            }
            continuation.invokeOnCancellation { webView.destroy() }
            webView.loadDataWithBaseURL(
                "file:///android_res/",
                html,
                "text/html",
                "utf-8",
                null,
            )
        }

    /**
     * Drives a [PrintDocumentAdapter] by hand to produce a file.
     *
     * This is the same two-step the system print dialog performs — lay out against
     * the paper, then write the pages — done here so the result is a file we can
     * attach to a WhatsApp message rather than a dialog the farmer has to navigate.
     */
    private suspend fun writeAdapterTo(adapter: PrintDocumentAdapter, target: File) {
        val attributes = PrintAttributes.Builder()
            .setMediaSize(A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                adapter.onLayout(
                    null,
                    attributes,
                    CancellationSignal(),
                    object : PdfLayoutCallback() {
                        override fun onLayoutFinished(info: android.print.PrintDocumentInfo, changed: Boolean) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onLayoutFailed(error: CharSequence?) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    PdfError("Could not lay the document out: $error")
                                )
                            }
                        }
                    },
                    null,
                )
            }

            val descriptor = ParcelFileDescriptor.open(
                target,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
                    or ParcelFileDescriptor.MODE_TRUNCATE,
            )
            try {
                suspendCancellableCoroutine { continuation ->
                    adapter.onWrite(
                        arrayOf(PageRange.ALL_PAGES),
                        descriptor,
                        CancellationSignal(),
                        object : PdfWriteCallback() {
                            override fun onWriteFinished(pages: Array<out PageRange>) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onWriteFailed(error: CharSequence?) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        PdfError("Could not write the document: $error")
                                    )
                                }
                            }

                            override fun onWriteCancelled() {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(PdfError("Cancelled"))
                                }
                            }
                        },
                    )
                }
            } finally {
                runCatching { descriptor.close() }
            }
        }
    }
}
