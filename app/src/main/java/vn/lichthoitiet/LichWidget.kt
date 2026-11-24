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

    fun build(context: Context): RemoteViews {
        val prefs = Prefs(context)
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val lunar = LunarCalendar.convert(today)
        val weather = WeatherRepo.load(context)
        val tips = SuggestionEngine.build(today, now, weather, prefs.spiritual)
        val tip = tips.first()

        val views = RemoteViews(context.packageName, R.layout.widget_4x2)

        views.setTextViewText(R.id.line_date, "${dayOfWeek(today)}, ${today.dayOfMonth}/${today.monthValue}")
        views.setTextViewText(
            R.id.line_temp,
            if (weather != null) "${WeatherText.icon(weather.codeNow)} ${weather.tempNow}°" else "--°",
        )

        val lunarText = StringBuilder("Âm ${lunar.day}/${LunarCalendar.monthName(lunar.month)}")
        if (lunar.leap) lunarText.append(" nhuận")
        lunarText.append(" ${LunarCalendar.canChiYear(lunar.year)}")
        if (prefs.spiritual) lunarText.append(if (LunarCalendar.isHoangDao(lunar)) " · Hoàng đạo" else " · Hắc đạo")
        views.setTextViewText(R.id.line_lunar, lunarText.toString())
        views.setTextViewText(R.id.line_city, weather?.city ?: prefs.city)

        views.setTextViewText(R.id.line_tip, tip.short)

        val bg = when {
            tip.kind == Kind.WARN -> R.drawable.widget_bg_warn
            tip.kind == Kind.SPECIAL || lunar.day == 1 || lunar.day == 15 -> R.drawable.widget_bg_special
            else -> R.drawable.widget_bg
        }
        views.setInt(R.id.root, "setBackgroundResource", bg)

        val open = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.root, pending)
        return views
    }
}
