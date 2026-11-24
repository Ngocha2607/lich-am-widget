package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AlmanacTest {
    // 29/11/2026: ngày Đinh Mùi, 21/10 âm, tiết Tiểu tuyết (tháng Hợi)
    private val day = LunarCalendar.convert(LocalDate.of(2026, 11, 29))

    @Test
    fun trucThanh() {
        assertEquals("Thành", Almanac.TRUC[Almanac.trucIndex(day)])
    }

    @Test
    fun napAmDinhMui() {
        assertEquals("Thiên Hà Thủy", Almanac.napAm(day.jd))
        // 1/1/2000 là ngày Mậu Ngọ: Thiên Thượng Hỏa
        assertEquals("Thiên Thượng Hỏa", Almanac.napAm(LunarCalendar.jdFromDate(1, 1, 2000)))
    }

    @Test
    fun lucDieu() {
        val a = Almanac.of(day)
        assertEquals("Không Vong", a.lucDieuDay)
        // Giờ Sửu (1–3h) là Đại An, giờ Tỵ (9–11h) là Tiểu Cát
        assertEquals("Đại An", a.lucDieu[1].name)
        assertEquals("Tiểu Cát", a.lucDieu[5].name)
        assertTrue(a.lucDieu[1].hours.startsWith("1–3h"))
    }

    @Test
    fun xungVaHuong() {
        val a = Almanac.of(day)
        assertEquals("Sửu", a.xung)
        assertEquals("Chính Nam", a.hyThan)   // can Đinh
    }

    @Test
    fun chonNgayTotKhongChonTrucXau() {
        val list = Almanac.goodDays("cuoi", LocalDate.of(2026, 11, 24), 60)
        assertTrue(list.isNotEmpty())
        assertTrue(list.none { it.truc in listOf("Phá", "Nguy", "Bế", "Trừ") })
        assertTrue(list.zipWithNext().all { (a, b) -> a.score >= b.score })
    }
}
