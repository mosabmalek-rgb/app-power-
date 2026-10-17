package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * On-device OCR for meter photos. It never writes to the reading record itself:
 * the detected value is returned to the form for the operator to review.
 */
object MeterReadingOcr {
    private const val MAX_FALLBACK_IMAGE_DIMENSION = 1800

    fun extract(context: Context, imageUri: Uri, onResult: (String?, String?) -> Unit) {
        val originalImage = runCatching { InputImage.fromFilePath(context, imageUri) }
            .getOrElse {
                onResult(null, "تعذر فتح صورة العداد. التقط الصورة مرة أخرى أو أدخل القراءة يدوياً.")
                return
            }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var completed = false

        fun finish(value: String?, message: String?) {
            if (completed) return
            completed = true
            recognizer.close()
            onResult(value, message)
        }

        fun runEnhancedPass() {
            val enhancedBitmap = runCatching { createHighContrastBitmap(context, imageUri) }.getOrNull()
            if (enhancedBitmap == null) {
                finish(null, "لم أتمكن من قراءة أرقام واضحة. قرّب الكاميرا من شاشة العداد ثم أعد المحاولة، أو أدخل الرقم يدوياً.")
                return
            }

            val fallbackImage = runCatching {
                InputImage.fromBitmap(enhancedBitmap, originalImage.rotationDegrees)
            }.getOrElse {
                enhancedBitmap.recycle()
                finish(null, "تعذر تجهيز الصورة للتحليل. أدخل القراءة يدوياً.")
                return
            }

            val task = runCatching { recognizer.process(fallbackImage) }.getOrElse {
                enhancedBitmap.recycle()
                finish(null, "تعذر استخراج القراءة من الصورة. أدخلها يدوياً.")
                return
            }
            task.addOnCompleteListener { completedTask ->
                val candidate = if (completedTask.isSuccessful) {
                    findReadingCandidate(completedTask.result?.text.orEmpty())
                } else null
                enhancedBitmap.recycle()
                if (candidate != null) {
                    finish(candidate, null)
                } else {
                    finish(null, "لم يتم العثور على رقم واضح حتى بعد تحسين الصورة. اجعل شاشة الأرقام واضحة وبإضاءة جيدة، أو أدخل القراءة يدوياً.")
                }
            }
        }

        val originalTask: Task<Text> = runCatching { recognizer.process(originalImage) }.getOrElse {
            runEnhancedPass()
            return
        }
        originalTask.addOnCompleteListener { task ->
            val candidate = if (task.isSuccessful) findReadingCandidate(task.result?.text.orEmpty()) else null
            if (candidate != null) finish(candidate, null) else runEnhancedPass()
        }
    }

    /**
     * A second local pass helps low-contrast LCD/LED displays. The original photo
     * remains untouched and is still attached to the saved field reading.
     */
    private fun createHighContrastBitmap(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_FALLBACK_IMAGE_DIMENSION) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val source = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return try {
            val grayscale = ColorMatrix().apply { setSaturation(0f) }
            val contrast = 1.65f
            val offset = -0.5f * 255f * (contrast - 1f)
            val contrastMatrix = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, offset,
                0f, contrast, 0f, 0f, offset,
                0f, 0f, contrast, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            ))
            grayscale.postConcat(contrastMatrix)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(grayscale) }
            Canvas(output).drawBitmap(source, 0f, 0f, paint)
            output
        } finally {
            source.recycle()
        }
    }

    internal fun findReadingCandidate(raw: String): String? {
        val normalized = raw.mapNotNull { char ->
            when (char) {
                in '٠'..'٩' -> ('0'.code + (char.code - '٠'.code)).toChar()
                in '۰'..'۹' -> ('0'.code + (char.code - '۰'.code)).toChar()
                ',' , '٫' -> '.'
                '٬' -> null
                else -> char
            }
        }.joinToString("")

        data class Candidate(val value: String, val score: Int)
        val candidates = normalized.lineSequence().flatMap { line ->
            Regex("(?<![\\d.])\\d{1,8}(?:\\.\\d+)?(?!\\d)")
                .findAll(line)
                .mapNotNull { match ->
                    val value = match.value
                    val decimals = value.substringAfter('.', "").length
                    if (decimals > 2 || value.toDoubleOrNull() == null) return@mapNotNull null
                    val lineContent = line.trim().replace(Regex("[^\\d.]"), "")
                    val numericOnlyLine = lineContent == value
                    val integerDigits = value.substringBefore('.').length
                    val score = integerDigits * 10 + (if (numericOnlyLine) 12 else 0) + (if (decimals > 0) 2 else 0)
                    Candidate(value, score)
                }
        }.distinctBy { it.value }.toList()

        return candidates.maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { it.value.length })?.value
    }
}
