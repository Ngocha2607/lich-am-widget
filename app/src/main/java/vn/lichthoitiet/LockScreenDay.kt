package vn.lichthoitiet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/** Nội dung thông báo màn hình khóa của một ngày. */
data class LockTexts(val title: String, val sub: String, val line: String, val full: String)

/**
 * Lịch tối giản trên màn hình khóa: một thông báo im lặng (không kêu, không rung, không hiện nổi),
 * luôn hiện ngày âm và câu ca dao, tục ngữ hôm nay; tự đổi lúc 0h. Máy có màn hình luôn bật (AOD)
 * hiện thông báo thì cũng thấy ở đó.
 */
object LockScreenDay {
    const val CHANNEL_ID = "lockday"
    private const val CHANNEL = CHANNEL_ID
    private const val OLD_CHANNEL = "today"
    private const val ID = 7
    const val PREF = "lockNote"
    const val ACTION_DISMISSED = "vn.lichthoitiet.LOCK_DISMISSED"
    private const val PREF_DISMISSED = "lockNoteDismissed"

    /** Người dùng vuốt bỏ thông báo: không hiện lại trong hôm nay (Android 14 cho vuốt bỏ cả thông báo luôn hiện). */
    fun dismissedToday(ctx: Context) {
        Prefs.get(ctx).edit().putString(PREF_DISMISSED, LocalDate.now().toString()).apply()
    }

    fun texts(date: LocalDate): LockTexts {
        val l = LunarCalendar.convert(date)
        val special = SuggestionEngine.specialDayName(l)
        val q = Quotes.forDate(date)
        val title = SkyScene.lunarTitle(l) + " âm lịch" + (special?.takeIf { !it.startsWith("Rằm") && !it.startsWith("Mùng 1") }?.let { " · $it" } ?: "")
        val sub = "${WidgetRenderer.dayOfWeek(date)} ${date.dayOfMonth}/${date.monthValue} · ${LunarCalendar.canChiYear(l.year)}"
        return LockTexts(title, sub, q.text.replace("\n", " / "), "${q.text}\n— ${q.kind}")
    }

    fun update(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val sp = Prefs.get(ctx)
        if (!sp.getBoolean(PREF, false) || sp.getString(PREF_DISMISSED, "") == LocalDate.now().toString()) {
            nm.cancel(ID)
            return
        }
        // Mức "mặc định" nhưng không tiếng, không rung: vẫn hiện trên màn hình khóa ở máy đặt ẩn thông báo im lặng
        nm.deleteNotificationChannel(OLD_CHANNEL)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lịch trên màn hình khóa", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ngày âm và câu ca dao hôm nay, không kêu, không rung"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val t = texts(LocalDate.now())
        val open = PendingIntent.getActivity(
            ctx, 7, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("target", "today"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            ctx, 8, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_DISMISSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(t.title)
            .setContentText(t.line)
            .setSubText(t.sub)
            .setStyle(Notification.BigTextStyle().bigText(t.full))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setDeleteIntent(dismiss)
            .setGroup("lockday")
            .build()
        try {
            nm.notify(ID, n)
        } catch (e: SecurityException) {
            // Chưa được cấp quyền thông báo
        }
    }
}
