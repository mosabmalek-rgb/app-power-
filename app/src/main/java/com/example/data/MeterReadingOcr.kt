package com.example.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition

object MeterReadingOcr {
    fun extract(context: Context, imageUri: Uri, onResult: (String?, String?) -> Unit) {
        runCatching { InputImage.fromFilePath(context, imageUri) }
            .onFailure { onResult(null, "تعذر فتح صورة العداد.") }
            .onSuccess { image ->
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        recognizer.close()
                        val value = findReadingCandidate(result.text)
                        onResult(value, if (value == null) "لم يتم العثور على رقم واضح. أدخل القراءة يدوياً ثم راجعها." else null)
                    }
                    .addOnFailureListener {
                        recognizer.close()
                        onResult(null, "تعذر استخراج القراءة من الصورة. أدخلها يدوياً.")
                    }
            }
    }

    private fun findReadingCandidate(raw: String): String? {
        val normalized = raw.map { char ->
            when (char) {
                in '٠'..'٩' -> ('0'.code + (char.code - '٠'.code)).toChar()
                in '۰'..'۹' -> ('0'.code + (char.code - '۰'.code)).toChar()
                ',' -> '.'
                else -> char
            }
        }.joinToString("")
        val candidates = Regex("(?<!\\d)\\d{1,8}(?:\\.\\d{1,3})?(?!\\d)")
            .findAll(normalized)
            .map { it.value }
            .filter { it.toDoubleOrNull()?.let { number -> number >= 0.0 } == true }
            .toList()
        return candidates.maxWithOrNull(compareBy<String> { it.substringBefore('.').length }.thenBy { it.length })
    }
}
