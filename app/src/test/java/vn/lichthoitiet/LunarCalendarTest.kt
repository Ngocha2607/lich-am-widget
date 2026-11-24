package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LunarCalendarTest {

    private fun lunar(d: Int, m: Int, y: Int) = LunarCalendar.convert(LocalDate.of(y, m, d))

    @Test
    fun tetNguyenDan() {
        // Mùng 1 Tết các năm gần đây
        assertEquals(Triple(1, 1, 2024), lunar(10, 2, 2024).let { Triple(it.day, it.month, it.year) })
        assertEquals(Triple(1, 1, 2025), lunar(29, 1, 2025).let { Triple(it.day, it.month, it.year) })
        assertEquals(Triple(1, 1, 2026), lunar(17, 2, 2026).let { Triple(it.day, it.month, it.year) })
        assertEquals(Triple(1, 1, 2027), lunar(6, 2, 2027).let { Triple(it.day, it.month, it.year) })
    }

    @Test
    fun ngayThuong() {
        val l = lunar(24, 11, 2026)
        assertEquals(16, l.day)
        assertEquals(10, l.month)
        assertEquals(2026, l.year)
        assertFalse(l.leap)
    }

    @Test
    fun thangNhuan2025() {
        // Năm Ất Tỵ 2025 nhuận tháng 6: 25/7/2025 là mùng 1 tháng 6 nhuận
        val l = lunar(25, 7, 2025)
        assertEquals(1, l.day)
        assertEquals(6, l.month)
        assertTrue(l.leap)

        val before = lunar(23, 7, 2025)
        assertEquals(29, before.day)
        assertEquals(6, before.month)
        assertFalse(before.leap)
    }

    @Test
    fun canChi() {
        assertEquals("Bính Ngọ", LunarCalendar.canChiYear(2026))
        assertEquals("Giáp Thìn", LunarCalendar.canChiYear(2024))
        // 1/1/2000 là ngày Mậu Ngọ
        assertEquals("Mậu Ngọ", LunarCalendar.canChiDay(LunarCalendar.jdFromDate(1, 1, 2000)))
    }

    @Test
    fun amSangDuong() {
        assertEquals(LocalDate.of(2026, 2, 17), LunarCalendar.toSolar(1, 1, 2026))
        assertEquals(LocalDate.of(2026, 11, 24), LunarCalendar.toSolar(16, 10, 2026))
        assertEquals(LocalDate.of(2026, 11, 23), LunarCalendar.toSolar(15, 10, 2026))
        assertEquals(LocalDate.of(2025, 7, 25), LunarCalendar.toSolar(1, 6, 2025, leap = true))
        assertEquals(LocalDate.of(2027, 2, 6), LunarCalendar.toSolar(1, 1, 2027))
    }

    @Test
    fun gioHoangDaoCoSauGio() {
        val l = lunar(24, 11, 2026)
        assertEquals(6, LunarCalendar.gioHoangDao(l.jd).size)
    }
}
