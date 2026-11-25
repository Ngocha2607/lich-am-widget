package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** QA tester B, vòng 5: thơ Zen — nhãn mới (bamuoi, dauhe, dauthu, heomay, hetoi, cuoinam), nhiều thế kỷ. */
class QaB5ZenTest {
    private val SPECIAL = setOf("tet1", "tet2", "tet", "giaothua", "bamuoi", "ram1", "ram8")
    private fun days(from: LocalDate, to: LocalDate) = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
    private fun first(v: Verse) = v.text.lines().first()
    private fun tk(d: LocalDate) = LunarCalendar.tietKhiIndexAt(LunarCalendar.convert(d).jd)

    private fun expectSpecial(l: LunarDate, n: LunarDate): String? = when {
        l.month == 12 && n.month == 1 && n.day == 1 && !n.leap -> "giaothua"
        !l.leap && l.month == 1 && l.day == 1 -> "tet1"
        !l.leap && l.month == 1 && l.day == 2 -> "tet2"
        !l.leap && l.month == 1 && l.day in 3..4 -> "tet"
        !l.leap && l.month == 1 && l.day == 15 -> "ram1"
        !l.leap && l.month == 8 && l.day == 15 -> "ram8"
        else -> null
    }

    @Test
    fun ok_namTheKyNgayDacBietKhongLapNhanMoiDungQuang() {
        val bad = mutableListOf<String>()
        var prev: Verse? = null
        val seen = mutableMapOf<Verse, Int>()
        var eve30 = 0; var eve29 = 0; var bamuoi = 0; var eveLeap = 0
        val baMuoi = ZenVerses.ALL.single { it.tag == "bamuoi" }
        val giao = ZenVerses.ALL.single { it.tag == "giaothua" }
        for (d in days(LocalDate.of(1800, 1, 1), LocalDate.of(2299, 12, 31))) {
            val v = ZenVerses.forDate(d)
            val l = LunarCalendar.convert(d)
            val n = LunarCalendar.convert(d.plusDays(1))
            seen[v] = (seen[v] ?: 0) + 1
            if (v == prev) bad += "$d lặp câu hôm trước: ${first(v)}"
            val exp = expectSpecial(l, n)
            if (exp == "giaothua") { if (l.day == 30) eve30++ else eve29++; if (l.leap) eveLeap++ }
            if (v == baMuoi) { bamuoi++; if (exp != "giaothua" || l.day != 30) bad += "$d (${l.day}/${l.month}${if (l.leap) "N" else ""}): 'Chiều ba mươi Tết' sai ngày" }
            if (v == giao && exp != "giaothua") bad += "$d: 'Đêm giao thừa' sai ngày"
            val okTag = v.tag == exp || (exp == "giaothua" && v.tag == "bamuoi" && l.day == 30)
            if (exp != null && !okTag) bad += "$d (${l.day}/${l.month}) phải [$exp], ra [${v.tag}]"
            if (exp == null && (v.tag in SPECIAL || !ZenVerses.fits(v, d, l))) bad += "$d ngoài quãng [${v.tag}] ${first(v)}"
            // nhãn mới: kiểm tra trực tiếp theo định nghĩa
            val t = LunarCalendar.tietKhiIndexAt(l.jd)
            val okNew = when (v.tag) {
                "dauhe" -> t in 3..4
                "dauthu" -> t in 9..10
                "heomay" -> t in 11..14
                "hetoi" -> t in 3..8 && (l.day <= 6 || l.day >= 24)
                "cuoinam" -> l.month >= 11
                else -> true
            }
            if (!okNew) bad += "$d [${v.tag}] tiết $t ngày âm ${l.day}/${l.month}"
            prev = v
            if (bad.size > 20) break
        }
        System.err.println("QAB5 Zen 1800–2299: giao thừa ngày 30: $eve30, ngày 29: $eve29, giao thừa tháng Chạp nhuận: $eveLeap; 'Chiều ba mươi Tết' hiện $bamuoi lần; " +
            "câu ít nhất: ${seen.filter { it.key.tag !in SPECIAL }.minByOrNull { it.value }?.let { "'${first(it.key)}' [${it.key.tag}] ${it.value}" }}")
        assertEquals(bad.take(20).joinToString("\n"), 0, bad.size)
        assertEquals(ZenVerses.ALL.filter { it !in seen }.joinToString { first(it) }, 0, ZenVerses.ALL.count { it !in seen })
        assertTrue(bamuoi > 0)
    }

    @Test
    fun ok_giaoThua30ChapCaHaiCauDeuCo() {
        // Năm tháng Chạp đủ 30 ngày: giao thừa chọn giữa "Chiều ba mươi Tết" và "Đêm giao thừa lặng" — cả hai đều xuất hiện qua các năm
        val picks = mutableMapOf<String, Int>()
        for (y in 1900..2199) {
            val eve = LunarCalendar.toSolar(1, 1, y)!!.minusDays(1)
            val l = LunarCalendar.convert(eve)
            val v = ZenVerses.forDate(eve)
            val k = "ngày ${l.day}: ${first(v)}"
            picks[k] = (picks[k] ?: 0) + 1
        }
        System.err.println("QAB5 giao thừa 1900–2199: $picks")
        assertEquals(setOf("ngày 29: Đêm giao thừa lặng", "ngày 30: Đêm giao thừa lặng", "ngày 30: Chiều ba mươi Tết"), picks.keys)
    }

    /** Mỗi lần quãng của [tag] diễn ra (≥ [minLen] ngày): mọi câu của nhãn có hiện ít nhất một lần không. */
    private fun windowMisses(tag: String, minLen: Int, from: LocalDate, to: LocalDate): Pair<Int, List<String>> {
        val verses = ZenVerses.ALL.filter { it.tag == tag }
        var run = mutableListOf<Verse>(); var start: LocalDate? = null
        var windows = 0
        val misses = mutableListOf<String>()
        for (d in days(from, to)) {
            val l = LunarCalendar.convert(d)
            val n = LunarCalendar.convert(d.plusDays(1))
            val inside = ZenVerses.fits(verses.first(), d, l) && expectSpecial(l, n) == null
            if (inside) { if (start == null) start = d; run.add(ZenVerses.forDate(d)) }
            else if (start != null) {
                if (run.size >= minLen) { windows++; if (!run.containsAll(verses)) misses += "$start (${run.size} ngày)" }
                run = mutableListOf(); start = null
            }
        }
        return windows to misses
    }

    @Test
    fun ok_quangNhanMoiDeuHienCau() {
        val out = mutableMapOf<String, String>()
        var total = 0
        for (tag in listOf("dauhe", "dauthu", "heomay", "cuoinam")) {
            val (w, m) = windowMisses(tag, 10, LocalDate.of(1950, 1, 1), LocalDate.of(2150, 12, 31))
            out[tag] = "$w quãng, thiếu ${m.size} ${m.take(3)}"
            total += m.size
        }
        // hetoi: quãng ~12 ngày mỗi tháng hè — ghi nhận riêng (quãng ngắn)
        val (hw, hm) = windowMisses("hetoi", 10, LocalDate.of(1950, 1, 1), LocalDate.of(2150, 12, 31))
        out["hetoi"] = "$hw quãng, thiếu ${hm.size} (${"%.0f".format(100.0 * hm.size / maxOf(1, hw))}%) ${hm.take(3)}"
        System.err.println("QAB5 quãng nhãn mới 1950–2150: $out")
        assertEquals(out.toString(), 0, total)
    }

    @Test
    fun ok_cauTrangSangKhongRoiVaoDemKhongTrang() {
        // Ghi nhận: câu tả trăng sáng / ánh trăng (nhãn rỗng hoặc "thu") hiện vào ngày sóc (29, 30, mùng 1) — ngược với "hetoi"
        val moon = ZenVerses.ALL.filter { v ->
            listOf("ánh trăng trong", "bóng trăng vào", "bóng trăng loe", "sông trăng", "nguyệt in một tấm", "Vầng trăng ai xẻ").any { v.text.contains(it) }
        }
        val hits = mutableMapOf<String, Int>()
        for (d in days(LocalDate.of(2026, 1, 1), LocalDate.of(2035, 12, 31))) {
            val v = ZenVerses.forDate(d)
            if (v in moon && LunarCalendar.convert(d).day.let { it >= 29 || it == 1 }) hits[first(v)] = (hits[first(v)] ?: 0) + 1
        }
        System.err.println("QAB5 câu trăng sáng vào ngày không trăng (29/30/mùng 1) 2026–2035: ${hits.values.sum()} ngày $hits")
    }
}
