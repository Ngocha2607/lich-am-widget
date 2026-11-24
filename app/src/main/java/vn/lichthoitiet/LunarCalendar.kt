package vn.lichthoitiet

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

data class LunarDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val leap: Boolean,
    /** Số ngày Julius của ngày dương tương ứng (dùng để tính can chi ngày). */
    val jd: Int,
)

/**
 * Lịch âm Việt Nam theo thuật toán của Hồ Ngọc Đức, múi giờ GMT+7.
 * Tính hoàn toàn offline.
 */
object LunarCalendar {
    private const val TZ = 7.0

    val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    // Giờ hoàng đạo theo chi của ngày (Tý/Ngọ, Sửu/Mùi, Dần/Thân, Mão/Dậu, Thìn/Tuất, Tỵ/Hợi)
    private val GIO_HOANG_DAO = arrayOf(
        "110100101100", "001101001011", "110011010010",
        "101100110100", "001011001101", "010010110011",
    )

    private val TRUC_THAN = arrayOf(
        "Thanh Long", "Minh Đường", "Thiên Hình", "Chu Tước", "Kim Quỹ", "Bảo Quang",
        "Bạch Hổ", "Ngọc Đường", "Thiên Lao", "Huyền Vũ", "Tư Mệnh", "Câu Trần",
    )
    private val HOANG_DAO_INDEX = setOf(0, 1, 4, 5, 7, 10)

    // Bắt đầu từ kinh độ mặt trời 0° (Xuân phân), mỗi tiết 15°
    val TIET_KHI = arrayOf(
        "Xuân phân", "Thanh minh", "Cốc vũ", "Lập hạ", "Tiểu mãn", "Mang chủng",
        "Hạ chí", "Tiểu thử", "Đại thử", "Lập thu", "Xử thử", "Bạch lộ",
        "Thu phân", "Hàn lộ", "Sương giáng", "Lập đông", "Tiểu tuyết", "Đại tuyết",
        "Đông chí", "Tiểu hàn", "Đại hàn", "Lập xuân", "Vũ thủy", "Kinh trập",
    )

    private fun int(x: Double): Int = floor(x).toInt()

    fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    private fun newMoonDay(k: Int): Int {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = PI / 180
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c1 = c1 - 0.4068 * sin(mpr * dr) + 0.0161 * sin(dr * 2 * mpr)
        c1 -= 0.0004 * sin(dr * 3 * mpr)
        c1 = c1 + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mpr))
        c1 = c1 - 0.0074 * sin(dr * (m - mpr)) + 0.0004 * sin(dr * (2 * f + m))
        c1 = c1 - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mpr))
        c1 = c1 + 0.0010 * sin(dr * (2 * f - mpr)) + 0.0005 * sin(dr * (2 * mpr + m))
        val deltat = if (t < -11) 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3 else -0.000278 + 0.000265 * t + 0.000262 * t2
        return int(jd1 + c1 - deltat + 0.5 + TZ / 24)
    }

    /** Kinh độ mặt trời (độ, 0..360) lúc 0h giờ địa phương của ngày Julius [jdn]. */
    private fun sunLongitudeDeg(jdn: Double): Double {
        val t = (jdn - 2451545.5 - TZ / 24) / 36525
        val t2 = t * t
        val dr = PI / 180
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl = dl + (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        val l = l0 + dl
        return l - 360.0 * floor(l / 360.0)
    }

    private fun sunSector(jd: Int): Int = int(sunLongitudeDeg(jd.toDouble()) / 30)

    private fun lunarMonth11(yy: Int): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = int(off / 29.530588853)
        var nm = newMoonDay(k)
        if (sunSector(nm) >= 9) nm = newMoonDay(k - 1)
        return nm
    }

    private fun leapMonthOffset(a11: Int): Int {
        val k = int((a11 - 2415021.076998695) / 29.530588853 + 0.5)
        var last: Int
        var i = 1
        var arc = sunSector(newMoonDay(k + i))
        do {
            last = arc
            i++
            arc = sunSector(newMoonDay(k + i))
        } while (arc != last && i < 14)
        return i - 1
    }

    fun convert(date: LocalDate): LunarDate {
        val yy = date.year
        val dayNumber = jdFromDate(date.dayOfMonth, date.monthValue, yy)
        val k = int((dayNumber - 2415021.076998695) / 29.530588853)
        var monthStart = newMoonDay(k + 1)
        if (monthStart > dayNumber) monthStart = newMoonDay(k)

        var a11 = lunarMonth11(yy)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = lunarMonth11(yy - 1)
        } else {
            lunarYear = yy + 1
            b11 = lunarMonth11(yy + 1)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = int((monthStart - a11) / 29.0)
        var leap = false
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapDiff = leapMonthOffset(a11)
            if (diff >= leapDiff) {
                lunarMonth = diff + 10
                if (diff == leapDiff) leap = true
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1
        return LunarDate(lunarDay, lunarMonth, lunarYear, leap, dayNumber)
    }

    // ---------- Can chi, hoàng đạo, tiết khí ----------

    fun canChiYear(year: Int) = "${CAN[(year + 6) % 10]} ${CHI[(year + 8) % 12]}"

    fun canChiMonth(l: LunarDate) = "${CAN[(l.year * 12 + l.month + 3) % 10]} ${CHI[(l.month + 1) % 12]}"

    private fun dayChiIndex(jd: Int) = (jd + 1) % 12

    fun canChiDay(jd: Int) = "${CAN[(jd + 9) % 10]} ${CHI[dayChiIndex(jd)]}"

    private fun trucThanIndex(l: LunarDate): Int {
        // Thanh Long khởi tại: tháng Dần/Thân → Tý, Mão/Dậu → Dần, Thìn/Tuất → Thìn, ...
        val start = ((l.month - 1) % 6) * 2
        return (dayChiIndex(l.jd) - start + 12) % 12
    }

    fun trucThan(l: LunarDate): String = TRUC_THAN[trucThanIndex(l)]

    fun isHoangDao(l: LunarDate): Boolean = trucThanIndex(l) in HOANG_DAO_INDEX

    fun gioHoangDao(jd: Int): List<String> {
        val pattern = GIO_HOANG_DAO[dayChiIndex(jd) % 6]
        return (0 until 12).filter { pattern[it] == '1' }.map { i ->
            val start = (i * 2 + 23) % 24
            "${CHI[i]} ($start-${(start + 2) % 24}h)"
        }
    }

    private fun tietKhiIndex(jdStart: Double): Int = int(sunLongitudeDeg(jdStart) / 15) % 24

    /** Tiết khí đang diễn ra (tính ở cuối ngày). */
    fun currentTietKhi(jd: Int): String = TIET_KHI[tietKhiIndex(jd + 1.0)]

    /** Tên tiết khí nếu tiết mới bắt đầu trong ngày hôm nay, ngược lại null. */
    fun tietKhiStartsToday(jd: Int): String? {
        val a = tietKhiIndex(jd.toDouble())
        val b = tietKhiIndex(jd + 1.0)
        return if (a != b) TIET_KHI[b] else null
    }

    fun monthName(m: Int): String = when (m) {
        1 -> "Giêng"
        12 -> "Chạp"
        else -> m.toString()
    }
}
