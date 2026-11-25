package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/** QA tester B, vòng 4: trăng qua nửa đêm (Hà Nội, thành phố xa múi giờ máy, đổi giờ mùa hè), ngày ngắn, la bàn, màn hình khóa. */
class QaB4SkyTest {
    private data class City(val name: String, val lat: Double, val lon: Double, val tz: Double)

    private val hanoi = City("Hà Nội", 21.0285, 105.8542, 7.0)
    private val cities = listOf(
        hanoi, City("TP.HCM", 10.82, 106.63, 7.0), City("Tokyo", 35.68, 139.69, 9.0), City("Sydney", -33.87, 151.21, 11.0),
        City("Auckland", -36.85, 174.76, 13.0), City("Honolulu", 21.31, -157.86, -10.0), City("Los Angeles", 34.05, -118.24, -8.0),
        City("New York", 40.71, -74.0, -5.0), City("London", 51.51, -0.13, 0.0), City("Paris", 48.86, 2.35, 1.0),
        City("Moskva", 55.76, 37.62, 3.0), City("Delhi", 28.61, 77.21, 5.5), City("Kathmandu", 27.72, 85.32, 5.75),
        City("Chatham", -43.95, -176.55, 12.75), City("Kiritimati", 1.87, -157.4, 14.0), City("Lima", -12.05, -77.04, -5.0),
        City("Cape Town", -33.92, 18.42, 2.0), City("Anchorage", 61.22, -149.9, -9.0), City("Reykjavik", 64.15, -21.94, 0.0),
    )
    private val devTzs = listOf(-12.0, -10.0, -5.0, 0.0, 3.0, 5.5, 7.0, 9.5, 12.75, 14.0)

    private fun local(utcMin: Long, tz: Double) = LocalDateTime.ofEpochSecond((utcMin + Math.round(tz * 60)) * 60, 0, ZoneOffset.UTC)

    /** Đổi pha trăng / trăng nhảy chỗ khi trăng đang hiện ở hai mẫu liền nhau. */
    private fun scan(c: City, devTz: Double, startUtcMin: Long, minutes: Long, step: Long): List<String> {
        val out = mutableListOf<String>()
        var prev: SkyState? = null
        var k = 0L
        while (k <= minutes) {
            val t = local(startUtcMin + k, devTz)
            val s = SkyScene.build(t, c.lat, c.lon, null, devTz)
            val p = prev
            if (p != null && p.moonPos != null && s.moonPos != null) {
                if (p.moonIllum != s.moonIllum || p.moonWaxing != s.moonWaxing) out += "máy %+.2f ${c.name} $t: pha %.3f→%.3f".format(devTz, p.moonIllum, s.moonIllum)
                else if (s.moonPos!! < p.moonPos!! - 1e-4f || s.moonPos!! - p.moonPos!! > 0.12f) out += "máy %+.2f ${c.name} $t: vị trí %.3f→%.3f".format(devTz, p.moonPos, s.moonPos)
            }
            if (out.size >= 3) break
            prev = s
            k += step
        }
        return out
    }

    @Test
    fun ok_trangLienTucNhieuThanhPhoVaMuiGioMayCaNam() {
        val start = LocalDate.of(2026, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) / 60
        val bad = mutableListOf<String>()
        for (c in cities) for (tz in (devTzs + c.tz).distinct()) {
            // 365 ngày, mỗi 20 phút
            bad += scan(c, tz, start, 365L * 24 * 60, 20).take(1)
        }
        assertEquals("${bad.size} cặp:\n" + bad.take(15).joinToString("\n"), 0, bad.size)
    }

    @Test
    fun ok_trangDungDemCuaThanhPho() {
        // 21:00 giờ thành phố: pha của ngày (giờ thành phố) đó; 02:00 giờ thành phố: pha của hôm trước — bất kể múi giờ máy
        // (thành phố bên kia đường đổi ngày như Kiritimati +14, Chatham +12:45: ngày mặt trời lệch ngày dân sự 1 ngày ⇒ chỉ so độc lập múi máy)
        val bad = mutableListOf<String>()
        for (c in cities.filter { abs(it.lat) < 50 }) {
            val dateLine = abs(c.tz * 15 - c.lon) > 180
            var d = LocalDate.of(2026, 1, 3)
            while (d.year == 2026) {
                for ((h, back) in listOf(21 to 0L, 2 to 1L, 23 to 0L, 0 to 1L)) {
                    val utcMin = d.atTime(h, 0).toEpochSecond(ZoneOffset.UTC) / 60 - Math.round(c.tz * 60)
                    val own = SkyScene.build(local(utcMin, c.tz), c.lat, c.lon, null, c.tz).moonIllum
                    val want = LunarCalendar.convert(d.minusDays(back)).day
                    if (!dateLine && own != SkyScene.moonIllumination(want) && bad.size < 15) bad += "${c.name} $d %02d:00: trăng ngày âm ≠ $want".format(h)
                    for (tz in devTzs) {
                        val s = SkyScene.build(local(utcMin, tz), c.lat, c.lon, null, tz)
                        if (s.moonIllum != own && bad.size < 15) bad += "máy %+.2f ${c.name} $d %02d:00 (giờ TP): trăng khác máy cùng múi TP".format(tz, h)
                    }
                }
                d = d.plusDays(5)
            }
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    @Test
    fun ok_trangHaNoiQuanhNuaDem10Nam() {
        var bad = 0
        val ex = mutableListOf<String>()
        var d = LocalDate.of(2026, 1, 1)
        while (d.year < 2036) {
            val ref = SkyScene.build(d.atTime(21, 0), hanoi.lat, hanoi.lon, null, 7.0)
            for (m in 0..(9 * 60) step 3) {
                val t = d.atTime(21, 0).plusMinutes(m.toLong())
                val s = SkyScene.build(t, hanoi.lat, hanoi.lon, null, 7.0)
                if (s.moonPos != null && (s.moonIllum != ref.moonIllum || s.moonWaxing != ref.moonWaxing)) { bad++; if (ex.size < 5) ex += "$t"; break }
            }
            d = d.plusDays(1)
        }
        assertEquals("$bad đêm đổi pha khi trăng đang hiện: $ex", 0, bad)
    }

    @Test
    fun ok_doiGioMuaHeTrenMayKhongLamTrangNhay() {
        // Máy ở múi có đổi giờ mùa hè (tz lấy theo offset hiện hành như LunarWallpaperService)
        val bad = mutableListOf<String>()
        val cases = listOf(ZoneId.of("Europe/Paris") to cities.first { it.name == "Paris" }, ZoneId.of("Australia/Sydney") to hanoi,
            ZoneId.of("America/New_York") to cities.first { it.name == "New York" }, ZoneId.of("America/Los_Angeles") to cities.first { it.name == "Sydney" })
        for ((zone, c) in cases) for (day in listOf(LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 27), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31))) {
            var inst = day.atStartOfDay(ZoneOffset.UTC).toInstant()
            var prev: SkyState? = null
            repeat(4 * 24 * 12) {
                val ldt = LocalDateTime.ofInstant(inst, zone)
                val tz = zone.rules.getOffset(inst).totalSeconds / 3600.0
                val s = SkyScene.build(ldt, c.lat, c.lon, null, tz)
                val p = prev
                if (p != null && p.moonPos != null && s.moonPos != null && bad.size < 6 &&
                    (p.moonIllum != s.moonIllum || abs(s.moonPos!! - p.moonPos!!) > 0.05f)) bad += "$zone ${c.name} $ldt: trăng ${p.moonIllum}/${p.moonPos} → ${s.moonIllum}/${s.moonPos}"
                prev = s
                inst = inst.plusSeconds(300)
            }
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    @Test
    fun nit_vungCucDemCucTrangDoiPhaLucTrua() {
        // Đêm vùng cực (rise = set = trưa): trăng hiện cả ngày và đổi pha + nhảy từ cuối cung về đầu cung lúc giữa trưa
        val c = City("Tromsø", 69.65, 18.96, 1.0)
        val start = LocalDate.of(2026, 12, 10).atStartOfDay().toEpochSecond(ZoneOffset.UTC) / 60
        val jumps = scan(c, 1.0, start, 5L * 24 * 60, 5)
        System.err.println("QAB4 Tromsø đêm cực: $jumps")
        assertEquals(jumps.joinToString("\n"), 0, jumps.size)
    }

    private fun ch(c: Int, s: Int) = (c shr s) and 0xFF

    @Test
    fun ok_mauTroiNgayNganKhongNhay() {
        val bad = mutableListOf<String>()
        for (lat10 in 580..720 step 5) {
            val lat = lat10 / 10.0
            var d = LocalDate.of(2026, 1, 1)
            while (d.year == 2026 && bad.size < 8) {
                val (r, s) = SkyScene.sunTimes(d, lat, 18.96, 1.0)
                var prev = SkyScene.skyColors(-1.0, r, s)
                var m = 0
                while (m <= 26 * 60) {
                    val h = -1.0 + m / 60.0
                    val c = SkyScene.skyColors(h, r, s)
                    val jump = listOf(0, 8, 16).maxOf { sh -> maxOf(abs(ch(c.first, sh) - ch(prev.first, sh)), abs(ch(c.second, sh) - ch(prev.second, sh))) }
                    if (jump > 40) { bad += "vĩ độ $lat $d (ngày %.2f h) %.2f h: nhảy $jump".format(s - r, h); break }
                    prev = c; m += 2
                }
                d = d.plusDays(3)
            }
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    @Test
    fun nit_ngayDuoi30PhutMatTroiTrenNenTroiDem() {
        // Ngày dài < 0,5 giờ: skyColors trả màu đêm cả ngày nhưng build vẫn vẽ mặt trời (sunPos ≠ null, daylight ≈ 0,5)
        val hits = mutableListOf<String>()
        for (lat100 in 6600..6800 step 2) {
            val lat = lat100 / 100.0
            for (d in listOf(LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 10))) {
                val (r, s) = SkyScene.sunTimes(d, lat, 18.96, 1.0)
                if (s - r in 0.05..0.49) {
                    val noon = (r + s) / 2
                    val st = SkyScene.build(d.atTime(noon.toInt(), ((noon - noon.toInt()) * 60).toInt()), lat, 18.96, null, 1.0)
                    if (st.sunPos != null && st.skyTop == 0xFF0A1230.toInt()) hits += "vĩ độ $lat $d: ngày %.0f phút, daylight %.2f".format((s - r) * 60, st.daylight)
                }
            }
        }
        System.err.println("QAB4 mặt trời trên nền đêm: ${hits.size} ca, vd ${hits.take(2)}")
        assertEquals(hits.take(3).joinToString("\n"), 0, hits.size)
    }

    // ---------- La bàn ----------

    @Test
    fun ok_moTaGocLamTronLenKhopHuong() {
        val bad = mutableListOf<String>()
        var i = -72000
        while (i <= 72000 && bad.size < 10) {
            for (a in listOf(i / 100f, Math.nextUp(i / 100f), Math.nextDown(i / 100f))) {
                val text = PhongThuy.describe(a)
                val n = text.substringBefore("°").toInt()
                val label = text.substringAfter("° ")
                val nd = PhongThuy.normD(a)
                val err = abs(((n - nd) % 360 + 540) % 360 - 180)
                if (n !in 0..359 || err > 0.5 + 1e-9 || Huong.nearest(n.toFloat()).label != label) bad += "$a → \"$text\""
                // làm tròn lên ở đúng x,5
                if (abs(nd - Math.floor(nd) - 0.5) < 1e-12 && n != Math.floorMod(Math.floor(nd).toInt() + 1, 360)) bad += "$a (x,5) → \"$text\""
            }
            i++
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    // ---------- Màn hình khóa / gợi ý tháng nhuận ----------

    @Test
    fun ok_manHinhKhoaVaGoiYThangNhuanNhieuNam() {
        val bad = mutableListOf<String>()
        val le = setOf("Tết Nguyên Đán", "Giỗ Tổ Hùng Vương", "Tết Đoan Ngọ", "Lễ Vu Lan", "Tết Trung Thu", "Ngày ông Táo về trời")
        for (d in days(LocalDate.of(1950, 1, 1), LocalDate.of(2150, 12, 31))) {
            val l = LunarCalendar.convert(d)
            val t = LockScreenDay.texts(d).title
            val shouldLe = !l.leap && (l.month to l.day) in setOf(1 to 1, 3 to 10, 5 to 5, 7 to 15, 8 to 15, 12 to 23)
            if (!t.startsWith(SkyScene.lunarTitle(l) + " âm lịch")) bad += "$d: $t"
            if (shouldLe != le.any { t.endsWith(" · $it") }) bad += "$d (${l.day}/${l.month}${if (l.leap) "N" else ""}): $t"
            if (l.leap != t.contains("nhuận")) bad += "$d: $t"
            if (l.leap && (l.day == 1 || l.day == 15)) {
                val s = SuggestionEngine.build(d, d.atTime(9, 0), null, false).joinToString { it.short }
                if (!s.contains("nhuận") || le.any { s.contains(it, ignoreCase = true) }) bad += "$d gợi ý: $s"
                val eve = SuggestionEngine.build(d.minusDays(1), d.minusDays(1).atTime(20, 0), null, false).joinToString { it.short }
                if (!eve.contains("Mai là") || !eve.contains("nhuận")) bad += "${d.minusDays(1)} tối: $eve"
            }
            if (bad.size > 10) break
        }
        assertEquals(bad.take(10).joinToString("\n"), 0, bad.size)
    }

    private fun days(from: LocalDate, to: LocalDate) = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
}
