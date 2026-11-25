package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/** QA tester B, vòng 3: bầu trời khi thành phố khác múi giờ của máy, trăng qua nửa đêm, ngày rất ngắn, la bàn. */
class QaB3SkyTest {
    private data class City(val name: String, val lat: Double, val lon: Double, val tz: Double)

    private val hanoi = City("Hà Nội", 21.0285, 105.8542, 7.0)
    private val hcm = City("TP.HCM", 10.8231, 106.6297, 7.0)
    private val sydney = City("Sydney", -33.8688, 151.2093, 11.0)        // giờ mùa hè tháng 11
    private val auckland = City("Auckland", -36.8485, 174.7633, 13.0)
    private val honolulu = City("Honolulu", 21.3069, -157.8583, -10.0)
    private val sanJose = City("San Jose", 37.3382, -121.8863, -8.0)
    private val buenos = City("Buenos Aires", -34.6037, -58.3816, -3.0)
    private val paris = City("Paris", 48.8566, 2.3522, 1.0)

    /** (múi giờ của máy, thành phố) */
    private val pairs = listOf(
        7.0 to hanoi, 14.0 to hanoi, -12.0 to hanoi, 5.5 to hcm, 5.75 to sydney, -3.0 to hanoi, 7.0 to auckland,
        7.0 to honolulu, -10.0 to auckland, 13.0 to honolulu, 7.0 to sanJose, 7.0 to buenos, 9.5 to hanoi, 7.0 to paris, -8.0 to hanoi,
    )

    /** Giờ trên máy (múi [tz]) tại thời điểm UTC [utcMin] (phút kể từ epoch). */
    private fun local(utcMin: Long, tz: Double) = LocalDateTime.ofEpochSecond((utcMin + Math.round(tz * 60)) * 60, 0, ZoneOffset.UTC)

    private val start = LocalDate.of(2026, 11, 20).atStartOfDay().toEpochSecond(ZoneOffset.UTC) / 60

    @Test
    fun ok_thanhPhoKhacMuiGioNgayDemKhopGioDiaPhuong() {
        val bad = mutableListOf<String>()
        for ((devTz, c) in pairs) {
            var worst = 0f
            for (k in 0 until 20 * 24 * 6) {
                val u = start + k * 10L
                val dev = SkyScene.build(local(u, devTz), c.lat, c.lon, null, devTz)
                val ref = SkyScene.build(local(u, c.tz), c.lat, c.lon, null, c.tz)
                val dd = abs(dev.daylight - ref.daylight)
                worst = maxOf(worst, dd)
                val sp = if (dev.sunPos != null && ref.sunPos != null) abs(dev.sunPos!! - ref.sunPos!!) else 0f
                if (dd > 0.06f || (dev.sunPos == null) != (ref.sunPos == null) && dd > 0.02f || sp > 0.03f) {
                    bad += "máy %+.2f, ${c.name}, ${local(u, devTz)} (địa phương ${local(u, c.tz)}): daylight %.2f vs %.2f, sunPos ${dev.sunPos} vs ${ref.sunPos}".format(devTz, dev.daylight, ref.daylight)
                    break
                }
            }
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    /**
     * Trong một đêm liên tục (moonPos khác null ở hai mẫu liền nhau, cách 5 phút) pha trăng không được đổi.
     * Trả về các chỗ vi phạm.
     */
    private fun moonJumps(devTz: Double, c: City, days: Int, stepMin: Long = 5): List<String> {
        val out = mutableListOf<String>()
        var prev: SkyState? = null
        var prevT: LocalDateTime? = null
        for (k in 0 until days * 24 * 60 / stepMin) {
            val t = local(start + k * stepMin, devTz)
            val s = SkyScene.build(t, c.lat, c.lon, null, devTz)
            val p = prev
            if (p != null && p.moonPos != null && s.moonPos != null && (p.moonIllum != s.moonIllum || p.moonWaxing != s.moonWaxing)) {
                out += "máy %+.2f, ${c.name}: $prevT → $t trăng đổi giữa đêm: sáng %.3f→%.3f, đầu tháng ${p.moonWaxing}→${s.moonWaxing} (moonPos %.2f→%.2f)"
                    .format(devTz, p.moonIllum, s.moonIllum, p.moonPos, s.moonPos)
            }
            prev = s; prevT = t
        }
        return out
    }

    @Test
    fun bug_trangDoiPhaTruocNuaDemOHaNoi() {
        // Máy và thành phố cùng múi +7 (trường hợp chính). Tháng 11: giữa trưa mặt trời ≈ 11:40 nên từ ≈ 23:40 giờ bị quấn về âm
        // ⇒ hour < rise ⇒ lấy pha trăng của HÔM TRƯỚC trong ~20 phút trước nửa đêm, rồi 0:00 lại về đúng.
        val jumps = moonJumps(7.0, hanoi, 12)
        assertEquals("${jumps.size} lần đổi pha giữa đêm, ví dụ:\n" + jumps.take(6).joinToString("\n"), 0, jumps.size)
    }

    @Test
    fun bug_trangHaNoiCaNam2026QuanhNuaDem() {
        // Thống kê cả năm: mỗi đêm so pha trăng lúc 22:30 với từng phút 23:00–00:59 (cùng một đêm)
        var nights = 0; var minutes = 0; var flips = 0; var maxWin = 0
        val examples = mutableListOf<String>()
        var d = LocalDate.of(2026, 1, 1)
        while (d.year == 2026) {
            val ref = SkyScene.build(d.atTime(22, 30), hanoi.lat, hanoi.lon, null, 7.0)
            var win = 0; var flip = false
            for (m in 0 until 120) {
                val s = SkyScene.build(d.atTime(23, 0).plusMinutes(m.toLong()), hanoi.lat, hanoi.lon, null, 7.0)
                if (s.moonIllum != ref.moonIllum || s.moonWaxing != ref.moonWaxing) { win++; if (s.moonWaxing != ref.moonWaxing) flip = true }
            }
            if (win > 0) {
                nights++; minutes += win; maxWin = maxOf(maxWin, win); if (flip) { flips++; if (examples.size < 4) examples += "$d (${ref.lunar.day}/${ref.lunar.month} âm): $win phút trăng lật mặt sáng" }
            }
            d = d.plusDays(1)
        }
        System.err.println("QAB3 Hà Nội 2026: $nights đêm sai pha trăng quanh nửa đêm, tổng $minutes phút, dài nhất $maxWin phút, $flips đêm trăng lật phía sáng")
        assertEquals("$nights đêm, dài nhất $maxWin phút, $flips đêm lật phía: ${examples.joinToString("; ")}", 0, nights)
    }

    @Test
    fun bug_trangDoiPhaGiuaDemKhiThanhPhoKhacMuiGio() {
        val all = pairs.filter { it.first != it.second.tz }.flatMap { (tz, c) -> moonJumps(tz, c, 6, 10).take(1) }
        assertEquals("${all.size} cặp (máy, thành phố) bị đổi pha trăng giữa đêm:\n" + all.joinToString("\n"), 0, all.size)
    }

    @Test
    fun ok_trangViTriLienTucQuaNuaDemVaQuanGio() {
        // moonPos phải đi đều trong đêm (không nhảy) dù giờ bị quấn qua mốc noon ± 12
        val bad = mutableListOf<String>()
        for ((tz, c) in pairs) {
            var prev: SkyState? = null
            for (k in 0 until 6 * 24 * 12) {
                val s = SkyScene.build(local(start + k * 5L, tz), c.lat, c.lon, null, tz)
                val p = prev
                if (p != null && p.moonPos != null && s.moonPos != null && abs(s.moonPos!! - p.moonPos!!) > 0.02f) {
                    bad += "máy $tz ${c.name} ${local(start + k * 5L, tz)}: moonPos ${p.moonPos} → ${s.moonPos}"
                    break
                }
                prev = s
            }
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    // ---------- Ngày rất ngắn (vùng cực, sát đêm vùng cực) ----------

    private fun ch(c: Int, s: Int) = (c shr s) and 0xFF

    @Test
    fun nit_ngayNgan05Den19GioMauTroiNhayTuXanhNgaySangHoangHon() {
        // Tromsø (69,65° B) 25–26/11/2026: ngày dài 1–2 giờ. Một giờ sau khi lặn (daylight = 0) trời vẫn xanh ban ngày,
        // rồi nhảy phắt sang màu hoàng hôn trong một phút.
        val bad = mutableListOf<String>()
        for (date in listOf(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 26))) {
            val (r, s) = SkyScene.sunTimes(date, 69.6492, 18.9553, 1.0)
            var prev = SkyScene.skyColors(r - 3, r, s)
            var m = 1
            while (m <= 6 * 60 + (s - r) * 60) {
                val h = r - 3 + m / 60.0
                val c = SkyScene.skyColors(h, r, s)
                val jump = listOf(0, 8, 16).maxOf { sh -> maxOf(abs(ch(c.first, sh) - ch(prev.first, sh)), abs(ch(c.second, sh) - ch(prev.second, sh))) }
                val dl = SkyScene.daylight(h, r, s)
                if (jump > 24 || (dl < 0.02f && ch(c.first, 8) > 0x60)) {
                    bad += "Tromsø $date (ngày %.2f h) %02d:%02d daylight %.2f màu trên #%06X, nhảy %d"
                        .format(s - r, h.toInt(), ((h - h.toInt()) * 60).toInt(), dl, c.first and 0xFFFFFF, jump)
                    break
                }
                prev = c; m++
            }
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    // ---------- La bàn ----------

    @Test
    fun nit_moTaGocDungBienHienSoDoKhacPhiaNhan() {
        // describe() làm tròn kiểu "chẵn gần nhất" (kotlin.math.round): 22,5° → "22° Đông Bắc" trong khi 22° thuộc Bắc
        val bad = listOf(22.5f, 112.5f, 202.5f, 292.5f, 67.5f, 157.5f, 247.5f, 337.5f).mapNotNull { a ->
            val text = PhongThuy.describe(a)
            val shown = text.substringBefore("°").toInt()
            if (Huong.nearest(shown.toFloat()).label == text.substringAfter("° ")) null else "$a → \"$text\" (số $shown° thuộc ${Huong.nearest(shown.toFloat()).label})"
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    // ---------- Màn hình khóa / gợi ý tháng nhuận (hồi quy) ----------

    @Test
    fun ok_manHinhKhoaThangNhuan() {
        val d = LunarCalendar.toSolar(15, 5, 2028, leap = true)!!
        assertEquals("Rằm tháng 5 nhuận âm lịch", LockScreenDay.texts(d).title)
        val d2 = LunarCalendar.toSolar(5, 5, 2028, leap = true)!!
        assertEquals("Mùng 5 tháng 5 nhuận âm lịch", LockScreenDay.texts(d2).title)
        val d3 = LunarCalendar.toSolar(5, 5, 2028)!!
        assertEquals("Mùng 5 tháng 5 âm lịch · Tết Đoan Ngọ", LockScreenDay.texts(d3).title)
        val eve = SuggestionEngine.build(d2.minusDays(1), d2.minusDays(1).atTime(20, 0), null, false)
        assertTrue(eve.joinToString { it.short }, eve.none { it.short.contains("Đoan Ngọ") })
    }
}
