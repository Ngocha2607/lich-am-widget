package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate

class QuotesTest {
    @Test
    fun moiNgayMotCauVaDiHetDanhSach() {
        val n = Quotes.ALL.size
        // Bước nhảy phải nguyên tố cùng nhau với số câu thì mới đi hết danh sách
        assertEquals(BigInteger.ONE, BigInteger.valueOf(37).gcd(BigInteger.valueOf(n.toLong())))
        val start = LocalDate.of(2026, 1, 1)
        val seen = (0 until n).map { Quotes.forDate(start.plusDays(it.toLong())) }.toSet()
        assertEquals(n, seen.size)
        assertNotEquals(Quotes.forDate(start), Quotes.forDate(start.plusDays(1)))
        assertEquals(Quotes.forDate(start), Quotes.forDate(start.plusDays(n.toLong())))
    }

    @Test
    fun khongTrungVaKhongRong() {
        assertEquals(Quotes.ALL.size, Quotes.ALL.map { it.text }.toSet().size)
        assertTrue(Quotes.ALL.all { it.text.isNotBlank() && it.text.length <= 90 })
    }
}
