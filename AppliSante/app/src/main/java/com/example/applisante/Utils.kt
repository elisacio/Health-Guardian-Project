package com.example.applisante

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Fonction utilitaire pour afficher le dernier caractère du mot de passe
class LastCharVisiblePasswordTransformation(private val lastCharIndex: Int) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val masked = buildString {
            text.forEachIndexed { i, c ->
                append(if (i == lastCharIndex) c else '*')
            }
        }
        return TransformedText(AnnotatedString(masked), OffsetMapping.Identity)
    }
}

fun copyAssetToInternalStorage(context: Context, filename: String) {
    val file = File(context.filesDir, filename)
    if (!file.exists()) {
        try {
            context.assets.open(filename).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}