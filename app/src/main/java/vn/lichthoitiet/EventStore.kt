package vn.lichthoitiet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * Sự kiện cá nhân: giỗ, sinh nhật, lịch hẹn... theo âm hoặc dương lịch.
 * [year] null khi lặp hằng năm. [remind] = nhắc trước bao nhiêu ngày.
 */
data class Event(
    val id: String,
    val title: String,
    val icon: String,
    val lunar: Boolean,
    val day: Int,
    val month: Int,
    val year: Int?,
    val yearly: Boolean,
    val time: String,
    val note: String,
    val remind: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("icon", icon)
        put("lunar", lunar); put("day", day); put("month", month)
        put("year", year ?: JSONObject.NULL); put("yearly", yearly)
        put("time", time); put("note", note); put("remind", remind)
    }

    /** Nhãn ngắn, ví dụ "18/10 âm · hằng năm". */
    val dateLabel: String
        get() {
            val base = if (lunar) "$day/${month} âm" else "$day/$month"
            return if (yearly) "$base · hằng năm" else if (lunar) "$base năm $year" else "$base/$year"
        }

    companion object {
        fun fromJson(j: JSONObject) = Event(
            id = j.optString("id").ifEmpty { UUID.randomUUID().toString() },
            title = j.optString("title").trim(),
            icon = j.optString("icon").filter { it !in "<>&\"'`" && !it.isWhitespace() }.takeSafe(12).ifEmpty { "📌" },
            lunar = j.optBoolean("lunar", false),
            day = j.optInt("day", 1).coerceIn(1, 31),
            month = j.optInt("month", 1).coerceIn(1, 12),
            year = if (j.isNull("year")) null else j.optInt("year"),
            yearly = j.optBoolean("yearly", true),
            time = j.optString("time", ""),
            note = j.optString("note", "").trim(),
            remind = j.optInt("remind", 1).coerceIn(0, 30),
        )
    }
}

/** Một lần diễn ra của sự kiện. */
data class Upcoming(val event: Event, val date: LocalDate, val daysLeft: Int)

object EventStore {
    private const val KEY = "events_json"

    /** Khóa cho mọi thao tác đọc-sửa-ghi (cầu nối JS, luồng nhập lịch Google, sổ giỗ có thể ghi cùng lúc). */
    private val LOCK = Any()

    /** Id của sự kiện sinh nhật sinh ra từ hồ sơ (không lưu trong danh sách sự kiện). */
    const val BIRTHDAY_ID = "profile-birthday"

    /**
     * Sinh nhật của chủ máy từ ngày sinh dương lịch [birthIso] ("1990-03-12"), lặp hằng năm
     * theo âm lịch nếu người dùng nhập ngày sinh âm ([lunar]), ngược lại theo dương lịch.
     */
    fun birthdayEvent(birthIso: String, lunar: Boolean): Event? {
        val solar = try { LocalDate.parse(birthIso) } catch (e: Exception) { return null }
        val l = LunarCalendar.convert(solar)
        return Event(
            id = BIRTHDAY_ID, title = "Sinh nhật của bạn", icon = "🎂", lunar = lunar,
            day = if (lunar) l.day else solar.dayOfMonth, month = if (lunar) l.month else solar.monthValue,
            year = null, yearly = true, time = "", note = "", remind = 1,
        )
    }

    fun load(ctx: Context): List<Event> {
        val s = Prefs.get(ctx).getString(KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            List(a.length()) { Event.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, events: List<Event>) {
        val arr = JSONArray().apply { events.forEach { put(it.toJson()) } }
        Prefs.get(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun upsert(ctx: Context, e: Event) = synchronized(LOCK) {
        save(ctx, load(ctx).filter { it.id != e.id } + e)
    }

    /** Thay toàn bộ sự kiện có id bắt đầu bằng [prefix] bằng [events] (một lượt, có khóa). */
    fun replaceByPrefix(ctx: Context, prefix: String, events: List<Event>) = synchronized(LOCK) {
        save(ctx, load(ctx).filter { !it.id.startsWith(prefix) } + events)
    }

    /** Đồng bộ một chiều từ lịch trên máy: ghi đè [list] và bỏ các id trong [remove] (một lượt, có khóa). */
    fun syncImported(ctx: Context, remove: Set<String>, list: List<Event>) = synchronized(LOCK) {
        val ids = list.map { it.id }.toSet()
        save(ctx, load(ctx).filter { it.id !in ids && it.id !in remove } + list)
    }

    /** Thêm hoặc ghi đè nhiều sự kiện một lượt (trùng id thì ghi đè). */
    fun upsertAll(ctx: Context, list: List<Event>) {
        if (list.isEmpty()) return
        val ids = list.map { it.id }.toSet()
        synchronized(LOCK) { save(ctx, load(ctx).filter { it.id !in ids } + list) }
    }

    /** Nội dung file sao lưu. */
    fun exportJson(events: List<Event>): String =
        JSONObject().put("app", "LichThoiTiet").put("version", 1)
            .put("events", JSONArray().apply { events.forEach { put(it.toJson()) } })
            .toString(2)

    /** Nhập từ file sao lưu (hoặc mảng JSON thuần); trùng id thì ghi đè. Trả về số sự kiện đã nhập. */
    fun importJson(ctx: Context, text: String): Int {
        val trimmed = text.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("events")
        val incoming = List(arr.length()) { Event.fromJson(arr.getJSONObject(it)) }.filter { it.title.isNotBlank() }
        val ids = incoming.map { it.id }.toSet()
        synchronized(LOCK) { save(ctx, load(ctx).filter { it.id !in ids } + incoming) }
        return incoming.size
    }

    private fun icsEscape(s: String) = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")
    private fun icsDate(d: LocalDate) = "%04d%02d%02d".format(d.year, d.monthValue, d.dayOfMonth)

    /**
     * File .ics cho Google Calendar: mỗi lần diễn ra trong [years] năm tới là một VEVENT
     * (vì Google Calendar không lặp được theo âm lịch).
     */
    fun toIcs(events: List<Event>, from: LocalDate, years: Int = 5): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//LichThoiTiet//VI\r\nCALSCALE:GREGORIAN\r\nX-WR-CALNAME:Sự kiện âm lịch\r\n")
        val stamp = icsDate(from) + "T000000Z"
        val end = from.plusYears(years.toLong())
        var d = from
        while (d.isBefore(end)) {
            eventsOn(events, d).forEach { e ->
                sb.append("BEGIN:VEVENT\r\n")
                sb.append("UID:${e.id}-${icsDate(d)}@lichthoitiet\r\n")
                sb.append("DTSTAMP:$stamp\r\n")
                val t = e.time.split(":")
                if (t.size == 2 && t[0].toIntOrNull() != null && t[1].toIntOrNull() != null) {
                    val start = "${icsDate(d)}T%02d%02d00".format(t[0].toInt(), t[1].toInt())
                    val endMin = (t[0].toInt() * 60 + t[1].toInt() + 60).coerceAtMost(23 * 60 + 59)
                    sb.append("DTSTART:$start\r\nDTEND:${icsDate(d)}T%02d%02d00\r\n".format(endMin / 60, endMin % 60))
                } else {
                    sb.append("DTSTART;VALUE=DATE:${icsDate(d)}\r\nDTEND;VALUE=DATE:${icsDate(d.plusDays(1))}\r\n")
                }
                sb.append("SUMMARY:${icsEscape("${e.icon} ${e.title}")}\r\n")
                val desc = listOf(e.dateLabel, e.note).filter { it.isNotBlank() }.joinToString(" · ")
                sb.append("DESCRIPTION:${icsEscape(desc)}\r\n")
                sb.append("END:VEVENT\r\n")
            }
            d = d.plusDays(1)
        }
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    fun delete(ctx: Context, id: String) = synchronized(LOCK) {
        save(ctx, load(ctx).filter { it.id != id })
    }

    /** Sự kiện có diễn ra vào ngày dương [date] không. */
    fun occursOn(e: Event, date: LocalDate): Boolean {
        if (!e.lunar) {
            if (date.monthValue != e.month || (!e.yearly && e.year != date.year)) return false
            if (date.dayOfMonth == e.day) return true
            // 29/2 lặp hằng năm: năm không nhuận làm vào 28/2
            return e.day == 29 && e.month == 2 && date.dayOfMonth == 28 && !date.isLeapYear
        }
        val l = LunarCalendar.convert(date)
        if (l.leap || l.month != e.month) return false
        if (!e.yearly && e.year != l.year) return false
        if (l.day == e.day) return true
        // Tháng thiếu không có ngày 30: làm vào ngày 29
        return e.day == 30 && l.day == 29 && LunarCalendar.convert(date.plusDays(1)).day == 1
    }

    fun eventsOn(events: List<Event>, date: LocalDate): List<Event> =
        events.filter { occursOn(it, date) }.sortedBy { it.time }

    /** Các lần diễn ra từ [from] trong [horizon] ngày tới, theo thứ tự thời gian. */
    fun upcoming(events: List<Event>, from: LocalDate, horizon: Int = 30): List<Upcoming> {
        if (events.isEmpty()) return emptyList()
        val out = mutableListOf<Upcoming>()
        for (i in 0..horizon) {
            val d = from.plusDays(i.toLong())
            eventsOn(events, d).forEach { out += Upcoming(it, d, i) }
        }
        return out
    }
}
