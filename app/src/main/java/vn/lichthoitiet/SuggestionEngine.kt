package vn.lichthoitiet

import java.time.LocalDate
import java.time.LocalDateTime

enum class Kind { WARN, SPECIAL, NORMAL }

/**
 * @param short Dòng ngắn cho widget, tối đa khoảng 35 ký tự.
 * @param detail Giải thích đầy đủ, hiện trong app.
 */
data class Suggestion(val short: String, val detail: String, val kind: Kind)

/**
 * Bộ gợi ý theo quy tắc, chạy offline.
 * Thứ tự ưu tiên: cảnh báo thời tiết → ngày đặc biệt âm lịch → tiết khí → hoàng đạo → mặc định.
 */
object SuggestionEngine {
    private val NGAY_LE = mapOf(
        (1 to 1) to "Tết Nguyên Đán",
        (1 to 15) to "Rằm tháng Giêng",
        (3 to 10) to "Giỗ Tổ Hùng Vương",
        (5 to 5) to "Tết Đoan Ngọ",
        (7 to 15) to "lễ Vu Lan",
        (8 to 15) to "Tết Trung Thu",
        (12 to 23) to "ngày ông Táo về trời",
    )

    fun build(today: LocalDate, now: LocalDateTime, weather: WeatherData?, spiritual: Boolean): List<Suggestion> {
        val lunar = LunarCalendar.convert(today)
        val lunarTomorrow = LunarCalendar.convert(today.plusDays(1))
        val hour = now.hour
        val evening = hour >= 18
        val out = mutableListOf<Suggestion>()

        // 1. Cảnh báo thời tiết
        if (weather != null) {
            if (evening) {
                if (weather.tomorrowRain >= 60) out += Suggestion(
                    "☔ Mai mưa ${weather.tomorrowRain}%, nhớ mang ô",
                    "Ngày mai xác suất mưa ${weather.tomorrowRain}%, ${WeatherText.label(weather.tomorrowCode)}. Chuẩn bị ô hoặc áo mưa trước khi ra khỏi nhà.",
                    Kind.WARN,
                )
                if (weather.tomorrowMin <= 13) out += Suggestion(
                    "🥶 Mai rét ${weather.tomorrowMin}°, mặc ấm",
                    "Sáng mai nhiệt độ thấp nhất ${weather.tomorrowMin}°. Mặc ấm, nhất là cho trẻ nhỏ và người già.",
                    Kind.WARN,
                )
                if (weather.tomorrowMax >= 35) out += Suggestion(
                    "🥵 Mai nắng nóng ${weather.tomorrowMax}°",
                    "Ngày mai nắng nóng tới ${weather.tomorrowMax}°. Nên làm việc ngoài trời vào sáng sớm, uống đủ nước.",
                    Kind.WARN,
                )
                val tonight = weather.maxRain(hour, 23)
                if (hour < 22 && tonight >= 60) out += Suggestion(
                    "☔ Tối nay mưa, mang ô nếu ra ngoài",
                    "Tối nay xác suất mưa $tonight%. Mang ô nếu còn ra ngoài.",
                    Kind.WARN,
                )
            } else {
                val soon = weather.maxRain(hour, hour + 3)
                val afternoon = weather.maxRain(12, 18)
                val rest = weather.maxRain(hour, 23)
                when {
                    soon >= 60 -> out += Suggestion(
                        "☔ Sắp mưa, mang ô theo",
                        "Trong 3 giờ tới xác suất mưa $soon%. Mang ô nếu ra ngoài.",
                        Kind.WARN,
                    )
                    hour < 12 && afternoon >= 60 -> out += Suggestion(
                        "☔ Chiều mưa $afternoon%, mang ô",
                        "Chiều nay xác suất mưa $afternoon%. Mang ô đi làm, không nên phơi đồ ngoài trời.",
                        Kind.WARN,
                    )
                    rest >= 60 -> out += Suggestion(
                        "☔ Hôm nay mưa $rest%, mang ô",
                        "Trong ngày xác suất mưa $rest%. Mang theo ô hoặc áo mưa.",
                        Kind.WARN,
                    )
                }
                if (weather.hasThunder(hour, 23)) out += Suggestion(
                    "⛈ Có dông, hạn chế ra ngoài",
                    "Dự báo có dông trong ngày. Tránh đứng dưới cây to, hạn chế đi xa khi trời chuyển.",
                    Kind.WARN,
                )
                if (hour < 15 && (weather.todayMax >= 35 || weather.todayUv >= 8)) out += Suggestion(
                    "🥵 Nắng gắt ${weather.todayMax}°, tránh 11-15h",
                    "Nhiệt độ cao nhất ${weather.todayMax}°, chỉ số UV ${weather.todayUv.toInt()}. Hạn chế ra ngoài 11-15h, bôi kem chống nắng.",
                    Kind.WARN,
                )
                if (weather.tempNow <= 13 || (hour < 10 && weather.todayMin <= 13)) out += Suggestion(
                    "🥶 Rét ${minOf(weather.tempNow, weather.todayMin)}°, mặc ấm",
                    "Trời rét, hiện ${weather.tempNow}° (cảm giác ${weather.feelsLike}°). Mặc ấm, giữ ấm cổ và chân.",
                    Kind.WARN,
                )
                if (weather.windNow >= 40) out += Suggestion(
                    "💨 Gió mạnh ${weather.windNow} km/h",
                    "Gió mạnh ${weather.windNow} km/h. Cẩn thận khi đi xe máy, chằng buộc đồ ngoài ban công.",
                    Kind.WARN,
                )
                if (weather.humidity >= 88 && today.monthValue in 2..4) out += Suggestion(
                    "💧 Nồm ẩm, không nên phơi đồ",
                    "Độ ẩm ${weather.humidity}%. Đóng cửa sổ, bật hút ẩm, không nên lau nhà bằng nước.",
                    Kind.WARN,
                )
            }
        }

        // 2. Ngày đặc biệt theo âm lịch
        val target = if (evening) lunarTomorrow else lunar
        val prefix = if (evening) "Mai là" else "Hôm nay"
        val le = NGAY_LE[target.month to target.day]
        when {
            le != null -> out += Suggestion(
                "🧧 $prefix $le",
                "$prefix $le (${target.day}/${target.month} âm lịch).",
                Kind.SPECIAL,
            )
            target.day == 15 -> out += Suggestion(
                "🙏 $prefix rằm tháng ${LunarCalendar.monthName(target.month)}",
                "$prefix rằm tháng ${LunarCalendar.monthName(target.month)}. " + templeHint(weather, evening),
                Kind.SPECIAL,
            )
            target.day == 1 -> out += Suggestion(
                "🙏 $prefix mùng 1 tháng ${LunarCalendar.monthName(target.month)}",
                "$prefix mùng 1 tháng ${LunarCalendar.monthName(target.month)}. " + templeHint(weather, evening),
                Kind.SPECIAL,
            )
        }

        // 3. Tiết khí
        LunarCalendar.tietKhiStartsToday(lunar.jd)?.let {
            out += Suggestion("🌿 Hôm nay vào tiết $it", "Hôm nay bắt đầu tiết $it.", Kind.NORMAL)
        }
        if (evening) LunarCalendar.tietKhiStartsToday(lunarTomorrow.jd)?.let {
            out += Suggestion("🌿 Mai vào tiết $it", "Ngày mai bắt đầu tiết $it.", Kind.NORMAL)
        }

        // 4. Hoàng đạo / hắc đạo (tùy chọn). Buổi tối nói về ngày mai để tiện lên kế hoạch.
        if (spiritual) {
            val truc = LunarCalendar.trucThan(target)
            val gio = LunarCalendar.gioHoangDao(target.jd).joinToString(", ")
            val ngay = if (evening) "Mai ngày" else "Ngày"
            if (LunarCalendar.isHoangDao(target)) out += Suggestion(
                "✅ $ngay hoàng đạo, hợp việc lớn",
                "$ngay hoàng đạo (trực $truc), hợp ký kết, khai trương, cưới hỏi. Giờ tốt: $gio.",
                Kind.NORMAL,
            ) else out += Suggestion(
                "🔸 $ngay hắc đạo, tránh việc lớn",
                "$ngay hắc đạo (trực $truc), nên tránh khởi sự việc lớn. Giờ tốt trong ngày: $gio.",
                Kind.NORMAL,
            )
        }

        // 5. Mặc định: mô tả thời tiết của ngày đang nói tới
        if (out.isEmpty()) out += defaultTip(weather, evening)
        return out
    }

    private fun defaultTip(w: WeatherData?, evening: Boolean): Suggestion {
        if (w == null) return Suggestion(
            "📅 Chúc bạn một ngày tốt lành",
            "Chưa có dữ liệu thời tiết. Mở app và bấm Làm mới.",
            Kind.NORMAL,
        )
        val day = if (evening) "Mai" else "Hôm nay"
        val code = if (evening) w.tomorrowCode else w.todayCode
        val min = if (evening) w.tomorrowMin else w.todayMin
        val max = if (evening) w.tomorrowMax else w.todayMax
        val rain = if (evening) w.tomorrowRain else w.todayRain
        val icon = WeatherText.icon(code)
        val label = WeatherText.label(code)
        val short = when {
            code >= 95 -> "$icon $day có dông, $min-$max°"
            code >= 51 -> "$icon $day $label, mang ô phòng khi"
            code >= 45 -> "$icon $day sương mù, đi xe cẩn thận"
            code <= 1 -> "$icon $day trời đẹp, $min-$max°"
            else -> "$icon $day $label, $min-$max°"
        }
        return Suggestion(short, "$day $label, $min-$max°, xác suất mưa $rain%.", Kind.NORMAL)
    }

    private fun templeHint(w: WeatherData?, evening: Boolean): String {
        if (w == null) return ""
        val rain = if (evening) w.tomorrowRain else w.todayRain
        val code = if (evening) w.tomorrowCode else w.todayCode
        val when_ = if (evening) "Sáng mai" else "Sáng nay"
        return if (rain >= 60) "$when_ có thể mưa, nếu đi chùa nhớ mang ô."
        else "$when_ ${WeatherText.label(code)}, hợp đi chùa sớm."
    }
}
