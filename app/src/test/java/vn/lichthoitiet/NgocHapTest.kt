package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Đối chiếu với danh sách sao của lichngaytot.com và xemlicham.com (cả hai trang ghi giống nhau). */
class NgocHapTest {
    private fun stars(y: Int, m: Int, d: Int) = NgocHap.of(LunarCalendar.convert(LocalDate.of(y, m, d)))

    @Test
    fun ngay10Thang12Nam2026() {
        val s = stars(2026, 12, 10)
        assertEquals(setOf("Giải Thần", "Thiên Quan", "Tục Thế"), s.filter { it.good }.map { it.name }.toSet())
        assertEquals(
            setOf("Hoang Vu", "Hỏa Tai", "Ngũ Hư", "Phi Ma Sát", "Thiên Hỏa", "Thiên Ngục", "Thiên Tặc"),
            s.filter { !it.good }.map { it.name }.toSet(),
        )
        // Sao tốt đứng trước sao xấu
        assertTrue(s.indexOfFirst { !it.good } > s.indexOfLast { it.good })
    }

    @Test
    fun mungMotTetBinhNgo() {
        assertEquals(
            setOf("Hoàng Ân", "Nguyệt Không", "Tam Hợp", "Thiên Hỷ", "Thiên Quan", "Cô Thần", "Nguyệt Yếm Đại Họa", "Quỷ Khốc", "Thụ Tử"),
            stars(2026, 2, 17).map { it.name }.toSet(),
        )
    }

    @Test
    fun thangNhuanTinhNhuThangThuong() {
        // 1/8/2025 là 8/6 nhuận Ất Tỵ
        assertTrue(LunarCalendar.convert(LocalDate.of(2025, 8, 1)).leap)
        assertEquals(
            setOf("Hoạt Điệu", "Mẫu Thương", "Ngũ Phú", "Thiên Tài", "Ly Sàng", "Thổ Cẩm", "Tội Chỉ"),
            stars(2025, 8, 1).map { it.name }.toSet(),
        )
    }

    @Test
    fun quyTacKinhDien() {
        // Thiên Đức tháng Giêng ngày Đinh, Nguyệt Đức tháng Giêng ngày Bính (bảng tra truyền thống)
        var d = LocalDate.of(2026, 2, 17)
        while (LunarCalendar.convert(d).month == 1) {
            val l = LunarCalendar.convert(d)
            val names = NgocHap.of(l).map { it.name }
            assertEquals("Thiên Đức ${d}", (l.jd + 9) % 10 == 3, "Thiên Đức" in names)
            assertEquals("Nguyệt Đức ${d}", (l.jd + 9) % 10 == 2, "Nguyệt Đức" in names)
            d = d.plusDays(1)
        }
    }
}
