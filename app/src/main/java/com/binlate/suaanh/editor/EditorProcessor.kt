package com.binlate.suaanh.editor

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.min

/**
 * Image loading and cheap blur/pixelate generation.
 * Pure bitmap work so the exported file looks exactly like the preview.
 */
object EditorProcessor {

    /** Max dimension used for the in-memory preview bitmap to keep memory low. */
    private const val PREVIEW_MAX = 2048

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