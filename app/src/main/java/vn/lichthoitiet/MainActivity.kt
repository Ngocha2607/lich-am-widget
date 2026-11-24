package vn.lichthoitiet

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Màn hình chính: giao diện là trang HTML trong assets/index.html,
 * dữ liệu (lịch âm, thời tiết, gợi ý) do Kotlin tính và đưa sang qua cầu nối `Android`.
 */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var prefs: Prefs
    private var pageReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.setBackgroundColor(Color.TRANSPARENT)
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                pushState()
            }
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")

        if (WeatherRepo.load(this) == null) refreshWeather()
    }

    override fun onResume() {
        super.onResume()
        if (pageReady) pushState()
    }

    private fun isDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun pushState() = runOnUiThread {
        web.evaluateJavascript("window.onState && onState(${stateJson()})", null)
    }

    private fun status(msg: String) = runOnUiThread {
        web.evaluateJavascript("window.onStatus && onStatus(${JSONObject.quote(msg)})", null)
    }

    private fun stateJson(): String {
        val now = LocalDateTime.now()
        val w = WeatherRepo.load(this)
        return JSONObject().apply {
            put("today", JSONObject().put("y", now.year).put("m", now.monthValue).put("d", now.dayOfMonth))
            put("hour", now.hour)
            put("minute", now.minute)
            put("dark", isDark())
            put("prefs", JSONObject().put("city", prefs.city).put("spiritual", prefs.spiritual))
            put("weather", if (w == null) JSONObject.NULL else weatherJson(w))
        }.toString()
    }

    private fun weatherJson(w: WeatherData): JSONObject = JSONObject().apply {
        val today = LocalDate.now()
        put("city", w.city)
        put("updated", String.format("%tH:%<tM", w.fetchedAt))
        put("tempNow", w.tempNow); put("feelsLike", w.feelsLike); put("humidity", w.humidity)
        put("iconNow", WeatherText.icon(w.codeNow)); put("labelNow", WeatherText.label(w.codeNow))
        put("windKmh", w.windNow); put("windDir", WeatherText.windDir(w.windDir)); put("beaufort", WeatherText.beaufort(w.windNow))
        put("pressure", w.pressure); put("visibility", w.visibility)
        put("uv", w.todayUv.toInt()); put("uvLabel", WeatherText.uvLabel(w.todayUv))
        put("daily", JSONArray().apply {
            w.daily.forEachIndexed { i, d ->
                val date = try { LocalDate.parse(d.date) } catch (e: Exception) { today.plusDays(i.toLong()) }
                put(JSONObject().apply {
                    put("date", d.date)
                    put("label", if (date == today) "Hôm nay" else SHORT_DOW[date.dayOfWeek.value - 1])
                    put("icon", WeatherText.icon(d.code)); put("text", WeatherText.label(d.code))
                    put("max", d.max); put("min", d.min); put("rain", d.rain)
                    put("sunrise", d.sunrise); put("sunset", d.sunset)
                })
            }
        })
    }

    private companion object {
        val SHORT_DOW = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    }

    private fun refreshWeather() {
        status("Đang tải thời tiết...")
        Thread {
            val err = try { WeatherRepo.refresh(this); null } catch (e: Exception) { e.message ?: "lỗi mạng" }
            status(if (err == null) "Đã cập nhật thời tiết." else "Không tải được thời tiết ($err).")
            pushState()
            LichWidget.updateAll(this)
        }.start()
    }

    /** Các hàm trang HTML gọi được qua `Android.*`. Chạy trên luồng riêng của WebView. */
    inner class Bridge {
        @JavascriptInterface
        fun month(y: Int, m: Int): String {
            val ym = YearMonth.of(y, m)
            val days = JSONArray()
            for (d in 1..ym.lengthOfMonth()) {
                val l = LunarCalendar.convert(LocalDate.of(y, m, d))
                days.put(
                    JSONObject().put("d", d).put("ld", l.day).put("lm", l.month)
                        .put("leap", l.leap).put("hd", LunarCalendar.isHoangDao(l)),
                )
            }
            return JSONObject().put("firstWeekday", ym.atDay(1).dayOfWeek.value).put("days", days).toString()
        }

        @JavascriptInterface
        fun day(y: Int, m: Int, d: Int): String {
            val date = LocalDate.of(y, m, d)
            val today = LocalDate.now()
            val now = LocalDateTime.now()
            val l = LunarCalendar.convert(date)
            val isToday = date == today
            val tips = if (isToday) {
                SuggestionEngine.build(date, now, WeatherRepo.load(this@MainActivity), prefs.spiritual)
            } else {
                SuggestionEngine.build(date, date.atTime(9, 0), null, prefs.spiritual)
            }
            val gio = JSONArray()
            LunarCalendar.gioHoangDaoList(l.jd).forEach { (chi, from, to) ->
                gio.put(JSONObject().put("chi", chi).put("from", from).put("to", to))
            }
            val tipsJson = JSONArray()
            tips.forEach { tipsJson.put(JSONObject().put("short", it.short).put("detail", it.detail).put("kind", it.kind.name)) }

            return JSONObject().apply {
                put("y", y); put("m", m); put("d", d)
                put("weekday", WidgetRenderer.dayOfWeek(date))
                put("ld", l.day); put("lm", l.month); put("leap", l.leap)
                put("canChiYear", LunarCalendar.canChiYear(l.year))
                put("canChiMonth", LunarCalendar.canChiMonth(l))
                put("canChiDay", LunarCalendar.canChiDay(l.jd))
                put("canChiHour", LunarCalendar.canChiHour(l.jd, now.hour))
                put("tietKhi", LunarCalendar.currentTietKhi(l.jd))
                put("hoangDao", LunarCalendar.isHoangDao(l))
                put("trucThan", LunarCalendar.trucThan(l))
                put("gioTot", gio)
                put("tips", tipsJson)
                put("isToday", isToday)
                put("isTomorrow", date == today.plusDays(1))
            }.toString()
        }

        @JavascriptInterface
        fun saveCity(name: String) {
            Thread {
                val geo = try { WeatherRepo.geocode(name) } catch (e: Exception) { null }
                if (geo == null) {
                    status("Không tìm thấy địa điểm. Thử gõ không dấu, ví dụ: Ha Noi, Da Nang.")
                } else {
                    prefs.lat = geo.lat
                    prefs.lon = geo.lon
                    prefs.city = geo.name
                    refreshWeather()
                }
            }.start()
        }

        @JavascriptInterface
        fun setSpiritual(on: Boolean) {
            prefs.spiritual = on
            LichWidget.updateAll(this@MainActivity)
        }

        @JavascriptInterface
        fun refresh() = refreshWeather()
    }
}
