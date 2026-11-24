package vn.lichthoitiet

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
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

    /** Nút Back: đóng màn Cài đặt nếu đang mở, không thì thoát app. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!pageReady) { super.onBackPressed(); return }
        web.evaluateJavascript("window.closeSettingsIfOpen ? closeSettingsIfOpen() : false") { r ->
            if (r != "true") finish()
        }
    }

    private fun isDark(): Boolean = when (prefs.theme) {
        "dark" -> true
        "light" -> false
        else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

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
            put("version", versionName())
            put("prefs", JSONObject().put("city", prefs.city).put("spiritual", prefs.spiritual).put("theme", prefs.theme))
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
            val events = EventStore.load(this@MainActivity)
            val days = JSONArray()
            for (d in 1..ym.lengthOfMonth()) {
                val date = LocalDate.of(y, m, d)
                val l = LunarCalendar.convert(date)
                val ev = EventStore.eventsOn(events, date)
                days.put(
                    JSONObject().put("d", d).put("ld", l.day).put("lm", l.month)
                        .put("leap", l.leap).put("hd", LunarCalendar.isHoangDao(l))
                        .put("ev", ev.firstOrNull()?.icon ?: ""),
                )
            }
            return JSONObject().put("firstWeekday", ym.atDay(1).dayOfWeek.value).put("days", days).toString()
        }

        private fun eventJson(e: Event): JSONObject = e.toJson().put("dateLabel", e.dateLabel)

        @JavascriptInterface
        fun day(y: Int, m: Int, d: Int): String {
            val date = LocalDate.of(y, m, d)
            val today = LocalDate.now()
            val now = LocalDateTime.now()
            val l = LunarCalendar.convert(date)
            val isToday = date == today
            val events = EventStore.load(this@MainActivity)
            val tips = if (isToday) {
                SuggestionEngine.build(date, now, WeatherRepo.load(this@MainActivity), prefs.spiritual, EventStore.upcoming(events, today))
            } else {
                SuggestionEngine.build(date, date.atTime(9, 0), null, prefs.spiritual)
            }
            val dayEvents = JSONArray().apply { EventStore.eventsOn(events, date).forEach { put(eventJson(it)) } }
            val gio = JSONArray()
            LunarCalendar.gioHoangDaoList(l.jd).forEach { (chi, from, to) ->
                gio.put(JSONObject().put("chi", chi).put("from", from).put("to", to))
            }
            val tipsJson = JSONArray()
            tips.forEach { tipsJson.put(JSONObject().put("short", it.short).put("detail", it.detail).put("kind", it.kind.name)) }

            return JSONObject().apply {
                put("y", y); put("m", m); put("d", d)
                put("weekday", WidgetRenderer.dayOfWeek(date))
                put("ld", l.day); put("lm", l.month); put("ly", l.year); put("leap", l.leap)
                put("events", dayEvents)
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

        // ---- Sự kiện ----

        /** Các lần diễn ra trong [days] ngày tới, kèm thông tin sự kiện để sửa. */
        @JavascriptInterface
        fun upcoming(days: Int): String {
            val today = LocalDate.now()
            val arr = JSONArray()
            EventStore.upcoming(EventStore.load(this@MainActivity), today, days.coerceIn(1, 366)).forEach { u ->
                arr.put(
                    eventJson(u.event)
                        .put("date", u.date.toString())
                        .put("daysLeft", u.daysLeft)
                        .put("solarLabel", "${WidgetRenderer.dayOfWeek(u.date)} ${u.date.dayOfMonth}/${u.date.monthValue}"),
                )
            }
            return arr.toString()
        }

        /** Lưu (thêm hoặc sửa) sự kiện từ JSON; trả về id. */
        @JavascriptInterface
        fun saveEvent(json: String): String {
            val e = Event.fromJson(JSONObject(json))
            if (e.title.isBlank()) return ""
            EventStore.upsert(this@MainActivity, e)
            LichWidget.updateAll(this@MainActivity)
            return e.id
        }

        @JavascriptInterface
        fun deleteEvent(id: String) {
            EventStore.delete(this@MainActivity, id)
            LichWidget.updateAll(this@MainActivity)
        }

        /** Ngày dương của một ngày âm, dạng "2026-11-23", hoặc "" nếu năm đó không có ngày này. */
        @JavascriptInterface
        fun lunarToSolar(d: Int, m: Int, y: Int): String =
            LunarCalendar.toSolar(d, m, y)?.toString() ?: ""

        /** Tìm thành phố; kết quả trả về trang qua `onCityResults([...])`. */
        @JavascriptInterface
        fun searchCity(query: String) {
            Thread {
                val list = try { WeatherRepo.geocodeList(query) } catch (e: Exception) { emptyList() }
                val arr = JSONArray()
                list.forEach {
                    arr.put(JSONObject().put("name", it.name).put("region", it.region).put("lat", it.lat).put("lon", it.lon))
                }
                runOnUiThread { web.evaluateJavascript("window.onCityResults && onCityResults($arr)", null) }
            }.start()
        }

        @JavascriptInterface
        fun setCity(lat: Double, lon: Double, name: String) {
            prefs.lat = lat
            prefs.lon = lon
            prefs.city = name
            refreshWeather()
        }

        @JavascriptInterface
        fun setSpiritual(on: Boolean) {
            prefs.spiritual = on
            LichWidget.updateAll(this@MainActivity)
        }

        @JavascriptInterface
        fun setTheme(mode: String) {
            prefs.theme = if (mode in listOf("light", "dark")) mode else "system"
            pushState()
        }

        @JavascriptInterface
        fun refresh() = refreshWeather()

        /** Mở hộp thoại hệ thống để ghim widget ra màn hình chính. */
        @JavascriptInterface
        fun pinWidget() = runOnUiThread {
            val mgr = AppWidgetManager.getInstance(this@MainActivity)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(this@MainActivity, LichWidget::class.java), null, null)
            } else {
                status("Máy không hỗ trợ thêm nhanh. Giữ lâu màn hình chính → Widget → Lịch âm & Thời tiết.")
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            if (url.startsWith("https://")) runOnUiThread { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }
}
