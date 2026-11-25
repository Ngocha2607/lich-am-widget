package vn.lichthoitiet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.File
import kotlin.math.max

/** Ảnh nền lịch do người dùng chọn: thu nhỏ, xoay đúng chiều, lưu trong bộ nhớ riêng của app. */
object BackgroundImage {
    private const val MAX_SIDE = 1600

    private fun file(ctx: Context) = File(ctx.filesDir, "bg.jpg")

    /** Đọc ảnh từ [uri], thu về cạnh dài tối đa 1600px, xoay theo EXIF rồi lưu JPEG. Trả về false nếu không đọc được. */
    fun saveFrom(ctx: Context, uri: Uri): Boolean {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longSide = max(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return false
        var sample = 1
        while (longSide / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val rotation = try {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) { 0f }
        val scale = minOf(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        file(ctx).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return true
    }

    /** Ảnh nền dạng data URL để trang hiện được; rỗng nếu chưa có ảnh. */
    fun dataUrl(ctx: Context): String {
        val f = file(ctx)
        if (!f.exists()) return ""
        return "data:image/jpeg;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }
}
