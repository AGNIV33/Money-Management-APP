package com.agniv.hisaab.feature.expenses.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads the text on a picture with ML Kit's bundled model (PRD F14): no internet, and the picture never
 * leaves the phone. Pictures are scaled down first so a 12 MP photo cannot run a 2 GB phone out of memory.
 */
internal object TextScanner {
    private const val MAX_SIDE = 2000
    private const val DEGREES_90 = 90f
    private const val DEGREES_180 = 180f
    private const val DEGREES_270 = 270f

    suspend fun read(
        context: Context,
        uri: Uri,
    ): String {
        val bitmap = withContext(Dispatchers.IO) { loadScaled(context, uri) } ?: return ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { cont ->
                recognizer
                    .process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { cont.resume(it.text) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    private fun loadScaled(
        context: Context,
        uri: Uri,
    ): Bitmap? {
        val decoded = decodeSampled(context, uri) ?: return null
        val degrees = exifDegrees(context, uri)
        val rotated =
            if (degrees == 0f) {
                decoded
            } else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
            }
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun decodeSampled(
        context: Context,
        uri: Uri,
    ): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    private fun exifDegrees(
        context: Context,
        uri: Uri,
    ): Float =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> DEGREES_90
                ExifInterface.ORIENTATION_ROTATE_180 -> DEGREES_180
                ExifInterface.ORIENTATION_ROTATE_270 -> DEGREES_270
                else -> 0f
            }
        } ?: 0f
}
