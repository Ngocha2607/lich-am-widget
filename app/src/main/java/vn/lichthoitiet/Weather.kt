package vn.lichthoitiet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Dự báo một ngày. [date] dạng "2026-11-24", [sunrise]/[sunset] dạng "06:09". */
data class DailyForecast(
    val date: String,
    val code: Int,
    val max: Int,
    val min: Int,
    val rain: Int,
    val uv: Double,
    val sunrise: String,
    val sunset: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date); put("code", code); put("max", max); put("min", min)
        put("rain", rain); put("uv", uv); put("sunrise", sunrise); put("sunset", sunset)
    }

    companion object {
        fun fromJson(j: JSONObject) = DailyForecast(
            j.getString("date"), j.getInt("code"), j.getInt("max"), j.getInt("min"),
            j.getInt("rain"), j.getDouble("uv"), j.getString("sunrise"), j.getString("sunset"),
        )
    }
}

/** Dữ liệu thời tiết lấy từ Open-Meteo (miễn phí, không cần API key). */
data class WeatherData(
    val city: String,
    val tempNow: Int,
    val feelsLike: Int,
    val humidity: Int,
    val windNow: Int,
    /** Hướng gió thổi tới, độ (0 = bắc). */
    val windDir: Int,
    /** hPa */
    val pressure: Int,
    /** km */
    val visibility: Int,
    val codeNow: Int,
    /** 7 ngày, phần tử đầu là hôm nay. */
    val daily: List<DailyForecast>,
    /** 48 giờ, bắt đầu từ 0h hôm nay. */
    val hourlyRain: List<Int>,
    val hourlyCode: List<Int>,
    val fetchedAt: Long,
) {
    private fun d(i: Int) = daily.getOrNull(i)
    val todayMax get() = d(0)?.max ?: tempNow
    val todayMin get() = d(0)?.min ?: tempNow
    val todayRain get() = d(0)?.rain ?: 0
    val todayUv get() = d(0)?.uv ?: 0.0
    val todayCode get() = d(0)?.code ?: codeNow
    val tomorrowMax get() = d(1)?.max ?: todayMax
    val tomorrowMin get() = d(1)?.min ?: todayMin
    val tomorrowRain get() = d(1)?.rain ?: todayRain
    val tomorrowCode get() = d(1)?.code ?: todayCode

    /** Xác suất mưa cao nhất trong khoảng giờ [from, to] (0..47). */
    fun maxRain(from: Int, to: Int): Int {
        if (hourlyRain.isEmpty()) return 0
        val a = from.coerceIn(0, hourlyRain.size - 1)
        val b = (to + 1).coerceIn(a + 1, hourlyRain.size)
        return hourlyRain.subList(a, b).maxOrNull() ?: 0
    }

    fun hasThunder(from: Int, to: Int): Boolean {
        if (hourlyCode.isEmpty()) return false
        val a = from.coerceIn(0, hourlyCode.size - 1)
        val b = (to + 1).coerceIn(a + 1, hourlyCode.size)
        return hourlyCode.subList(a, b).any { it >= 95 }
    }

    fun isStale(maxAgeMs: Long): Boolean = System.currentTimeMillis() - fetchedAt > maxAgeMs

    fun toJson(): String = JSONObject().apply {
        put("city", city)
        put("tempNow", tempNow); put("feelsLike", feelsLike); put("humidity", humidity)
        put("windNow", windNow); put("windDir", windDir); put("pressure", pressure); put("visibility", visibility)
        put("codeNow", codeNow)
        put("daily", JSONArray().apply { daily.forEach { put(it.toJson()) } })
        put("hourlyRain", JSONArray(hourlyRain)); put("hourlyCode", JSONArray(hourlyCode))
        put("fetchedAt", fetchedAt)
    }.toString()

    companion object {
        fun fromJson(s: String): WeatherData? = try {
            val j = JSONObject(s)
            val dailyArr = j.getJSONArray("daily")
            WeatherData(
                city = j.getString("city"),
                tempNow = j.getInt("tempNow"), feelsLike = j.getInt("feelsLike"), humidity = j.getInt("humidity"),
                windNow = j.getInt("windNow"), windDir = j.getInt("windDir"),
                pressure = j.getInt("pressure"), visibility = j.getInt("visibility"),
                codeNow = j.getInt("codeNow"),
                daily = List(dailyArr.length()) { DailyForecast.fromJson(dailyArr.getJSONObject(it)) },
                hourlyRain = j.getJSONArray("hourlyRain").toIntList(),
                hourlyCode = j.getJSONArray("hourlyCode").toIntList(),
                fetchedAt = j.getLong("fetchedAt"),
            )
        } catch (e: Exception) {
            null
        }

        private fun JSONArray.toIntList(): List<Int> = List(length()) { i -> optDouble(i, 0.0).let { if (it.isNaN()) 0 else it.roundToInt() } }
    }
}

/** Một kết quả tìm địa điểm. [region] ví dụ "Thành phố Hải Phòng, Việt Nam". */
data class GeoResult(val lat: Double, val lon: Double, val name: String, val region: String = "")

object WeatherRepo {
    private const val KEY_JSON = "weather_json"

    fun load(ctx: Context): WeatherData? =
        Prefs.get(ctx).getString(KEY_JSON, null)?.let { WeatherData.fromJson(it) }

    fun save(ctx: Context, data: WeatherData) {
        Prefs.get(ctx).edit().putString(KEY_JSON, data.toJson()).apply()
    }

    /** Tải thời tiết rồi lưu vào bộ nhớ đệm. Gọi ở luồng nền. */
    fun refresh(ctx: Context): WeatherData {
        val p = Prefs(ctx)
        val data = fetch(p.lat, p.lon, p.city)
        save(ctx, data)
        return data
    }

    fun fetch(lat: Double, lon: Double, city: String): WeatherData {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m,surface_pressure" +
            "&hourly=precipitation_probability,weather_code,visibility" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,sunrise,sunset" +
            "&timezone=Asia%2FBangkok&forecast_days=7"
        val j = JSONObject(httpGet(url))
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        val hourly = j.getJSONObject("hourly")

        fun JSONArray.num(i: Int): Double = optDouble(i, 0.0).let { if (it.isNaN()) 0.0 else it }
        fun JSONArray.ints(limit: Int): List<Int> = List(minOf(length(), limit)) { num(it).roundToInt() }

        val dDate = daily.getJSONArray("time")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dRain = daily.getJSONArray("precipitation_probability_max")
        val dCode = daily.getJSONArray("weather_code")
        val dUv = daily.getJSONArray("uv_index_max")
        val dRise = daily.getJSONArray("sunrise")
        val dSet = daily.getJSONArray("sunset")
        val days = List(dDate.length()) { i ->
            DailyForecast(
                date = dDate.getString(i),
                code = dCode.num(i).roundToInt(),
                max = dMax.num(i).roundToInt(), min = dMin.num(i).roundToInt(),
                rain = dRain.num(i).roundToInt(), uv = dUv.num(i),
                sunrise = dRise.optString(i).substringAfter('T', "06:00"),
                sunset = dSet.optString(i).substringAfter('T', "18:00"),
            )
        }

        val hourNow = LocalDateTime.now().hour
        val visibilityM = hourly.optJSONArray("visibility")?.num(hourNow) ?: 10_000.0

        return WeatherData(
            city = city,
            tempNow = cur.optDouble("temperature_2m", 0.0).roundToInt(),
            feelsLike = cur.optDouble("apparent_temperature", 0.0).roundToInt(),
            humidity = cur.optDouble("relative_humidity_2m", 0.0).roundToInt(),
            windNow = cur.optDouble("wind_speed_10m", 0.0).roundToInt(),
            windDir = cur.optDouble("wind_direction_10m", 0.0).roundToInt(),
            pressure = cur.optDouble("surface_pressure", 0.0).roundToInt(),
            visibility = (visibilityM / 1000).roundToInt(),
            codeNow = cur.optInt("weather_code", 0),
            daily = days,
            hourlyRain = hourly.getJSONArray("precipitation_probability").ints(48),
            hourlyCode = hourly.getJSONArray("weather_code").ints(48),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** Tìm địa điểm theo tên, trả về tối đa [count] kết quả. */
    fun geocodeList(name: String, count: Int = 5): List<GeoResult> {
        val q = URLEncoder.encode(name, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=$count&language=vi&format=json"
        val j = JSONObject(httpGet(url))
        val results = j.optJSONArray("results") ?: return emptyList()
        return List(results.length()) { i ->
            val r = results.getJSONObject(i)
            val region = listOf(r.optString("admin1", ""), r.optString("country", ""))
                .filter { it.isNotBlank() }.joinToString(", ")
            GeoResult(r.getDouble("latitude"), r.getDouble("longitude"), r.getString("name"), region)
        }
    }

    /** Tìm tọa độ theo tên thành phố. Trả về null nếu không thấy. */
    fun geocode(name: String): GeoResult? = geocodeList(name, 1).firstOrNull()

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "LichThoiTiet/0.1")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }
}

/** Đổi số liệu thời tiết sang biểu tượng và chữ tiếng Việt. */
object WeatherText {
    fun icon(code: Int): String = when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun label(code: Int): String = when (code) {
        0 -> "trời quang"
        1 -> "ít mây"
        2 -> "có mây"
        3 -> "nhiều mây"
        45, 48 -> "sương mù"
        in 51..57 -> "mưa phùn"
        61, 80 -> "mưa nhẹ"
        63, 81 -> "mưa vừa"
        65, 82 -> "mưa to"
        66, 67 -> "mưa lạnh"
        in 71..77, 85, 86 -> "có tuyết"
        in 95..99 -> "dông"
        else -> "không rõ"
    }

    fun windDir(deg: Int): String {
        val names = listOf("bắc", "đông bắc", "đông", "đông nam", "nam", "tây nam", "tây", "tây bắc")
        return "Gió " + names[(((deg % 360) + 22.5) / 45).toInt() % 8]
    }

    /** Cấp gió Beaufort từ km/h. */
    fun beaufort(kmh: Int): Int = when {
        kmh < 1 -> 0; kmh <= 5 -> 1; kmh <= 11 -> 2; kmh <= 19 -> 3; kmh <= 28 -> 4
        kmh <= 38 -> 5; kmh <= 49 -> 6; kmh <= 61 -> 7; kmh <= 74 -> 8; kmh <= 88 -> 9
        kmh <= 102 -> 10; kmh <= 117 -> 11; else -> 12
    }

    fun uvLabel(uv: Double): String = when {
        uv < 3 -> "Thấp"; uv < 6 -> "Trung bình"; uv < 8 -> "Cao"; uv < 11 -> "Rất cao"; else -> "Cực cao"
    }
}
