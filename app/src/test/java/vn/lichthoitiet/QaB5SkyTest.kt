package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/** QA tester B, vòng 5: đêm vùng cực (ngày < 0,5 giờ) — trăng cố định giữa trời, khuất quanh trưa, đổi ngày âm lúc trưa; không có mặt trời. */
class QaB5SkyTest {
    private data class City(val name: String, val lat: Double, val lon: Double, val tz: Double)

    private val polar = listOf(
        City("Tromsø", 69.65, 18.96, 1.0), City("Murmansk", 68.97, 33.08, 3.0), City("Longyearbyen", 78.22, 15.65, 1.0),
        City("Utqiagvik", 71.29, -156.79, -9.0), City("Norilsk", 69.35, 88.2, 7.0), City("McMurdo", -77.85, 166.67, 13.0),
        City("Rovaniemi", 66.5, 25.73, 2.0), City("Vòng cực 66.6", 66.6, 105.85, 7.0), City("Kiruna", 67.86, 20.23, 1.0),
    )

    private fun local(utcMin: Long, tz: Double) = LocalDateTime.ofEpochSecond((utcMin + Math.round(tz * 60)) * 60, 0, ZoneOffset.UTC)
    private fun ch(c: Int, s: Int) = (c shr s) and 0xFF
    private fun colorJump(a: Int, b: Int) = listOf(0, 8, 16).maxOf { abs(ch(a, it) - ch(b, it)) }

    class Scan {
        val bad = mutableListOf<String>()
        val boundary = mutableListOf<String>()
        val white = mutableListOf<String>()
        var boundaryMax = 0f
        var whiteMax = 0f
    }

    private fun scan(c: City, devTz: Double, sc: Scan) {
        val start = LocalDate.of(2026, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) / 60
        var prev: SkyState? = null
        var k = 0L
        var reported = 0
        var bReported = 0
        var wReported = 0
        while (k <= 366L * 24 * 60) {
            val t = local(start + k, devTz)
            val s = SkyScene.build(t, c.lat, c.lon, null, devTz)
            val len = s.sunset - s.sunrise
            if (len < 0.5 && s.sunPos != null && reported++ < 2) sc.bad += "${c.name} máy %+.1f $t: ngày %.2f h vẫn có mặt trời".format(devTz, len)
            if (s.moonPos?.isNaN() == true && reported++ < 2) sc.bad += "${c.name} $t: trăng NaN"
            val p = prev
            if (p != null) {
                val pLen = p.sunset - p.sunrise
                if (p.moonPos != null && s.moonPos != null) {
                    if (p.moonIllum != s.moonIllum || p.moonWaxing != s.moonWaxing) { if (reported++ < 3) sc.bad += "${c.name} máy %+.1f $t: đổi pha khi trăng đang hiện (ngày %.2f h)".format(devTz, len) }
                    val dp = s.moonPos!! - p.moonPos!!
                    val modeSwitch = (pLen < 0.5) != (len < 0.5)
                    if (modeSwitch) {
                        if (abs(dp) > sc.boundaryMax) sc.boundaryMax = abs(dp)
                        if (abs(dp) > 0.05f && bReported++ < 2) sc.boundary += "${c.name} máy %+.1f $t: %.3f→%.3f (ngày %.2f→%.2f h)".format(devTz, p.moonPos, s.moonPos, pLen, len)
                    } else if (dp > 0.12f || dp < -0.05f) {
                        val nightLen = 24 - len
                        if (nightLen < 1.5) {
                            if (dp > sc.whiteMax) sc.whiteMax = dp
                            if (wReported++ < 1) sc.white += "${c.name} máy %+.1f $t: đêm %.0f phút, trăng %.3f→%.3f trong 5 phút".format(devTz, nightLen * 60, p.moonPos, s.moonPos)
                        } else if (reported++ < 3) sc.bad += "${c.name} máy %+.1f $t: trăng nhảy %.3f→%.3f (ngày %.2f h)".format(devTz, p.moonPos, s.moonPos, len)
                    }
                }
                if (t.toLocalDate() == t.minusMinutes(5).toLocalDate()) {
                    val j = maxOf(colorJump(p.skyTop, s.skyTop), colorJump(p.skyBottom, s.skyBottom))
                    if (j > 60 && reported++ < 3) sc.bad += "${c.name} máy %+.1f $t: màu trời nhảy $j (ngày %.2f h)".format(devTz, len)
                }
            }
            prev = s
            k += 5
        }
    }

    private val scanResult: Scan get() = (cache as Scan?) ?: Scan().also { sc ->
        for (c in polar) for (tz in listOf(c.tz, 7.0).distinct()) scan(c, tz, sc)
        cache = sc
    }

    companion object { @Volatile private var cache: Any? = null }

    @Test
    fun ok_vungCucCaNamKhongDoiPhaKhongMatTroiMauLienTuc() {
        val sc = scanResult
        assertEquals(sc.bad.joinToString("\n"), 0, sc.bad.size)
    }

    @Test
    fun bug_ranhGioiDemCucTrangNhayLuc0hMay() {
        // Ngày đầu / cuối đêm cực: chế độ (cố định 0,5 ↔ theo giờ lặn–mọc) đổi theo NGÀY CỦA MÁY, lúc 0h máy trăng đang hiện nhảy chỗ.
        // Cùng múi thành phố vẫn nhảy 0,05–0,09 cung (kinh độ lệch tâm múi); máy khác múi (vd điện thoại để giờ VN xem Tromsø) nhảy tới ~0,29.
        val sc = scanResult
        System.err.println("QAB5 ranh giới đêm cực: nhảy lớn nhất %.3f cung; ví dụ:\n  ".format(sc.boundaryMax) + sc.boundary.joinToString("\n  "))
        assertEquals(sc.boundary.joinToString("\n"), 0, sc.boundary.size)
    }

    @Test
    fun bug_demTrangTrangVutQuaCaCungTroiTrongVaiPhut() {
        // Đêm trắng (đêm < 1 giờ, vĩ độ > ~65° mùa hè): trăng đi trọn cung trời trong độ dài đêm ⇒ ~0,18 cung / 5 phút (đối xứng với lỗi đêm cực đã sửa)
        val sc = scanResult
        System.err.println("QAB5 đêm trắng: trăng nhanh nhất %.3f cung / 5 phút; ví dụ:\n  ".format(sc.whiteMax) + sc.white.joinToString("\n  "))
        assertEquals(sc.white.joinToString("\n"), 0, sc.white.size)
    }

    @Test
    fun ok_demCucTrangGiuaTroiKhuatQuanhTruaDoiNgayAmLucTrua() {
        val c = polar.first()
        val bad = mutableListOf<String>()
        var d = LocalDate.of(2026, 12, 1)
        while (d.isBefore(LocalDate.of(2027, 1, 10))) {
            val (r, s) = SkyScene.sunTimes(d, c.lat, c.lon, c.tz)
            if (s - r < 0.5) {
                val noon = (r + s) / 2
                for (m in 0 until 24 * 60 step 10) {
                    val t = d.atStartOfDay().plusMinutes(m.toLong())
                    val st = SkyScene.build(t, c.lat, c.lon, null, c.tz)
                    val h = m / 60.0
                    val hidden = abs(h - noon) < 0.5
                    if (hidden != (st.moonPos == null)) bad += "$t: trăng ${st.moonPos} (trưa %.2f)".format(noon)
                    if (st.moonPos != null && st.moonPos != 0.5f) bad += "$t: trăng ${st.moonPos} ≠ 0.5"
                    if (st.sunPos != null) bad += "$t: có mặt trời"
                    val want = LunarCalendar.convert(if (h < noon) d.minusDays(1) else d).day
                    if (st.moonIllum != SkyScene.moonIllumination(want)) bad += "$t: pha trăng không theo ngày âm ${want}"
                }
            }
            d = d.plusDays(1)
            if (bad.size > 10) break
        }
        assertEquals(bad.take(10).joinToString("\n"), 0, bad.size)
    }

    @Test
    fun ok_mauTroiGiuaTruaNgayChuyenNguong() {
        // Ghi nhận: ngày có mặt trời 0,49 h → trời đêm cả trưa; ngày sau 0,5 h → giữa trưa màu "ban ngày" đầy đủ
        val out = mutableListOf<String>()
        for (c in polar.take(3)) {
            var d = LocalDate.of(2026, 1, 1)
            var prevLen = -1.0
            var prevTop = 0
            while (d.year == 2026) {
                val (r, s) = SkyScene.sunTimes(d, c.lat, c.lon, c.tz)
                val noon = (r + s) / 2
                val top = SkyScene.skyColors(noon, r, s).first
                if (prevLen >= 0 && (prevLen < 0.5) != (s - r < 0.5)) out += "${c.name} $d: ngày %.2f→%.2f h, trời giữa trưa #%06X→#%06X (lệch ${colorJump(prevTop, top)})".format(prevLen, s - r, prevTop and 0xFFFFFF, top and 0xFFFFFF)
                prevLen = s - r; prevTop = top
                d = d.plusDays(1)
            }
        }
        System.err.println("QAB5 ngưỡng 0,5 h: " + out.joinToString("; "))
    }
}
