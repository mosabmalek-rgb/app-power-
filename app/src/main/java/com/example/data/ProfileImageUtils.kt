package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Keeps profile photos small enough for the dashboard avatar while storing them
 * inside app-private storage. The selected source image is never modified.
 */
object ProfileImageUtils {
    private const val MAX_PROFILE_IMAGE_DIMENSION = 1024

    fun decodeSampledFile(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_PROFILE_IMAGE_DIMENSION) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
    }

    fun saveFromUri(context: Context, uri: Uri): String {
        val directory = File(context.filesDir, "profile_images").apply {
            if (!exists() && !mkdirs()) error("تعذر إنشاء مجلد صورة الملف.")
        }
        val sourceFile = File(directory, "source_${System.nanoTime()}.tmp")
        var targetFile: File? = null
        var bitmap: Bitmap? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(sourceFile).use { output -> input.copyTo(output) }
            } ?: error("تعذر قراءة الصورة المختارة.")

            bitmap = decodeSampledFile(sourceFile.absolutePath)
                ?: error("ملف الصورة غير صالح أو لا يحتوي على صورة قابلة للعرض.")

            targetFile = File(directory, "profile_${System.currentTimeMillis()}_${System.nanoTime()}.jpg")
            FileOutputStream(targetFile).use { output ->
                if (!bitmap!!.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    error("تعذر حفظ صورة الملف.")
                }
                output.flush()
            }
            return targetFile!!.absolutePath
        } catch (error: Throwable) {
            targetFile?.delete()
            throw error
        } finally {
            bitmap?.recycle()
            sourceFile.delete()
        }
    }
}
