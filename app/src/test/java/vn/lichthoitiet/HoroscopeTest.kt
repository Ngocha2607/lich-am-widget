package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HoroscopeTest {
    private fun sign(m: Int, d: Int) = Horoscope.zodiac(LocalDate.of(2000, m, d)).name

    @Test
    fun cungHoangDaoTheoNgay() {
        assertEquals("Bạch Dương", sign(3, 21))
        assertEquals("Song Ngư", sign(3, 20))
        assertEquals("Ma Kết", sign(1, 1))
        assertEquals("Ma Kết", sign(12, 31))
        assertEquals("Bảo Bình", sign(1, 20))
        assertEquals("Nhân Mã", sign(11, 25))
        assertEquals("Bọ Cạp", sign(11, 21))
        // Bảng phổ biến: Cự Giải từ 22/6, Bọ Cạp từ 24/10
        assertEquals("Song Tử", sign(6, 21))
        assertEquals("Cự Giải", sign(6, 22))
        assertEquals("Thiên Bình", sign(10, 23))
        assertEquals("Bọ Cạp", sign(10, 24))
    }

    /** Điểm thientue.vn công bố (đã đối chiếu tay) cho Tết 2027 Đinh Mùi. */
    private fun assertScores(owner: Int, want: Map<Int, Int>) =
        want.forEach { (c, s) -> assertEquals("gia chủ $owner, người xông $c", s, Horoscope.xongDatScore(owner, 2027, c)) }

    @Test
    fun xongDatKhopThientue() {
        assertScores(1990, mapOf(1962 to 82, 1971 to 72, 1992 to 71, 1995 to 68, 1970 to 67, 2002 to 65, 2001 to 64,
            1986 to 61, 1979 to 61, 1973 to 24, 1997 to 24, 1996 to 24))
        assertScores(1985, mapOf(2005 to 68, 1975 to 65, 1992 to 64, 1962 to 64, 1964 to 17, 1994 to 17, 1979 to 25))
        assertScores(1975, mapOf(1970 to 77, 1980 to 74, 1971 to 73, 1962 to 71, 1992 to 71, 2002 to 68, 2003 to 68,
            1963 to 65, 1989 to 64, 1983 to 62))
    }

    @Test
    fun xongDatKetQua() {
        val r = Horoscope.xongDat(1990, 2027)
        assertEquals("Đinh Mùi", r.canChi)
        assertEquals(listOf("Tý", "Sửu"), r.avoid)           // xung Ngọ (gia chủ) và xung Mùi (năm)
        // Chỉ gợi ý tuổi từ 62 điểm: 82, 72, 71, 68, 67, 65, 64 (1986, 1979 được 61 nên không có)
        assertEquals(listOf(1962, 1971, 1992, 1995, 1970, 2002, 2001), r.picks.map { it.year })
        assertEquals(1962, r.picks[0].year)
        assertEquals("Hợp để ưu tiên", r.picks[0].label)
        assertEquals(r.picks.sortedByDescending { it.score }.map { it.score }, r.picks.map { it.score })
        // Tân Hợi 1971: Hợi Mão Mùi phạm Tam Tai năm Tỵ, Ngọ, Mùi → năm Mùi là năm cuối
        assertEquals(listOf("phạm Tam Tai năm cuối"), r.picks.first { it.year == 1971 }.warnings)
        // Chỉ người xông 18–65 tuổi
        assertEquals(-1, Horoscope.xongDatScore(1990, 2027, 2010))
        assertEquals(-1, Horoscope.xongDatScore(1990, 2027, 1961))
        assertEquals("Rất nên mời", Horoscope.label(88))
        assertEquals("Nên tránh ưu tiên", Horoscope.label(61))
    }

    @Test
    fun menhNapAm() {
        assertEquals("Lộ Bàng Thổ", Horoscope.napAmYear(1990))   // Canh Ngọ
        assertEquals("Hải Trung Kim", Horoscope.napAmYear(1984)) // Giáp Tý
        assertEquals("Thiên Hà Thủy", Horoscope.napAmYear(2026)) // Bính Ngọ
        // Sinh 20/1/1990 là trước Tết: năm âm 1989 Kỷ Tỵ, mệnh Đại Lâm Mộc
        val h = Horoscope.basic(LocalDate.of(1990, 1, 20))
        assertEquals("Kỷ Tỵ", h.canChi)
        assertEquals("Đại Lâm Mộc", h.menh)
        assertEquals("Mộc", h.hanh)
        assertEquals("Rắn", h.animal)
    }
}
