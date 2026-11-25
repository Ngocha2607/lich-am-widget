package vn.lichthoitiet

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Vẽ ảnh "Hôm nay" (1080×1350) để chia sẻ Zalo, Facebook. */
object ShareCard {
    private const val W = 1080
    private const val H = 1350

    private fun colors(now: LocalDateTime, weather: WeatherData?): IntArray {
        val t = now.hour * 60 + now.minute
        val rainy = weather != null && weather.codeNow >= 51
        return when {
            t < 5 * 60 + 30 || t >= 18 * 60 + 30 -> intArrayOf(0xFF141A33.toInt(), 0xFF243459.toInt())
            rainy -> intArrayOf(0xFF2B3447.toInt(), 0xFF4A5A78.toInt())
            t < 8 * 60 + 30 -> intArrayOf(0xFFE0695E.toInt(), 0xFFF2A65B.toInt())
            t < 16 * 60 + 30 -> intArrayOf(0xFF2F6FE0.toInt(), 0xFF3DA3E8.toInt())
            else -> intArrayOf(0xFF6B4DA8.toInt(), 0xFFE96A5C.toInt())
        }
    }

    private fun paint(size: Float, bold: Boolean = false, alpha: Int = 255): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        this.alpha = alpha
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setShadowLayer(6f, 0f, 2f, 0x55000000)
    }

    fun render(activity: Activity): Bitmap {
        val prefs = Prefs(activity)
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val l = LunarCalendar.convert(today)
        val w = WeatherRepo.load(activity)
        val tips = SuggestionEngine.build(today, now, w, prefs.spiritual, EventStore.upcoming(Profile.eventsWithBirthday(activity), today))

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cols = colors(now, w)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, H.toFloat(), W.toFloat(), 0f, cols[0], cols[1], Shader.TileMode.CLAMP)
        })

        val x = 80f
        // Dương lịch
        c.drawText("${WidgetRenderer.dayOfWeek(today).uppercase()}, ${today.dayOfMonth}/${today.monthValue}/${today.year}", x, 150f, paint(44f, true, 220).apply { letterSpacing = 0.06f })

        // Âm lịch
        val big = paint(300f, true)
        c.drawText("${l.day}", x, 440f, big)
        val bigW = big.measureText("${l.day}")
        c.drawText("tháng ${LunarCalendar.monthName(l.month)}${if (l.leap) " nhuận" else ""}", x + bigW + 36f, 350f, paint(76f, true))
        c.drawText("${LunarCalendar.canChiYear(l.year)}  ${LunarCalendar.moonPhase(l.day)}", x + bigW + 36f, 432f, paint(56f, false, 230))

        val special = SuggestionEngine.specialDayName(l)
        val sub = mutableListOf<String>()
        if (special != null) sub += "✦ $special"
        if (prefs.spiritual) sub += if (LunarCalendar.isHoangDao(l)) "Hoàng đạo" else "Hắc đạo"
        sub += "Tiết ${LunarCalendar.currentTietKhi(l.jd)}"
        c.drawText(sub.joinToString(" · "), x, 530f, paint(44f, false, 230).apply { if (special != null) color = 0xFFFFD966.toInt() })

        // Kẻ ngang
        c.drawRect(x, 590f, W - x, 593f, Paint().apply { color = 0x55FFFFFF })

        // Thời tiết
        if (w != null) {
            c.drawText(WeatherText.icon(w.codeNow), x, 740f, paint(120f))
            c.drawText("${w.tempNow}°", x + 170f, 740f, paint(150f, true))
            val tw = paint(150f, true).measureText("${w.tempNow}°")
            c.drawText("${WeatherText.label(w.codeNow)} · ${w.todayMin}–${w.todayMax}°", x + 170f + tw + 30f, 690f, paint(44f, false, 230))
            c.drawText("mưa ${w.todayRain}% · ${w.city}", x + 170f + tw + 30f, 748f, paint(40f, false, 200))
        } else {
            c.drawText("Chưa có dữ liệu thời tiết", x, 720f, paint(44f, false, 200))
        }

        // Gợi ý
        var y = 850f
        val pill = Paint().apply { color = 0x33FFFFFF }
        tips.take(3).forEach { tip ->
            val tp = paint(44f, false)
            val layout = StaticLayout.Builder.obtain(tip.short, 0, tip.short.length, tp, (W - 2 * x - 60).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(2).build()
            val hgt = layout.height + 40f
            c.drawRoundRect(RectF(x, y, W - x, y + hgt), 28f, 28f, pill)
            c.save(); c.translate(x + 30f, y + 20f); layout.draw(c); c.restore()
            y += hgt + 20f
        }

        c.drawText("Lịch & Thời tiết", x, H - 70f, paint(34f, false, 170))
        return bmp
    }

    /** Lưu ảnh vào cache rồi mở bảng chia sẻ. */
    fun share(activity: Activity) {
        val bmp = render(activity)
        val dir = File(activity.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "hom-nay.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, "Chia sẻ hôm nay"))
    }
}
