package vn.lichthoitiet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Cắt tối đa [n] ký tự mà không để lại nửa cặp surrogate (emoji bị vỡ). */
internal fun String.takeSafe(n: Int): String {
    if (length <= n) return this
    val cut = if (n > 0 && this[n - 1].isHighSurrogate()) n - 1 else n
    return substring(0, cut)
}

/** Mã băm ngắn, ổn định của một chuỗi (để cấp id cố định cho dữ liệu nhập tay thiếu id). */
internal fun stableId(s: String): String {
    val md = java.security.MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
    return md.take(6).joinToString("") { "%02x".format(it) }
}

/**
 * Một người đã khuất trong sổ giỗ: ngày giỗ theo âm lịch (ngày mất âm lịch), người lo đồ cúng.
 * [deleted] là dấu xóa để khi gộp sổ giữa các máy, việc xóa cũng được truyền đi.
 */
data class FamilyMember(
    val id: String,
    val name: String,
    val relation: String,
    val lunarDay: Int,
    val lunarMonth: Int,
    val deathYear: Int?,
    val place: String,
    val assignee: String,
    val note: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
) {
    /** Tên hiện trên lịch, ví dụ "Giỗ ông nội (Nguyễn Văn An)". */
    val eventTitle: String
        get() {
            val who = relation.ifBlank { name }
            return if (relation.isNotBlank() && name.isNotBlank()) "Giỗ $who ($name)" else "Giỗ $who"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("rel", relation); put("d", lunarDay); put("m", lunarMonth)
        put("y", deathYear ?: JSONObject.NULL); put("place", place); put("who", assignee); put("note", note)
        put("u", updatedAt); if (deleted) put("del", true)
    }

    companion object {
        fun fromJson(j: JSONObject) = FamilyMember(
            // Thiếu id (mã soạn tay): id cố định theo nội dung để dán lại không sinh bản sao
            id = j.optString("id").filter { it.isLetterOrDigit() }.take(32)
                .ifEmpty { stableId("${j.optString("name")}|${j.optString("rel")}|${j.optInt("d")}|${j.optInt("m")}") },
            name = j.optString("name").trim().takeSafe(60),
            relation = j.optString("rel").trim().takeSafe(40),
            lunarDay = j.optInt("d", 1).coerceIn(1, 30),
            lunarMonth = j.optInt("m", 1).coerceIn(1, 12),
            deathYear = if (j.isNull("y") || !j.has("y")) null else j.optInt("y").takeIf { it in 1800..2200 },
            place = j.optString("place").trim().takeSafe(80),
            assignee = j.optString("who").trim().takeSafe(40),
            note = j.optString("note").trim().takeSafe(120),
            updatedAt = j.optLong("u", 0L),
            deleted = j.optBoolean("del", false),
        ).let { if (it.deleted) it.copy(name = "", relation = "", place = "", assignee = "", note = "", deathYear = null) else it }
    }
}

/** Một sổ giỗ (một nhánh gia đình), chia sẻ cho cả nhà bằng mã. [remind] = nhắc trước bao nhiêu ngày. */
data class FamilyGroup(
    val id: String,
    val name: String,
    val members: List<FamilyMember>,
    val remind: Int,
    val updatedAt: Long,
    /** Lần sửa tên sổ (tách khỏi [updatedAt] để thêm người ở máy khác không ghi đè tên mới). */
    val metaAt: Long = updatedAt,
    /** Lần sửa số ngày nhắc (tách khỏi tên sổ: hai máy đổi hai thứ khác nhau thì giữ cả hai). */
    val remindAt: Long = metaAt,
) {
    val active: List<FamilyMember> get() = members.filter { !it.deleted }

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 1); put("id", id); put("name", name); put("remind", remind); put("u", updatedAt); put("mu", metaAt); put("ru", remindAt)
        put("members", JSONArray().apply { members.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(j: JSONObject): FamilyGroup {
            val arr = j.optJSONArray("members") ?: JSONArray()
            val u = j.optLong("u", 0L)
            val mu = j.optLong("mu", u)
            val name = j.optString("name").trim().ifEmpty { "Gia đình" }.takeSafe(40)
            return FamilyGroup(
                id = j.optString("id").filter { it.isLetterOrDigit() }.take(32).ifEmpty { stableId("group|$name") },
                name = name,
                // Bỏ người không có cả tên lẫn quan hệ (trừ dấu xóa); giới hạn 300 người còn trong sổ,
                // dấu xóa giữ tối đa 300 cái mới nhất (không chiếm chỗ của người thật)
                members = FamilyBook.capMembers(
                    (0 until minOf(arr.length(), FamilyBook.MAX_ENTRIES))
                        .map { FamilyMember.fromJson(arr.getJSONObject(it)) }   // phần tử không phải đối tượng: mã hỏng
                        .filter { it.deleted || it.name.isNotBlank() || it.relation.isNotBlank() }
                        .distinctBy { it.id },
                ),
                remind = j.optInt("remind", 3).coerceIn(0, 30),
                updatedAt = u,
                metaAt = mu,
                remindAt = j.optLong("ru", mu),
            )
        }
    }
}

/** Kết quả nhập mã: sổ sau khi gộp, số người mới và số người được cập nhật. */
data class FamilyImport(
    val group: FamilyGroup,
    val added: Int,
    val updated: Int,
    val isNew: Boolean,
    val removed: Int = 0,
    /** Tên sổ hoặc số ngày nhắc đã đổi theo bản nhận được. */
    val metaChanged: Boolean = false,
)

/** Mã hóa, gộp và đổi sổ giỗ thành sự kiện; phần thuần (không cần Context) để kiểm thử. */
object FamilyBook {
    const val PREFIX = "LICHGIAPHA1:"
    const val MAX_MEMBERS = 300
    /** Số phần tử tối đa đọc từ mã (JSON đã giới hạn 512 KB nên đây chỉ là chặn trên an toàn). */
    const val MAX_ENTRIES = 5000

    /**
     * Giữ tối đa [MAX_MEMBERS] người còn trong sổ (ưu tiên những người trong [prefer], ví dụ người đã có trên máy)
     * và [MAX_MEMBERS] dấu xóa mới nhất; giữ nguyên thứ tự.
     */
    fun capMembers(list: List<FamilyMember>, prefer: Set<String> = emptySet(), keepDeleted: Set<String> = emptySet()): List<FamilyMember> {
        val live = list.filter { !it.deleted }
        val del = list.filter { it.deleted }
        if (live.size <= MAX_MEMBERS && del.size <= MAX_MEMBERS) return list
        val keep = (live.filter { it.id in prefer } + live.filter { it.id !in prefer }).take(MAX_MEMBERS).map { it.id }.toSet() +
            del.sortedWith(compareByDescending<FamilyMember> { it.id in keepDeleted }.thenByDescending { it.updatedAt })
                .take(MAX_MEMBERS).map { it.id }
        return list.filter { it.id in keep }
    }
    private const val MAX_JSON = 512 * 1024
    private const val MAX_DEPTH = 16
    private val CODE = Regex("""LICHGIAPHA1:\s*([A-Za-z0-9_-]+)""")
    const val EVENT_PREFIX = "fam-"

    /** Mã chia sẻ: tiền tố + JSON nén deflate, base64 an toàn cho URL (gửi qua Zalo, Messenger không vỡ). */
    fun encode(g: FamilyGroup): String {
        val raw = g.toJson().toString().toByteArray(Charsets.UTF_8)
        val d = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(raw); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    /** Tìm và giải mã trong một đoạn văn bản bất kỳ (cả tin nhắn được dán vào); null nếu không có mã hợp lệ. */
    /** Sổ (đã gộp mọi phiên bản) của mã hợp lệ cuối cùng trong đoạn văn bản; null nếu không có mã hợp lệ. */
    fun decode(text: String): FamilyGroup? = decodeAll(text).lastOrNull()

    /**
     * Mọi sổ trong đoạn dán (có thể là cả đoạn chat): mỗi mã hợp lệ được giải; mã bị ứng dụng nhắn tin bẻ xuống dòng
     * được nối lại; nhiều phiên bản của cùng một sổ được gộp với nhau (không phụ thuộc thứ tự tin nhắn).
     * Thứ tự kết quả theo lần xuất hiện cuối của từng sổ.
     */
    fun decodeAll(input: String, now: Long = System.currentTimeMillis()): List<FamilyGroup> {
        // Chuẩn hoá đoạn dán: khoảng trắng không ngắt dòng thành dấu cách, bỏ ký tự rộng 0; mỗi mã bắt đầu một dòng mới
        // (hai mã dính liền nhau không bị nuốt vào nhau)
        val text = input
            .replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ')
            .replace("\u200B", "").replace("\u200C", "").replace("\u200D", "").replace("\uFEFF", "")
            .replace(PREFIX, "\n" + PREFIX)
        val found = LinkedHashMap<String, FamilyGroup>()
        var total = 20_000   // tổng số lần thử cho cả lần dán (đoạn dán rất dài, rất nhiều mã hỏng)
        for (m in CODE.findAll(text)) {
            if (total <= 0) break
            val code = m.groupValues[1]
            // Mỗi mã có lượt thử riêng: một mã hỏng (bị cắt rồi nối nhầm vào đoạn chat dài) không làm bỏ qua mã sau
            val budget = intArrayOf(minOf(2000, total))
            val g = decodeLoose(code, budget) ?: decodeWrapped(text, m, budget)
            total -= minOf(2000, total) - budget[0].coerceAtLeast(0)
            if (g == null) continue
            val prev = found.remove(g.id)
            // Nhiều phiên bản của cùng một sổ trong một lần dán: gộp giao hoán (không phụ thuộc thứ tự tin nhắn)
            found[g.id] = if (prev == null) g else combine(prev, g, now)
        }
        return found.values.toList()
    }

    /** Giải mã; mã dính liền chữ phía sau (ví dụ "…<mã>Cảm ơn") thì thử bỏ 1–3 ký tự cuối. */
    private fun decodeLoose(code: String, budget: IntArray): FamilyGroup? {
        for (k in 0..3) {
            if (code.length <= k || budget[0]-- <= 0) return null
            decodeOne(if (k == 0) code else code.dropLast(k))?.let { return it }
        }
        return null
    }

    /**
     * Gộp hai phiên bản của cùng một sổ có trong một lần dán: giao hoán; một bên mang mốc "tương lai" (máy chạy nhanh giờ)
     * thì bên kia thắng, cả hai cùng tương lai hoặc cùng hiện tại thì bản sửa sau thắng. Giữ nguyên mốc gốc để bước nhập
     * vào máy ([merge]) còn xét luật giờ tương lai. Không cắt 300 người ở bước này (cắt khi nhập, ưu tiên người trên máy).
     */
    private fun combine(a: FamilyGroup, b: FamilyGroup, now: Long): FamilyGroup {
        val skew = now + SKEW
        fun firstWins(ta: Long, tb: Long, tie: Boolean): Boolean {
            val fa = ta > skew
            val fb = tb > skew
            return when {
                fa != fb -> !fa
                ta != tb -> ta > tb
                else -> tie
            }
        }
        val am = a.members.associateBy { it.id }
        val bm = b.members.associateBy { it.id }
        val members = (am.keys + bm.keys).map { id ->
            val x = am[id]
            val y = bm[id]
            when {
                x == null -> y!!
                y == null -> x
                firstWins(x.updatedAt, y.updatedAt, x.toJson().toString() >= y.toJson().toString()) -> x
                else -> y
            }
        }.sortedWith(compareBy({ it.lunarMonth }, { it.lunarDay }, { it.id }))
        val nameA = firstWins(a.metaAt, b.metaAt, a.name >= b.name)
        val remA = firstWins(a.remindAt, b.remindAt, a.remind >= b.remind)
        return FamilyGroup(
            id = a.id, name = if (nameA) a.name else b.name, members = members, remind = if (remA) a.remind else b.remind,
            updatedAt = maxOf(a.updatedAt, b.updatedAt), metaAt = if (nameA) a.metaAt else b.metaAt,
            remindAt = if (remA) a.remindAt else b.remindAt,
        )
    }


    /** Mã bị xuống dòng giữa chừng: nối dần các đoạn chữ base64 ngay sau, thử giải tới khi khớp (tối đa 12 đoạn). */
    private fun decodeWrapped(text: String, m: MatchResult, budget: IntArray): FamilyGroup? {
        var code = m.groupValues[1]
        var pos = m.range.last + 1
        // Bẻ dòng 64–76 ký tự: sổ 300 người có thể thành vài trăm dòng
        repeat(1000) {
            if (code.length > MAX_JSON * 2) return null
            // Đoạn tiếp theo: khoảng trắng / xuống dòng rồi một dãy ký tự base64 (đọc trực tiếp, không giới hạn độ dài dòng)
            var i = pos
            while (i < text.length && (text[i].isWhitespace() || text[i] == '>')) i++
            if (i == pos) return null
            var j = i
            while (j < text.length && text[j].let { it < '\u0080' && (it.isLetterOrDigit() || it == '_' || it == '-') }) j++
            if (j == i) return null
            code += text.substring(i, j)
            pos = j
            decodeLoose(code, budget)?.let { return it }
            if (budget[0] <= 0) return null
        }
        return null
    }

    /** Độ lồng ngoặc lớn nhất của JSON (bỏ qua ngoặc trong chuỗi); chặn JSON lồng sâu làm tràn ngăn xếp. */
    private fun depth(json: String): Int {
        var d = 0; var max = 0; var inStr = false; var esc = false
        for (ch in json) {
            when {
                esc -> esc = false
                inStr && ch == '\\' -> esc = true
                ch == '"' -> inStr = !inStr
                !inStr && (ch == '{' || ch == '[') -> { d++; if (d > max) max = d }
                !inStr && (ch == '}' || ch == ']') -> d--
            }
        }
        return max
    }

    private fun decodeOne(code: String): FamilyGroup? {
        val inf = Inflater()
        return try {
            val bytes = Base64.getUrlDecoder().decode(code)
            inf.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            var guard = 0
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > MAX_JSON || ++guard > 10_000) return null   // chặn dữ liệu bất thường (bom nén)
            }
            // Mã bị cắt cụt (chưa tới cuối dòng nén, chưa kiểm tổng): không đoán, coi là hỏng
            if (!inf.finished()) return null
            val json = out.toString(Charsets.UTF_8.name())
            if (depth(json) > MAX_DEPTH) return null
            FamilyGroup.fromJson(JSONObject(json))
        } catch (e: Exception) {
            null
        } catch (e: StackOverflowError) {
            null
        } finally {
            inf.end()
        }
    }

    /**
     * Gộp sổ nhận được [incoming] vào sổ trên máy [local] (cùng id): mỗi người lấy bản sửa sau cùng
     * (kể cả dấu xóa); tên sổ, số ngày nhắc lấy theo bản sửa sau cùng.
     */
    fun merge(local: FamilyGroup?, incomingRaw: FamilyGroup, now: Long = System.currentTimeMillis()): FamilyImport {
        // Máy để sai giờ ở tương lai (quá 10 phút) không được đè dữ liệu đã có: bản sửa đó chỉ được thêm người mới,
        // và mốc của nó bị kéo về bây giờ để không thắng mãi các lần sửa sau
        val skew = now + 10 * 60 * 1000L
        val future = incomingRaw.members.filter { it.updatedAt > skew }.map { it.id }.toSet()
        val incoming = incomingRaw.copy(
            members = incomingRaw.members.map { if (it.updatedAt > skew) it.copy(updatedAt = 1L) else it },
            updatedAt = minOf(incomingRaw.updatedAt, now),
            metaAt = minOf(incomingRaw.metaAt, now),
            remindAt = minOf(incomingRaw.remindAt, now),
        )
        val metaFromFuture = incomingRaw.metaAt > skew
        val remindFromFuture = incomingRaw.remindAt > skew
        if (local == null) {
            val capped = incoming.copy(members = capMembers(incoming.members))
            return FamilyImport(capped, capped.active.size, 0, true)
        }
        val byId = local.members.associateBy { it.id }.toMutableMap()
        var added = 0
        var updated = 0
        var removed = 0
        for (m in incoming.members) {
            val old = byId[m.id]
            // Trùng mốc nhưng khác nội dung: chọn cố định theo nội dung để mọi máy cùng ra một kết quả
            // Bản trên máy mang mốc tương lai (máy từng chạy nhanh giờ) thì bản hiện tại nhận được sẽ thắng
            val wins = old != null && m.id !in future && (old.updatedAt > skew || m.updatedAt > old.updatedAt ||
                (m.updatedAt == old.updatedAt && m != old && m.toJson().toString() > old.toJson().toString()))
            when {
                old == null -> { byId[m.id] = m; if (!m.deleted) added++ }
                wins -> {
                    val before = old!!
                    byId[m.id] = m
                    when {
                        m.deleted && !before.deleted -> removed++
                        !m.deleted && before.deleted -> added++
                        !m.deleted -> updated++
                    }
                }
            }
        }
        val metaNewer = !metaFromFuture && (local.metaAt > skew || incoming.metaAt > local.metaAt ||
            (incoming.metaAt == local.metaAt && incoming.name > local.name))
        val remindNewer = !remindFromFuture && (local.remindAt > skew || incoming.remindAt > local.remindAt ||
            (incoming.remindAt == local.remindAt && incoming.remind > local.remind))
        val localLive = local.active.map { it.id }.toSet()
        // Dấu xóa vừa thắng một bản còn sống của bên gửi tới phải được giữ, nếu không lần dán sau người đó sống lại
        val incomingLive = incoming.active.map { it.id }.toSet()
        val members = capMembers(byId.values.sortedWith(compareBy({ it.lunarMonth }, { it.lunarDay })), localLive, incomingLive)
        // Người mới bị loại vì quá giới hạn thì không tính là đã thêm
        added = members.count { !it.deleted && it.id !in localLive && local.members.none { o -> o.id == it.id && !o.deleted } }
        val merged = FamilyGroup(
            id = local.id,
            name = if (metaNewer) incoming.name else local.name,
            members = members,
            remind = if (remindNewer) incoming.remind else local.remind,
            updatedAt = maxOf(local.updatedAt, incoming.updatedAt),
            // Mốc tên sổ chỉ đổi khi nhận tên mới (bản từ máy sai giờ bị bỏ thì giữ mốc cũ)
            metaAt = if (metaNewer) incoming.metaAt else local.metaAt,
            remindAt = if (remindNewer) incoming.remindAt else local.remindAt,
        )
        val metaChanged = merged.name != local.name || merged.remind != local.remind
        return FamilyImport(merged, added, updated, false, removed, metaChanged)
    }

    /** Sự kiện giỗ âm lịch lặp hằng năm cho từng người trong sổ (id cố định để cập nhật, xóa đúng). */
    fun toEvents(g: FamilyGroup): List<Event> = g.active.map { m ->
        val notes = mutableListOf<String>()
        if (m.assignee.isNotBlank()) notes += "Lo đồ cúng: ${m.assignee}"
        if (m.place.isNotBlank()) notes += m.place
        if (m.note.isNotBlank()) notes += m.note
        notes += "Sổ giỗ ${g.name}"
        Event(
            id = eventId(g.id, m.id), title = m.eventTitle, icon = "🕯️", lunar = true,
            day = m.lunarDay, month = m.lunarMonth, year = null, yearly = true, time = "",
            note = notes.joinToString(" · ").takeSafe(160), remind = g.remind,
        )
    }

    private const val SKEW = 10 * 60 * 1000L

    /**
     * Mốc cho một lần sửa trên máy: luôn sau mốc cũ (máy vừa nhận từ máy chạy nhanh vài phút vẫn thắng khi sửa lại),
     * trừ khi mốc cũ ở tương lai xa (máy từng sai giờ) thì lấy giờ hiện tại.
     */
    fun stamp(old: Long, now: Long): Long = if (old > now + SKEW) now else maxOf(now, old + 1)

    /** Thêm / sửa một người; null nếu sổ đã đủ [MAX_MEMBERS] người. */
    fun saveMember(g: FamilyGroup, m: FamilyMember, now: Long): FamilyGroup? {
        val old = g.members.firstOrNull { it.id == m.id }
        if ((old == null || old.deleted) && g.active.size >= MAX_MEMBERS) return null
        val t = stamp(old?.updatedAt ?: 0L, now)
        return g.copy(members = g.members.filter { it.id != m.id } + m.copy(updatedAt = t, deleted = false), updatedAt = stamp(g.updatedAt, now))
    }

    /** Xóa một người: để lại dấu xóa không mang thông tin cá nhân (để máy khác cũng xóa theo). */
    fun deleteMember(g: FamilyGroup, id: String, now: Long): FamilyGroup = g.copy(
        members = g.members.map {
            if (it.id == id) it.copy(name = "", relation = "", place = "", assignee = "", note = "", deathYear = null, deleted = true,
                updatedAt = stamp(it.updatedAt, now))
            else it
        },
        updatedAt = stamp(g.updatedAt, now),
    )

    /** Đổi tên sổ / số ngày nhắc (mỗi thứ một mốc riêng); null nếu không có gì đổi. */
    fun updateMeta(g: FamilyGroup, name: String, remind: Int, now: Long): FamilyGroup? {
        val n = name.trim().ifEmpty { g.name }.takeSafe(40)
        val r = remind.coerceIn(0, 30)
        if (n == g.name && r == g.remind) return null
        return g.copy(
            name = n, remind = r, updatedAt = stamp(g.updatedAt, now),
            metaAt = if (n != g.name) stamp(g.metaAt, now) else g.metaAt,
            remindAt = if (r != g.remind) stamp(g.remindAt, now) else g.remindAt,
        )
    }

    fun eventId(groupId: String, memberId: String) = "$EVENT_PREFIX$groupId-$memberId"

    /** Id ngắn chỉ gồm chữ số hex (không có dấu gạch, để tiền tố sự kiện "fam-<sổ>-" không lẫn giữa các sổ). */
    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    /** Ngày dương của lần giỗ tới, tính từ [from]; tháng âm không có ngày 30 thì lấy ngày cuối tháng. */
    fun nextDate(m: FamilyMember, from: LocalDate): LocalDate? {
        val ly = LunarCalendar.convert(from).year
        for (y in ly..ly + 2) {
            val d = LunarCalendar.toSolar(m.lunarDay, m.lunarMonth, y)
                ?: if (m.lunarDay == 30) LunarCalendar.toSolar(29, m.lunarMonth, y) else null
            if (d != null && !d.isBefore(from)) return d
        }
        return null
    }

    /** Phần chữ đọc được trong tin nhắn không được chứa chuỗi giống mã (tên người, tên sổ gõ "LICHGIAPHA1:…"). */
    fun safe(s: String) = s.replace("LICHGIAPHA", "LICH GIAPHA")

    /** Tin nhắn chia sẻ: danh sách dễ đọc cho người chưa cài app, kèm mã để app nhập. */
    fun shareText(g: FamilyGroup, today: LocalDate): String {
        val lines = mutableListOf("📜 Sổ giỗ \"${safe(g.name)}\" (${g.active.size} ngày giỗ)")
        g.active.sortedWith(compareBy({ it.lunarMonth }, { it.lunarDay })).forEach { m ->
            val next = nextDate(m, today)
            val whenText = next?.let { " (lần tới ${it.dayOfMonth}/${it.monthValue}/${it.year})" } ?: ""
            val who = if (m.assignee.isNotBlank()) " · lo đồ cúng: ${m.assignee}" else ""
            lines += safe("• ${m.eventTitle}: ${m.lunarDay}/${m.lunarMonth} âm$whenText$who")
        }
        lines += ""
        val nhac = if (g.remind == 0) "được nhắc đúng ngày giỗ" else "được nhắc trước ${g.remind} ngày"
        lines += "Mở app Lịch & Thời tiết → Công cụ → Sổ giỗ gia đình → Nhập mã, dán cả tin nhắn này để cùng $nhac."
        lines += "Tải app: https://github.com/Ngocha2607/lich-am-widget/releases/latest"
        lines += encode(g)
        return lines.joinToString("\n")
    }
}

/** Lưu sổ giỗ trên máy và đồng bộ thành sự kiện trong [EventStore]. */
object FamilyStore {
    private const val KEY = "family_json"
    private val LOCK = Any()

    fun load(ctx: Context): List<FamilyGroup> {
        val s = Prefs.get(ctx).getString(KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            List(a.length()) { FamilyGroup.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            // Không đọc được: cất bản gốc (một lần) để còn cứu, rồi coi như chưa có sổ
            val sp = Prefs.get(ctx)
            if (!sp.contains(KEY + "_bad")) sp.edit().putString(KEY + "_bad", s).apply()
            emptyList()
        }
    }

    private fun saveAll(ctx: Context, groups: List<FamilyGroup>) {
        val a = JSONArray().apply { groups.forEach { put(it.toJson()) } }
        Prefs.get(ctx).edit().putString(KEY, a.toString()).apply()
    }

    fun get(ctx: Context, id: String): FamilyGroup? = load(ctx).firstOrNull { it.id == id }

    /** Ghi sổ và thay toàn bộ sự kiện giỗ của sổ đó trong danh sách sự kiện. */
    fun put(ctx: Context, g: FamilyGroup) = synchronized(LOCK) {
        saveAll(ctx, load(ctx).filter { it.id != g.id } + g)
        syncEvents(ctx, g.id, FamilyBook.toEvents(g))
    }

    /** Đọc sổ [id], sửa bằng [change] rồi ghi lại, trong một khóa; null nếu không có sổ. */
    fun update(ctx: Context, id: String, change: (FamilyGroup) -> FamilyGroup?): FamilyGroup? = synchronized(LOCK) {
        val g = get(ctx, id) ?: return null
        val n = change(g) ?: return null
        put(ctx, n)
        n
    }

    fun delete(ctx: Context, id: String) = synchronized(LOCK) {
        saveAll(ctx, load(ctx).filter { it.id != id })
        syncEvents(ctx, id, emptyList())
    }

    private fun syncEvents(ctx: Context, groupId: String, events: List<Event>) =
        EventStore.replaceByPrefix(ctx, "${FamilyBook.EVENT_PREFIX}$groupId-", events)

    fun create(ctx: Context, name: String): FamilyGroup {
        val g = FamilyGroup(FamilyBook.newId(), name.trim().ifEmpty { "Gia đình" }.takeSafe(40), emptyList(), 3, System.currentTimeMillis())
        put(ctx, g)
        return g
    }

    /** Nhập mọi sổ có trong đoạn dán; danh sách rỗng nếu không có mã hợp lệ. */
    fun importText(ctx: Context, text: String): List<FamilyImport> = FamilyBook.decodeAll(text).map { incoming ->
        synchronized(LOCK) {
            val r = FamilyBook.merge(get(ctx, incoming.id), incoming)
            put(ctx, r.group)
            r
        }
    }

    /** Nhập sổ cuối cùng trong đoạn dán; null nếu không có mã hợp lệ. */
    fun import(ctx: Context, text: String): FamilyImport? = importText(ctx, text).lastOrNull()

    /** Sao lưu: mảng JSON các sổ. */
    fun exportAll(ctx: Context): JSONArray = JSONArray().apply { load(ctx).forEach { put(it.toJson()) } }

    /** Khôi phục từ sao lưu: gộp từng sổ như khi nhập mã. Trả về số sổ. */
    fun importAll(ctx: Context, arr: JSONArray): Int {
        var n = 0
        for (i in 0 until arr.length()) {
            val incoming = try { FamilyGroup.fromJson(arr.getJSONObject(i)) } catch (e: Exception) { continue }
            synchronized(LOCK) { put(ctx, FamilyBook.merge(get(ctx, incoming.id), incoming).group) }
            n++
        }
        return n
    }
}
