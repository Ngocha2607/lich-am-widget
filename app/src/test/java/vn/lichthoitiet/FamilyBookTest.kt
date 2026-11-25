package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FamilyBookTest {
    private fun m(id: String, name: String, d: Int, mo: Int, u: Long, who: String = "", del: Boolean = false) =
        FamilyMember(id, name, "ông nội", d, mo, 1998, "Nghĩa trang Văn Điển", who, "", u, del)

    private val group = FamilyGroup(
        "abc123", "Nhà nội",
        listOf(m("m1", "Nguyễn Văn An", 18, 10, 100, "Chú Ba"), m("m2", "Trần Thị Bình", 5, 3, 100)),
        3, 100,
    )

    @Test
    fun maHoaVaGiaiMa() {
        val code = FamilyBook.encode(group)
        assertTrue(code.startsWith(FamilyBook.PREFIX))
        assertTrue(code.matches(Regex("""LICHGIAPHA1:[A-Za-z0-9_-]+""")))
        val back = FamilyBook.decode("Tin nhắn Zalo…\n$code\ncảm ơn")
        assertNotNull(back)
        assertEquals(group, back)
    }

    @Test
    fun maHongThiTraNull() {
        assertNull(FamilyBook.decode("không có mã"))
        assertNull(FamilyBook.decode("LICHGIAPHA1:abc"))
        assertNull(FamilyBook.decode("LICHGIAPHA1:" + "A".repeat(40)))
    }

    @Test
    fun gopSoGiuBanSuaSauCung() {
        val incoming = group.copy(
            members = listOf(
                m("m1", "Nguyễn Văn An", 18, 10, 200, "Cô Tư"),       // sửa sau: đổi người lo đồ cúng
                m("m2", "Trần Thị Bình", 5, 3, 50, "Ai đó"),           // bản cũ hơn: bỏ qua
                m("m3", "Lê Văn Cường", 2, 1, 150),                   // người mới
            ),
            updatedAt = 200, metaAt = 200, name = "Nhà nội họ Nguyễn",
        )
        val r = FamilyBook.merge(group, incoming)
        assertEquals(1, r.added)
        assertEquals(1, r.updated)
        assertEquals("Nhà nội họ Nguyễn", r.group.name)
        assertEquals("Cô Tư", r.group.members.first { it.id == "m1" }.assignee)
        assertEquals("", r.group.members.first { it.id == "m2" }.assignee)
        assertEquals(3, r.group.active.size)
    }

    @Test
    fun xoaCungDuocTruyenDi() {
        val incoming = group.copy(members = listOf(m("m2", "Trần Thị Bình", 5, 3, 300, del = true)), updatedAt = 300)
        val r = FamilyBook.merge(group, incoming)
        assertEquals(listOf("m1"), r.group.active.map { it.id })
        assertEquals(listOf("fam-abc123-m1"), FamilyBook.toEvents(r.group).map { it.id })
    }

    @Test
    fun doiThanhSuKienAmLich() {
        val ev = FamilyBook.toEvents(group)
        assertEquals(2, ev.size)
        val e = ev.first { it.id == "fam-abc123-m1" }
        assertTrue(e.lunar)
        assertTrue(e.yearly)
        assertEquals(18, e.day)
        assertEquals(10, e.month)
        assertEquals(3, e.remind)
        assertEquals("Giỗ ông nội (Nguyễn Văn An)", e.title)
        assertTrue(e.note.contains("Lo đồ cúng: Chú Ba"))
    }

    @Test
    fun ngayGioToiVaTinNhan() {
        val from = LocalDate.of(2026, 11, 25)
        val next = FamilyBook.nextDate(group.members[0], from)!!
        assertEquals(LunarCalendar.toSolar(18, 10, 2026), next)
        val text = FamilyBook.shareText(group, from)
        assertTrue(text.contains("Nhà nội"))
        assertTrue(text.contains("18/10 âm"))
        assertEquals(group, FamilyBook.decode(text))
    }
}
