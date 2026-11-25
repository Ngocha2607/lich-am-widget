package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** QA tester B, vòng 4: thơ Zen — nhãn mùa theo tiết khí, Tết theo ngày, quãng hẹp, nhiều thế kỷ. */
class QaB4ZenTest {
    private val SPECIAL = setOf("tet1", "tet2", "tet", "giaothua", "bamuoi", "ram1", "ram8")
    private val KNOWN = SPECIAL + setOf("", "xuan", "he", "thu", "dong", "xuanmuon", "thumuon", "daudong", "cuoinam", "thang7", "thang12", "trangnon",
        "dauhe", "dauthu", "heomay", "hetoi")

    private fun days(from: LocalDate, to: LocalDate) = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
    private fun first(v: Verse) = v.text.lines().first()

    @Test
    fun ok_haikuNhip454VaNhanHopLe() {
        val bad = ZenVerses.HAIKU.mapNotNull { v ->
            val counts = v.text.lines().map { it.trim().split(Regex("\\s+")).size }
            if (counts != listOf(4, 5, 4) || v.text.contains("  ") || v.text.lines().any { it != it.trim() }) "${v.text.replace("\n", " / ")} → $counts" else null
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
        assertEquals(emptyList<String>(), ZenVerses.ALL.map { it.tag }.filter { it !in KNOWN })
        assertEquals(ZenVerses.ALL.size, ZenVerses.ALL.distinct().size)
        assertEquals(setOf("Câu đối đỏ tươi", "Pháo hoa đã tắt"), ZenVerses.ALL.filter { it.tag == "tet" }.map(::first).toSet())
        assertEquals(listOf("Mùng Một đầu năm"), ZenVerses.ALL.filter { it.tag == "tet1" }.map(::first))
        assertEquals(listOf("Sáng mùng Hai Tết"), ZenVerses.ALL.filter { it.tag == "tet2" }.map(::first))
    }

    @Test
    fun ok_bonTheKyKhongLapDungNgayDungQuang() {
        val bad = mutableListOf<String>()
        var prev: Verse? = null
        val seen = mutableMapOf<Verse, Int>()
        var giaothua29 = 0
        for (d in days(LocalDate.of(1850, 1, 1), LocalDate.of(2199, 12, 31))) {
            val v = ZenVerses.forDate(d)
            val l = LunarCalendar.convert(d)
            val n = LunarCalendar.convert(d.plusDays(1))
            seen[v] = (seen[v] ?: 0) + 1
            if (v == prev) bad += "$d lặp câu hôm trước: ${first(v)}"
            val expect = when {
                l.month == 12 && n.month == 1 && n.day == 1 && !n.leap -> "giaothua"
                !l.leap && l.month == 1 && l.day == 1 -> "tet1"
                !l.leap && l.month == 1 && l.day == 2 -> "tet2"
                !l.leap && l.month == 1 && l.day in 3..4 -> "tet"
                !l.leap && l.month == 1 && l.day == 15 -> "ram1"
                !l.leap && l.month == 8 && l.day == 15 -> "ram8"
                else -> null
            }
            if (expect == "giaothua" && l.day == 29) giaothua29++
            val okTag = v.tag == expect || (expect == "giaothua" && v.tag == "bamuoi" && l.day == 30)
            if (expect != null && !okTag) bad += "$d (${l.day}/${l.month}) phải [$expect], ra [${v.tag}]"
            if (expect == null && (v.tag in SPECIAL || !ZenVerses.fits(v, d, l))) bad += "$d ngoài quãng [${v.tag}] ${first(v)}"
            prev = v
            if (bad.size > 20) break
        }
        assertEquals(bad.take(20).joinToString("\n"), 0, bad.size)
        assertEquals(ZenVerses.ALL.filter { it !in seen }.joinToString { first(it) }, 0, ZenVerses.ALL.count { it !in seen })
        System.err.println("QAB4 Zen 1850–2199: $giaothua29 năm giao thừa vào ngày 29; câu ít nhất ${seen.filter { it.key.tag !in SPECIAL }.minByOrNull { it.value }?.let { "'${first(it.key)}' [${it.key.tag}] ${it.value}" }}")
    }

    @Test
    fun ok_moiQuangHepDeuHienDuCauCuaQuang() {
        // Mỗi lần một quãng hẹp (≥ 10 ngày) diễn ra, mọi câu của nhãn đó hiện ít nhất một lần
        val tags = listOf("thang7", "thang12", "cuoinam", "xuanmuon", "thumuon", "daudong")
        val misses = mutableMapOf<String, Int>()
        val windows = mutableMapOf<String, Int>()
        for (tag in tags) {
            val verses = ZenVerses.ALL.filter { it.tag == tag }
            var run = mutableListOf<Verse>()
            var len = 0
            for (d in days(LocalDate.of(1950, 1, 1), LocalDate.of(2150, 12, 31))) {
                val l = LunarCalendar.convert(d)
                val inside = ZenVerses.fits(verses.first(), d, l)
                if (inside) { run.add(ZenVerses.forDate(d)); len++ }
                if (!inside && len > 0) {
                    if (len >= 10) {
                        windows[tag] = (windows[tag] ?: 0) + 1
                        if (!run.containsAll(verses)) misses[tag] = (misses[tag] ?: 0) + 1
                    }
                    run = mutableListOf(); len = 0
                }
            }
        }
        System.err.println("QAB4 quãng hẹp: số quãng $windows, quãng thiếu câu $misses")
        assertEquals(misses.toString(), 0, misses.values.sum())
    }

    @Test
    fun nit_cauBaMuoiTetHienVaoNgay29() {
        // Tháng Chạp thiếu (29 ngày): giao thừa là 29 Tết nhưng câu "Chiều ba mươi Tết…" vẫn được chọn (cùng loại lỗi câu Tết ghi rõ ngày vòng 3)
        val wrong = mutableListOf<String>()
        var y = 2026
        while (y <= 2100) {
            val tet = LunarCalendar.toSolar(1, 1, y)!!
            val eve = tet.minusDays(1)
            val l = LunarCalendar.convert(eve)
            val v = ZenVerses.forDate(eve)
            if (l.day == 29 && v.text.contains("ba mươi", ignoreCase = true)) wrong += "$eve (29 tháng Chạp, Tết $y): \"${first(v)}\""
            y++
        }
        System.err.println("QAB4 '${"Chiều ba mươi Tết"}' vào ngày 29: ${wrong.size} năm 2026–2100, gần nhất ${wrong.take(3)}")
        assertEquals("${wrong.size} năm:\n" + wrong.take(8).joinToString("\n"), 0, wrong.size)
    }

    @Test
    fun nit_thangNhuanCuoiNamKhongCoCauCuoiNam() {
        // Tháng 11/12 nhuận (vd tháng 11 nhuận năm 2033) vẫn là cuối năm nhưng "cuoinam" loại tháng nhuận ⇒ cả tháng không có câu cuối năm
        val months = mutableListOf<String>()
        for (d in days(LocalDate.of(1900, 1, 1), LocalDate.of(2199, 12, 31))) {
            val l = LunarCalendar.convert(d)
            if (l.leap && l.month >= 11 && l.day == 1) months += "${l.month} nhuận năm ${l.year} ($d)"
        }
        val cuoinam = ZenVerses.ALL.first { it.tag == "cuoinam" }
        val hits = months.map { m ->
            val start = LocalDate.parse(m.substringAfter("(").removeSuffix(")"))
            m to days(start, start.plusDays(28)).count { ZenVerses.forDate(it) == cuoinam }
        }
        System.err.println("QAB4 tháng 11/12 nhuận 1900–2199: $hits")
        assertTrue("tiền đề: có tháng 11/12 nhuận", months.isNotEmpty())
        assertEquals("tháng nhuận cuối năm không hiện câu cuối năm: $hits", 0, hits.count { it.second == 0 })
    }

    @Test
    fun nit_cauKhoiDauMuaHienCuoiMua() {
        // Câu tả lúc mùa bắt đầu (nhãn cả mùa) hiện ở tiết cuối mùa, và câu cuối thu hiện ngay đầu Lập thu
        val rules = listOf(
            Triple("Tiếng ve đầu hè", setOf(7, 8), "Tiểu thử/Đại thử (cuối hè)"),
            Triple("Hạt mưa đầu mùa", setOf(7, 8), "Tiểu thử/Đại thử (cuối hè)"),
            Triple("Thu về rất khẽ", setOf(13, 14), "Hàn lộ/Sương giáng (cuối thu)"),
            Triple("Quả hồng chín đỏ", setOf(9), "Lập thu (giữa tháng 8 dương)"),
            Triple("Gió heo may về", setOf(9), "Lập thu (giữa tháng 8 dương)"),
        )
        val count = mutableMapOf<String, MutableList<LocalDate>>()
        for (d in days(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 12, 31))) {
            val v = ZenVerses.forDate(d)
            val tk = LunarCalendar.tietKhiIndexAt(LunarCalendar.convert(d).jd)
            for ((head, bad, why) in rules) if (first(v) == head && tk in bad) count.getOrPut("$head @ $why") { mutableListOf() } += d
        }
        System.err.println("QAB4 câu đầu mùa ở cuối mùa 2026–2030: " + count.map { "${it.key}: ${it.value.size} ngày (vd ${it.value.take(2)})" })
        assertEquals(count.keys.toString(), 0, count.size)
    }

    @Test
    fun ok_demKhongTrangKhongHienNgayRam() {
        val v = ZenVerses.ALL.first { first(it) == "Đêm không trăng sao" }
        val hits = days(LocalDate.of(2026, 1, 1), LocalDate.of(2035, 12, 31)).filter { ZenVerses.forDate(it) == v && LunarCalendar.convert(it).day in 14..16 }.toList()
        System.err.println("QAB4 'Đêm không trăng sao' vào ngày 14–16 âm (trăng tròn) 2026–2035: ${hits.size} ngày, vd ${hits.take(3)}")
        assertEquals(0, hits.size)
    }

    @Test
    fun perf_forDate() {
        val start = LocalDate.of(2026, 1, 1)
        repeat(3000) { ZenVerses.forDate(start.plusDays(it.toLong())) }
        val n = 20_000
        val t0 = System.nanoTime()
        for (i in 0 until n) ZenVerses.forDate(start.plusDays(i.toLong()))
        val us = (System.nanoTime() - t0) / 1000.0 / n
        System.err.println("QAB4 ZenVerses.forDate %.1f µs/lần".format(us))
        assertTrue(us < 500)
    }
}
