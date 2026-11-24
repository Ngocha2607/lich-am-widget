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
            icon = j.optString("icon").ifEmpty { "📌" },
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

    fun upsert(ctx: Context, e: Event) {
        save(ctx, load(ctx).filter { it.id != e.id } + e)
    }

    fun delete(ctx: Context, id: String) {
        save(ctx, load(ctx).filter { it.id != id })
    }

    /** Sự kiện có diễn ra vào ngày dương [date] không. */
    fun occursOn(e: Event, date: LocalDate): Boolean {
        if (!e.lunar) {
            return date.dayOfMonth == e.day && date.monthValue == e.month && (e.yearly || e.year == date.year)
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
