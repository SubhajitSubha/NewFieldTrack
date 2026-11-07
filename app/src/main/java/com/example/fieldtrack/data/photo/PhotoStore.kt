package com.example.fieldtrack.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.tasks.await

/**
 * Photos a crew takes as proof, kept on the phone first and sent when there is a signal.
 *
 * A job names a photo by its id only. The picture itself is shrunk to a size a phone can send
 * in a field (about 150 KB), saved here, and queued; a worker sends it when a connection comes.
 * Anyone else who opens the job fetches it by the same id, once, and keeps a copy. Photos are
 * filed under the business, and the storage rules let only its members read or write them.
 *
 * A photo that has not reached the server yet is still shown on the phone that took it.
 */
class PhotoStore(private val context: Context, private val storage: () -> FirebaseStorage = { FirebaseStorage.getInstance() }) {

    private val dir get() = File(context.filesDir, "photos").apply { mkdirs() }
    private val cache get() = File(context.cacheDir, "photos").apply { mkdirs() }
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Where a photo's bytes are held on this phone, if they are. */
    fun localFile(photoId: String): File? =
        listOf(File(dir, "$photoId.jpg"), File(cache, "$photoId.jpg")).firstOrNull { it.exists() }

    /**
     * Takes a freshly captured picture, turns it the right way up, shrinks it, files it under
     * [orgId] and queues it to be sent. Returns the id to write on the job, or null if the
     * picture could not be read.
     */
    fun ingest(orgId: String, captured: File): String? {
        val bitmap = decodeUpright(captured) ?: return null
        val id = UUID.randomUUID().toString()
        File(dir, "$id.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        bitmap.recycle()
        captured.delete()
        prefs.edit().putStringSet(KEY_PENDING, pending() + "$orgId/$id").apply()
        schedule()
        return id
    }

    /** The photo as a file this phone can show, fetching it once if it has never been here. Null if it cannot be had. */
    suspend fun open(orgId: String, photoId: String): File? {
        localFile(photoId)?.let { return it }
        val target = File(cache, "$photoId.jpg")
        return runCatching {
            storage().reference.child(remotePath(orgId, photoId)).getFile(target).await()
            target.takeIf { it.exists() && it.length() > 0 }
        }.getOrNull().also { if (it == null) target.delete() }
    }

    fun pending(): Set<String> = prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty()

    /** Sends whatever is waiting. Returns true when nothing is left waiting. */
    suspend fun sendPending(): Boolean {
        for (entry in pending()) {
            val orgId = entry.substringBefore('/')
            val id = entry.substringAfter('/')
            val file = File(dir, "$id.jpg")
            if (!file.exists()) { forget(entry); continue }
            val sent = runCatching {
                storage().reference.child(remotePath(orgId, id)).putFile(android.net.Uri.fromFile(file)).await()
            }.isSuccess
            if (sent) forget(entry)
        }
        return pending().isEmpty()
    }

    private fun forget(entry: String) {
        prefs.edit().putStringSet(KEY_PENDING, pending() - entry).apply()
    }

    fun schedule() {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PhotoUploadWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }

    private fun decodeUpright(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val turn = when (runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = MAX_SIDE.toFloat() / maxOf(raw.width, raw.height)
        val matrix = Matrix().apply {
            if (turn != 0f) postRotate(turn)
            if (scale < 1f) postScale(scale, scale)
        }
        return if (matrix.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }

    companion object {
        const val PREFS = "fieldtrack.photos"
        private const val KEY_PENDING = "pending"
        private const val WORK_NAME = "photo-upload"
        private const val MAX_SIDE = 1280
        private const val QUALITY = 70
        fun remotePath(orgId: String, photoId: String) = "orgs/$orgId/photos/$photoId.jpg"
    }
}

/** Sends the photos waiting to go. Tries again later if any could not be sent. */
class PhotoUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? com.example.fieldtrack.FieldFlowApplication ?: return Result.success()
        return if (app.photoStore.sendPending()) Result.success() else Result.retry()
    }
}
