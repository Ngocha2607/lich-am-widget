package vn.lichthoitiet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class LichWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, WidgetRenderer.build(context, manager.getAppWidgetOptions(id)))
        }

        WeatherWorker.schedule(context)
        Reminders.schedule(context) // gồm báo thức nửa đêm để widget đổi ngày đúng lúc
        val cached = WeatherRepo.load(context)
        if (cached == null || cached.isStale(2 * 60 * 60 * 1000L)) WeatherWorker.refreshNow(context)
    }

    /** Người dùng đổi kích thước widget: chọn lại bố cục 2×2 / 4×2 / 4×4. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        manager.updateAppWidget(id, WidgetRenderer.build(context, newOptions))
    }

    override fun onEnabled(context: Context) {
        WeatherWorker.schedule(context)
        WeatherWorker.refreshNow(context)
        Reminders.schedule(context)
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
            ids.forEach { id ->
                manager.updateAppWidget(id, WidgetRenderer.build(context, manager.getAppWidgetOptions(id)))
            }
        }
    }
}

object WidgetRenderer {
    private val THU = mapOf(
        DayOfWeek.MONDAY to "Thứ Hai", DayOfWeek.TUESDAY to "Thứ Ba", DayOfWeek.WEDNESDAY to "Thứ Tư",
        DayOfWeek.THURSDAY to "Thứ Năm", DayOfWeek.FRIDAY to "Thứ Sáu", DayOfWeek.SATURDAY to "Thứ Bảy",
        DayOfWeek.SUNDAY to "Chủ nhật",
    )
    private val SHORT_DOW = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

    fun dayOfWeek(d: LocalDate): String = THU[d.dayOfWeek] ?: ""

    private val GOLD = 0xFFFFD966.toInt()
    private val SUB_WHITE = 0xC7FFFFFF.toInt()

    /** Ba bố cục và kích thước nhỏ nhất (dp) mỗi bố cục cần để hiện đủ nội dung. */
    private enum class Size(val layout: Int, val minW: Float, val minH: Float) {
        SMALL(R.layout.widget_2x2, 110f, 110f),
        MEDIUM(R.layout.widget_4x2, 200f, 110f),
        LARGE(R.layout.widget_4x4, 200f, 260f),
    }

    /**
     * Android 11 trở xuống: đoán bố cục theo kích thước launcher báo.
     * MIN_HEIGHT là chiều cao khi xoay ngang (nhỏ hơn), dùng nó để bố cục 4×4 không bị cắt.
     */
    private fun sizeOf(options: Bundle?): Size {
        val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        return when {
            w in 1 until 200 -> Size.SMALL
            h >= 230 -> Size.LARGE
            else -> Size.MEDIUM
        }
    }

    /** Dữ liệu dùng chung cho mọi cỡ widget, tính một lần mỗi lượt vẽ. */
    private class Snapshot(context: Context) {
        val prefs = Prefs(context)
        val today: LocalDate = LocalDate.now()
        val now: LocalDateTime = LocalDateTime.now()
        val lunar = LunarCalendar.convert(today)
        val weather = WeatherRepo.load(context)
        val upcoming = EventStore.upcoming(EventStore.load(context), today)
        val tip = SuggestionEngine.build(today, now, weather, prefs.spiritual, upcoming).first()
    }

    private fun openApp(context: Context, target: String?, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (target != null) putExtra("target", target)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun build(context: Context, options: Bundle? = null): RemoteViews {
        val snap = Snapshot(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: gửi cả ba bố cục, launcher tự chọn bố cục lớn nhất vừa với kích thước thật
            // (đúng cả khi màn hình dọc lẫn ngang, không phải đoán).
            return RemoteViews(Size.values().associate { SizeF(it.minW, it.minH) to render(context, snap, it) })
        }
        return render(context, snap, sizeOf(options))
    }

    private fun render(context: Context, snap: Snapshot, size: Size): RemoteViews {
        val prefs = snap.prefs
        val today = snap.today
        val now = snap.now
        val lunar = snap.lunar
        val weather = snap.weather
        val upcoming = snap.upcoming
        val tip = snap.tip
        val views = RemoteViews(context.packageName, size.layout)

        // Dòng 1: thứ, ngày dương, tuần trăng
        views.setTextViewText(
            R.id.line_date,
            "${dayOfWeek(today)}, ${today.dayOfMonth}/${today.monthValue}  ${LunarCalendar.moonPhase(lunar.day)}",
        )

        // Dòng 2: ngày âm (dòng chính)
        val lunarText = StringBuilder("${lunar.day} tháng ${LunarCalendar.monthName(lunar.month)}")
        if (lunar.leap) lunarText.append(" nhuận")
        views.setTextViewText(R.id.line_lunar, lunarText.toString())

        // Dòng 3: ngày đặc biệt (vàng), hoặc năm can chi + hoàng đạo + (đếm ngược Tết | tiết khí)
        val special = SuggestionEngine.specialDayName(lunar)
        val daysToTet = ChronoUnit.DAYS.between(today, LunarCalendar.nextTet(today)).toInt()
        val parts = mutableListOf<String>()
        if (special != null) parts += "✦ $special" else parts += LunarCalendar.canChiYear(lunar.year)
        if (prefs.spiritual) parts += if (LunarCalendar.isHoangDao(lunar)) "Hoàng đạo" else "Hắc đạo"
        if (special == null) {
            parts += if (daysToTet in 1..90) "Còn $daysToTet ngày đến Tết" else LunarCalendar.currentTietKhi(lunar.jd)
        }
        views.setTextViewText(R.id.line_sub, parts.joinToString(" · "))
        views.setTextColor(R.id.line_sub, if (special != null) GOLD else SUB_WHITE)

        // Bên phải: nhiệt độ (+ mô tả ở bản 4×2 và 4×4)
        if (weather != null) {
            views.setTextViewText(R.id.line_temp, "${WeatherText.icon(weather.codeNow)} ${weather.tempNow}°")
        } else {
            views.setTextViewText(R.id.line_temp, "--°")
        }
        if (size != Size.SMALL) {
            views.setTextViewText(
                R.id.line_wx,
                if (weather != null) "${WeatherText.label(weather.codeNow)} · ${weather.city}" else prefs.city,
            )
        }

        // Gợi ý
        views.setTextViewText(R.id.line_tip, tip.short)

        // Bản 4×4: dự báo 3 ngày tới + sự kiện sắp tới
        if (size == Size.LARGE) {
            bindForecast(views, weather, today)
            bindEvents(views, upcoming)
        }

        // Nền theo thời điểm trong ngày và thời tiết
        views.setInt(R.id.root, "setBackgroundResource", background(now, weather))

        // Vùng chạm: cả widget mở app; nhiệt độ/dự báo mở thẳng thẻ thời tiết
        views.setOnClickPendingIntent(R.id.root, openApp(context, null, 0))
        val toWeather = openApp(context, "weather", 1)
        views.setOnClickPendingIntent(R.id.line_temp, toWeather)
        if (size != Size.SMALL) views.setOnClickPendingIntent(R.id.line_wx, toWeather)
        if (size == Size.LARGE) views.setOnClickPendingIntent(R.id.f_row, toWeather)
        return views
    }

    private fun bindForecast(views: RemoteViews, weather: WeatherData?, today: LocalDate) {
        val days = weather?.daily?.drop(1)?.take(3).orEmpty()
        if (days.size < 3) {
            views.setViewVisibility(R.id.f_row, View.GONE)
            return
        }
        views.setViewVisibility(R.id.f_row, View.VISIBLE)
        val labelIds = intArrayOf(R.id.f1_label, R.id.f2_label, R.id.f3_label)
        val iconIds = intArrayOf(R.id.f1_icon, R.id.f2_icon, R.id.f3_icon)
        val tempIds = intArrayOf(R.id.f1_temp, R.id.f2_temp, R.id.f3_temp)
        days.forEachIndexed { i, d ->
            val date = try { LocalDate.parse(d.date) } catch (e: Exception) { today.plusDays(i + 1L) }
            val label = if (i == 0) "Mai" else SHORT_DOW[date.dayOfWeek.value - 1]
            views.setTextViewText(labelIds[i], label)
            views.setTextViewText(iconIds[i], WeatherText.icon(d.code))
            views.setTextViewText(tempIds[i], "${d.max}°/${d.min}°")
        }
    }

    private fun bindEvents(views: RemoteViews, upcoming: List<Upcoming>) {
        val next = upcoming.filter { it.daysLeft > 0 }.take(2)
        val ids = intArrayOf(R.id.ev1, R.id.ev2)
        ids.forEachIndexed { i, id ->
            val u = next.getOrNull(i)
            if (u == null) {
                views.setViewVisibility(id, View.GONE)
            } else {
                views.setViewVisibility(id, View.VISIBLE)
                views.setTextViewText(id, "${u.event.icon} Còn ${u.daysLeft} ngày · ${u.event.title}")
            }
        }
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
