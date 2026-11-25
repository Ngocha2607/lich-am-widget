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
        /** Vẽ lại tất cả widget đang có trên màn hình (widget chính và tờ lịch 1×1). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, LichWidget::class.java)).forEach { id ->
                manager.updateAppWidget(id, WidgetRenderer.build(context, manager.getAppWidgetOptions(id)))
            }
            val iconIds = manager.getAppWidgetIds(ComponentName(context, LichIconWidget::class.java))
            if (iconIds.isNotEmpty()) manager.updateAppWidget(iconIds, WidgetRenderer.buildIcon(context))
        }
    }
}

/**
 * Widget 1×1 "Lịch hôm nay": tờ lịch nhỏ cỡ một biểu tượng app, hiện thứ, ngày dương và ngày âm.
 * Android không cho app tự đổi số ngày trên icon, nên đặt widget này thay icon để có "icon lịch" đổi theo ngày.
 */
class LichIconWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, WidgetRenderer.buildIcon(context))
        Reminders.schedule(context) // gồm báo thức nửa đêm để tờ lịch sang ngày mới đúng lúc
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
    private val WHITE = 0xFFFFFFFF.toInt()

    /**
     * Bốn bố cục, mỗi cỡ một thiết kế riêng, kèm kích thước nhỏ nhất (dp) bố cục cần:
     * 2×2 "Vầng trăng", 3×2 "Tờ lịch", 4×2 "Bầu trời", 4×4 "Toàn cảnh".
     */
    private enum class Size(val layout: Int, val minW: Float, val minH: Float) {
        SMALL(R.layout.widget_2x2, 110f, 110f),
        MEDIUM(R.layout.widget_3x2, 200f, 110f),
        WIDE(R.layout.widget_4x2, 290f, 110f),
        LARGE(R.layout.widget_4x4, 290f, 260f),
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
            w in 200 until 290 -> Size.MEDIUM
            h >= 230 -> Size.LARGE
            else -> Size.WIDE
        }
    }

    /** Dữ liệu dùng chung cho mọi cỡ widget, tính một lần mỗi lượt vẽ. */
    private class Snapshot(context: Context) {
        val prefs = Prefs(context)
        val today: LocalDate = LocalDate.now()
        val now: LocalDateTime = LocalDateTime.now()
        val lunar = LunarCalendar.convert(today)
        val weather = WeatherRepo.load(context)
        val upcoming = EventStore.upcoming(Profile.eventsWithBirthday(context), today)
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
            // Android 12+: gửi cả bốn bố cục, launcher tự chọn bố cục lớn nhất vừa với kích thước thật
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
        val special = SuggestionEngine.specialDayName(lunar)
        val thu = dayOfWeek(today)

        // Thứ và ngày dương: bản 3×2 chỉ ghi thứ vì số ngày đã in lớn ngay dưới
        views.setTextViewText(R.id.line_date, when (size) {
            Size.SMALL -> "$thu · ${today.dayOfMonth}/${today.monthValue}"
            Size.MEDIUM -> "$thu · tháng ${today.monthValue}"
            else -> "$thu, ${today.dayOfMonth}/${today.monthValue}"
        })
        if (size == Size.MEDIUM) views.setTextViewText(R.id.line_day, today.dayOfMonth.toString())

        // Trăng đúng tuần trăng và ngày âm; ngày lễ, rằm, mùng 1 chữ vàng
        views.setImageViewResource(R.id.moon, moonDrawable(lunar.day))
        val lunarText = "${lunar.day} tháng ${LunarCalendar.monthName(lunar.month)}" + if (lunar.leap) " nhuận" else ""
        views.setTextViewText(R.id.line_lunar, lunarText)
        views.setTextColor(R.id.line_lunar, if (special != null && size != Size.WIDE && size != Size.LARGE) GOLD else WHITE)

        // Dòng phụ (không có ở 3×2), tối đa hai phần cho khỏi bị cắt chữ:
        // ngày đặc biệt (vàng) / "Tết còn N ngày" trong 90 ngày cuối năm / năm can chi, rồi hoàng hay hắc đạo.
        // Bản 2×2 hẹp nên chỉ ghi phần đầu.
        if (size != Size.MEDIUM) {
            val daysToTet = ChronoUnit.DAYS.between(today, LunarCalendar.nextTet(today)).toInt()
            val first = when {
                special != null -> "✦ $special"
                daysToTet in 1..90 && size != Size.SMALL -> "Tết còn $daysToTet ngày"
                else -> LunarCalendar.canChiYear(lunar.year)
            }
            val parts = mutableListOf(first)
            if (size != Size.SMALL && prefs.spiritual) parts += if (LunarCalendar.isHoangDao(lunar)) "Hoàng đạo" else "Hắc đạo"
            views.setTextViewText(R.id.line_sub, parts.joinToString(" · "))
            views.setTextColor(R.id.line_sub, if (special != null) GOLD else SUB_WHITE)
        }

        // Nhiệt độ (+ mô tả và thành phố, trừ bản 2×2)
        views.setTextViewText(R.id.line_temp, if (weather != null) "${WeatherText.icon(weather.codeNow)} ${weather.tempNow}°" else "--°")
        if (size != Size.SMALL) {
            views.setTextViewText(
                R.id.line_wx,
                if (weather != null) "${WeatherText.label(weather.codeNow)} · ${weather.city}" else prefs.city,
            )
        }

        // Gợi ý (bản 2×2 không có chỗ)
        if (size != Size.SMALL) views.setTextViewText(R.id.line_tip, tip.short)

        // Bản 4×4: dự báo 3 ngày tới + sự kiện sắp tới
        if (size == Size.LARGE) {
            bindForecast(views, weather, today)
            bindEvents(views, upcoming)
        }

        // Nền theo thời điểm trong ngày và thời tiết (dải màu trời + sao/quầng sáng + mây cát tường).
        // Gốc layout dùng id @android:id/background để launcher Android 12+ bo góc và cắt phần trang trí.
        views.setInt(android.R.id.background, "setBackgroundResource", background(now, weather))

        // Vùng chạm: cả widget mở app; nhiệt độ/dự báo mở thẳng thẻ thời tiết
        views.setOnClickPendingIntent(android.R.id.background, openApp(context, null, 0))
        val toWeather = openApp(context, "weather", 1)
        views.setOnClickPendingIntent(R.id.line_temp, toWeather)
        if (size != Size.SMALL) views.setOnClickPendingIntent(R.id.line_wx, toWeather)
        if (size == Size.LARGE) views.setOnClickPendingIntent(R.id.f_row, toWeather)
        return views
    }

    private val INK = 0xFF1C2A52.toInt()
    private val RED = 0xFFC8323A.toInt()
    private val LUNAR_INK = 0xFF8A7A62.toInt()

    private val PAPER_MOONS = intArrayOf(
        R.drawable.moonp_0, R.drawable.moonp_1, R.drawable.moonp_2, R.drawable.moonp_3,
        R.drawable.moonp_4, R.drawable.moonp_5, R.drawable.moonp_6, R.drawable.moonp_7,
    )

    /** Tờ lịch 1×1: thứ trên gáy đỏ, ngày dương lớn (đỏ vào Chủ nhật, lễ, rằm, mùng 1), ngày âm kèm trăng. */
    fun buildIcon(context: Context): RemoteViews {
        val today = LocalDate.now()
        val lunar = LunarCalendar.convert(today)
        val special = SuggestionEngine.specialDayName(lunar)
        val todayEvent = EventStore.eventsOn(Profile.eventsWithBirthday(context), today).firstOrNull()
        val views = RemoteViews(context.packageName, R.layout.widget_icon)

        views.setTextViewText(R.id.icon_head, dayOfWeek(today).uppercase(java.util.Locale("vi")))
        views.setTextViewText(R.id.icon_day, today.dayOfMonth.toString())
        views.setTextColor(R.id.icon_day, if (today.dayOfWeek == DayOfWeek.SUNDAY || special != null) RED else INK)

        // Ngày âm: rằm, mùng 1 ghi chữ; ngày có sự kiện thêm biểu tượng sự kiện
        val lunarText = when (lunar.day) {
            15 -> "Rằm"
            1 -> "Mùng 1"
            else -> "${lunar.day}/${lunar.month}"
        }
        views.setTextViewText(R.id.icon_lunar, (todayEvent?.icon?.plus(" ") ?: "") + lunarText)
        views.setTextColor(R.id.icon_lunar, if (special != null) RED else LUNAR_INK)
        views.setImageViewResource(R.id.icon_moon, PAPER_MOONS[moonPhase(lunar.day)])

        views.setOnClickPendingIntent(android.R.id.background, openApp(context, null, 2))
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

    private val MOONS = intArrayOf(
        R.drawable.moon_0, R.drawable.moon_1, R.drawable.moon_2, R.drawable.moon_3,
        R.drawable.moon_4, R.drawable.moon_5, R.drawable.moon_6, R.drawable.moon_7,
    )

    /** Hình trăng cho ngày âm [lunarDay]: moon_0 trăng mới, moon_4 trăng tròn (8 tuần trăng). */
    fun moonDrawable(lunarDay: Int): Int = MOONS[moonPhase(lunarDay)]

    /** Chỉ số tuần trăng 0..7 của ngày âm (0 trăng mới, 4 trăng tròn). */
    private fun moonPhase(lunarDay: Int): Int = Math.floorMod(Math.round((lunarDay - 1) / 29.53 * 8).toInt(), 8)

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
