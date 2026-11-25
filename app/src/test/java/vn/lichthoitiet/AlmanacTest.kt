package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
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

    private fun tuOn(y: Int, m: Int, d: Int) = Almanac.tu(LunarCalendar.jdFromDate(d, m, y)).name

    @Test
    fun nhiThapBatTuNgayMoc() {
        // Đối chiếu lichngaytot, xemlicham, lichvannien.net, licham.vn và hoàng lịch 值日星宿
        assertEquals("Giác", tuOn(2026, 5, 28))
        assertEquals("Bích", tuOn(2026, 11, 25))
        assertEquals("Khuê", tuOn(2026, 11, 26))
        assertEquals("Tỉnh", tuOn(2026, 12, 3))
        assertEquals("Quỷ", tuOn(2027, 1, 1))
        assertEquals("Đê", tuOn(2027, 2, 6))
        assertEquals("Vị", tuOn(2000, 1, 1))
    }

    @Test
    fun nhiThapBatTuGanVoiThuTrongTuan() {
        // Giác (Mộc) luôn thứ Năm, Cang (Kim) thứ Sáu, ... Cơ (Thủy) thứ Tư
        var d = LocalDate.of(2026, 1, 1)
        repeat(800) {
            val i = Almanac.tuIndex(LunarCalendar.jdFromDate(d.dayOfMonth, d.monthValue, d.year))
            assertEquals(DayOfWeek.THURSDAY.plus((i % 7).toLong()), d.dayOfWeek)
            d = d.plusDays(1)
        }
        assertEquals(28, Almanac.NHI_THAP_BAT_TU.size)
        assertEquals(14, Almanac.NHI_THAP_BAT_TU.count { it.good })
    }

    @Test
    fun ngaySinh() {
        val today = LocalDate.of(2026, 11, 25)
        // Mùng 1 Tết Canh Ngọ là 27/1/1990
        assertEquals(LocalDate.of(1990, 1, 27), Almanac.birthToSolar(true, 1, 1, 1990, today))
        assertEquals(LocalDate.of(1990, 3, 12), Almanac.birthToSolar(false, 12, 3, 1990, today))
        // Sinh 20/1/1990 dương lịch là trước Tết: vẫn tuổi Kỷ Tỵ (năm âm 1989), không phải Canh Ngọ
        val l = LunarCalendar.convert(Almanac.birthToSolar(false, 20, 1, 1990, today)!!)
        assertEquals(1989, l.year)
        assertEquals(5, Almanac.birthChi(l.year)) // Tỵ
        // Ngày không tồn tại hoặc ở tương lai
        assertNull(Almanac.birthToSolar(false, 31, 2, 1990, today))
        assertNull(Almanac.birthToSolar(false, 26, 11, 2026, today))
    }

    @Test
    fun chiTuoi() {
        assertEquals(4, Almanac.birthChi(2000)) // Canh Thìn
    }

    @Test
    fun hopXungTuoi() {
        assertEquals(-1, Almanac.tuoiRelation(2, 8))  // Dần xung Thân
        assertEquals(1, Almanac.tuoiRelation(2, 6))   // Dần – Ngọ tam hợp
        assertEquals(1, Almanac.tuoiRelation(0, 1))   // Tý – Sửu lục hợp
        assertEquals(0, Almanac.tuoiRelation(0, 2))   // Tý – Dần bình thường
    }

    @Test
    fun chonNgayTotTranhNgayXungTuoi() {
        // Tuổi Thân: không được gợi ý ngày Dần
        val list = Almanac.goodDays("cuoi", LocalDate.of(2026, 11, 24), 60, 10, birthChi = 8)
        assertTrue(list.isNotEmpty())
        assertTrue(list.none { LunarCalendar.dayChiIndex(LunarCalendar.convert(it.date).jd) == 2 })
    }

    @Test
    fun chonNgayTotKhongChonTrucXau() {
        val list = Almanac.goodDays("cuoi", LocalDate.of(2026, 11, 24), 60)
        assertTrue(list.isNotEmpty())
        assertTrue(list.none { it.truc in listOf("Phá", "Nguy", "Bế", "Trừ") })
        assertTrue(list.zipWithNext().all { (a, b) -> a.score >= b.score })
    }
}
