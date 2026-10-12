package com.example.fieldtrack.document

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.core.content.FileProvider

/**
 * Getting a finished document out of the app.
 *
 * Sharing hands over a content URI rather than a path, so the app the farmer picks
 * gets read access to that one file and to nothing else of theirs.
 */
object DocumentShare {

    /** Builds the PDF and opens the share sheet with it attached. */
    suspend fun share(context: Context, document: Document, chooserTitle: String) {
        val file = PdfWriter.write(context, document)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, document.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /**
     * Hands the document to the system print dialog.
     *
     * Which is also how it gets saved to the phone's storage: "Save as PDF" is a
     * printer as far as Android is concerned, so one action covers both the farmer
     * who wants paper and the one who wants a copy on the phone.
     */
    suspend fun print(context: Context, document: Document) {
        val adapter = PdfWriter.printAdapter(context, document)
        val manager = context.getSystemService(PrintManager::class.java) ?: return
        manager.print(
            document.fileName,
            adapter,
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .build(),
        )
    }
}
