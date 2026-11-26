package vn.lichthoitiet

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class CityPref(val name: String, val lat: Double, val lon: Double)

/** Cài đặt người dùng: vị trí và tùy chọn hiển thị. */
class Prefs(ctx: Context) {
    private val sp = get(ctx)

    var lat: Double
        get() = sp.getString("lat", null)?.toDoubleOrNull() ?: DEFAULT_LAT
        set(v) = sp.edit().putString("lat", v.toString()).apply()

    var lon: Double
        get() = sp.getString("lon", null)?.toDoubleOrNull() ?: DEFAULT_LON
        set(v) = sp.edit().putString("lon", v.toString()).apply()

    var city: String
        get() = sp.getString("city", DEFAULT_CITY) ?: DEFAULT_CITY
        set(v) = sp.edit().putString("city", v).apply()

    /** Hiện ngày hoàng đạo/hắc đạo và giờ tốt. */
    var spiritual: Boolean
        get() = sp.getBoolean("spiritual", true)
        set(v) = sp.edit().putBoolean("spiritual", v).apply()

    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun put(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()

    // Các mục hiển thị trong app
    var showAlmanac: Boolean
        get() = bool("showAlmanac", true)
        set(v) = put("showAlmanac", v)
    var showLucDieu: Boolean
        get() = bool("showLucDieu", true)
        set(v) = put("showLucDieu", v)
    var showHuong: Boolean
        get() = bool("showHuong", true)
        set(v) = put("showHuong", v)
    var showGoodDays: Boolean
        get() = bool("showGoodDays", true)
        set(v) = put("showGoodDays", v)

    // Thông báo
    var notifyEvents: Boolean
        get() = bool("notifyEvents", true)
        set(v) = put("notifyEvents", v)
    var notifyLunar: Boolean
        get() = bool("notifyLunar", false)
        set(v) = put("notifyLunar", v)
    var notifyHour: Int
        get() = sp.getInt("notifyHour", 7)
        set(v) = sp.edit().putInt("notifyHour", v).apply()

    var notifyWeather: Boolean
        get() = bool("notifyWeather", true)
        set(v) = put("notifyWeather", v)
    var notifyWeatherHour: Int
        get() = sp.getInt("notifyWeatherHour", 20)
        set(v) = sp.edit().putInt("notifyWeatherHour", v).apply()

    /**
     * Năm sinh âm lịch để xét hợp/xung tuổi; 0 = chưa đặt.
     * Có [birthDate] thì giá trị này được tính từ ngày sinh; bản cũ chỉ lưu mỗi năm.
     */
    var birthYear: Int
        get() = sp.getInt("birthYear", 0)
        set(v) = sp.edit().putInt("birthYear", v).apply()

    /** Họ tên đầy đủ trong hồ sơ. */
    var profileName: String
        get() = sp.getString("profileName", "") ?: ""
        set(v) = sp.edit().putString("profileName", v).apply()

    /** Ngày sinh dương lịch dạng "1990-03-12"; "" = chưa nhập. */
    var birthDate: String
        get() = sp.getString("birthDate", "") ?: ""
        set(v) = sp.edit().putString("birthDate", v).apply()

    /** Người dùng nhập ngày sinh theo âm lịch (để mở lại đúng kiểu lịch khi sửa). */
    var birthLunarInput: Boolean
        get() = bool("birthLunarInput", false)
        set(v) = put("birthLunarInput", v)

    /** Các thành phố đã lưu; luôn chứa thành phố đang chọn ở đầu. */
    var cities: List<CityPref>
        get() {
            val list = mutableListOf<CityPref>()
            try {
                val a = JSONArray(sp.getString("cities", "[]"))
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    list += CityPref(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"))
                }
            } catch (e: Exception) { }
            val active = CityPref(city, lat, lon)
            return listOf(active) + list.filter { it.name != active.name }
        }
        set(v) {
            val a = JSONArray()
            v.forEach { a.put(JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon)) }
            sp.edit().putString("cities", a.toString()).apply()
        }

    /** Hình nền lịch: "none", tranh có sẵn (sen, halong, dao, ruong, cachep) hoặc "photo" (ảnh của người dùng). */
    var background: String
        get() = sp.getString("bg", "none") ?: "none"
        set(v) = sp.edit().putString("bg", v).apply()

    /** Tăng mỗi lần đổi ảnh nền cá nhân để trang biết tải lại ảnh. */
    var backgroundVersion: Int
        get() = sp.getInt("bgVer", 0)
        set(v) = sp.edit().putInt("bgVer", v).apply()

    /** Giao diện màn hình app: "system", "light" hoặc "dark". */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()

    /** Tự lấy sự kiện từ một lịch trên máy (lịch Google) vài giờ một lần và mỗi lần mở app. */
    var gcalAuto: Boolean
        get() = sp.getBoolean("gcalAuto", false)
        set(v) = sp.edit().putBoolean("gcalAuto", v).apply()

    /** Lịch trên máy dùng để tự lấy sự kiện (id trong CalendarContract), -1 nếu chưa chọn. */
    var gcalCalId: Long
        get() = sp.getLong("gcalCalId", -1L)
        set(v) = sp.edit().putLong("gcalCalId", v).apply()

    companion object {
        const val DEFAULT_LAT = 21.0285
        const val DEFAULT_LON = 105.8542
        const val DEFAULT_CITY = "Hà Nội"

        fun get(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences("lichthoitiet", Context.MODE_PRIVATE)
    }
}
