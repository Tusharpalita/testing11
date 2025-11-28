package com.nightlynexus.backgroundremover.extra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.concurrent.Executor
import kotlin.math.sqrt

internal class BackgroundRemover(private val executor: Executor) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val confidenceThreshold = 0.5f
    private val maxPixels = 3072 * 4080     // ~12MP safe limit

    private val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                    .setExecutor(executor)
                    .enableForegroundBitmap()
                    .enableForegroundConfidenceMask()
                    .build()
    )

    init {
        // Warm-up model
        val dummy = createBitmap(1, 1)
        segmenter.process(InputImage.fromBitmap(dummy, 0))
    }

    @MainThread
    interface Callback {
        fun onSuccess(
                imageUri: Uri,
                fileName: String,
                foregroundToDisplay: Bitmap,
                foregroundToSave: Bitmap
        )

        fun onFailure(
                imageUri: Uri,
                e: Exception
        )
    }

    fun removeBackground(context: Context, imageUri: Uri, callback: Callback) {
        executor.execute {

            val fileName = fileName(context.contentResolver, imageUri)
            if (fileName == null) {
                postFailure(callback, imageUri, IOException("File name not found"))
                return@execute
            }

            val inputImage = try {
                InputImage.fromFilePath(context, imageUri)
            } catch (e: Exception) {
                postFailure(callback, imageUri, e)
                return@execute
            }

            val originalBitmap = inputImage.bitmapInternal
            if (originalBitmap == null) {
                postFailure(callback, imageUri, IOException("BitmapInternal null"))
                return@execute
            }

            val w = originalBitmap.width
            val h = originalBitmap.height
            val totalPixels = w * h

            // SMALL image → no mask upscale needed
            if (totalPixels <= maxPixels) {
                processSmall(imageUri, fileName, inputImage, callback)
                return@execute
            }

            // LARGE image → safe scaled mask
            processLarge(imageUri, fileName, originalBitmap, callback)
        }
    }

    // -----------------------------------------------------------------------
    // SMALL IMAGE
    // -----------------------------------------------------------------------
    private fun processSmall(
            imageUri: Uri,
            fileName: String,
            inputImage: InputImage,
            callback: Callback
    ) {
        segmenter.process(inputImage)
                .addOnSuccessListener { result ->

                    val fg = result.foregroundBitmap
                    if (fg == null) {
                        postFailure(callback, imageUri, IOException("foregroundBitmap null"))
                        return@addOnSuccessListener
                    }

                    // SMALL → foreground = final
                    postSuccess(callback, imageUri, fileName, fg, fg)
                }
                .addOnFailureListener {
                    postFailure(callback, imageUri, it)
                }
    }

    // -----------------------------------------------------------------------
    // LARGE IMAGE
    // -----------------------------------------------------------------------
    private fun processLarge(
            imageUri: Uri,
            fileName: String,
            original: Bitmap,
            callback: Callback
    ) {
        val w = original.width
        val h = original.height

        val scale = sqrt(maxPixels / (w * h).toFloat()).coerceAtMost(1f)

        val sw = (w * scale).toInt().coerceAtLeast(1)
        val sh = (h * scale).toInt().coerceAtLeast(1)

        val scaled = original.scale(sw, sh)
        val scaledInput = InputImage.fromBitmap(scaled, 0)

        segmenter.process(scaledInput)
                .addOnSuccessListener { result ->

                    val rawMask = result.foregroundConfidenceMask
                    if (rawMask == null) {
                        postFailure(callback, imageUri, IOException("Mask null"))
                        return@addOnSuccessListener
                    }

                    val mask = convertMaskToFloatArray(rawMask, sw, sh)
                    if (mask == null) {
                        postFailure(callback, imageUri, IOException("Mask convert fail"))
                        return@addOnSuccessListener
                    }

                    executor.execute {
                        try {
                            val out = original.copy(Bitmap.Config.ARGB_8888, true)
                            val row = IntArray(w)

                            for (y in 0 until h) {
                                out.getPixels(row, 0, w, 0, y, w, 1)

                                val my = (y * sh.toFloat() / h).toInt().coerceIn(0, sh - 1)
                                val maskRowOffset = my * sw

                                for (x in 0 until w) {
                                    val mx = (x * sw.toFloat() / w).toInt().coerceIn(0, sw - 1)
                                    val idx = maskRowOffset + mx

                                    if (idx < mask.size && mask[idx] < confidenceThreshold) {
                                        row[x] = Color.TRANSPARENT
                                    }
                                }

                                out.setPixels(row, 0, w, 0, y, w, 1)
                            }

                            val displayBitmap =
                                    out.scale(sw, sh)

                            postSuccess(callback, imageUri, fileName, displayBitmap, out)

                        } catch (e: Exception) {
                            postFailure(callback, imageUri, e)
                        }
                    }

                }.addOnFailureListener {
                    postFailure(callback, imageUri, it)
                }
    }

    // -----------------------------------------------------------------------
    // MASK CONVERSION (Universal & Safe)
    // -----------------------------------------------------------------------
    private fun convertMaskToFloatArray(maskObj: Any?, w: Int, h: Int): FloatArray? {
        if (maskObj == null) return null

        val total = w * h

        return when (maskObj) {

            is FloatArray -> {
                if (maskObj.size < total) null else maskObj
            }

            is ByteBuffer -> try {
                val fb = maskObj.asFloatBuffer()
                if (fb.limit() < total) return null
                val arr = FloatArray(total)
                fb.get(arr, 0, total)
                arr
            } catch (e: Exception) {
                null
            }

            is FloatBuffer -> {
                if (maskObj.limit() < total) return null
                val arr = FloatArray(total)
                maskObj.get(arr, 0, total)
                arr
            }

            else -> null
        }
    }

    // -----------------------------------------------------------------------
    // CALLBACK HELPERS
    // -----------------------------------------------------------------------
    private fun postSuccess(
            callback: Callback,
            imageUri: Uri,
            fileName: String,
            display: Bitmap,
            save: Bitmap
    ) {
        mainHandler.post {
            callback.onSuccess(imageUri, fileName, display, save)
        }
    }

    private fun postFailure(
            callback: Callback,
            imageUri: Uri,
            e: Exception
    ) {
        mainHandler.post {
            callback.onFailure(imageUri, e)
        }
    }


}