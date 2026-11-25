package vn.lichthoitiet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Một việc cần làm ghi theo ngày. */
data class Note(val id: String, val text: String, val done: Boolean)

/**
 * Ghi chú nhanh theo ngày, lưu trong máy dạng {"2026-11-25": [{id, text, done}, ...]}.
 * Có trong file sao lưu cùng sự kiện.
 */
object NoteStore {
    private const val KEY = "notes_json"
    const val MAX_LEN = 200

    private fun loadAll(ctx: Context): JSONObject =
        try { JSONObject(Prefs.get(ctx).getString(KEY, "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun saveAll(ctx: Context, all: JSONObject) {
        Prefs.get(ctx).edit().putString(KEY, all.toString()).apply()
    }

    private fun parse(arr: JSONArray?): List<Note> = if (arr == null) emptyList() else List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        Note(o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("text"), o.optBoolean("done"))
    }

    private fun toJson(notes: List<Note>) = JSONArray().apply {
        notes.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
    }

    fun forDate(ctx: Context, iso: String): List<Note> = parse(loadAll(ctx).optJSONArray(iso))

    /** Số việc chưa xong của từng ngày trong tháng "2026-11" (để đánh dấu trên lưới lịch). */
    fun openCountsInMonth(ctx: Context, yearMonth: String): Map<String, Int> {
        val all = loadAll(ctx)
        val out = mutableMapOf<String, Int>()
        all.keys().forEach { k -> if (k.startsWith(yearMonth)) out[k] = parse(all.optJSONArray(k)).count { !it.done } }
        return out
    }

    private val LOCK = Any()

    private fun update(ctx: Context, iso: String, change: (List<Note>) -> List<Note>): List<Note> = synchronized(LOCK) {
        val all = loadAll(ctx)
        val next = change(parse(all.optJSONArray(iso)))
        if (next.isEmpty()) all.remove(iso) else all.put(iso, toJson(next))
        saveAll(ctx, all)
        next
    }

    fun add(ctx: Context, iso: String, text: String): List<Note> {
        val t = text.trim().take(MAX_LEN)
        return if (t.isEmpty()) forDate(ctx, iso) else update(ctx, iso) { it + Note(UUID.randomUUID().toString(), t, false) }
    }

    fun toggle(ctx: Context, iso: String, id: String) = update(ctx, iso) { list -> list.map { if (it.id == id) it.copy(done = !it.done) else it } }

    fun delete(ctx: Context, iso: String, id: String) = update(ctx, iso) { list -> list.filter { it.id != id } }

    fun toJsonArray(notes: List<Note>): JSONArray = toJson(notes)

    /** Toàn bộ ghi chú để đưa vào file sao lưu. */
    fun exportAll(ctx: Context): JSONObject = loadAll(ctx)

    /** Gộp ghi chú từ file sao lưu: trùng id thì ghi đè. Trả về số ghi chú đã nhập. */
    fun importAll(ctx: Context, incoming: JSONObject): Int = synchronized(LOCK) {
        val all = loadAll(ctx)
        var n = 0
        incoming.keys().forEach { iso ->
            val add = parse(incoming.optJSONArray(iso)).filter { it.text.isNotBlank() }
            val ids = add.map { it.id }.toSet()
            val merged = parse(all.optJSONArray(iso)).filter { it.id !in ids } + add
            if (merged.isNotEmpty()) all.put(iso, toJson(merged))
            n += add.size
        }
        saveAll(ctx, all)
        n
    }
}
