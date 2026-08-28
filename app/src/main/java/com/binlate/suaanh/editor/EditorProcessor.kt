package com.binlate.suaanh.editor

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.WorkerThread
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Image loading and cheap blur/pixelate generation.
 * Pure bitmap work so the exported file looks exactly like the preview.
 */
object EditorProcessor {

    /** Max dimension used for the in-memory preview bitmap to keep memory low. */
    private const val PREVIEW_MAX = 2048

    /** Maps a user strength (1..10) to the actual downscale divisor used for blur. */
    fun blurDivisor(strength: Int): Int {
        val clamped = strength.coerceIn(1, 10)
        // 1 -> 4 (subtle), 10 -> 64 (very strong); linear in between.
        return 4 + (clamped - 1) * 6
    }

    /** Decode the photo, downsampled for preview. */
    fun decodePreview(resolver: ContentResolver, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        var sample = 1
        while (bounds.outWidth / sample > PREVIEW_MAX && bounds.outHeight / sample > PREVIEW_MAX) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts) ?: throw IllegalStateException("Cannot decode image")
        } ?: throw IllegalStateException("Cannot open image")
    }

    /** Decode at (close to) full resolution for the final export. */
    fun decodeFull(resolver: ContentResolver, uri: Uri): Bitmap {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts) ?: throw IllegalStateException("Cannot decode image")
        } ?: throw IllegalStateException("Cannot open image")
    }

    /** Cheap gaussian-like blur produced by downscale + upscale. */
    fun blur(bmp: Bitmap): Bitmap {
        val sW = (bmp.width / 32).coerceAtLeast(1)
        val sH = (bmp.height / 32).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bmp, sW, sH, true)
        return Bitmap.createScaledBitmap(small, bmp.width, bmp.height, true)
    }

    // Cache of blurred variants keyed by strength, per source identity.
    private val blurCache = ConcurrentHashMap<String, Bitmap>()

    /** Clears cached blurred bitmaps (call when a new image is loaded). */
    fun clearBlurCache() {
        blurCache.values.forEach { it.recycle() }
        blurCache.clear()
    }

    /**
     * Returns a blurred copy of [bmp] for the given [strength]. Results are
     * cached per (source identity + strength) so slider changes stay cheap.
     * Keep at most two cached variants to bound memory.
     */
    @WorkerThread
    fun blurredFor(source: Bitmap, strength: Int): Bitmap {
        val key = "${source.generationId}:${strength.coerceIn(1, 10)}"
        blurCache[key]?.let { if (!it.isRecycled) return it }
        val divisor = blurDivisor(strength)
        val sW = (source.width / divisor).coerceAtLeast(1)
        val sH = (source.height / divisor).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, sW, sH, true)
        val result = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        small.recycle()
        blurCache[key] = result
        while (blurCache.size > 2) {
            val eldest = blurCache.entries.firstOrNull { it.key != key } ?: break
            eldest.value.recycle()
            blurCache.remove(eldest.key)
        }
        return result
    }

    /** Mosaic / pixelate produced by a strong nearest-neighbour downsample. */
    fun pixelate(bmp: Bitmap): Bitmap {
        val block = if (bmp.width >= 600) 18 else 12
        val sW = (bmp.width / block).coerceAtLeast(1)
        val sH = (bmp.height / block).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bmp, sW, sH, false)
        return Bitmap.createScaledBitmap(small, bmp.width, bmp.height, false)
    }

    internal fun shorter(bmp: Bitmap): Int = min(bmp.width, bmp.height)
}