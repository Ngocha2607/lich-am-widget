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
    fun sapToi() {
        val list = listOf(ev(lunar = true, d = 18, m = 10))
        val up = EventStore.upcoming(list, LocalDate.of(2026, 11, 24), 30)
        assertEquals(1, up.size)
        assertEquals(LocalDate.of(2026, 11, 26), up[0].date)
        assertEquals(2, up[0].daysLeft)
    }
}
