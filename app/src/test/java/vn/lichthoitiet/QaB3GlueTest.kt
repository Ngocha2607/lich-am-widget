package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** QA tester B, vòng 3: nối dòng có thể "dán" chữ lạ vào mã bị cụt và ra một sổ hợp lệ nhưng SAI nội dung. */
class QaB3GlueTest {
    private val T = 1_795_500_000_000L

    /** Sổ cố định (id, mốc cố định) để kết quả lặp lại được. */
    private fun fixedBook() = FamilyGroup(
        "3c211dfad33f", "Họ Đặng chi 2",
        listOf(
            FamilyMember("51e3f00d5f56", "Nguyễn Kim Uyên", "bác cả", 20, 12, 2009, "", "", "cúng trước 1 ngày", T + 79893),
            FamilyMember("3a25423e167a", "Bùi Thu Nga", "chú út", 3, 5, null, "Thủy Nguyên, Hải Phòng", "chú Út", "", T + 30958),
            FamilyMember("61371ac60075", "Vũ Minh Hòa", "ông nội", 9, 9, 1967, "nhà thờ họ", "chị Ba", "", T + 27021),
        ),
        3, T,
    )

    private val words = listOf("ok", "Cảm", "ơn", "bố", "Nhớ", "nhập", "nha", "A", "QQ", "x_y", "abc-def", "LICHGIAPHA1", "👍", "1234", "Yes", "thanks", "OK", "da", "Vang", "Bo")

    /** Mã mất vài ký tự cuối (tin nhắn bị cắt) rồi dòng sau là một chữ ASCII: tìm các trường hợp ra sổ khác sổ gốc. */
    private fun findWrong(limit: Int, code: String = FamilyBook.encode(fixedBook())): List<String> {
        val g = fixedBook()
        val out = mutableListOf<String>()
        for (cut in 1..24) for (w1 in words) for (w2 in words) {
            val text = "Bố gửi: " + code.dropLast(cut) + "\n" + w1 + " " + w2
            val d = FamilyBook.decode(text) ?: continue
            if (d != g) {
                val diff = buildList {
                    if (d.id != g.id) add("id sổ '${d.id}' (gốc '${g.id}')")
                    if (d.name != g.name) add("tên sổ '${d.name}'")
                    if (d.remind != g.remind) add("nhắc ${d.remind}")
                    if (d.updatedAt != g.updatedAt) add("u ${d.updatedAt}")
                    if (d.metaAt != g.metaAt) add("mu ${d.metaAt}")
                    if (d.remindAt != g.remindAt) add("ru ${d.remindAt}")
                    for ((a, b) in g.members.zip(d.members)) if (a != b) add("người ${a.name}: $b")
                    if (g.members.size != d.members.size) add("số người ${d.members.size}")
                }
                out += "mất $cut ký tự cuối, dòng sau \"$w1 $w2\" → ${diff.joinToString("; ")}"
                if (out.size >= limit) return out
            }
        }
        return out
    }

    @Test
    fun bug_maCutNoiVoiChuLaRaSoHopLeNhungSai() {
        System.err.println("QAB3 JSON gốc (JVM): " + fixedBook().toJson().toString().takeLast(80))
        val wrong = findWrong(10_000)
        assertEquals("${wrong.size} trường hợp (trên 24×400 lần dán mã cụt + 2 chữ) giải ra sổ SAI (không phải null):\n" + wrong.take(12).joinToString("\n"), 0, wrong.size)
    }

    /** JSON theo đúng thứ tự khóa của org.json trên Android (LinkedHashMap: thứ tự put), "members" ở cuối. */
    private fun androidOrderJson(g: FamilyGroup): String {
        fun q(s: String) = org.json.JSONObject.quote(s)
        val ms = g.members.joinToString(",") { m ->
            "{\"id\":${q(m.id)},\"name\":${q(m.name)},\"rel\":${q(m.relation)},\"d\":${m.lunarDay},\"m\":${m.lunarMonth}," +
                "\"y\":${m.deathYear ?: "null"},\"place\":${q(m.place)},\"who\":${q(m.assignee)},\"note\":${q(m.note)},\"u\":${m.updatedAt}" +
                (if (m.deleted) ",\"del\":true" else "") + "}"
        }
        return "{\"v\":1,\"id\":${q(g.id)},\"name\":${q(g.name)},\"remind\":${g.remind},\"u\":${g.updatedAt},\"mu\":${g.metaAt},\"ru\":${g.remindAt},\"members\":[$ms]}"
    }

    private fun rawCode(json: String): String {
        val d = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION).apply { setInput(json.toByteArray(Charsets.UTF_8)); finish() }
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return FamilyBook.PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    @Test
    fun ok_maCutNoiVoiChuLaThuTuKhoaAndroid() {
        val json = androidOrderJson(fixedBook())
        assertEquals(fixedBook(), FamilyBook.decode(rawCode(json)))
        System.err.println("QAB3 JSON gốc (thứ tự Android): ..." + json.takeLast(70))
        val wrong = findWrong(10_000, rawCode(json))
        assertEquals("${wrong.size} trường hợp (trên 24×400 lần dán mã cụt + 2 chữ) giải ra sổ SAI:\n" + wrong.take(12).joinToString("\n"), 0, wrong.size)
    }
}

class QaB3GlueWideTest {
    private val words = listOf("ok", "Cảm", "ơn", "bố", "Nhớ", "nhập", "nha", "A", "QQ", "x_y", "abc-def", "1234", "Yes", "thanks", "OK", "da", "Vang", "Bo", "con", "Me")

    private fun book(seed: Int): FamilyGroup {
        val rnd = kotlin.random.Random(seed)
        val ho = listOf("Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi")
        val ten = listOf("An", "Bình", "Cường", "Dung", "Giang", "Hạnh", "Khánh", "Lan", "Mạnh", "Nga")
        val who = listOf("nhà bác Cả", "chú Út", "cô Tư", "anh Hai", "", "chị Ba")
        val notes = listOf("", "", "Cỗ chay", "nhớ mời họ ngoại", "")
        val n = rnd.nextInt(1, 8)
        val members = List(n) { i ->
            FamilyMember(
                "%012x".format(rnd.nextLong(1L shl 47)), "${ho[rnd.nextInt(ho.size)]} Văn ${ten[rnd.nextInt(ten.size)]}", "ông bà", rnd.nextInt(1, 31), rnd.nextInt(1, 13),
                null, "", who[rnd.nextInt(who.size)], notes[rnd.nextInt(notes.size)], 1_795_500_000_000L + rnd.nextLong(100_000_000), deleted = false,
            )
        }.sortedWith(compareBy({ it.lunarMonth }, { it.lunarDay })) +
            (if (rnd.nextBoolean()) listOf(FamilyMember("%012x".format(rnd.nextLong(1L shl 47)), "", "", 30, 12, null, "", "", "", 1_795_500_000_000L + rnd.nextLong(100_000_000), deleted = true)) else emptyList())
        return FamilyGroup("%012x".format(rnd.nextLong(1L shl 47)), "Họ ${ho[rnd.nextInt(ho.size)]}", members, rnd.nextInt(0, 8), 1_795_400_000_000L)
    }

    private fun androidOrderJson(g: FamilyGroup): String {
        fun q(s: String) = org.json.JSONObject.quote(s)
        val ms = g.members.joinToString(",") { m ->
            "{\"id\":${q(m.id)},\"name\":${q(m.name)},\"rel\":${q(m.relation)},\"d\":${m.lunarDay},\"m\":${m.lunarMonth}," +
                "\"y\":${m.deathYear ?: "null"},\"place\":${q(m.place)},\"who\":${q(m.assignee)},\"note\":${q(m.note)},\"u\":${m.updatedAt}" +
                (if (m.deleted) ",\"del\":true" else "") + "}"
        }
        return "{\"v\":1,\"id\":${q(g.id)},\"name\":${q(g.name)},\"remind\":${g.remind},\"u\":${g.updatedAt},\"mu\":${g.metaAt},\"ru\":${g.remindAt},\"members\":[$ms]}"
    }

    private fun rawCode(json: String): String {
        val d = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION).apply { setInput(json.toByteArray(Charsets.UTF_8)); finish() }
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return FamilyBook.PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    @Test
    fun ok_maCutNoiChuLaThuTuKhoaAndroidNhieuSo() {
        val wrong = mutableListOf<String>()
        var tries = 0
        for (seed in 1..40) {
            val g = book(seed)
            val code = rawCode(androidOrderJson(g))
            assertEquals(g, FamilyBook.decode(code))
            for (cut in 1..30) for (w1 in words) for (w2 in listOf("ok", "Cảm", "A", "1234", "da")) {
                tries++
                val d = FamilyBook.decode("Mã: " + code.dropLast(cut) + "\n" + w1 + " " + w2) ?: continue
                if (d != g) {
                    val diffs = mutableListOf<String>()
                    if (d.id != g.id) diffs += "id ${d.id}"
                    if (d.name != g.name || d.remind != g.remind) diffs += "tên/nhắc ${d.name}/${d.remind}"
                    for ((a, b) in g.members.zip(d.members)) if (a != b) diffs += "người ${a.id}: u ${a.updatedAt}→${b.updatedAt}, note '${a.note}'→'${b.note}', del ${a.deleted}→${b.deleted}, who '${a.assignee}'→'${b.assignee}'"
                    if (g.members.size != d.members.size) diffs += "số người ${g.members.size}→${d.members.size}"
                    wrong += "sổ $seed, mất $cut ký tự, \"$w1 $w2\": ${diffs.joinToString("; ")}"
                }
            }
        }
        System.err.println("QAB3 Android-order: $tries lần dán mã cụt + chữ, ${wrong.size} lần ra sổ SAI")
        assertEquals("${wrong.size}/$tries:\n" + wrong.take(12).joinToString("\n"), 0, wrong.size)
    }
}
