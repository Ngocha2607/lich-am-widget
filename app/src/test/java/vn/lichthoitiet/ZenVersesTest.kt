package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer
import java.time.LocalDate

class ZenVersesTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val days = (0 until 10 * 366).map { start.plusDays(it.toLong()) }

    @Test
    fun moiCauDeuDuocChonVaNgayKeNhauKhongTrung() {
        val picks = days.map { ZenVerses.forDate(it) }
        val missing = ZenVerses.ALL.toSet() - picks.toSet()
        assertTrue(missing.joinToString { it.text.lines().first() }, missing.isEmpty())
        for (i in 1 until picks.size) assertTrue("${days[i]}", picks[i] != picks[i - 1])
    }

    @Test
    fun cauHopMuaVaNgayLe() {
        fun tag(d: LocalDate) = ZenVerses.forDate(d).tag
        assertEquals("ram8", tag(LunarCalendar.toSolar(15, 8, 2026)!!))
        assertEquals("ram1", tag(LunarCalendar.toSolar(15, 1, 2027)!!))
        assertEquals("tet1", tag(LunarCalendar.toSolar(1, 1, 2027)!!))
        assertEquals("tet2", tag(LunarCalendar.toSolar(2, 1, 2027)!!))
        assertEquals("tet", tag(LunarCalendar.toSolar(3, 1, 2027)!!))
        assertEquals("giaothua", tag(LunarCalendar.toSolar(1, 1, 2027)!!.minusDays(1)))
        for (d in days) {
            val v = ZenVerses.forDate(d)
            if (v.tag in setOf("tet1", "tet2", "tet", "giaothua", "bamuoi", "ram1", "ram8")) continue
            assertTrue("${d}: ${v.text.lines().first()} [${v.tag}]", ZenVerses.fits(v, d, LunarCalendar.convert(d)))
        }
    }

    @Test
    fun noiDungHopLe() {
        assertTrue(ZenVerses.ALL.size >= 60)
        assertEquals(ZenVerses.ALL.size, ZenVerses.ALL.map { it.text }.toSet().size)
        for (v in ZenVerses.ALL) {
            assertTrue(v.text, v.text.isNotBlank() && v.text.length <= 160)
            assertTrue(v.text, v.text.lines().size in 1..4)
            assertEquals(v.text, Normalizer.normalize(v.text, Normalizer.Form.NFC), v.text)
        }
        // Thơ ba dòng của app có đúng ba dòng
        ZenVerses.HAIKU.forEach { assertEquals(it.text, 3, it.text.lines().size) }
        // Trích thơ cổ phải ghi tác giả
        ZenVerses.CLASSICS.forEach { assertTrue(it.text, it.by.isNotBlank()) }
    }
}
