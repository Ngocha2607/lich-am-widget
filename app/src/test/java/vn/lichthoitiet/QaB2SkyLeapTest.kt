package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** QA tester B, vòng 2: lễ trong tháng nhuận (gợi ý, màn hình khóa), mặt trời ở múi giờ khác / vĩ độ cao, la bàn. */
class QaB2SkyLeapTest {
    private val LE = mapOf(
        (1 to 1) to "Tết Nguyên Đán", (1 to 15) to "Rằm tháng Giêng", (3 to 10) to "Giỗ Tổ Hùng Vương", (5 to 5) to "Tết Đoan Ngọ",
        (7 to 15) to "lễ Vu Lan", (8 to 15) to "Tết Trung Thu", (12 to 23) to "ngày ông Táo về trời",
    )

    // ---------- Lễ, rằm, mùng 1 khi có tháng nhuận ----------

    @Test
    fun ok_leChiTheoThangThuong_ramMung1VanCoTrongThangNhuan() {
        val wrong = mutableListOf<String>()
        val leapMonths = mutableSetOf<String>()
        var d = LocalDate.of(1990, 1, 1)
        while (d.year <= 2050) {
            for ((hour, target) in listOf(8 to LunarCalendar.convert(d), 20 to LunarCalendar.convert(d.plusDays(1)))) {
                val s = SuggestionEngine.build(d, d.atTime(hour, 0), null, false)
                val le = if (target.leap) null else LE[target.month to target.day]
                val hasLe = s.any { it.short.startsWith("🧧") }
                val hasPray = s.any { it.short.startsWith("🙏") }
                if (hasLe != (le != null)) wrong += "$d ${hour}h ${target.day}/${target.month}${if (target.leap) "N" else ""}: 🧧=$hasLe"
                if (hasPray != (le == null && target.day in setOf(1, 15))) wrong += "$d ${hour}h ${target.day}/${target.month}${if (target.leap) "N" else ""}: 🙏=$hasPray"
                if (le != null && s.none { it.short.contains(le) }) wrong += "$d ${hour}h thiếu '$le'"
                if (target.leap) leapMonths += "${target.year}/${target.month}"
            }
            val l = LunarCalendar.convert(d)
            val name = SuggestionEngine.specialDayName(l)
            val expect = (if (l.leap) null else LE[l.month to l.day]) ?: when (l.day) { 1, 15 -> "x"; else -> null }
            if ((name == null) != (expect == null)) wrong += "$d specialDayName=$name"
            if (l.leap && LockScreenDay.texts(d).title.contains(" · ")) wrong += "$d màn hình khóa: ${LockScreenDay.texts(d).title}"
            d = d.plusDays(1)
        }
        // Có các năm nhuận đúng tháng lễ: 1995/8 (Trung Thu), 2006/7 (Vu Lan), 2009, 2028/5 (Đoan Ngọ), 2031/3 (Giỗ Tổ)
        assertTrue(leapMonths.toString(), leapMonths.containsAll(listOf("1995/8", "2006/7", "2009/5", "2028/5", "2031/3", "2044/7")))
        assertEquals(wrong.take(20).joinToString("\n"), 0, wrong.size)
    }

    @Test
    fun nit_ramMung1ThangNhuanKhongNoiLaNhuan() {
        // Năm 2028 có tháng 5 nhuận: "Mai là Mùng 1 tháng 5" được báo hai lần cách nhau một tháng, không phân biệt
        val m1 = LunarCalendar.toSolar(1, 5, 2028, leap = true)!!
        val r15 = LunarCalendar.toSolar(15, 5, 2028, leap = true)!!
        val names = listOf(m1, r15).map { SuggestionEngine.specialDayName(LunarCalendar.convert(it)) }
        val evening = SuggestionEngine.build(m1.minusDays(1), m1.minusDays(1).atTime(20, 0), null, false).first { it.short.startsWith("🙏") }.short
        assertTrue("specialDayName: $names; gợi ý tối hôm trước: '$evening'", names.all { it!!.contains("nhuận") } && evening.contains("nhuận"))
    }

    // ---------- Mặt trời: so với thuật toán NOAA đầy đủ ----------

    private fun rad(x: Double) = Math.toRadians(x)
    private fun deg(x: Double) = Math.toDegrees(x)

    /** Thuật toán bảng tính NOAA (Julian century), lặp một lần tại thời điểm mọc/lặn. Trả về giờ thập phân theo múi [tz]. */
    private fun noaa(date: LocalDate, lat: Double, lon: Double, tz: Double, rise: Boolean): Double {
        val jdn = LunarCalendar.jdFromDate(date.dayOfMonth, date.monthValue, date.year).toDouble()
        fun at(hourLocal: Double): Double {
            val jd = jdn - 0.5 + (hourLocal - tz) / 24.0
            val t = (jd - 2451545.0) / 36525.0
            val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
            val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
            val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
            val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
            val omega = 125.04 - 1934.136 * t
            val lambda = l0 + c - 0.00569 - 0.00478 * sin(rad(omega))
            val eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
            val eps = eps0 + 0.00256 * cos(rad(omega))
            val decl = asin(sin(rad(eps)) * sin(rad(lambda)))
            val y = tan(rad(eps / 2)).let { it * it }
            val eq = 4 * deg(
                y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) -
                    0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m)),
            )
            val ha = deg(acos((cos(rad(90.833)) / (cos(rad(lat)) * cos(decl)) - tan(rad(lat)) * tan(decl)).coerceIn(-1.0, 1.0)))
            val noon = 720 - 4 * lon - eq + tz * 60
            return (if (rise) noon - 4 * ha else noon + 4 * ha) / 60
        }
        return at(at(12.0))
    }

    private data class City(val name: String, val lat: Double, val lon: Double, val tzWinter: Double, val tzSummer: Double)

    private val cities = listOf(
        City("Hà Nội", 21.0285, 105.8542, 7.0, 7.0), City("TP.HCM", 10.8231, 106.6297, 7.0, 7.0),
        City("Sydney", -33.8688, 151.2093, 10.0, 11.0), City("Melbourne", -37.8136, 144.9631, 10.0, 11.0),
        City("Auckland", -36.8485, 174.7633, 12.0, 13.0), City("Johannesburg", -26.2041, 28.0473, 2.0, 2.0),
        City("Buenos Aires", -34.6037, -58.3816, -3.0, -3.0), City("Lima", -12.0464, -77.0428, -5.0, -5.0),
        City("Paris", 48.8566, 2.3522, 1.0, 2.0), City("Berlin", 52.52, 13.405, 1.0, 2.0), City("Moskva", 55.7558, 37.6173, 3.0, 3.0),
        City("Helsinki", 60.1699, 24.9384, 2.0, 3.0), City("San Jose", 37.3382, -121.8863, -8.0, -7.0),
        City("Houston", 29.7604, -95.3698, -6.0, -5.0), City("Toronto", 43.6532, -79.3832, -5.0, -4.0),
        City("Tokyo", 35.6762, 139.6503, 9.0, 9.0), City("Đài Bắc", 25.033, 121.5654, 8.0, 8.0), City("Singapore", 1.3521, 103.8198, 8.0, 8.0),
    )

    @Test
    fun ok_matTroiMocLanNhieuMuiGioVaNamBanCau() {
        val bad = mutableListOf<String>()
        var worst = 0.0
        for (c in cities) {
            var d = LocalDate.of(2026, 1, 1)
            while (d.year == 2026) {
                val tz = if (d.monthValue in 4..10) c.tzSummer else c.tzWinter   // gần đúng giờ mùa hè, chỉ để thử múi khác +7
                val (r, s) = SkyScene.sunTimes(d, c.lat, c.lon, tz)
                val er = abs(r - noaa(d, c.lat, c.lon, tz, true)) * 60
                val es = abs(s - noaa(d, c.lat, c.lon, tz, false)) * 60
                worst = maxOf(worst, er, es)
                val tol = if (abs(c.lat) > 50) 5.0 else 3.0
                if (er > tol || es > tol) bad += "${c.name} $d lệch mọc %.1f', lặn %.1f'".format(er, es)
                assertTrue("${c.name} $d", r in 0.0..24.0 && s in 0.0..24.0 && r < s)
                d = d.plusDays(5)
            }
        }
        System.err.println("QAB2 sai lệch lớn nhất so với NOAA đầy đủ: %.2f phút".format(worst))
        assertEquals(bad.take(15).joinToString("\n"), 0, bad.size)
    }

    // ---------- Thành phố xa múi giờ của máy ----------

    @Test
    fun bug_thanhPhoXaMuiGioCuaMayThiBauTroiNguocNgayDem() {
        // Máy ở California (múi −8) nhưng thành phố thời tiết là Hà Nội (mặc định). 20:00 PST 25/11 = 11:00 trưa 26/11 ở Hà Nội.
        val a = SkyScene.build(LocalDateTime.of(2026, 11, 25, 20, 0), 21.0285, 105.8542, null, -8.0)
        // Máy ở Việt Nam (múi +7), thành phố San Jose: 03:00 ICT 26/11 = 12:00 trưa 25/11 ở San Jose.
        val b = SkyScene.build(LocalDateTime.of(2026, 11, 26, 3, 0), 37.3382, -121.8863, null, 7.0)
        // Đối chứng: cùng thời điểm, đúng múi của thành phố
        val a0 = SkyScene.build(LocalDateTime.of(2026, 11, 26, 11, 0), 21.0285, 105.8542, null, 7.0)
        val b0 = SkyScene.build(LocalDateTime.of(2026, 11, 25, 12, 0), 37.3382, -121.8863, null, -8.0)
        assertTrue(a0.daylight == 1f && b0.daylight == 1f)
        assertEquals(
            "daylight (Hà Nội từ máy −8, San Jose từ máy +7); mọc/lặn tính ra: (%.2f, %.2f), (%.2f, %.2f)".format(a.sunrise, a.sunset, b.sunrise, b.sunset),
            listOf(1f, 1f), listOf(a.daylight, b.daylight),
        )
    }

    // ---------- Vĩ độ cao ----------

    private fun green(c: Int) = (c shr 8) and 0xFF

    @Test
    fun ok_vungCucKhongVoKhongNaN() {
        for (lat in listOf(60.0, 66.0, 70.0, 78.2, 89.9, -66.0, -78.0, -89.9)) for (date in listOf(LocalDate.of(2026, 6, 21), LocalDate.of(2026, 12, 21), LocalDate.of(2026, 3, 20))) {
            for (q in 0 until 96) {
                val s = SkyScene.build(date.atStartOfDay().plusMinutes(q * 15L), lat, 15.0, null, 1.0)
                val ctx = "lat $lat $date ${q / 4}h${q % 4 * 15}"
                assertTrue(ctx, s.daylight in 0f..1f && s.sunrise.isFinite() && s.sunset.isFinite())
                assertTrue("$ctx moonPos=${s.moonPos}", s.moonPos == null || s.moonPos!! in 0f..1f)
                assertTrue("$ctx sunPos=${s.sunPos}", s.sunPos == null || s.sunPos!! in 0f..1f)
            }
        }
    }

    @Test
    fun nit_ngayNganDuoi3GioTroiXanhGiuaDem() {
        // Khi daylight = 0 (đêm hẳn) màu trời phải tối. Đúng ở vĩ độ thường; sai khi ngày < 3 giờ (vĩ độ > ~65°, mùa đông)
        val bad = mutableListOf<String>()
        for (lat in listOf(21.0, 50.0, 60.0, 64.0, 66.0, 70.0, 75.0)) {
            val (r, s) = SkyScene.sunTimes(LocalDate.of(2026, 12, 21), lat, 25.0, 2.0)
            for (q in 0 until 24 * 12) {
                val h = q / 12.0
                if (SkyScene.daylight(h, r, s) == 0f && green(SkyScene.skyColors(h, r, s).first) > 0x40) {
                    bad += "lat $lat %02d:%02d (mọc %.2f lặn %.2f) màu trên #%06X".format(q / 12, q % 12 * 5, r, s, SkyScene.skyColors(h, r, s).first and 0xFFFFFF)
                    break
                }
            }
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    // ---------- La bàn ----------

    /** Sơn thứ i thuộc cung (i+1)/3: Nhâm-Tý-Quý = Bắc, Sửu-Cấn-Dần = Đông Bắc… */
    private fun mismatches(angles: List<Float>) = angles.mapNotNull { a ->
        val i = PhongThuy.SON.indexOf(PhongThuy.son(a))
        if (Huong.values()[((i + 1) / 3) % 8] == Huong.nearest(a)) null else "góc $a: sơn ${PhongThuy.son(a)}, hướng ${Huong.nearest(a).label}"
    }

    @Test
    fun ok_sonVaHuongKhopNhauMoiGoc() {
        val angles = mutableListOf<Float>()
        for (k in -720..1440) angles += k * 0.5f
        for (b in 0 until 48) {
            val x = b * 7.5f
            angles += listOf(x - 0.001f, x, x + 0.001f, x + 360f, x - 360f)
        }
        val bad = mismatches(angles)
        assertEquals(bad.take(10).joinToString("\n"), 0, bad.size)
        for (x in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1e9f, 1e9f)) {
            PhongThuy.son(x); Huong.nearest(x); PhongThuy.describe(x)   // không ném
        }
    }

    @Test
    fun nit_sonVaHuongLechNhauTrongMotUlpSatBien() {
        // son() cộng 360 trong norm() rồi mới chia nên làm tròn float khác Huong.nearest(): sát biên < 1e-5° hai dòng mâu thuẫn
        val angles = (0 until 48).flatMap { b -> listOf(Math.nextDown(b * 7.5f), Math.nextUp(b * 7.5f)) }
        val bad = mismatches(angles)
        assertEquals(bad.take(10).joinToString("\n"), 0, bad.size)
    }

    @Test
    fun ok_cungPhiNamAmSoAmVaRatXa() {
        // Công thức chu kỳ 9: năm y và y + 9k cùng cung; năm âm lịch rất xa không vỡ
        for (y in listOf(-500, 0, 1, 1800, 2100, 9999)) for (male in listOf(true, false)) {
            assertEquals(PhongThuy.cungPhi(y, male), PhongThuy.cungPhi(y + 9 * 37, male))
        }
    }
}
