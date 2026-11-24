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
    fun xuatIcsCaNgay() {
        val e = Event("x", "Giỗ, ông", "🕯️", true, 18, 10, null, true, "", "Về quê; sớm", 3)
        val ics = EventStore.toIcs(listOf(e), LocalDate.of(2026, 11, 24), 1)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("BEGIN:VEVENT"))
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261126"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261127"))
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
