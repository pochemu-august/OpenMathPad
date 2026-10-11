package com.augustuz.openmathpad.canvas.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages secure importing, downscaling, and internal persistence of problem images.
 */
object TaskImageManager {
    private const val MAX_DIMENSION = 2048
    private const val TASKS_DIR = "tasks"

    /**
     * Safely copies an external image Uri into internal app storage (cacheDir/tasks/),
     * downscaling to at most 2048x2048 to prevent memory exhaustion (OOM), and returns
     * the persistent absolute file path.
     */
    fun importAndDownscaleImage(context: Context, sourceUri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver

            // 1. Decode bounds only to inspect dimensions without heap allocation
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            } ?: return null

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return null

            // 2. Compute integer downsample factor so max dimension <= 2048
            var sampleSize = 1
            while ((originalWidth / sampleSize) > MAX_DIMENSION || (originalHeight / sampleSize) > MAX_DIMENSION) {
                sampleSize *= 2
            }

            // 3. Decode sampled bitmap safely
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            // 4. Persist in internal app storage (cacheDir/tasks/)
            val tasksDir = File(context.cacheDir, TASKS_DIR).apply {
                if (!exists()) mkdirs()
            }
            val destFile = File(tasksDir, "task_${UUID.randomUUID()}.jpg")
            FileOutputStream(destFile).use { out ->
                sampledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            sampledBitmap.recycle()

            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
