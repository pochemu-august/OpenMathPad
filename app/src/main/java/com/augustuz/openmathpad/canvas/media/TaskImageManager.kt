package com.augustuz.openmathpad.canvas.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Result data holder for imported task image metadata.
 */
data class ImportedTaskImage(
    val filePath: String,
    val width: Float,
    val height: Float
)

/**
 * Manages secure importing, single-pass streaming, downscaling, and internal persistence of problem images.
 */
object TaskImageManager {
    private const val MAX_DIMENSION = 2048
    private const val TASK_IMAGES_DIR = "task_images"

    /**
     * Safely copies an external image Uri stream in a single pass into internal app storage,
     * protecting against stream exhaustion or ContentProvider permission expiration.
     * Decodes with safe downsampling (max 2048px) to prevent OOM, and saves as a PNG.
     *
     * @param context Application context.
     * @param sourceUri Source Uri from gallery or document picker.
     * @return [ImportedTaskImage] containing absolute file path and dimensions, or null on failure.
     */
    fun importAndDownscaleImage(context: Context, sourceUri: Uri): ImportedTaskImage? {
        val imagesDir = File(context.filesDir, TASK_IMAGES_DIR).apply {
            if (!exists()) mkdirs()
        }
        val tempRawFile = File(imagesDir, "raw_${UUID.randomUUID()}.tmp")

        return try {
            val contentResolver = context.contentResolver

            // 1. Single-pass stream copy into local internal cache file
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempRawFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (!tempRawFile.exists() || tempRawFile.length() <= 0) {
                tempRawFile.delete()
                return null
            }

            // 2. Decode bounds from local file to inspect dimensions without heap allocation
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(tempRawFile.absolutePath, boundsOptions)

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) {
                tempRawFile.delete()
                return null
            }

            // 3. Compute integer downsample factor so max dimension <= 2048
            var sampleSize = 1
            while ((originalWidth / sampleSize) > MAX_DIMENSION || (originalHeight / sampleSize) > MAX_DIMENSION) {
                sampleSize *= 2
            }

            // 4. Decode sampled bitmap safely from the local file
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = BitmapFactory.decodeFile(tempRawFile.absolutePath, decodeOptions)
            tempRawFile.delete() // Clean up raw temp file
            if (sampledBitmap == null) return null

            // 5. Persist as PNG into internal app storage (context.filesDir/task_images/${UUID.randomUUID()}.png)
            val finalFile = File(imagesDir, "${UUID.randomUUID()}.png")
            FileOutputStream(finalFile).use { out ->
                sampledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val finalWidth = sampledBitmap.width.toFloat()
            val finalHeight = sampledBitmap.height.toFloat()
            sampledBitmap.recycle()

            ImportedTaskImage(
                filePath = finalFile.absolutePath,
                width = finalWidth,
                height = finalHeight
            )
        } catch (e: Exception) {
            tempRawFile.delete()
            null
        }
    }
}
