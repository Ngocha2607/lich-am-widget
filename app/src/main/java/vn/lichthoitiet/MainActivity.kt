package vn.lichthoitiet

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ClipboardManager
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
    /** Vừa xin quyền thông báo vì người dùng bật lịch màn hình khóa (để mở cài đặt nếu bị từ chối hẳn). */
    private var lockNoteAsked = false
    /** Lần mở app này có nên vào thẳng chế độ Zen không (chỉ khi mở mới, không mở từ vùng chạm widget). */
    private var zenOnLaunch = false

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
            // Không cho trang điều hướng ra ngoài (trang ngoài sẽ có cầu nối Android.*)
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                !request.url.toString().startsWith("file:///android_asset/")

            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                pushState()
                applyTarget()
            }
        }
        setContentView(web)
        applySystemBars()
        web.loadUrl("file:///android_asset/index.html")
        // Dựng lại activity (đổi ngôn ngữ, cỡ chữ máy…) thì không cuộn lại theo intent cũ, không hỏi quyền lại
        pendingTarget = if (savedInstanceState == null) intent?.getStringExtra("target") else null
        zenOnLaunch = savedInstanceState == null && pendingTarget == null

        if (WeatherRepo.load(this) == null) refreshWeather()
        Reminders.schedule(this)
        if (savedInstanceState == null && (prefs.notifyEvents || prefs.notifyLunar || prefs.notifyWeather)) ensureNotificationPermission()
    }

    override fun onDestroy() {
        (web.parent as? android.view.ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    /** Mở màn hình của app khác / hệ thống; máy không có app phù hợp thì báo thay vì sập. */
    private fun tryStart(i: Intent, request: Int? = null) {
        try {
            @Suppress("DEPRECATION")
            if (request != null) startActivityForResult(i, request) else startActivity(i)
        } catch (e: android.content.ActivityNotFoundException) {
            status("Máy không có ứng dụng để mở mục này.")
        }
    }

    /** Thanh trạng thái, thanh điều hướng cùng màu nền trang theo giao diện sáng / tối đang dùng. */
    private fun applySystemBars() {
        val dark = isDark()
        val bg = if (dark) 0xFF0F1115.toInt() else 0xFFEEF2F7.toInt()
        @Suppress("DEPRECATION")
        window.statusBarColor = bg
        @Suppress("DEPRECATION")
        window.navigationBarColor = bg
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    /** Xoay màn hình, đổi giao diện sáng / tối: giữ nguyên trang (tháng đang xem, nội dung đang nhập), chỉ báo trạng thái mới. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars()
        if (pageReady) pushState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTarget = intent.getStringExtra("target")
        if (pageReady) applyTarget()
    }

    /** Màn hình thêm tài khoản Google của máy (dự phòng khi không mở được bảng chọn tài khoản). */
    private fun openAddGoogleAccount() {
        val add = Intent(android.provider.Settings.ACTION_ADD_ACCOUNT)
            .putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf(CalendarSync.GOOGLE))
        try {
            startActivity(add)
        } catch (e: Exception) {
            tryStart(Intent(android.provider.Settings.ACTION_SYNC_SETTINGS))
        }
    }

    /** Tài khoản Google đã biết: đã chọn trong app, app được thấy, hoặc có lịch trên máy. */
    private fun googleAccounts(): LinkedHashSet<String> {
        val names = LinkedHashSet<String>()
        prefs.gcalAccount.takeIf { it.isNotBlank() }?.let { names += it }
        try {
            android.accounts.AccountManager.get(this).getAccountsByType(CalendarSync.GOOGLE).forEach { names += it.name }
        } catch (e: Exception) { }
        if (CalendarSync.hasPermission(this)) {
            try {
                CalendarSync.calendars(this).filter { it.accountType == CalendarSync.GOOGLE }.forEach { names += it.account }
            } catch (e: Exception) { }
        }
        return names
    }

    /** Vừa chọn tài khoản Google: nhớ tài khoản, bật đồng bộ Lịch nếu đang tắt (người dùng chủ động kết nối) rồi đồng bộ ngay. */
    private fun connectGoogle(name: String) {
        prefs.gcalAccount = name
        Thread {
            when {
                !CalendarSync.hasGoogleCalendarAdapter(this) ->
                    status(
                        if (CalendarSync.versionOf(this, CalendarSync.GCAL_APP) == null) "Máy chưa có phần đồng bộ Lịch Google. Cài ứng dụng Lịch Google rồi quay lại."
                        else "Máy chưa có bộ đồng bộ Lịch Google nên lịch chưa về máy được.",
                    )
                CalendarSync.syncOn(name) == false && !CalendarSync.enableSync(name) ->
                    status("Máy đang tắt tự đồng bộ dữ liệu. Bật lên để lấy lịch của $name.")
                else -> {
                    CalendarSync.requestSync(name)
                    status("Đã kết nối $name, đang lấy lịch về máy…")
                }
            }
            runOnUiThread { web.evaluateJavascript("window.onGoogleAccount && onGoogleAccount(${JSONObject.quote(name)})", null) }
        }.start()
    }

    /** Cuộn trang tới thẻ tương ứng với vùng vừa chạm trên widget. */
    private fun applyTarget() {
        val card = when (pendingTarget) {
            "weather" -> "wxCard"
            "events" -> "evCard"
            else -> null
        }
        val today = pendingTarget == "today"
        pendingTarget = null
        if (card != null) runOnUiThread { web.evaluateJavascript("window.scrollToCard && scrollToCard('$card')", null) }
        if (today) runOnUiThread { web.evaluateJavascript("window.openToday && openToday()", null) }
    }

    private fun createDocument(mime: String, name: String, content: String) {
        pendingDoc = content
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, name)
        }
        tryStart(i, REQ_EXPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ACCOUNT) {
            val name = data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (resultCode == RESULT_OK && !name.isNullOrBlank()) connectGoogle(name)
            return
        }
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
            REQ_BG -> {
                status("Đang xử lý ảnh…")
                Thread {
                    // Ảnh quá lớn có thể hết bộ nhớ khi giải mã: báo lỗi thay vì làm sập app
                    val ok = try { BackgroundImage.saveFrom(this, uri) } catch (e: Throwable) { false }
                    if (ok) {
                        prefs.background = "photo"
                        prefs.backgroundVersion = prefs.backgroundVersion + 1
                        pushState()
                        status("Đã đặt ảnh làm hình nền.")
                    } else {
                        status("Không đọc được ảnh này.")
                    }
                }.start()
            }
            REQ_IMPORT -> {
                try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                    val n = EventStore.importJson(this, text)
                    val notes = try { JSONObject(text.trim()).optJSONObject("notes") } catch (e: Exception) { null }
                    val nn = if (notes != null) NoteStore.importAll(this, notes) else 0
                    val fams = try { JSONObject(text.trim()).optJSONArray("families") } catch (e: Exception) { null }
                    val nf = if (fams != null) FamilyStore.importAll(this, fams) else 0
                    val parts = mutableListOf("$n sự kiện")
                    if (nn > 0) parts += "$nn ghi chú"
                    if (nf > 0) parts += "$nf sổ giỗ"
                    status("Đã nhập " + parts.joinToString(", ") + ".")
                    LichWidget.updateAll(this)
                    pushState()
                } catch (e: Exception) {
                    status("File không đúng định dạng sao lưu.")
                }
            }
        }
    }

    /** Kết quả xin quyền lịch: báo trang để tải lại danh sách lịch. */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Vừa được cho phép thông báo: hiện ngay lịch trên màn hình khóa nếu đã bật.
        // Bị từ chối hẳn (hệ thống không hỏi nữa) khi đang bật lịch màn hình khóa: mở cài đặt thông báo của app.
        if (requestCode == 1) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (!granted && lockNoteAsked && Build.VERSION.SDK_INT >= 33 &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) openNotificationSettingsScreen()
            lockNoteAsked = false
            LockScreenDay.update(this)
            if (pageReady) pushState()
        }
        if (requestCode == REQ_CAL) {
            val ok = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            web.evaluateJavascript("window.onCalendarPermission && onCalendarPermission($ok)", null)
        }
    }

    private fun notificationsAllowed(): Boolean {
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return false
        val ch = nm.getNotificationChannel(LockScreenDay.CHANNEL_ID)
        return nm.areNotificationsEnabled() && (ch == null || ch.importance != android.app.NotificationManager.IMPORTANCE_NONE)
    }

    private fun openNotificationSettingsScreen() {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        val channelOff = nm != null && nm.areNotificationsEnabled() &&
            nm.getNotificationChannel(LockScreenDay.CHANNEL_ID)?.importance == android.app.NotificationManager.IMPORTANCE_NONE
        try {
            // Chỉ kênh "lịch màn hình khóa" bị tắt: mở thẳng trang của kênh đó
            if (channelOff) {
                startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, LockScreenDay.CHANNEL_ID))
                return
            }
            startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
        } catch (e: Exception) {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
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
        LockScreenDay.update(this)
        if (pageReady) pushState()
        // Tự lấy sự kiện mới từ lịch Google nếu đã bật (tối đa 30 phút một lần)
        Thread {
            try {
                if (CalendarSync.autoImport(this@MainActivity) >= 0) {
                    if (pageReady) pushState()
                    LichWidget.updateAll(this@MainActivity)
                }
            } catch (e: Exception) { }
        }.start()
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

    /** Ngày sinh cả hai lịch để trang hiện và điền sẵn khi sửa; null nếu chưa nhập. */
    private fun birthJson(): Any {
        val solar = try { LocalDate.parse(prefs.birthDate) } catch (e: Exception) { return JSONObject.NULL }
        val l = LunarCalendar.convert(solar)
        return JSONObject()
            .put("sd", solar.dayOfMonth).put("sm", solar.monthValue).put("sy", solar.year)
            .put("ld", l.day).put("lm", l.month).put("ly", l.year).put("leap", l.leap)
            .put("lunarInput", prefs.birthLunarInput)
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
            put("zenLaunch", zenOnLaunch && pageReady)
            if (pageReady) zenOnLaunch = false
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
                .put("birthYear", prefs.birthYear).put("birthLabel", birthLabel())
                .put("name", prefs.profileName).put("birth", birthJson())
                .put("bg", prefs.background).put("bgVer", prefs.backgroundVersion)
                .put("lockNote", Prefs.get(this@MainActivity).getBoolean(LockScreenDay.PREF, false))
                .put("notifAllowed", notificationsAllowed())
                .put("wpText", Prefs.get(this@MainActivity).getBoolean("wpText", true))
                .put("wpActive", liveWallpaperActive())
                .put("zenStart", Prefs.get(this@MainActivity).getBoolean("zenStart", false))
                .put("gcalAuto", prefs.gcalAuto).put("gcalCal", prefs.gcalCalId.toString())
                .put("zenPaper", Prefs.get(this@MainActivity).getString("zenPaper", "auto") ?: "auto"))
            put("weather", if (w == null) JSONObject.NULL else weatherJson(w))
        }.toString()
    }

    /** Hình nền đang dùng có phải hình nền động của app không. */
    private fun liveWallpaperActive(): Boolean = try {
        WallpaperManager.getInstance(this).wallpaperInfo?.packageName == packageName
    } catch (e: Exception) {
        false
    }

    /** Sổ giỗ dạng JSON cho trang: thêm ngày dương lần giỗ tới và số ngày còn lại. */
    private fun familyJson(g: FamilyGroup): JSONObject {
        val today = LocalDate.now()
        val j = g.toJson()
        val arr = JSONArray()
        g.active.sortedWith(compareBy({ it.lunarMonth }, { it.lunarDay })).forEach { m ->
            val o = m.toJson()
            FamilyBook.nextDate(m, today)?.let { d ->
                o.put("next", "${d.dayOfMonth}/${d.monthValue}/${d.year}").put("daysLeft", ChronoUnit.DAYS.between(today, d))
            }
            o.put("title", m.eventTitle)
            arr.put(o)
        }
        return j.put("members", arr)
    }

    private fun weatherJson(w: WeatherData): JSONObject = JSONObject().apply {
        val today = LocalDate.now()
        put("city", w.city)
        put("updated", String.format("%tH:%<tM", w.fetchedAt))
        put("tempNow", w.tempNow); put("feelsLike", w.feelsLike); put("humidity", w.humidity)
        put("iconNow", WeatherText.icon(w.codeNow)); put("labelNow", WeatherText.label(w.codeNow)); put("codeNow", w.codeNow)
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
        const val REQ_CAL = 13
        const val REQ_BG = 14
        const val REQ_ACCOUNT = 15
        val BACKGROUNDS = setOf("none", "sen", "halong", "dao", "ruong", "cachep", "photo")
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
            val events = Profile.eventsWithBirthday(this@MainActivity)
            val openNotes = NoteStore.openCountsInMonth(this@MainActivity, "%04d-%02d".format(y, m))
            val days = JSONArray()
            for (d in 1..ym.lengthOfMonth()) {
                val date = LocalDate.of(y, m, d)
                val l = LunarCalendar.convert(date)
                val ev = EventStore.eventsOn(events, date)
                days.put(
                    JSONObject().put("d", d).put("ld", l.day).put("lm", l.month)
                        .put("leap", l.leap).put("hd", LunarCalendar.isHoangDao(l))
                        .put("ev", ev.firstOrNull()?.icon ?: "").put("evn", ev.size)
                        .put("nt", openNotes[date.toString()] ?: 0),
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
            val events = Profile.eventsWithBirthday(this@MainActivity)
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
                put("tu", a.tu.name); put("tuFull", a.tu.fullName); put("tuGood", a.tu.good)
                put("tuNen", a.tu.nen); put("tuKy", a.tu.ky)
                val q = Quotes.forDate(date)
                put("quote", q.text); put("quoteKind", q.kind)
                val z = ZenVerses.forDate(date)
                put("zen", JSONObject().put("text", z.text).put("by", z.by))
                put("ngocHap", JSONArray().apply {
                    NgocHap.of(l).forEach { put(JSONObject().put("name", it.name).put("good", it.good).put("desc", it.desc)) }
                })
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

        // ---- Tử vi cơ bản ----

        /** Cung hoàng đạo, con giáp, mệnh theo ngày sinh trong hồ sơ; "null" nếu chưa nhập ngày sinh. */
        @JavascriptInterface
        fun tuvi(): String {
            val birth = try { LocalDate.parse(prefs.birthDate) } catch (e: Exception) { return "null" }
            val h = Horoscope.basic(birth)
            return JSONObject()
                .put("sign", h.sign.name).put("symbol", h.sign.symbol).put("element", h.sign.element)
                .put("range", h.sign.range).put("traits", h.sign.traits)
                .put("canChi", h.canChi).put("animal", h.animal).put("menh", h.menh).put("hanh", h.hanh).toString()
        }

        /** Tuổi xông đất Tết sắp tới cho gia chủ sinh năm âm [owner]. */
        @JavascriptInterface
        fun xongDat(owner: Int): String {
            val year = LunarCalendar.convert(LunarCalendar.nextTet(LocalDate.now())).year
            val r = Horoscope.xongDat(owner, year)
            val list = JSONArray()
            r.picks.forEach { p ->
                list.put(JSONObject().put("year", p.year).put("canChi", p.canChi).put("score", p.score).put("label", p.label)
                    .put("reasons", JSONArray(p.reasons)).put("warnings", JSONArray(p.warnings)))
            }
            return JSONObject().put("year", r.year).put("canChi", r.canChi).put("avoid", JSONArray(r.avoid)).put("list", list).toString()
        }

        // ---- Lịch trên máy (gồm lịch Google đã đồng bộ) ----

        /** Danh sách lịch trên máy; chưa có quyền thì xin quyền và trả "null" (trang chờ onCalendarPermission). */
        @JavascriptInterface
        fun calendarList(): String {
            if (!CalendarSync.hasPermission(this@MainActivity)) {
                runOnUiThread { requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), REQ_CAL) }
                return "null"
            }
            val arr = JSONArray()
            try {
                val sync = HashMap<String, Boolean?>()
                CalendarSync.calendars(this@MainActivity).forEach {
                    val o = JSONObject().put("id", it.id.toString()).put("name", it.name).put("account", it.account).put("type", it.accountType)
                        .put("color", String.format("#%06X", it.color and 0xFFFFFF)).put("writable", it.writable)
                    if (it.accountType == CalendarSync.GOOGLE) o.put("sync", sync.getOrPut(it.account) { CalendarSync.syncOn(it.account) } ?: JSONObject.NULL)
                    arr.put(o)
                }
            } catch (e: Exception) {
                status("Không đọc được danh sách lịch: ${e.message}")
            }
            return arr.toString()
        }

        /** Nhập sự kiện 1 năm tới từ lịch [calId] vào app (chạy nền). */
        @JavascriptInterface
        fun importCalendar(calId: String) {
            Thread {
                try {
                    val n = CalendarSync.importFrom(this@MainActivity, calId.toLong(), LocalDate.now(), 365)
                    status(if (n > 0) "Đã nhập $n sự kiện từ lịch trên máy." else "Lịch này không có sự kiện nào trong 1 năm tới.")
                    pushState()
                    LichWidget.updateAll(this@MainActivity)
                } catch (e: Exception) {
                    status("Không nhập được: ${e.message}")
                }
            }.start()
        }

        /** Xuất sự kiện của app (5 năm tới) sang lịch [calId]; thay các mục đã xuất lần trước (chạy nền). */
        @JavascriptInterface
        fun exportCalendar(calId: String) {
            Thread {
                try {
                    val n = CalendarSync.exportTo(this@MainActivity, calId.toLong(), LocalDate.now(), 5)
                    status(if (n > 0) "Đã xuất $n mục sang lịch đã chọn. Lịch Google sẽ tự đồng bộ." else "Chưa có sự kiện nào để xuất.")
                } catch (e: Exception) {
                    status("Không xuất được: ${e.message}")
                }
            }.start()
        }

        /** Bảng chọn tài khoản Google của máy: tài khoản đã có trên máy và mục "Thêm tài khoản" (đăng nhập bằng màn hình của Google). */
        @JavascriptInterface
        fun chooseGoogleAccount() = runOnUiThread {
            val pick = android.accounts.AccountManager.newChooseAccountIntent(null, null, arrayOf(CalendarSync.GOOGLE), null, null, null, null)
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(pick, REQ_ACCOUNT)
            } catch (e: Exception) {
                openAddGoogleAccount()
            }
        }

        /**
         * Tài khoản Google đã biết (đã chọn trong app, app được thấy, hoặc có lịch trên máy), trạng thái đồng bộ Lịch,
         * máy có bộ đồng bộ Lịch Google không, đã cài ứng dụng Lịch Google chưa.
         */
        @JavascriptInterface
        fun googleStatus(): String {
            val arr = JSONArray()
            googleAccounts().forEach { n -> arr.put(JSONObject().put("name", n).put("sync", CalendarSync.syncOn(n) ?: JSONObject.NULL)) }
            return JSONObject().put("adapter", CalendarSync.hasGoogleCalendarAdapter(this@MainActivity))
                .put("app", CalendarSync.versionOf(this@MainActivity, CalendarSync.GCAL_APP) != null).put("accounts", arr).toString()
        }

        /** Thông tin kiểm tra vì sao lịch Google chưa về máy (hiện trong bảng Google Calendar, sao chép được). */
        @JavascriptInterface
        fun googleDiag(): String = try {
            CalendarSync.diagnostics(this@MainActivity, googleAccounts())
        } catch (e: Exception) {
            "Không lấy được thông tin: ${e.message}"
        }

        /** Mở ứng dụng Lịch Google; chưa cài thì mở trang CH Play. */
        @JavascriptInterface
        fun openGoogleCalendar() = runOnUiThread {
            val i = packageManager.getLaunchIntentForPackage(CalendarSync.GCAL_APP)
            if (i != null) tryStart(i) else installGoogleCalendar()
        }

        /** Yêu cầu đồng bộ ngay lịch của mọi tài khoản Google đã biết. */
        @JavascriptInterface
        fun syncGoogleAll() {
            val names = googleAccounts()
            names.forEach { CalendarSync.requestSync(it) }
            status(if (names.isEmpty()) "Chưa có tài khoản Google nào." else "Đang đồng bộ lịch của ${names.size} tài khoản Google…")
        }

        /** Chép [text] vào bộ nhớ tạm. */
        @JavascriptInterface
        fun copyText(text: String) = runOnUiThread {
            try {
                getSystemService(ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("Lịch & Thời tiết", text))
                if (Build.VERSION.SDK_INT < 33) status("Đã sao chép.")   // Android 13+ tự báo
            } catch (e: Exception) {
                status("Không sao chép được.")
            }
        }

        /** Mở trang ứng dụng Lịch Google trên CH Play (bộ đồng bộ Lịch Google đi kèm ứng dụng này). */
        @JavascriptInterface
        fun installGoogleCalendar() = runOnUiThread {
            val id = "com.google.android.calendar"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
            } catch (e: Exception) {
                tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")))
            }
        }

        /** Cài đặt tài khoản và đồng bộ của máy (bật Lịch cho tài khoản Google). */
        @JavascriptInterface
        fun openAccountSettings() = runOnUiThread { tryStart(Intent(android.provider.Settings.ACTION_SYNC_SETTINGS)) }

        /** Đồng bộ ngay lịch của tài khoản Google [account], đợi một lúc rồi lấy sự kiện mới nếu đang bật tự lấy. */
        @JavascriptInterface
        fun syncGoogle(account: String) {
            if (!CalendarSync.requestSync(account)) { status("Không đồng bộ được. Thử trong Cài đặt máy → Tài khoản → Google."); return }
            status("Đang đồng bộ lịch Google…")
            Thread {
                try {
                    Thread.sleep(8000)
                    val n = CalendarSync.autoImport(this@MainActivity, force = true)
                    if (n >= 0) {
                        status("Đã cập nhật $n sự kiện từ lịch Google.")
                        pushState()
                        LichWidget.updateAll(this@MainActivity)
                    }
                } catch (e: Exception) { }
            }.start()
        }

        /** Bật đồng bộ Lịch cho tài khoản Google [account]; máy đang tắt tự đồng bộ thì mở cài đặt để người dùng bật. */
        @JavascriptInterface
        fun enableGoogleSync(account: String) {
            if (CalendarSync.enableSync(account)) { status("Đã bật đồng bộ Lịch cho $account."); return }
            status("Máy đang tắt tự đồng bộ dữ liệu. Bật lên rồi quay lại app.")
            runOnUiThread { try { startActivity(Intent(android.provider.Settings.ACTION_SYNC_SETTINGS)) } catch (e: Exception) { } }
        }

        /** Bật / tắt tự lấy sự kiện từ lịch [calId] (vài giờ một lần và mỗi lần mở app). */
        @JavascriptInterface
        fun setCalendarAuto(on: Boolean, calId: String) {
            calId.toLongOrNull()?.let { prefs.gcalCalId = it }
            prefs.gcalAuto = on
            if (!on) { status("Đã tắt tự lấy sự kiện từ lịch."); return }
            Thread {
                try {
                    val n = CalendarSync.autoImport(this@MainActivity, force = true)
                    status(if (n >= 0) "Đã bật. Lấy được $n sự kiện, app sẽ tự cập nhật vài giờ một lần." else "Đã bật tự lấy sự kiện.")
                    pushState()
                    LichWidget.updateAll(this@MainActivity)
                } catch (e: Exception) {
                    status("Không nhập được: ${e.message}")
                }
            }.start()
        }

        // ---- Ghi chú theo ngày ----

        private fun iso(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toString()

        @JavascriptInterface
        fun notes(y: Int, m: Int, d: Int): String = NoteStore.toJsonArray(NoteStore.forDate(this@MainActivity, iso(y, m, d))).toString()

        @JavascriptInterface
        fun addNote(y: Int, m: Int, d: Int, text: String): String =
            NoteStore.toJsonArray(NoteStore.add(this@MainActivity, iso(y, m, d), text)).toString()

        @JavascriptInterface
        fun toggleNote(y: Int, m: Int, d: Int, id: String): String =
            NoteStore.toJsonArray(NoteStore.toggle(this@MainActivity, iso(y, m, d), id)).toString()

        @JavascriptInterface
        fun deleteNote(y: Int, m: Int, d: Int, id: String): String =
            NoteStore.toJsonArray(NoteStore.delete(this@MainActivity, iso(y, m, d), id)).toString()

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

        /** Kim Lâu, Hoang Ốc, Tam Tai, Thái Tuế của 5 năm âm lịch từ năm nay cho người sinh năm âm [birthYear]. */
        @JavascriptInterface
        fun tuoi(birthYear: Int): String {
            val now = LunarCalendar.convert(LocalDate.now()).year
            val arr = JSONArray()
            if (birthYear !in 1900..now) return arr.toString()
            for (y in now until now + 5) {
                val c = Almanac.checkYear(birthYear, y)
                arr.put(JSONObject().put("year", c.year).put("canChi", c.canChi).put("tuoi", c.tuoi)
                    .put("kimLau", c.kimLau ?: "").put("hoangOc", c.hoangOc ?: "").put("hoangOcBad", c.hoangOcBad)
                    .put("tamTai", c.tamTai ?: "").put("thaiTue", c.thaiTue ?: "").put("badForHouse", c.badForHouse))
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

        /**
         * Lưu họ tên và ngày sinh (nhập theo âm hoặc dương lịch). Năm sinh âm lịch để xét tuổi
         * được tính từ ngày sinh. Trả về "" nếu lưu được, ngược lại là câu báo lỗi.
         */
        @JavascriptInterface
        fun setProfile(name: String, lunar: Boolean, d: Int, m: Int, y: Int): String {
            val birth = Almanac.birthToSolar(lunar, d, m, y, LocalDate.now())
                ?: return if (lunar) "Năm $y không có ngày $d tháng $m âm lịch." else "Ngày sinh không hợp lệ."
            prefs.profileName = name.trim().take(60)
            prefs.birthDate = birth.toString()
            prefs.birthLunarInput = lunar
            prefs.birthYear = LunarCalendar.convert(birth).year
            pushState()
            LichWidget.updateAll(this@MainActivity) // sinh nhật có thể vừa thành sự kiện sắp tới
            return ""
        }

        @JavascriptInterface
        fun clearProfile() {
            prefs.profileName = ""
            prefs.birthDate = ""
            prefs.birthLunarInput = false
            prefs.birthYear = 0
            pushState()
            LichWidget.updateAll(this@MainActivity)
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
            val backup = JSONObject(EventStore.exportJson(EventStore.load(this@MainActivity)))
                .put("notes", NoteStore.exportAll(this@MainActivity))
                .put("families", FamilyStore.exportAll(this@MainActivity))
            createDocument("application/json", "lich-su-kien-${LocalDate.now()}.json", backup.toString(2))
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
            tryStart(i, REQ_IMPORT)
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
            EventStore.upcoming(Profile.eventsWithBirthday(this@MainActivity), today, days.coerceIn(1, 366)).forEach { u ->
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
            if (e.title.isBlank() || e.id == EventStore.BIRTHDAY_ID) return "" // sinh nhật sửa trong Hồ sơ
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

        /** Chọn hình nền lịch (tranh có sẵn hoặc "none"); "photo" chỉ đặt lại được khi đã có ảnh. */
        @JavascriptInterface
        fun setBackground(kind: String) {
            prefs.background = if (kind in BACKGROUNDS && (kind != "photo" || BackgroundImage.dataUrl(this@MainActivity).isNotEmpty())) kind else "none"
            pushState()
        }

        /** Mở trình chọn ảnh để đặt ảnh của người dùng làm hình nền. */
        @JavascriptInterface
        fun pickBackground() = runOnUiThread {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            tryStart(i, REQ_BG)
        }

        /** Ảnh nền của người dùng dạng data URL (rỗng nếu chưa có). */
        @JavascriptInterface
        fun backgroundPhoto(): String = BackgroundImage.dataUrl(this@MainActivity)

        @JavascriptInterface
        fun setTheme(mode: String) {
            prefs.theme = if (mode in listOf("light", "dark")) mode else "system"
            runOnUiThread { applySystemBars() }
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

        /** Ghim widget "Lịch bloc" (tờ lịch ngày kèm câu ca dao, tục ngữ). */
        @JavascriptInterface
        fun pinBlocWidget() = runOnUiThread {
            val mgr = AppWidgetManager.getInstance(this@MainActivity)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(this@MainActivity, LichBlocWidget::class.java), null, null)
            } else {
                status("Máy không hỗ trợ thêm nhanh. Giữ lâu màn hình chính → Widget → Lịch bloc.")
            }
        }

        /** Ghim tờ lịch 1×1 "Lịch hôm nay" (đặt thay icon app để có biểu tượng lịch đổi theo ngày). */
        @JavascriptInterface
        fun pinIconWidget() = runOnUiThread {
            val mgr = AppWidgetManager.getInstance(this@MainActivity)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(this@MainActivity, LichIconWidget::class.java), null, null)
            } else {
                status("Máy không hỗ trợ thêm nhanh. Giữ lâu màn hình chính → Widget → Lịch hôm nay.")
            }
        }

        // ---- La bàn AR ----

        @JavascriptInterface
        fun openCompass() = runOnUiThread {
            startActivity(Intent(this@MainActivity, CompassActivity::class.java))
        }

        // ---- Hình nền động, màn hình khóa, Zen ----

        /** Mở màn hình đặt hình nền động của máy với hình nền lịch âm đã chọn sẵn. */
        @JavascriptInterface
        fun setLiveWallpaper() = runOnUiThread {
            val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, LunarWallpaperService::class.java))
            try {
                startActivity(direct)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                    status("Chọn \"Lịch âm · trời trăng\" trong danh sách hình nền động.")
                } catch (e2: Exception) {
                    status("Máy không hỗ trợ hình nền động.")
                }
            }
        }

        @JavascriptInterface
        fun setWallpaperText(on: Boolean) {
            Prefs.get(this@MainActivity).edit().putBoolean("wpText", on).apply()
            pushState()
        }

        /** Bật / tắt lịch trên màn hình khóa (thông báo im lặng); trả về máy có đang cho app gửi thông báo không. */
        @JavascriptInterface
        fun setLockNote(on: Boolean): Boolean {
            Prefs.get(this@MainActivity).edit().putBoolean(LockScreenDay.PREF, on).remove("lockNoteDismissed").apply()
            val allowed = notificationsAllowed()
            if (on && !allowed) runOnUiThread {
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    lockNoteAsked = true
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                } else {
                    // Đã có quyền nhưng thông báo của app bị tắt trong cài đặt máy
                    openNotificationSettingsScreen()
                }
            }
            LockScreenDay.update(this@MainActivity)
            pushState()
            return allowed
        }

        @JavascriptInterface
        fun openNotificationSettings() = runOnUiThread { openNotificationSettingsScreen() }

        /** Màu thanh hệ thống theo lớp đang phủ (Zen giấy dó / mực đen); chuỗi rỗng = về màu theo giao diện. */
        @JavascriptInterface
        fun setSystemBars(color: String, lightBars: Boolean) = runOnUiThread {
            val c = try { if (color.isBlank()) null else Color.parseColor(color) } catch (e: Exception) { null }
            if (c == null) { applySystemBars(); return@runOnUiThread }
            @Suppress("DEPRECATION")
            window.statusBarColor = c
            @Suppress("DEPRECATION")
            window.navigationBarColor = c
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }

        @JavascriptInterface
        fun setZen(start: Boolean, paper: String) {
            Prefs.get(this@MainActivity).edit()
                .putBoolean("zenStart", start)
                .putString("zenPaper", if (paper in listOf("paper", "ink")) paper else "auto")
                .apply()
            pushState()
        }

        // ---- Sổ giỗ gia đình ----

        @JavascriptInterface
        fun families(): String = JSONArray().apply {
            FamilyStore.load(this@MainActivity).sortedBy { it.name }.forEach { put(familyJson(it)) }
        }.toString()

        /** Tạo sổ mới; trả về id. */
        @JavascriptInterface
        fun familyCreate(name: String): String {
            val g = FamilyStore.create(this@MainActivity, name)
            LichWidget.updateAll(this@MainActivity)
            return g.id
        }

        @JavascriptInterface
        fun familyUpdate(id: String, name: String, remind: Int) {
            val now = System.currentTimeMillis()
            FamilyStore.update(this@MainActivity, id) { g -> FamilyBook.updateMeta(g, name, remind, now) }
            LichWidget.updateAll(this@MainActivity)
        }

        /** Thêm hoặc sửa một người trong sổ (id rỗng = thêm mới). */
        @JavascriptInterface
        fun familySaveMember(groupId: String, json: String): Boolean {
            val now = System.currentTimeMillis()
            // fromJson tự cấp id mới khi id rỗng và lọc id về chữ, số
            val raw = try { JSONObject(json) } catch (e: Exception) { return false }
            val parsed = FamilyMember.fromJson(raw)
            if (parsed.name.isBlank() && parsed.relation.isBlank()) return false
            // Người thêm mới: id ngẫu nhiên (id theo nội dung chỉ dành cho mã soạn tay thiếu id)
            val base = if (raw.optString("id").isBlank()) parsed.copy(id = FamilyBook.newId()) else parsed
            FamilyStore.update(this@MainActivity, groupId) { g -> FamilyBook.saveMember(g, base, now) } ?: return false
            LichWidget.updateAll(this@MainActivity)
            return true
        }

        @JavascriptInterface
        fun familyDeleteMember(groupId: String, memberId: String) {
            val now = System.currentTimeMillis()
            // Giữ dấu xóa để khi gửi lại mã, máy khác cũng xóa theo
            FamilyStore.update(this@MainActivity, groupId) { g -> FamilyBook.deleteMember(g, memberId, now) }
            LichWidget.updateAll(this@MainActivity)
        }

        @JavascriptInterface
        fun familyDelete(id: String) {
            FamilyStore.delete(this@MainActivity, id)
            LichWidget.updateAll(this@MainActivity)
        }

        /** Gửi sổ cho cả nhà qua Zalo, Messenger… (tin nhắn có danh sách và mã nhập). */
        @JavascriptInterface
        fun familyShare(id: String) = runOnUiThread {
            val g = FamilyStore.get(this@MainActivity, id) ?: return@runOnUiThread
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, FamilyBook.safe("Sổ giỗ ${g.name}"))
                putExtra(Intent.EXTRA_TEXT, FamilyBook.shareText(g, LocalDate.now()))
            }
            tryStart(Intent.createChooser(send, "Gửi sổ giỗ cho cả nhà"))
        }

        /** Nhập mã từ tin nhắn đã dán; trả về JSON kết quả hoặc {"error": …}. */
        @JavascriptInterface
        fun familyImport(text: String): String {
            val all = FamilyStore.importText(this@MainActivity, text)
            if (all.isEmpty()) return JSONObject().put("error", "Không tìm thấy mã sổ giỗ hợp lệ trong nội dung đã dán.").toString()
            val r = all.last()
            Reminders.schedule(this@MainActivity)
            LichWidget.updateAll(this@MainActivity)
            return JSONObject().put("id", r.group.id).put("name", r.group.name).put("added", r.added)
                .put("updated", r.updated).put("removed", r.removed).put("isNew", r.isNew)
                .put("metaChanged", r.metaChanged).put("books", all.size).toString()
        }

        /** Chữ đang có trong bộ nhớ tạm (để dán mã sổ giỗ bằng một chạm). */
        @JavascriptInterface
        fun clipboardText(): String {
            val cm = getSystemService(ClipboardManager::class.java) ?: return ""
            return try {
                cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString() ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            if (url.startsWith("https://")) runOnUiThread { tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }
}
