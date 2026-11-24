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

    companion object {
        const val DEFAULT_LAT = 21.0285
        const val DEFAULT_LON = 105.8542
        const val DEFAULT_CITY = "Hà Nội"

        fun get(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences("lichthoitiet", Context.MODE_PRIVATE)
    }
}
