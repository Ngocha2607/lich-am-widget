package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** QA tester B, vòng 3: chọn thơ Zen theo mùa / tháng âm / ngày đặc biệt. */
class QaB3ZenTest {
    private val SPECIAL = setOf("tet1", "tet2", "tet", "giaothua", "bamuoi", "ram1", "ram8")

    private fun days(from: LocalDate, to: LocalDate) = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }

    private fun seasonOf(d: LocalDate) = when (d.monthValue) { 2, 3, 4 -> "xuan"; 5, 6, 7 -> "he"; 8, 9, 10 -> "thu"; else -> "dong" }

    @Test
    fun ok_300NamKhongLapHomTruocDungMuaNgayLeDungCau() {
        val bad = mutableListOf<String>()
        var prev: Verse? = null
        val seen = mutableMapOf<Verse, Int>()
        for (d in days(LocalDate.of(1900, 2, 1), LocalDate.of(2199, 12, 31))) {
            val v = ZenVerses.forDate(d)
            val l = LunarCalendar.convert(d)
            val n = LunarCalendar.convert(d.plusDays(1))
            seen[v] = (seen[v] ?: 0) + 1
            if (v == prev) bad += "$d lặp câu hôm trước: ${v.text.lines().first()}"
            val expectTag = when {
                l.month == 12 && n.month == 1 && n.day == 1 && !n.leap -> "giaothua"
                !l.leap && l.month == 1 && l.day == 1 -> "tet1"
                !l.leap && l.month == 1 && l.day == 2 -> "tet2"
                !l.leap && l.month == 1 && l.day in 3..4 -> "tet"
                !l.leap && l.month == 1 && l.day == 15 -> "ram1"
                !l.leap && l.month == 8 && l.day == 15 -> "ram8"
                else -> null
            }
            val okTag = v.tag == expectTag || (expectTag == "giaothua" && v.tag == "bamuoi" && l.day == 30)
            if (expectTag != null && !okTag) bad += "$d (${l.day}/${l.month}${if (l.leap) "N" else ""}) phải là câu $expectTag, ra [${v.tag}]"
            if (expectTag == null) {
                if (v.tag in SPECIAL) bad += "$d ngày thường ra câu ngày lễ [${v.tag}]"
                if (!ZenVerses.fits(v, d, l)) bad += "$d không hợp mùa / quãng [${v.tag}]"
            }
            prev = v
            if (bad.size > 20) break
        }
        assertEquals(bad.take(20).joinToString("\n"), 0, bad.size)
        val missing = ZenVerses.ALL.filter { it !in seen }
        assertEquals(missing.joinToString { it.text.lines().first() }, 0, missing.size)
        // Câu ngày thường ít gặp nhất vẫn xuất hiện vài lần mỗi năm (không bị "chết đói")
        val rare = seen.filter { it.key.tag !in SPECIAL }.minByOrNull { it.value }!!
        System.err.println("QAB3 Zen 300 năm: câu ít nhất '${rare.key.text.lines().first()}' [${rare.key.tag}] ${rare.value} lần; nhiều nhất ${seen.values.maxOrNull()}")
        assertTrue(rare.value >= 50)
    }

    @Test
    fun bug_cauTetGhiRoNgayHienSaiNgay() {
        // Hai câu "tet" luân phiên trên mùng 1–4: "Mùng Một đầu năm…" và "Sáng mùng Hai Tết…" ⇒ năm thì mùng 1 hiện "Sáng mùng Hai Tết",
        // mùng 2 hiện "Mùng Một đầu năm", mùng 3/4 hiện câu ghi mùng Một / mùng Hai.
        val wrong = mutableListOf<String>()
        for (y in 2026..2040) for (dd in 1..4) {
            val d = LunarCalendar.toSolar(dd, 1, y)!!
            val v = ZenVerses.forDate(d)
            val first = v.text.lines().first()
            val saysDay = when {
                first.contains("Mùng Một", ignoreCase = true) -> 1
                first.contains("mùng Hai", ignoreCase = true) -> 2
                else -> null
            }
            if (saysDay != null && saysDay != dd) wrong += "Tết $y mùng $dd ($d): \"$first\""
        }
        assertEquals("${wrong.size} ngày Tết hiện câu ghi sai ngày:\n" + wrong.take(12).joinToString("\n"), 0, wrong.size)
    }

    @Test
    fun nit_cauCuoiNamHienSauTetCauXuanTanHienTruocTet() {
        // Mùa theo tháng dương: tháng 1 là "dong" kể cả sau Tết (Tết sớm 21/1–31/1), tháng 2 là "xuan" kể cả trước Tết.
        val endOfYear = listOf("cuối năm", "mùa đông trở về", "đầu đông")
        val lateSpring = listOf("xuân tàn", "Xuân vãn", "Chúa xuân nay đã")
        val bad = mutableListOf<String>()
        for (d in days(LocalDate.of(2026, 1, 1), LocalDate.of(2060, 12, 31))) {
            val l = LunarCalendar.convert(d)
            val v = ZenVerses.forDate(d)
            val txt = v.text + " " + v.by
            if (l.month == 1 && !l.leap && endOfYear.any { txt.contains(it) }) bad += "$d (${l.day} tháng Giêng): ${v.text.replace("\n", " / ")}"
            if (l.month == 12 && lateSpring.any { txt.contains(it, ignoreCase = true) }) bad += "$d (${l.day} tháng Chạp): ${v.text.replace("\n", " / ")}"
        }
        assertEquals("${bad.size} ngày:\n" + bad.take(10).joinToString("\n"), 0, bad.size)
    }

    @Test
    fun ok_forDateNhanhTrenLuongGiaoDien() {
        val start = LocalDate.of(2026, 1, 1)
        repeat(2000) { ZenVerses.forDate(start.plusDays(it.toLong())) }   // làm nóng JIT
        val n = 20_000
        val t0 = System.nanoTime()
        for (i in 0 until n) ZenVerses.forDate(start.plusDays(i.toLong()))
        val us = (System.nanoTime() - t0) / 1000.0 / n
        // Một lần convert để so sánh
        val t1 = System.nanoTime()
        for (i in 0 until n) LunarCalendar.convert(start.plusDays(i.toLong()))
        val convUs = (System.nanoTime() - t1) / 1000.0 / n
        System.err.println("QAB3 ZenVerses.forDate: %.1f µs/lần trên JVM (LunarCalendar.convert %.1f µs ⇒ ≈ %.0f lần convert)".format(us, convUs, us / convUs))
        // Máy Android tầm thấp chậm hơn JVM desktop ~20–50 lần: vẫn cần < 0,5 ms trên JVM để < 16 ms một khung hình
        assertTrue("%.1f µs".format(us), us < 500)
    }
}
