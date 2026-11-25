package vn.lichthoitiet

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Nhận báo thức hằng ngày (và sau khi khởi động máy) để gửi thông báo.
 * Mọi lần nhận (kể cả báo thức nửa đêm [Reminders.ACTION_MIDNIGHT]) đều đặt lại báo thức và vẽ lại widget.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION -> Reminders.notifyNow(context)
            Reminders.ACTION_WEATHER -> {
                val pending = goAsync()
                Thread {
                    try { Reminders.notifyWeather(context) } finally { pending.finish() }
                }.start()
            }
        }
        Reminders.schedule(context)
        LichWidget.updateAll(context)
    }
}

object Reminders {
    const val ACTION = "vn.lichthoitiet.REMIND"
    const val ACTION_WEATHER = "vn.lichthoitiet.REMIND_WEATHER"
    const val ACTION_MIDNIGHT = "vn.lichthoitiet.MIDNIGHT"
    private const val CHANNEL = "events"
    private const val CHANNEL_WEATHER = "weather"
    private const val ID_LUNAR_TODAY = 1
    private const val ID_LUNAR_TOMORROW = 2
    private const val ID_TEST = 3
    private const val ID_WEATHER = 4

    private fun alarmIntent(ctx: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, ReminderReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nextAt(hour: Int): Long {
        var next = LocalDate.now().atTime(hour.coerceIn(0, 23), 0)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** Đặt báo thức cho lần nhắc tiếp theo. Gọi lại sau mỗi lần chạy. */
    fun schedule(ctx: Context) {
        val prefs = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return

        val events = alarmIntent(ctx, ACTION, 100)
        am.cancel(events)
        if (prefs.notifyEvents || prefs.notifyLunar) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt(prefs.notifyHour), events)
        }

        val weather = alarmIntent(ctx, ACTION_WEATHER, 101)
        am.cancel(weather)
        if (prefs.notifyWeather) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt(prefs.notifyWeatherHour), weather)
        }

        // Qua 0h vẽ lại widget để ngày dương, ngày âm đổi ngay. Báo thức RTC không đánh thức máy:
        // máy đang tắt màn hình thì chạy khi người dùng mở máy, đúng lúc cần nhìn widget.
        val midnight = LocalDate.now().plusDays(1).atStartOfDay().plusSeconds(5)
        am.set(AlarmManager.RTC, midnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), alarmIntent(ctx, ACTION_MIDNIGHT, 102))
    }

    private fun manager(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Nhắc sự kiện và ngày lễ", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Giỗ, sinh nhật, lịch hẹn và rằm, mùng 1" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WEATHER, "Cảnh báo thời tiết", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Mưa, rét, nắng nóng ngày mai" },
        )
        return nm
    }

    private fun post(ctx: Context, nm: NotificationManager, id: Int, title: String, text: String, channel: String = CHANNEL) {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(id, n)
        } catch (e: SecurityException) {
            // Chưa được cấp quyền thông báo
        }
    }

    /** Gửi thông báo cho sự kiện sắp tới và ngày đặc biệt (hôm nay, ngày mai). */
    fun notifyNow(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.notifyEvents && !prefs.notifyLunar) return
        val today = LocalDate.now()
        val nm = manager(ctx)

        if (prefs.notifyEvents) {
            EventStore.upcoming(Profile.eventsWithBirthday(ctx), today, 30)
                .filter { it.daysLeft <= it.event.remind }
                .forEach { u ->
                    val e = u.event
                    val title = when (u.daysLeft) {
                        0 -> "Hôm nay: ${e.title}"
                        1 -> "Ngày mai: ${e.title}"
                        else -> "Còn ${u.daysLeft} ngày: ${e.title}"
                    }
                    val parts = mutableListOf("${e.icon} ${WidgetRenderer.dayOfWeek(u.date)} ${u.date.dayOfMonth}/${u.date.monthValue}")
                    if (e.lunar) parts += "${e.day}/${e.month} âm lịch"
                    if (e.time.isNotBlank()) parts += "lúc ${e.time}"
                    if (e.note.isNotBlank()) parts += e.note
                    post(ctx, nm, 1000 + (e.id.hashCode() and 0xFFFFF), title, parts.joinToString(" · "))
                }
        }

        if (prefs.notifyLunar) {
            val l = LunarCalendar.convert(today)
            SuggestionEngine.specialDayName(l)?.let { name ->
                val hd = if (LunarCalendar.isHoangDao(l)) " Ngày hoàng đạo." else ""
                post(ctx, nm, ID_LUNAR_TODAY, "Hôm nay $name", "${l.day}/${l.month} âm lịch, ${LunarCalendar.canChiYear(l.year)}.$hd")
            }
            val t = LunarCalendar.convert(today.plusDays(1))
            SuggestionEngine.specialDayName(t)?.let { name ->
                post(ctx, nm, ID_LUNAR_TOMORROW, "Mai là $name", "${t.day}/${t.month} âm lịch. Chuẩn bị lễ từ hôm nay.")
            }
        }
    }

    /** Cảnh báo thời tiết ngày mai (chạy ở luồng nền, có thể tải lại dữ liệu). */
    fun notifyWeather(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.notifyWeather) return
        var w = WeatherRepo.load(ctx)
        if (w == null || w.isStale(3 * 60 * 60 * 1000L)) {
            w = try { WeatherRepo.refresh(ctx) } catch (e: Exception) { w }
        }
        if (w == null) return
        val warns = mutableListOf<String>()
        if (w.tomorrowRain >= 60) warns += "☔ Mưa ${w.tomorrowRain}%, nhớ mang ô"
        if (w.tomorrowCode >= 95) warns += "⛈ Có dông"
        if (w.tomorrowMin <= 13) warns += "🥶 Rét ${w.tomorrowMin}°, mặc ấm"
        if (w.tomorrowMax >= 35) warns += "🥵 Nắng nóng ${w.tomorrowMax}°"
        if (warns.isEmpty()) return
        post(
            ctx, manager(ctx), ID_WEATHER,
            "Ngày mai · ${w.city}: ${WeatherText.label(w.tomorrowCode)}, ${w.tomorrowMin}–${w.tomorrowMax}°",
            warns.joinToString("\n"), CHANNEL_WEATHER,
        )
    }

    fun test(ctx: Context) {
        val h = Prefs(ctx).notifyHour
        post(ctx, manager(ctx), ID_TEST, "Thông báo thử", "Nhắc sự kiện đang hoạt động. Mỗi ngày lúc $h:00 app sẽ nhắc khi có sự kiện sắp tới.")
    }
}
