package vn.lichthoitiet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class LichWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = WidgetRenderer.build(context)
        ids.forEach { manager.updateAppWidget(it, views) }

        WeatherWorker.schedule(context)
        val cached = WeatherRepo.load(context)
        if (cached == null || cached.isStale(2 * 60 * 60 * 1000L)) WeatherWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        WeatherWorker.schedule(context)
        WeatherWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        WeatherWorker.cancel(context)
    }

    companion object {
        /** Vẽ lại tất cả widget đang có trên màn hình. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LichWidget::class.java))
            if (ids.isEmpty()) return
            val views = WidgetRenderer.build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}

object WidgetRenderer {
    private val THU = mapOf(
        DayOfWeek.MONDAY to "Thứ Hai", DayOfWeek.TUESDAY to "Thứ Ba", DayOfWeek.WEDNESDAY to "Thứ Tư",
        DayOfWeek.THURSDAY to "Thứ Năm", DayOfWeek.FRIDAY to "Thứ Sáu", DayOfWeek.SATURDAY to "Thứ Bảy",
        DayOfWeek.SUNDAY to "Chủ nhật",
    )

    fun dayOfWeek(d: LocalDate): String = THU[d.dayOfWeek] ?: ""

    private val GOLD = 0xFFFFD966.toInt()
    private val SUB_WHITE = 0xC7FFFFFF.toInt()

    fun build(context: Context): RemoteViews {
        val prefs = Prefs(context)
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val lunar = LunarCalendar.convert(today)
        val weather = WeatherRepo.load(context)
        val upcoming = EventStore.upcoming(EventStore.load(context), today)
        val tip = SuggestionEngine.build(today, now, weather, prefs.spiritual, upcoming).first()

        val views = RemoteViews(context.packageName, R.layout.widget_4x2)

        // Dòng 1: thứ, ngày dương
        views.setTextViewText(R.id.line_date, "${dayOfWeek(today)}, ${today.dayOfMonth}/${today.monthValue}")

        // Dòng 2: ngày âm (dòng chính)
        val lunarText = StringBuilder("${lunar.day} tháng ${LunarCalendar.monthName(lunar.month)}")
        if (lunar.leap) lunarText.append(" nhuận")
        views.setTextViewText(R.id.line_lunar, lunarText.toString())

        // Dòng 3: ngày đặc biệt (vàng) hoặc năm can chi + hoàng đạo + tiết khí
        val special = SuggestionEngine.specialDayName(lunar)
        val parts = mutableListOf<String>()
        if (special != null) parts += "✦ $special" else parts += LunarCalendar.canChiYear(lunar.year)
        if (prefs.spiritual) parts += if (LunarCalendar.isHoangDao(lunar)) "Hoàng đạo" else "Hắc đạo"
        if (special == null) parts += LunarCalendar.currentTietKhi(lunar.jd)
        views.setTextViewText(R.id.line_sub, parts.joinToString(" · "))
        views.setTextColor(R.id.line_sub, if (special != null) GOLD else SUB_WHITE)

        // Bên phải: nhiệt độ + mô tả
        if (weather != null) {
            views.setTextViewText(R.id.line_temp, "${WeatherText.icon(weather.codeNow)} ${weather.tempNow}°")
            views.setTextViewText(R.id.line_wx, "${WeatherText.label(weather.codeNow)} · ${weather.city}")
        } else {
            views.setTextViewText(R.id.line_temp, "--°")
            views.setTextViewText(R.id.line_wx, prefs.city)
        }

        // Gợi ý
        views.setTextViewText(R.id.line_tip, tip.short)

        // Nền theo thời điểm trong ngày và thời tiết
        views.setInt(R.id.root, "setBackgroundResource", background(now, weather))

        val open = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.root, pending)
        return views
    }

    private fun toMinutes(hhmm: String): Int? {
        val p = hhmm.split(":")
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    /** Chọn nền: đêm, sáng, ngày, chiều tà, hoặc mưa. */
    private fun background(now: LocalDateTime, weather: WeatherData?): Int {
        val todayF = weather?.daily?.firstOrNull()
        val rise = todayF?.sunrise?.let(::toMinutes) ?: (6 * 60)
        val set = todayF?.sunset?.let(::toMinutes) ?: (18 * 60)
        val t = now.hour * 60 + now.minute
        val rainy = weather != null && weather.codeNow >= 51
        return when {
            t < rise - 30 || t >= set + 30 -> R.drawable.widget_bg_night
            rainy -> R.drawable.widget_bg_rain
            t < rise + 150 -> R.drawable.widget_bg_morning
            t < set - 90 -> R.drawable.widget_bg_day
            else -> R.drawable.widget_bg_evening
        }
    }
}
