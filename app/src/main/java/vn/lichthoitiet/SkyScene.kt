package vn.lichthoitiet

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Hiệu ứng thời tiết trên hình nền động, chọn theo tiết khí (hoặc thời tiết thật nếu đang mưa, sương mù). */
enum class SkyEffect { NONE, PETALS, DRIZZLE, RAIN, SUMMER, LEAVES, MIST, FOG }

/**
 * Trạng thái bầu trời tại một thời điểm, tính thuần (không phụ thuộc Android) để hình nền động vẽ và để kiểm thử.
 * Màu là ARGB. [daylight] 0 là đêm hẳn, 1 là ngày hẳn. [sunPos], [moonPos] là vị trí 0..1 trên cung trời.
 */
data class SkyState(
    val hour: Double,
    val sunrise: Double,
    val sunset: Double,
    val daylight: Float,
    val skyTop: Int,
    val skyBottom: Int,
    val sunPos: Float?,
    val moonPos: Float?,
    val moonIllum: Float,
    val moonWaxing: Boolean,
    val effect: SkyEffect,
    val tietKhi: String,
    val lunar: LunarDate,
    val title: String,
    val subtitle: String,
)

object SkyScene {
    /** Ngày âm dạng chữ: "Mùng 5 tháng 10", "Rằm tháng Giêng", "23 tháng Chạp", thêm "nhuận" nếu là tháng nhuận. */
    fun lunarTitle(l: LunarDate): String {
        val d = when {
            l.day == 15 -> "Rằm"
            l.day <= 10 -> "Mùng ${l.day}"
            else -> l.day.toString()
        }
        return "$d tháng ${LunarCalendar.monthName(l.month)}" + if (l.leap) " nhuận" else ""
    }

    /**
     * Giờ mặt trời mọc, lặn (giờ thập phân theo múi [tz]) tại vĩ độ [lat], kinh độ [lon]
     * theo công thức gần đúng của NOAA, sai số vài phút.
     */
    fun sunTimes(date: LocalDate, lat: Double, lon: Double, tz: Double = 7.0): Pair<Double, Double> {
        val g = 2 * PI / 365 * (date.dayOfYear - 1 + 0.5)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latR = Math.toRadians(lat)
        val cosH = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
        val h = Math.toDegrees(acos(cosH.coerceIn(-1.0, 1.0)))
        val noon = 720 - 4 * lon - eqTime + tz * 60
        return Pair((noon - 4 * h) / 60, (noon + 4 * h) / 60)
    }

    /** Phần trăng được chiếu sáng theo ngày âm (rằm gần 1, mùng 1 gần 0; tối thiểu một vành mảnh để vẫn thấy trăng non). */
    fun moonIllumination(lunarDay: Int): Float {
        val age = (lunarDay - 0.5) / 29.53
        return ((1 - cos(2 * PI * age)) / 2).toFloat().coerceAtLeast(0.04f)
    }

    /** Trăng đầu tháng (đến rằm) khuyết bên trái, sáng bên phải; sau rằm ngược lại. */
    fun moonWaxing(lunarDay: Int): Boolean = lunarDay <= 15

    /** Hiệu ứng theo tiết khí; [index] theo LunarCalendar.TIET_KHI (0 = Xuân phân). */
    fun effectForTietKhi(index: Int): SkyEffect = when (Math.floorMod(index, 24)) {
        21, 23, 0, 1 -> SkyEffect.PETALS      // Lập xuân, Kinh trập, Xuân phân, Thanh minh: hoa đào bay
        22 -> SkyEffect.DRIZZLE               // Vũ thủy: mưa phùn
        2 -> SkyEffect.RAIN                   // Cốc vũ: mưa rào
        in 3..8 -> SkyEffect.SUMMER           // Lập hạ … Đại thử: nắng hè, đom đóm đêm
        9, 10, 12 -> SkyEffect.LEAVES         // Lập thu, Xử thử, Thu phân: lá vàng rơi
        11, 13 -> SkyEffect.MIST              // Bạch lộ, Hàn lộ: sương mỏng
        else -> SkyEffect.FOG                 // Sương giáng … Đại hàn (Đông chí): sương mù
    }

    /** Thời tiết thật lấn át tiết khí: mưa phùn, mưa, dông, sương mù (mã WMO của Open-Meteo). */
    fun effectForWeather(code: Int?): SkyEffect? = when (code) {
        null -> null
        45, 48 -> SkyEffect.FOG
        in 51..57 -> SkyEffect.DRIZZLE
        in 61..67, in 80..82, in 95..99 -> SkyEffect.RAIN
        else -> null
    }

    // Khung màu trời theo giờ (lệch so với mặt trời mọc / lặn), nội suy tuyến tính
    private const val NIGHT_TOP = 0xFF0A1230.toInt(); private const val NIGHT_BOT = 0xFF26335F.toInt()
    private const val DAWN0_TOP = 0xFF1D2A5C.toInt(); private const val DAWN0_BOT = 0xFFC9798A.toInt()
    private const val DAWN_TOP = 0xFF6C8FC9.toInt(); private const val DAWN_BOT = 0xFFF6C79A.toInt()
    private const val DAY_TOP = 0xFF3F86D6.toInt(); private const val DAY_BOT = 0xFFBFE0F5.toInt()
    private const val GOLD_TOP = 0xFF5C7BC0.toInt(); private const val GOLD_BOT = 0xFFF5B27A.toInt()
    private const val DUSK_TOP = 0xFF2A2C66.toInt(); private const val DUSK_BOT = 0xFFE0796A.toInt()

    private fun lerp(a: Int, b: Int, t: Float): Int {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
        return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** Màu trời (trên, dưới) lúc [hour] với giờ mọc [rise], lặn [set]. */
    fun skyColors(hour: Double, rise: Double, set: Double): Pair<Int, Int> {
        val len = set - rise
        if (len < 0.5) return NIGHT_TOP to NIGHT_BOT
        // Ngày ngắn (dưới 3,2 giờ, vùng cực): co các mốc ban ngày theo độ dài ngày để màu chuyển dần, không nhảy
        val m = if (len < 3.2) len / 3.2 else 1.0
        val keys = mutableListOf(
            rise - 1.2 to (NIGHT_TOP to NIGHT_BOT),
            rise - 0.3 to (DAWN0_TOP to DAWN0_BOT),
            rise + 0.4 * m to (DAWN_TOP to DAWN_BOT),
            rise + 1.5 * m to (DAY_TOP to DAY_BOT),
            set - 1.5 * m to (DAY_TOP to DAY_BOT),
            set - 0.4 * m to (GOLD_TOP to GOLD_BOT),
            set + 0.3 to (DUSK_TOP to DUSK_BOT),
            set + 1.2 to (NIGHT_TOP to NIGHT_BOT),
        )
        // Ngày rất ngắn (vùng cực): các mốc không được đi lùi, nếu không giữa đêm lại ra màu ban ngày
        for (k in 1 until keys.size) if (keys[k].first < keys[k - 1].first) keys[k] = keys[k - 1].first to keys[k].second
        if (hour <= keys.first().first || hour >= keys.last().first) return NIGHT_TOP to NIGHT_BOT
        val i = keys.indexOfLast { it.first <= hour }.coerceAtMost(keys.size - 2)
        val (h0, c0) = keys[i]
        val (h1, c1) = keys[i + 1]
        val t = if (h1 > h0) ((hour - h0) / (h1 - h0)).toFloat().coerceIn(0f, 1f) else 1f
        return lerp(c0.first, c1.first, t) to lerp(c0.second, c1.second, t)
    }

    /** 0 đêm → 1 ngày, chuyển mượt quanh lúc mọc, lặn. */
    fun daylight(hour: Double, rise: Double, set: Double): Float {
        val up = ((hour - (rise - 0.5)) / 1.3).coerceIn(0.0, 1.0)
        val down = (((set + 0.5) - hour) / 1.3).coerceIn(0.0, 1.0)
        return minOf(up, down).toFloat()
    }

    fun build(now: LocalDateTime, lat: Double, lon: Double, weatherCode: Int? = null, tz: Double = 7.0): SkyState {
        val date = now.toLocalDate()
        val clock = now.hour + now.minute / 60.0 + now.second / 3600.0
        val (rise0, set0) = sunTimes(date, lat, lon, tz)
        // Thành phố ở xa múi giờ của máy: giờ mọc, lặn có thể ra ngoài 0–24h; đưa giờ hiện tại về gần buổi trưa của thành phố
        val noon0 = (rise0 + set0) / 2
        var hour = clock
        // Ngày "của thành phố" đi cùng giờ đã quy đổi (để trăng không đổi pha trước nửa đêm hay giữa đêm)
        var ref = date
        while (hour < noon0 - 12) { hour += 24; ref = ref.minusDays(1) }
        while (hour >= noon0 + 12) { hour -= 24; ref = ref.plusDays(1) }
        // Giờ mọc, lặn của đúng ngày thành phố (ngày chuyển đêm cực / đêm trắng không nhảy theo ngày của máy)
        // Màu trời, mặt trời: theo giờ mọc lặn của ngày đang tính (liên tục qua nửa đêm, kể cả đêm trắng)
        val rise = rise0
        val set = set0
        val (top, bottom) = skyColors(hour, rise, set)
        val dl = daylight(hour, rise, set)
        val sunPos = if (set - rise >= 0.5 && hour in rise..set) ((hour - rise) / (set - rise)).toFloat() else null
        // Trăng: theo giờ mọc lặn của đúng ngày thành phố (ngày chuyển sang / ra khỏi đêm cực không nhảy theo ngày của máy)
        val (mRise, mSet) = if (ref == date) rise0 to set0 else sunTimes(ref, lat, lon, tz)
        val noon = (mRise + mSet) / 2
        // Trăng đi trọn cung trời trong đêm: từ lúc lặn mặt trời tới lúc mọc hôm sau
        val nightLen = 24 - mSet + mRise
        val sinceSet = if (hour >= mSet) hour - mSet else hour + 24 - mSet
        val polarNight = mSet - mRise < 0.5
        // Đêm vùng cực: trăng đứng giữa trời, khuất một giờ quanh giữa trưa (lúc trời sáng nhất) và đổi ngày âm lúc đó
        val moonPos = when {
            polarNight -> if (Math.abs(hour - noon) < 0.5) null else 0.5f
            nightLen < 1.5 -> null   // đêm trắng (đêm dưới 1,5 giờ): trời còn sáng, không vẽ trăng chạy vụt qua
            dl < 0.75f && sinceSet in 0.0..nightLen -> (sinceSet / nightLen).toFloat().coerceIn(0f, 1f)
            else -> null
        }
        // Ngày âm theo nửa đêm; trước 6 giờ sáng vẫn coi là "đêm hôm trước" cho trăng
        val lunar = LunarCalendar.convert(date)
        val moonLunar = LunarCalendar.convert(if ((polarNight && hour < noon) || (!polarNight && hour < mRise)) ref.minusDays(1) else ref)
        val tkIndex = LunarCalendar.tietKhiIndexAt(lunar.jd)
        val effect = effectForWeather(weatherCode) ?: effectForTietKhi(tkIndex)
        val tk = LunarCalendar.TIET_KHI[tkIndex]
        return SkyState(
            hour = clock, sunrise = rise, sunset = set, daylight = dl, skyTop = top, skyBottom = bottom,
            sunPos = sunPos, moonPos = moonPos,
            moonIllum = moonIllumination(moonLunar.day), moonWaxing = moonWaxing(moonLunar.day),
            effect = effect, tietKhi = tk, lunar = lunar,
            title = lunarTitle(lunar),
            subtitle = "${LunarCalendar.canChiYear(lunar.year)} · tiết ${tk.replaceFirstChar { it.uppercase() }}",
        )
    }
}
