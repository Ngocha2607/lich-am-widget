package vn.lichthoitiet

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Màn hình chính: giao diện là trang HTML trong assets/index.html,
 * dữ liệu (lịch âm, thời tiết, gợi ý) do Kotlin tính và đưa sang qua cầu nối `Android`.
 */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var prefs: Prefs
    private var pageReady = false
    /** Thẻ cần cuộn tới khi mở từ widget (ví dụ "weather"). */
    private var pendingTarget: String? = null
    /** Nội dung chờ ghi ra file khi người dùng chọn nơi lưu. */
    private var pendingDoc: String? = null

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
                applyTarget()
            }
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
        pendingTarget = intent?.getStringExtra("target")

        if (WeatherRepo.load(this) == null) refreshWeather()
        Reminders.schedule(this)
        if (prefs.notifyEvents || prefs.notifyLunar || prefs.notifyWeather) ensureNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTarget = intent.getStringExtra("target")
        if (pageReady) applyTarget()
    }

    /** Cuộn trang tới thẻ tương ứng với vùng vừa chạm trên widget. */
    private fun applyTarget() {
        val card = when (pendingTarget) {
            "weather" -> "wxCard"
            "events" -> "evCard"
            else -> null
        }
        pendingTarget = null
        if (card != null) runOnUiThread { web.evaluateJavascript("window.scrollToCard && scrollToCard('$card')", null) }
    }

    private fun createDocument(mime: String, name: String, content: String) {
        pendingDoc = content
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, name)
        }
        startActivityForResult(i, REQ_EXPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_EXPORT -> {
                val content = pendingDoc ?: return
                pendingDoc = null
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                    status("Đã lưu file.")
                } catch (e: Exception) {
                    status("Không lưu được file: ${e.message}")
                }
            }
            REQ_IMPORT -> {
                try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                    val n = EventStore.importJson(this, text)
                    status("Đã nhập $n sự kiện.")
                    LichWidget.updateAll(this)
                    pushState()
                } catch (e: Exception) {
                    status("File không đúng định dạng sao lưu.")
                }
            }
        }
    }

    /** Android 13+ cần xin quyền hiện thông báo. */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
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

    private fun birthLabel(): String {
        val y = prefs.birthYear
        if (y <= 0) return ""
        return "${LunarCalendar.canChiYear(y)} ${CON_GIAP[Almanac.birthChi(y)]}"
    }

    private fun stateJson(): String {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val w = WeatherRepo.load(this)
        val tet = LunarCalendar.nextTet(today)
        return JSONObject().apply {
            put("today", JSONObject().put("y", now.year).put("m", now.monthValue).put("d", now.dayOfMonth))
            put("hour", now.hour)
            put("minute", now.minute)
            put("dark", isDark())
            put("version", versionName())
            put("tet", JSONObject()
                .put("date", tet.toString())
                .put("daysLeft", ChronoUnit.DAYS.between(today, tet))
                .put("name", "Tết " + LunarCalendar.canChiYear(LunarCalendar.convert(tet).year)))
            put("cities", JSONArray().apply {
                prefs.cities.forEach { put(JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon)) }
            })
            put("prefs", JSONObject()
                .put("city", prefs.city).put("spiritual", prefs.spiritual).put("theme", prefs.theme)
                .put("showAlmanac", prefs.showAlmanac).put("showLucDieu", prefs.showLucDieu)
                .put("showHuong", prefs.showHuong).put("showGoodDays", prefs.showGoodDays)
                .put("notifyEvents", prefs.notifyEvents).put("notifyLunar", prefs.notifyLunar).put("notifyHour", prefs.notifyHour)
                .put("notifyWeather", prefs.notifyWeather).put("notifyWeatherHour", prefs.notifyWeatherHour)
                .put("birthYear", prefs.birthYear).put("birthLabel", birthLabel()))
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
        val CON_GIAP = listOf("🐭", "🐮", "🐯", "🐱", "🐲", "🐍", "🐴", "🐐", "🐵", "🐔", "🐶", "🐷")
        const val REQ_EXPORT = 11
        const val REQ_IMPORT = 12
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
                put("moon", LunarCalendar.moonPhase(l.day))
                put("tuoi", if (prefs.birthYear > 0) Almanac.tuoiRelation(LunarCalendar.dayChiIndex(l.jd), Almanac.birthChi(prefs.birthYear)) else JSONObject.NULL)
                put("events", dayEvents)
                val a = Almanac.of(l)
                put("truc", a.truc); put("trucNen", JSONArray(a.nen)); put("trucKy", JSONArray(a.ky))
                put("napAm", a.napAm); put("nguHanh", a.nguHanh); put("xung", a.xung)
                put("hyThan", a.hyThan); put("taiThan", a.taiThan); put("lucDieuDay", a.lucDieuDay)
                put("lucDieu", JSONArray().apply {
                    a.lucDieu.forEach { put(JSONObject().put("name", it.name).put("hours", it.hours).put("good", it.good).put("desc", it.desc)) }
                })
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

        // ---- Xem ngày ----

        /** Ngày tốt cho một việc trong [days] ngày tới. */
        @JavascriptInterface
        fun goodDays(task: String, days: Int): String {
            val arr = JSONArray()
            val birthChi = if (prefs.birthYear > 0) Almanac.birthChi(prefs.birthYear) else null
            Almanac.goodDays(task, LocalDate.now(), days.coerceIn(7, 366), birthChi = birthChi).forEach { g ->
                val l = LunarCalendar.convert(g.date)
                arr.put(JSONObject().apply {
                    put("date", g.date.toString())
                    put("d", g.date.dayOfMonth); put("m", g.date.monthValue); put("y", g.date.year)
                    put("dow", SHORT_DOW[g.date.dayOfWeek.value - 1])
                    put("lunar", "${l.day}/${l.month} âm")
                    put("canChi", LunarCalendar.canChiDay(l.jd))
                    put("truc", g.truc); put("hoangDao", g.hoangDao)
                    put("score", g.score); put("reasons", JSONArray(g.reasons))
                })
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun setSections(almanac: Boolean, lucDieu: Boolean, huong: Boolean, goodDays: Boolean) {
            prefs.showAlmanac = almanac; prefs.showLucDieu = lucDieu
            prefs.showHuong = huong; prefs.showGoodDays = goodDays
        }

        // ---- Thông báo ----

        @JavascriptInterface
        fun setNotify(events: Boolean, lunar: Boolean, hour: Int) {
            prefs.notifyEvents = events
            prefs.notifyLunar = lunar
            prefs.notifyHour = hour.coerceIn(0, 23)
            Reminders.schedule(this@MainActivity)
            if (events || lunar) runOnUiThread { ensureNotificationPermission() }
        }

        @JavascriptInterface
        fun testNotify() = runOnUiThread {
            ensureNotificationPermission()
            Reminders.test(this@MainActivity)
        }

        @JavascriptInterface
        fun setNotifyWeather(on: Boolean, hour: Int) {
            prefs.notifyWeather = on
            prefs.notifyWeatherHour = hour.coerceIn(0, 23)
            Reminders.schedule(this@MainActivity)
            if (on) runOnUiThread { ensureNotificationPermission() }
        }

        // ---- Hồ sơ ----

        @JavascriptInterface
        fun setBirthYear(year: Int) {
            prefs.birthYear = if (year in 1900..2100) year else 0
            pushState()
        }

        // ---- Thành phố ----

        @JavascriptInterface
        fun addCity(lat: Double, lon: Double, name: String) {
            val c = CityPref(name, lat, lon)
            prefs.cities = prefs.cities.filter { it.name != name } + c
            WeatherRepo.setActive(this@MainActivity, c)
            pushState()
            refreshWeather()
        }

        @JavascriptInterface
        fun setActiveCity(name: String) {
            val c = prefs.cities.find { it.name == name } ?: return
            WeatherRepo.setActive(this@MainActivity, c)
            pushState()
            LichWidget.updateAll(this@MainActivity)
            val cached = WeatherRepo.load(this@MainActivity)
            if (cached == null || cached.isStale(60 * 60 * 1000L)) refreshWeather()
        }

        @JavascriptInterface
        fun removeCity(name: String) {
            if (name == prefs.city) return
            prefs.cities = prefs.cities.filter { it.name != name }
            pushState()
        }

        // ---- File: sao lưu, khôi phục, lịch Google ----

        @JavascriptInterface
        fun exportEvents() = runOnUiThread {
            createDocument("application/json", "lich-su-kien-${LocalDate.now()}.json", EventStore.exportJson(EventStore.load(this@MainActivity)))
        }

        @JavascriptInterface
        fun exportIcs() = runOnUiThread {
            createDocument("text/calendar", "su-kien-am-lich.ics", EventStore.toIcs(EventStore.load(this@MainActivity), LocalDate.now()))
        }

        @JavascriptInterface
        fun importEvents() = runOnUiThread {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(i, REQ_IMPORT)
        }

        // ---- Chia sẻ ----

        @JavascriptInterface
        fun shareToday() = runOnUiThread {
            try {
                ShareCard.share(this@MainActivity)
            } catch (e: Exception) {
                status("Không tạo được ảnh: ${e.message}")
            }
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
