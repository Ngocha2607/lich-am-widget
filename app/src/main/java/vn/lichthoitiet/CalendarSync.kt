package vn.lichthoitiet

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars as Cal
import android.provider.CalendarContract.Instances as Inst
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Một lịch trên máy (lịch Google đã đồng bộ, lịch điện thoại...). */
data class DeviceCalendar(val id: Long, val name: String, val account: String, val accountType: String, val color: Int, val writable: Boolean)

/**
 * Nhập / xuất sự kiện với lịch trên máy qua CalendarContract, gồm các lịch Google mà máy đã đồng bộ
 * (không cần đăng nhập trong app). Gọi ở luồng nền.
 */
object CalendarSync {
    /** Sự kiện nhập từ lịch máy có id "gcal-<id sự kiện>-<ngày>" để nhập lại không bị trùng. */
    const val IMPORT_PREFIX = "gcal-"
    /** Ghi ở cuối mô tả các mục app xuất ra, để lúc nhập bỏ qua (khỏi nhập ngược lại chính mình). */
    const val MARKER = "— Xuất từ Lịch & Thời tiết"
    private const val KEY_EXPORTED = "gcal_exported_ids"
    private const val KEY_AUTO_AT = "gcal_auto_at"
    private const val AUTO_GAP_MS = 30 * 60_000L
    /** Loại tài khoản Google trong AccountManager / CalendarContract. */
    const val GOOGLE = "com.google"
    /** Ứng dụng Lịch Google (CH Play) và gói bộ đồng bộ Lịch Google của hệ thống. */
    const val GCAL_APP = "com.google.android.calendar"
    const val GCAL_SYNC_PKG = "com.google.android.syncadapters.calendar"

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(ctx: Context): List<DeviceCalendar> {
        val proj = arrayOf(Cal._ID, Cal.CALENDAR_DISPLAY_NAME, Cal.ACCOUNT_NAME, Cal.CALENDAR_COLOR, Cal.CALENDAR_ACCESS_LEVEL, Cal.ACCOUNT_TYPE)
        val out = mutableListOf<DeviceCalendar>()
        // Gồm cả lịch đang ẩn trong app lịch khác (visible = 0) mà vẫn đồng bộ sự kiện
        val where = "(${Cal.VISIBLE} = 1 OR ${Cal.SYNC_EVENTS} = 1)"
        ctx.contentResolver.query(Cal.CONTENT_URI, proj, where, null, "${Cal.ACCOUNT_NAME}, ${Cal.CALENDAR_DISPLAY_NAME}")?.use { cur ->
            while (cur.moveToNext()) {
                out += DeviceCalendar(
                    id = cur.getLong(0), name = cur.getString(1) ?: "", account = cur.getString(2) ?: "",
                    accountType = cur.getString(5) ?: "", color = cur.getInt(3), writable = cur.getInt(4) >= Cal.CAL_ACCESS_CONTRIBUTOR,
                )
            }
        }
        return out
    }

    /** Phiên bản gói [pkg] đã cài (thêm "(đang tắt)" nếu bị tắt); null nếu chưa cài hoặc app không được thấy. */
    fun versionOf(ctx: Context, pkg: String): String? = try {
        val info = ctx.packageManager.getPackageInfo(pkg, 0)
        (info.versionName ?: "?") + if (info.applicationInfo?.enabled == false) " (đang tắt)" else ""
    } catch (e: Exception) {
        null
    }

    /**
     * Máy có bộ đồng bộ Lịch cho tài khoản Google không: hệ thống báo có, hoặc có gói bộ đồng bộ Lịch Google.
     * Không đọc được thì coi như có.
     */
    fun hasGoogleCalendarAdapter(ctx: Context): Boolean = try {
        ContentResolver.getSyncAdapterTypes().any { it.accountType == GOOGLE && it.authority == CalendarContract.AUTHORITY } ||
            versionOf(ctx, GCAL_SYNC_PKG) != null
    } catch (e: Exception) {
        true
    }

    /** Loại tài khoản và kho dữ liệu mà dịch vụ đồng bộ [si] khai báo (đọc từ meta-data android.content.SyncAdapter). */
    private fun adapterOf(pm: PackageManager, si: ServiceInfo): Pair<String, String>? {
        val xml = try { si.loadXmlMetaData(pm, "android.content.SyncAdapter") } catch (e: Exception) { null } ?: return null
        return try {
            val ns = "http://schemas.android.com/apk/res/android"
            val res = pm.getResourcesForApplication(si.applicationInfo)
            fun attr(name: String): String {
                val id = xml.getAttributeResourceValue(ns, name, 0)
                return if (id != 0) res.getString(id) else xml.getAttributeValue(ns, name) ?: ""
            }
            var ev = xml.eventType
            var found: Pair<String, String>? = null
            while (ev != XmlPullParser.END_DOCUMENT && found == null) {
                if (ev == XmlPullParser.START_TAG && xml.name == "sync-adapter") found = attr("accountType") to attr("contentAuthority")
                else ev = xml.next()
            }
            found
        } catch (e: Exception) {
            null
        } finally {
            xml.close()
        }
    }

    private fun mask(email: String): String {
        val at = email.indexOf('@')
        return if (at <= 3) email else email.take(3) + "…" + email.substring(at)
    }

    private fun onOff(b: Boolean?) = when (b) { true -> "bật"; false -> "tắt"; null -> "?" }

    private inline fun <T> orNull(f: () -> T): T? = try { f() } catch (e: Exception) { null }

    /**
     * Thông tin để tìm vì sao lịch Google chưa về máy: phiên bản Android, các gói liên quan, bộ đồng bộ hệ thống thấy được,
     * trạng thái đồng bộ từng tài khoản [accounts] và các lịch đang có trong kho lịch của máy (email được che bớt).
     */
    fun diagnostics(ctx: Context, accounts: Collection<String>): String {
        val out = mutableListOf<String>()
        val pm = ctx.packageManager
        out += "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
        out += "Ứng dụng Lịch Google: ${versionOf(ctx, GCAL_APP) ?: "không có"}"
        out += "Gói đồng bộ Lịch Google: ${versionOf(ctx, GCAL_SYNC_PKG) ?: "không có"}"
        out += "Dịch vụ Google Play: ${versionOf(ctx, "com.google.android.gms") ?: "không có"}"
        out += "Kho lịch của máy: ${orNull { pm.resolveContentProvider(CalendarContract.AUTHORITY, 0)?.packageName } ?: "không thấy"}"
        out += "Tự đồng bộ cả máy: ${onOff(orNull { ContentResolver.getMasterSyncAutomatically() })}"
        val types = orNull { ContentResolver.getSyncAdapterTypes().toList() } ?: emptyList()
        val google = types.filter { it.accountType == GOOGLE }.map { it.authority }.distinct().sorted()
        out += "Bộ đồng bộ hệ thống báo: ${types.size}; của Google: ${google.joinToString().ifEmpty { "không có" }}"
        @Suppress("DEPRECATION")
        val services = orNull { pm.queryIntentServices(Intent("android.content.SyncAdapter"), PackageManager.GET_META_DATA) } ?: emptyList()
        val calAdapters = services.mapNotNull { ri ->
            val (type, auth) = adapterOf(pm, ri.serviceInfo) ?: return@mapNotNull null
            if (auth.split(',').any { it.trim() == CalendarContract.AUTHORITY }) "${ri.serviceInfo.packageName} ($type)" else null
        }
        out += "Dịch vụ đồng bộ lịch (trong ${services.size} dịch vụ đồng bộ): ${calAdapters.joinToString().ifEmpty { "không có" }}"
        accounts.forEach { n ->
            val a = Account(n, GOOGLE)
            val auth = CalendarContract.AUTHORITY
            out += "Tài khoản ${mask(n)}: tự đồng bộ Lịch ${onOff(orNull { ContentResolver.getSyncAutomatically(a, auth) })}" +
                ", syncable ${orNull { ContentResolver.getIsSyncable(a, auth) } ?: "?"}" +
                ", đang chạy ${onOff(orNull { ContentResolver.isSyncActive(a, auth) })}" +
                ", đang chờ ${onOff(orNull { ContentResolver.isSyncPending(a, auth) })}"
        }
        @Suppress("DEPRECATION")
        val running = orNull { ContentResolver.getCurrentSyncs() }
        out += "Đang đồng bộ: ${running?.joinToString { "${it.authority} (${mask(it.account.name)})" }?.ifEmpty { "không" } ?: "?"}"
        if (!hasPermission(ctx)) {
            out += "Chưa có quyền đọc lịch"
            return out.joinToString("\n")
        }
        try {
            val cr = ctx.contentResolver
            val proj = arrayOf(Cal._ID, Cal.ACCOUNT_TYPE, Cal.ACCOUNT_NAME, Cal.CALENDAR_DISPLAY_NAME, Cal.VISIBLE, Cal.SYNC_EVENTS)
            val read = cr.query(Cal.CONTENT_URI, proj, null, null, "${Cal.ACCOUNT_TYPE}, ${Cal.ACCOUNT_NAME}")?.use { c ->
                out += "Lịch trong kho: ${c.count}"
                var shown = 0
                while (c.moveToNext() && shown < 20) {
                    shown++
                    val id = c.getLong(0)
                    val acc = c.getString(2) ?: ""
                    val name = (c.getString(3) ?: "").let { if (it == acc) "lịch chính" else it }
                    val events = orNull {
                        cr.query(
                            CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID),
                            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0", arrayOf(id.toString()), null,
                        )?.use { it.count }
                    }
                    out += "- ${c.getString(1)} · ${mask(acc)} · ${mask(name)} · hiện ${onOff(c.getInt(4) == 1)}" +
                        " · đồng bộ ${onOff(c.getInt(5) == 1)} · ${events ?: "?"} sự kiện"
                }
                true
            }
            if (read == null) out += "Không đọc được kho lịch"
        } catch (e: Exception) {
            out += "Lỗi đọc kho lịch: ${e.message}"
        }
        return out.joinToString("\n")
    }

    /** Lịch của tài khoản Google [account] có đang tự đồng bộ không; null nếu không đọc được. */
    fun syncOn(account: String): Boolean? = try {
        ContentResolver.getMasterSyncAutomatically() &&
            ContentResolver.getSyncAutomatically(Account(account, GOOGLE), CalendarContract.AUTHORITY)
    } catch (e: Exception) {
        null
    }

    /** Yêu cầu đồng bộ ngay lịch của tài khoản Google [account]. */
    fun requestSync(account: String): Boolean = try {
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        ContentResolver.requestSync(Account(account, GOOGLE), CalendarContract.AUTHORITY, extras)
        true
    } catch (e: Exception) {
        false
    }

    /** Bật tự đồng bộ Lịch cho tài khoản [account] (người dùng vừa bấm) rồi đồng bộ ngay; false nếu cả máy đang tắt tự đồng bộ. */
    fun enableSync(account: String): Boolean = try {
        if (!ContentResolver.getMasterSyncAutomatically()) false
        else {
            ContentResolver.setSyncAutomatically(Account(account, GOOGLE), CalendarContract.AUTHORITY, true)
            requestSync(account)
            true
        }
    } catch (e: Exception) {
        false
    }

    /**
     * Tự lấy sự kiện từ lịch đã chọn nếu người dùng bật (gọi ở luồng nền: lúc mở app, WeatherWorker).
     * Bỏ qua khi chưa đủ 30 phút từ lần trước (trừ [force]) hoặc lịch không còn trên máy (đăng xuất tài khoản
     * không làm mất sự kiện đã nhập). Trả về số sự kiện trong 1 năm tới của lịch, -1 nếu không chạy.
     */
    fun autoImport(ctx: Context, force: Boolean = false): Int {
        val p = Prefs(ctx)
        if (!p.gcalAuto || p.gcalCalId < 0 || !hasPermission(ctx)) return -1
        val sp = Prefs.get(ctx)
        val now = System.currentTimeMillis()
        if (!force && now - sp.getLong(KEY_AUTO_AT, 0L) in 0L until AUTO_GAP_MS) return -1
        if (calendars(ctx).none { it.id == p.gcalCalId }) return -1
        sp.edit().putLong(KEY_AUTO_AT, now).apply()
        return importFrom(ctx, p.gcalCalId, LocalDate.now(), 365)
    }

    private fun dateOfId(id: String): LocalDate? = try { LocalDate.parse(id.takeLast(10)) } catch (e: Exception) { null }

    /**
     * Nhập các lần diễn ra trong [days] ngày tới của lịch [calId] thành sự kiện dương lịch một lần.
     * Sự kiện lần trước nhập mà nay không còn (đã xóa hay dời ngày bên lịch Google) thì bỏ khỏi app; sự kiện đã qua giữ lại.
     * Trả về số sự kiện đã nhập (nhập lại thì ghi đè, không trùng).
     */
    fun importFrom(ctx: Context, calId: Long, from: LocalDate, days: Int): Int {
        val zone = ZoneId.systemDefault()
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = from.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Inst.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start); ContentUris.appendId(it, end)
        }.build()
        val proj = arrayOf(Inst.EVENT_ID, Inst.TITLE, Inst.BEGIN, Inst.ALL_DAY, Inst.DESCRIPTION)
        val imported = mutableListOf<Event>()
        ctx.contentResolver.query(uri, proj, "${Inst.CALENDAR_ID} = ?", arrayOf(calId.toString()), "${Inst.BEGIN}")?.use { cur ->
            while (cur.moveToNext()) {
                val desc = cur.getString(4) ?: ""
                if (desc.contains(MARKER)) continue
                val title = (cur.getString(1) ?: "").trim()
                if (title.isEmpty()) continue
                val allDay = cur.getInt(3) == 1
                val begin = Instant.ofEpochMilli(cur.getLong(2))
                // Sự kiện cả ngày được lưu theo giờ UTC; sự kiện có giờ theo múi giờ máy
                val date = if (allDay) begin.atZone(ZoneOffset.UTC).toLocalDate() else begin.atZone(zone).toLocalDate()
                val time = if (allDay) "" else begin.atZone(zone).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
                imported += Event(
                    id = "$IMPORT_PREFIX${cur.getLong(0)}-$date", title = title.take(60), icon = "📅",
                    lunar = false, day = date.dayOfMonth, month = date.monthValue, year = date.year, yearly = false,
                    time = time, note = desc.trim().take(120), remind = 1,
                )
            }
        }
        val prefs = Prefs.get(ctx)
        val key = "gcal_imported_$calId"
        val prev = try {
            JSONArray(prefs.getString(key, "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
        } catch (e: Exception) {
            emptyList()
        }
        val now = imported.map { it.id }.toSet()
        val gone = prev.filter { it !in now && dateOfId(it)?.isBefore(from) == false }.toSet()
        val keep = prev.filter { it !in now && it !in gone }
        EventStore.syncImported(ctx, gone, imported)
        prefs.edit().putString(key, JSONArray(keep + now).toString()).apply()
        return imported.size
    }

    /**
     * Xuất sự kiện của app (trừ sự kiện nhập từ lịch máy): mỗi lần diễn ra trong [years] năm tới là một mục,
     * vì lịch Google không lặp được theo âm lịch. Các mục đã xuất lần trước bị xóa rồi ghi lại.
     * Trả về số mục đã ghi.
     */
    fun exportTo(ctx: Context, calId: Long, from: LocalDate, years: Int): Int {
        val cr = ctx.contentResolver
        val prefs = Prefs.get(ctx)
        val prev = try { JSONArray(prefs.getString(KEY_EXPORTED, "[]")) } catch (e: Exception) { JSONArray() }
        for (k in 0 until prev.length()) {
            try { cr.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, prev.getLong(k)), null, null) } catch (e: Exception) { }
        }
        val events = EventStore.load(ctx).filter { !it.id.startsWith(IMPORT_PREFIX) }
        val zone = ZoneId.systemDefault()
        val ids = JSONArray()
        var d = from
        val end = from.plusYears(years.toLong())
        while (d.isBefore(end)) {
            for (e in EventStore.eventsOn(events, d)) {
                val v = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calId)
                    put(CalendarContract.Events.TITLE, "${e.icon} ${e.title}")
                    put(CalendarContract.Events.DESCRIPTION, listOf(e.dateLabel, e.note).filter { it.isNotBlank() }.joinToString(" · ") + "\n" + MARKER)
                    val t = e.time.split(":").mapNotNull { it.toIntOrNull() }
                    if (t.size == 2) {
                        val startAt = d.atTime(LocalTime.of(t[0].coerceIn(0, 23), t[1].coerceIn(0, 59))).atZone(zone)
                        put(CalendarContract.Events.DTSTART, startAt.toInstant().toEpochMilli())
                        put(CalendarContract.Events.DTEND, startAt.plusHours(1).toInstant().toEpochMilli())
                        put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                    } else {
                        put(CalendarContract.Events.DTSTART, d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                        put(CalendarContract.Events.DTEND, d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                        put(CalendarContract.Events.ALL_DAY, 1)
                        put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                    }
                }
                cr.insert(CalendarContract.Events.CONTENT_URI, v)?.let { ids.put(ContentUris.parseId(it)) }
            }
            d = d.plusDays(1)
        }
        prefs.edit().putString(KEY_EXPORTED, ids.toString()).apply()
        return ids.length()
    }
}
