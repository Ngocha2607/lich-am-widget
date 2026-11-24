package vn.lichthoitiet

import android.content.Context
import android.content.SharedPreferences

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

    /** Giao diện màn hình app: "system", "light" hoặc "dark". */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()

    companion object {
        const val DEFAULT_LAT = 21.0285
        const val DEFAULT_LON = 105.8542
        const val DEFAULT_CITY = "Hà Nội"

        fun get(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences("lichthoitiet", Context.MODE_PRIVATE)
    }
}
