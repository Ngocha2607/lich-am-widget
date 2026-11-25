package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EventStoreTest {
    private fun ev(lunar: Boolean, d: Int, m: Int, year: Int? = null, yearly: Boolean = true) =
        Event("x", "Giỗ", "🕯️", lunar, d, m, year, yearly, "", "", 3)

    @Test
    fun gioTheoAmLichHangNam() {
        val e = ev(lunar = true, d = 15, m = 10)
        assertTrue(EventStore.occursOn(e, LocalDate.of(2026, 11, 23)))   // rằm tháng 10 Bính Ngọ
        assertFalse(EventStore.occursOn(e, LocalDate.of(2026, 11, 24)))
    }

    @Test
    fun sinhNhatDuongLich() {
        val e = ev(lunar = false, d = 24, m = 11)
        assertTrue(EventStore.occursOn(e, LocalDate.of(2026, 11, 24)))
        assertTrue(EventStore.occursOn(e, LocalDate.of(2027, 11, 24)))
        val once = ev(lunar = false, d = 24, m = 11, year = 2026, yearly = false)
        assertFalse(EventStore.occursOn(once, LocalDate.of(2027, 11, 24)))
    }

    @Test
    fun ngay29Thang2() {
        val e = ev(lunar = false, d = 29, m = 2)
        assertTrue(EventStore.occursOn(e, LocalDate.of(2028, 2, 29)))   // năm nhuận: đúng ngày
        assertFalse(EventStore.occursOn(e, LocalDate.of(2028, 2, 28)))
        assertTrue(EventStore.occursOn(e, LocalDate.of(2027, 2, 28)))   // năm thường: làm vào 28/2
        assertFalse(EventStore.occursOn(ev(lunar = false, d = 28, m = 2), LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun sinhNhatTuHoSo() {
        // Sinh 12/3/1990 dương lịch = 16/2 âm lịch năm Canh Ngọ
        val duong = EventStore.birthdayEvent("1990-03-12", lunar = false)!!
        assertEquals(EventStore.BIRTHDAY_ID, duong.id)
        assertTrue(EventStore.occursOn(duong, LocalDate.of(2027, 3, 12)))
        val am = EventStore.birthdayEvent("1990-03-12", lunar = true)!!
        assertEquals(16, am.day); assertEquals(2, am.month); assertTrue(am.lunar)
        assertTrue(EventStore.occursOn(am, LunarCalendar.toSolar(16, 2, 2027)!!))
        assertEquals(null, EventStore.birthdayEvent("", lunar = false))
    }

    @Test
    fun xuatIcsCaNgay() {
        val e = Event("x", "Giỗ, ông", "🕯️", true, 18, 10, null, true, "", "Về quê; sớm", 3)
        val ics = EventStore.toIcs(listOf(e), LocalDate.of(2026, 11, 24), 1)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        // Cửa sổ 1 năm (24/11/2026 → 24/11/2027) có hai lần 18/10 âm: 26/11/2026 và 15/11/2027
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261126"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261127"))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20271115"))
        assertTrue(ics.contains("UID:x-20271115@lichthoitiet"))
        assertTrue(ics.contains("SUMMARY:🕯️ Giỗ\\, ông"))
        assertTrue(ics.contains("DESCRIPTION:"))
        assertTrue(ics.contains("Về quê\\; sớm"))
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
    }

    @Test
    fun xuatIcsCoGio() {
        val e = Event("y", "Họp họ", "📌", true, 18, 10, null, true, "09:30", "", 1)
        val ics = EventStore.toIcs(listOf(e), LocalDate.of(2026, 11, 24), 1)
        assertTrue(ics.contains("DTSTART:20261126T093000"))
        assertTrue(ics.contains("DTEND:20261126T103000"))
    }

    @Test
    fun sapToi() {
        val list = listOf(ev(lunar = true, d = 18, m = 10))
        val up = EventStore.upcoming(list, LocalDate.of(2026, 11, 24), 30)
        assertEquals(1, up.size)
        assertEquals(LocalDate.of(2026, 11, 26), up[0].date)
        assertEquals(2, up[0].daysLeft)
    }
}
