package vn.lichthoitiet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Dữ liệu thời tiết lấy từ Open-Meteo (miễn phí, không cần API key). */
data class WeatherData(
    val city: String,
    val tempNow: Int,
    val feelsLike: Int,
    val humidity: Int,
    val windNow: Int,
    val codeNow: Int,
    val todayMax: Int,
    val todayMin: Int,
    val todayRain: Int,
    val todayUv: Double,
    val todayCode: Int,
    val tomorrowMax: Int,
    val tomorrowMin: Int,
    val tomorrowRain: Int,
    val tomorrowCode: Int,
    /** 48 giờ, bắt đầu từ 0h hôm nay. */
    val hourlyRain: List<Int>,
    val hourlyCode: List<Int>,
    val fetchedAt: Long,
) {
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
        put("windNow", windNow); put("codeNow", codeNow)
        put("todayMax", todayMax); put("todayMin", todayMin); put("todayRain", todayRain)
        put("todayUv", todayUv); put("todayCode", todayCode)
        put("tomorrowMax", tomorrowMax); put("tomorrowMin", tomorrowMin)
        put("tomorrowRain", tomorrowRain); put("tomorrowCode", tomorrowCode)
        put("hourlyRain", JSONArray(hourlyRain)); put("hourlyCode", JSONArray(hourlyCode))
        put("fetchedAt", fetchedAt)
    }.toString()

    companion object {
        fun fromJson(s: String): WeatherData? = try {
            val j = JSONObject(s)
            WeatherData(
                city = j.getString("city"),
                tempNow = j.getInt("tempNow"), feelsLike = j.getInt("feelsLike"),
                humidity = j.getInt("humidity"), windNow = j.getInt("windNow"), codeNow = j.getInt("codeNow"),
                todayMax = j.getInt("todayMax"), todayMin = j.getInt("todayMin"), todayRain = j.getInt("todayRain"),
                todayUv = j.getDouble("todayUv"), todayCode = j.getInt("todayCode"),
                tomorrowMax = j.getInt("tomorrowMax"), tomorrowMin = j.getInt("tomorrowMin"),
                tomorrowRain = j.getInt("tomorrowRain"), tomorrowCode = j.getInt("tomorrowCode"),
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

data class GeoResult(val lat: Double, val lon: Double, val name: String)

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
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
            "&hourly=precipitation_probability,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max" +
            "&timezone=Asia%2FBangkok&forecast_days=2"
        val j = JSONObject(httpGet(url))
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        val hourly = j.getJSONObject("hourly")

        fun JSONArray.num(i: Int): Double = optDouble(i, 0.0).let { if (it.isNaN()) 0.0 else it }
        fun JSONArray.ints(): List<Int> = List(length()) { num(it).roundToInt() }

        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dRain = daily.getJSONArray("precipitation_probability_max")
        val dCode = daily.getJSONArray("weather_code")
        val dUv = daily.getJSONArray("uv_index_max")

        return WeatherData(
            city = city,
            tempNow = cur.optDouble("temperature_2m", 0.0).roundToInt(),
            feelsLike = cur.optDouble("apparent_temperature", 0.0).roundToInt(),
            humidity = cur.optDouble("relative_humidity_2m", 0.0).roundToInt(),
            windNow = cur.optDouble("wind_speed_10m", 0.0).roundToInt(),
            codeNow = cur.optInt("weather_code", 0),
            todayMax = dMax.num(0).roundToInt(), todayMin = dMin.num(0).roundToInt(),
            todayRain = dRain.num(0).roundToInt(), todayUv = dUv.num(0), todayCode = dCode.num(0).roundToInt(),
            tomorrowMax = dMax.num(1).roundToInt(), tomorrowMin = dMin.num(1).roundToInt(),
            tomorrowRain = dRain.num(1).roundToInt(), tomorrowCode = dCode.num(1).roundToInt(),
            hourlyRain = hourly.getJSONArray("precipitation_probability").ints(),
            hourlyCode = hourly.getJSONArray("weather_code").ints(),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** Tìm tọa độ theo tên thành phố. Trả về null nếu không thấy. */
    fun geocode(name: String): GeoResult? {
        val q = URLEncoder.encode(name, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=vi&format=json"
        val j = JSONObject(httpGet(url))
        val results = j.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val r = results.getJSONObject(0)
        return GeoResult(r.getDouble("latitude"), r.getDouble("longitude"), r.getString("name"))
    }

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

/** Đổi mã thời tiết WMO sang biểu tượng và chữ tiếng Việt. */
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
}
