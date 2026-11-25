package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer
import java.time.LocalDate
import kotlin.math.abs

/** QA tester B: bầu trời/trăng/tiết khí, màn hình khóa, thơ Zen. */
class QaBSkyZenLockTest {
    private val hanoi = 21.0285 to 105.8542
    private val hcm = 10.8231 to 106.6297

    private fun hm(h: Double) = "%d:%02d".format(h.toInt(), Math.round((h - h.toInt()) * 60).toInt())

    @Test
    fun ok_matTroiSoVoiTimeAndDate() {
        // timeanddate.com (giờ địa phương): (ngày, lat/lon, mọc, lặn)
        val ref = listOf(
            Triple(LocalDate.of(2026, 1, 1), hcm, 6 + 11 / 60.0 to 17 + 41 / 60.0),
            Triple(LocalDate.of(2026, 1, 31), hcm, 6 + 16 / 60.0 to 17 + 56 / 60.0),
            Triple(LocalDate.of(2026, 6, 21), hcm, 5 + 32 / 60.0 to 18 + 17 / 60.0),
            Triple(LocalDate.of(2026, 12, 1), hanoi, 6 + 16 / 60.0 to 17 + 14 / 60.0),
            Triple(LocalDate.of(2026, 12, 22), hanoi, 6 + 29 / 60.0 to 17 + 20 / 60.0),
            Triple(LocalDate.of(2026, 12, 31), hanoi, 6 + 33 / 60.0 to 17 + 25 / 60.0),
        )
        for ((d, ll, rs) in ref) {
            val (r, s) = SkyScene.sunTimes(d, ll.first, ll.second)
            assertTrue("$d mọc ${hm(r)} vs ${hm(rs.first)}", abs(r - rs.first) <= 4 / 60.0)
            assertTrue("$d lặn ${hm(s)} vs ${hm(rs.second)}", abs(s - rs.second) <= 4 / 60.0)
        }
    }

    @Test
    fun ok_dongChi2026SuongMu() {
        // Đông chí 2026: 22/12 lúc 3:50 giờ VN (timeanddate)
        assertEquals("Đông chí", LunarCalendar.tietKhiStartsToday(LunarCalendar.jdFromDate(22, 12, 2026)))
        assertNull(LunarCalendar.tietKhiStartsToday(LunarCalendar.jdFromDate(21, 12, 2026)))
        for (code in listOf(null, 0, 1, 3)) {
            val s = SkyScene.build(LocalDate.of(2026, 12, 22).atTime(20, 0), hanoi.first, hanoi.second, code)
            assertEquals("Đông chí", s.tietKhi)
            assertEquals("mã thời tiết $code", SkyEffect.FOG, s.effect)
        }
    }

    @Test
    fun ok_trangRamTronMung1LuoiLiem() {
        for (y in 2026..2027) for (mo in 1..12) {
            val ram = LunarCalendar.toSolar(15, mo, y) ?: continue
            val night = SkyScene.build(ram.atTime(21, 0), hanoi.first, hanoi.second)
            assertTrue("rằm $mo/$y", night.moonIllum > 0.99f)
            assertNotNull(night.moonPos)
            // Sau nửa đêm, trước lúc mọc: vẫn là trăng rằm
            val late = SkyScene.build(ram.plusDays(1).atTime(2, 0), hanoi.first, hanoi.second)
            assertTrue(late.moonIllum > 0.99f)
            val m1 = LunarCalendar.toSolar(1, mo, y) ?: continue
            val eve = SkyScene.build(m1.atTime(19, 0), hanoi.first, hanoi.second)
            assertTrue("mùng 1 $mo/$y: ${eve.moonIllum}", eve.moonIllum in 0.01f..0.06f)
            assertTrue("trăng non sáng bên phải", eve.moonWaxing)
            assertNotNull(eve.moonPos)
        }
        for (d in 1..15) assertTrue(SkyScene.moonWaxing(d))
        for (d in 16..30) assertFalse(SkyScene.moonWaxing(d))
        // Độ sáng tăng dần tới rằm rồi giảm
        for (d in 1 until 15) assertTrue(SkyScene.moonIllumination(d) <= SkyScene.moonIllumination(d + 1))
        for (d in 16 until 30) assertTrue(SkyScene.moonIllumination(d) >= SkyScene.moonIllumination(d + 1))
    }

    @Test
    fun ok_mauTroiLienTuc() {
        val (r, s) = SkyScene.sunTimes(LocalDate.of(2026, 6, 21), hanoi.first, hanoi.second)
        var prev = SkyScene.skyColors(0.0, r, s)
        for (i in 1..1440) {
            val c = SkyScene.skyColors(i / 60.0, r, s)
            for (sh in listOf(0, 8, 16)) {
                assertTrue("phút $i", abs(((c.first shr sh) and 0xFF) - ((prev.first shr sh) and 0xFF)) <= 12)
                assertTrue("phút $i", abs(((c.second shr sh) and 0xFF) - ((prev.second shr sh) and 0xFF)) <= 12)
            }
            prev = c
        }
    }

    // ---------- Màn hình khóa ----------

    @Test
    fun ok_tieuDeManHinhKhoa() {
        val tet = LunarCalendar.toSolar(1, 1, 2027)!!
        assertEquals("Mùng 1 tháng Giêng âm lịch · Tết Nguyên Đán", LockScreenDay.texts(tet).title)
        assertEquals("Rằm tháng Giêng âm lịch", LockScreenDay.texts(LunarCalendar.toSolar(15, 1, 2027)!!).title)
        assertEquals("Rằm tháng 7 âm lịch · Lễ Vu Lan", LockScreenDay.texts(LunarCalendar.toSolar(15, 7, 2027)!!).title)
        val t = LockScreenDay.texts(LocalDate.of(2026, 11, 25))
        assertTrue(t.sub, t.sub.startsWith("Thứ Tư 25/11 · Bính Ngọ"))
        assertFalse(t.line.contains("\n"))
        assertTrue(t.full.contains("\n— "))
    }

    @Test
    fun bug_ngayLeHienCaTrongThangNhuan() {
        // Tết Đoan Ngọ, Giỗ Tổ, Vu Lan… chỉ ở tháng thường, không ở tháng nhuận cùng số
        val wrong = mutableListOf<String>()
        var d = LocalDate.of(2025, 1, 1)
        while (d.year <= 2050) {
            val l = LunarCalendar.convert(d)
            if (l.leap) {
                val title = LockScreenDay.texts(d).title
                if (title.contains(" · ")) wrong += "$d: $title"
            }
            d = d.plusDays(1)
        }
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
    }

    // ---------- Thơ Zen ----------

    @Test
    fun ok_thoZenSoLuongVaChuan() {
        assertEquals(64, ZenVerses.HAIKU.size)
        assertEquals(44, ZenVerses.CLASSICS.size)
        val allLines = ZenVerses.ALL.flatMap { it.text.lines() }.map { it.trim(' ', ',', '.', '?', '!').lowercase() }
        assertEquals(allLines.size, allLines.toSet().size)
        for (v in ZenVerses.ALL) {
            assertEquals(Normalizer.normalize(v.by, Normalizer.Form.NFC), v.by)
            for (l in v.text.lines()) {
                assertEquals("'$l'", l.trim(), l)
                assertFalse(l, l.contains("  "))
                // Tiếng Việt không dùng f, j, w, z
                assertFalse(l, l.lowercase().any { it in "fjwz" })
            }
        }
        ZenVerses.CLASSICS.forEach { assertTrue(it.by, it.by.matches(Regex("""[^·]+ · [^·]+"""))) }
        ZenVerses.HAIKU.forEach { assertEquals("", it.by) }
        // Hai ngày liền nhau không ra hai trích đoạn của cùng một bài
        val start = LocalDate.of(2026, 1, 1)
        for (i in 0 until ZenVerses.ALL.size) {
            val a = ZenVerses.forDate(start.plusDays(i.toLong()))
            val b = ZenVerses.forDate(start.plusDays(i + 1L))
            assertFalse("${a.by}", a.by.isNotEmpty() && a.by == b.by)
        }
        assertNotNull(ZenVerses.forDate(LocalDate.of(1900, 1, 1)))
    }

    @Test
    fun nit_haikuLechNhip454() {
        val off = ZenVerses.HAIKU.filter { h -> h.text.lines().map { it.split(" ").size } != listOf(4, 5, 4) }
        assertEquals(off.joinToString(" | ") { it.text.replace("\n", " / ") }, 0, off.size)
    }
}
