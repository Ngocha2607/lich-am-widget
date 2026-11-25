package vn.lichthoitiet

import android.content.Context

/** Hồ sơ chủ máy dùng cho phần hiển thị và nhắc việc. */
object Profile {
    /**
     * Sự kiện cá nhân kèm sinh nhật của chủ máy (nếu đã nhập ngày sinh).
     * Dùng để hiển thị và nhắc; sửa, sao lưu, xuất .ics vẫn dùng [EventStore.load].
     */
    fun eventsWithBirthday(ctx: Context): List<Event> {
        val p = Prefs(ctx)
        val birthday = EventStore.birthdayEvent(p.birthDate, p.birthLunarInput)
        val events = EventStore.load(ctx)
        return if (birthday == null) events else events + birthday
    }
}
