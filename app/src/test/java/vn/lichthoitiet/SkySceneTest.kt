package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

class SkySceneTest {
    private val hanoi = 21.0285 to 105.8542

    private fun hm(h: Double) = "%d:%02d".format(h.toInt(), Math.round((h - h.toInt()) * 60).toInt())

    @Test
    fun matTroiMocLanHaNoi() {
        // Hạ chí: mọc khoảng 5:14, lặn khoảng 18:40; Đông chí: mọc khoảng 6:28, lặn khoảng 17:20 (sai số ≤ 10 phút)
        val (r1, s1) = SkyScene.sunTimes(LocalDate.of(2026, 6, 21), hanoi.first, hanoi.second)
        assertTrue(hm(r1), abs(r1 - (5 + 14 / 60.0)) < 10 / 60.0)
        assertTrue(hm(s1), abs(s1 - (18 + 40 / 60.0)) < 10 / 60.0)
        val (r2, s2) = SkyScene.sunTimes(LocalDate.of(2026, 12, 21), hanoi.first, hanoi.second)
        assertTrue(hm(r2), abs(r2 - (6 + 28 / 60.0)) < 10 / 60.0)
        assertTrue(hm(s2), abs(s2 - (17 + 20 / 60.0)) < 10 / 60.0)
    }

    @Test
    fun trangTheoNgayAm() {
        assertTrue(SkyScene.moonIllumination(15) > 0.99f)
        assertTrue(SkyScene.moonIllumination(1) < 0.05f)
        assertTrue(SkyScene.moonIllumination(1) > 0f)               // mùng 1 vẫn còn vành trăng non mảnh
        assertTrue(SkyScene.moonIllumination(8) in 0.4f..0.6f)       // thượng huyền
        assertTrue(SkyScene.moonWaxing(8))
        assertTrue(!SkyScene.moonWaxing(22))
    }

    @Test
    fun hieuUngTheoTietKhi() {
        val idx = { name: String -> LunarCalendar.TIET_KHI.indexOf(name) }
        assertEquals(SkyEffect.FOG, SkyScene.effectForTietKhi(idx("Đông chí")))
        assertEquals(SkyEffect.PETALS, SkyScene.effectForTietKhi(idx("Lập xuân")))
        assertEquals(SkyEffect.RAIN, SkyScene.effectForTietKhi(idx("Cốc vũ")))
        assertEquals(SkyEffect.LEAVES, SkyScene.effectForTietKhi(idx("Lập thu")))
        assertEquals(SkyEffect.SUMMER, SkyScene.effectForTietKhi(idx("Hạ chí")))
        // Thời tiết thật lấn át: sương mù, mưa phùn, mưa
        assertEquals(SkyEffect.FOG, SkyScene.effectForWeather(45))
        assertEquals(SkyEffect.DRIZZLE, SkyScene.effectForWeather(53))
        assertEquals(SkyEffect.RAIN, SkyScene.effectForWeather(95))
        assertNull(SkyScene.effectForWeather(1))
    }

    @Test
    fun bauTroiTheoGio() {
        val noon = SkyScene.build(LocalDateTime.of(2026, 12, 22, 12, 0), hanoi.first, hanoi.second)
        assertEquals(1f, noon.daylight, 1e-3f)
        assertNotNull(noon.sunPos)
        assertNull(noon.moonPos)
        // Đông chí 2026 (22/12): hiệu ứng sương mù
        assertEquals(SkyEffect.FOG, noon.effect)
        val midnight = SkyScene.build(LocalDateTime.of(2026, 12, 23, 0, 30), hanoi.first, hanoi.second)
        assertEquals(0f, midnight.daylight, 1e-3f)
        assertNull(midnight.sunPos)
        assertNotNull(midnight.moonPos)
        // Rằm: đêm 15 âm có trăng tròn
        val ram = LunarCalendar.toSolar(15, 10, 2026)!!
        val fullMoon = SkyScene.build(ram.atTime(21, 0), hanoi.first, hanoi.second)
        assertTrue(fullMoon.moonIllum > 0.99f)
        assertEquals("Rằm tháng 10", fullMoon.title)
    }

    @Test
    fun tenNgayAm() {
        assertEquals("Mùng 1 tháng Giêng", SkyScene.lunarTitle(LunarDate(1, 1, 2027, false, 0)))
        assertEquals("Rằm tháng 7", SkyScene.lunarTitle(LunarDate(15, 7, 2026, false, 0)))
        assertEquals("23 tháng Chạp", SkyScene.lunarTitle(LunarDate(23, 12, 2026, false, 0)))
        assertEquals("Mùng 10 tháng 6 nhuận", SkyScene.lunarTitle(LunarDate(10, 6, 2025, true, 0)))
    }
}
