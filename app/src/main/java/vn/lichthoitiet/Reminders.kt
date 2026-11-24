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

/** Nhận báo thức hằng ngày (và sau khi khởi động máy) để gửi thông báo. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Reminders.ACTION) Reminders.notifyNow(context)
        Reminders.schedule(context)
        LichWidget.updateAll(context)
    }
}

object Reminders {
    const val ACTION = "vn.lichthoitiet.REMIND"
    private const val CHANNEL = "events"
    private const val ID_LUNAR_TODAY = 1
    private const val ID_LUNAR_TOMORROW = 2
    private const val ID_TEST = 3

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 100, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Đặt báo thức cho lần nhắc tiếp theo (giờ do người dùng chọn). Gọi lại sau mỗi lần chạy. */
    fun schedule(ctx: Context) {
        val prefs = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(ctx)
        am.cancel(pi)
        if (!prefs.notifyEvents && !prefs.notifyLunar) return
        var next = LocalDate.now().atTime(prefs.notifyHour.coerceIn(0, 23), 0)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun manager(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "Nhắc sự kiện và ngày lễ", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "Giỗ, sinh nhật, lịch hẹn và rằm, mùng 1"
        nm.createNotificationChannel(ch)
        return nm
    }

    private fun post(ctx: Context, nm: NotificationManager, id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
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
            EventStore.upcoming(EventStore.load(ctx), today, 30)
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

    fun test(ctx: Context) {
        val h = Prefs(ctx).notifyHour
        post(ctx, manager(ctx), ID_TEST, "Thông báo thử", "Nhắc sự kiện đang hoạt động. Mỗi ngày lúc $h:00 app sẽ nhắc khi có sự kiện sắp tới.")
    }
}
