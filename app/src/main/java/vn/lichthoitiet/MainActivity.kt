package vn.lichthoitiet

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime

/** Màn hình cài đặt và xem chi tiết. Mở khi chạm vào widget. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var cityInput: EditText
    private lateinit var status: TextView
    private lateinit var details: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        cityInput = findViewById(R.id.city_input)
        status = findViewById(R.id.status)
        details = findViewById(R.id.details)
        val saveBtn = findViewById<Button>(R.id.save_btn)
        val refreshBtn = findViewById<Button>(R.id.refresh_btn)
        val spiritualSwitch = findViewById<Switch>(R.id.spiritual_switch)

        cityInput.setText(prefs.city)
        spiritualSwitch.isChecked = prefs.spiritual
        spiritualSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.spiritual = checked
            render()
            LichWidget.updateAll(this)
        }

        saveBtn.setOnClickListener {
            val name = cityInput.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener
            status.text = "Đang tìm \"$name\"..."
            Thread {
                val geo = try { WeatherRepo.geocode(name) } catch (e: Exception) { null }
                runOnUiThread {
                    if (geo == null) {
                        status.text = "Không tìm thấy địa điểm. Thử gõ tên không dấu, ví dụ: Ha Noi, Da Nang."
                    } else {
                        prefs.lat = geo.lat
                        prefs.lon = geo.lon
                        prefs.city = geo.name
                        cityInput.setText(geo.name)
                        doRefresh()
                    }
                }
            }.start()
        }

        refreshBtn.setOnClickListener { doRefresh() }

        if (WeatherRepo.load(this) == null) doRefresh()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun doRefresh() {
        status.text = "Đang tải thời tiết..."
        Thread {
            val err = try { WeatherRepo.refresh(this); null } catch (e: Exception) { e.message ?: "lỗi mạng" }
            runOnUiThread {
                status.text = if (err == null) "Đã cập nhật." else "Không tải được thời tiết ($err)."
                render()
                LichWidget.updateAll(this)
            }
        }.start()
    }

    private fun render() {
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val lunar = LunarCalendar.convert(today)
        val weather = WeatherRepo.load(this)
        val tips = SuggestionEngine.build(today, now, weather, prefs.spiritual)

        val sb = StringBuilder()
        sb.appendLine("📅 ${WidgetRenderer.dayOfWeek(today)}, ${today.dayOfMonth}/${today.monthValue}/${today.year}")
        sb.append("🌙 Âm lịch: ${lunar.day}/${LunarCalendar.monthName(lunar.month)}")
        if (lunar.leap) sb.append(" (nhuận)")
        sb.appendLine(" năm ${LunarCalendar.canChiYear(lunar.year)}")
        sb.appendLine("   Ngày ${LunarCalendar.canChiDay(lunar.jd)}, tháng ${LunarCalendar.canChiMonth(lunar)}")
        sb.appendLine("   Tiết: ${LunarCalendar.currentTietKhi(lunar.jd)}")
        if (prefs.spiritual) {
            val hd = if (LunarCalendar.isHoangDao(lunar)) "Hoàng đạo" else "Hắc đạo"
            sb.appendLine("   $hd (trực ${LunarCalendar.trucThan(lunar)})")
            sb.appendLine("   Giờ tốt: ${LunarCalendar.gioHoangDao(lunar.jd).joinToString(", ")}")
        }
        sb.appendLine()

        if (weather == null) {
            sb.appendLine("🌡️ Chưa có dữ liệu thời tiết.")
        } else {
            sb.appendLine("${WeatherText.icon(weather.codeNow)} ${weather.city}: ${weather.tempNow}° (cảm giác ${weather.feelsLike}°), ${WeatherText.label(weather.codeNow)}")
            sb.appendLine("   Độ ẩm ${weather.humidity}%, gió ${weather.windNow} km/h")
            sb.appendLine("   Hôm nay: ${weather.todayMin}-${weather.todayMax}°, mưa ${weather.todayRain}%, UV ${weather.todayUv.toInt()}")
            sb.appendLine("   Ngày mai: ${weather.tomorrowMin}-${weather.tomorrowMax}°, mưa ${weather.tomorrowRain}%, ${WeatherText.label(weather.tomorrowCode)}")
        }
        sb.appendLine()

        sb.appendLine("💡 Gợi ý hôm nay")
        tips.forEach { sb.appendLine("• ${it.short}\n   ${it.detail}") }

        details.text = sb.toString()
    }
}
