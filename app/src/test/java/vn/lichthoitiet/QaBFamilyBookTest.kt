package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64
import java.util.zip.Deflater

/**
 * QA tester B: sổ giỗ. Test tên "bug_…"/"nit_…" khẳng định hành vi ĐÚNG mong đợi nên đang FAIL để chứng minh lỗi;
 * test "ok_…" là phần đã kiểm tra và đúng.
 */
class QaBFamilyBookTest {
    /** Mã từ JSON tùy ý (giống encode nhưng nhận chuỗi thô), để tạo mã độc/hỏng. */
    private fun rawCode(json: String): String {
        val d = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(json.toByteArray(Charsets.UTF_8)); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return FamilyBook.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    private fun mem(id: String, name: String, d: Int, mo: Int, u: Long, del: Boolean = false, who: String = "") =
        FamilyMember(id, name, "bà nội", d, mo, null, "", who, "", u, del)

    // Mô phỏng đúng các hàm cầu nối trong MainActivity (familySaveMember / familyDeleteMember / familyUpdate)
    private fun saveMember(g: FamilyGroup, m: FamilyMember, now: Long) =
        g.copy(members = g.members.filter { it.id != m.id } + m.copy(updatedAt = now, deleted = false), updatedAt = now)

    private fun deleteMember(g: FamilyGroup, id: String, now: Long) =
        g.copy(members = g.members.map { if (it.id == id) it.copy(deleted = true, updatedAt = now) else it }, updatedAt = now)

    private fun rename(g: FamilyGroup, name: String, remind: Int, now: Long) = g.copy(name = name, remind = remind, updatedAt = now)

    /** Gửi sổ qua tin nhắn rồi máy kia dán vào. */
    private fun viaChat(g: FamilyGroup) = FamilyBook.decode("Gửi cả nhà nhé\n" + FamilyBook.shareText(g, LocalDate.of(2026, 11, 25)) + "\n👍")!!

    private val base = FamilyGroup("g1", "Họ Nguyễn", listOf(mem("a", "Nguyễn Văn An", 1, 1, 100)), 3, 100)

    // ---------- Gộp sổ giữa hai máy ----------

    @Test
    fun bug_doiTenSoBiMatKhiMayKhacSuaNguoiSauDo() {
        // Máy A đổi tên sổ + số ngày nhắc lúc t=1000; máy B (chưa nhận) thêm một người lúc t=2000.
        val phoneA = rename(base, "Nhà nội", 7, 1000)
        val phoneB = saveMember(base, mem("b", "Trần Thị Bình", 2, 2, 0), 2000)
        // Hai máy gửi mã cho nhau
        val aAfter = FamilyBook.merge(phoneA, viaChat(phoneB)).group
        val bAfter = FamilyBook.merge(phoneB, viaChat(phoneA)).group
        assertEquals(2, aAfter.active.size)
        assertEquals(2, bAfter.active.size)
        // Mong đợi: cả hai máy giữ tên mới "Nhà nội", nhắc 7 ngày. Thực tế: tên/nhắc bị mất ở CẢ HAI máy.
        assertEquals(
            "tên sổ, nhắc trước (máy A, máy B)",
            listOf("Nhà nội", 7, "Nhà nội", 7),
            listOf(aAfter.name, aAfter.remind, bAfter.name, bAfter.remind),
        )
    }

    @Test
    fun ok_cungMotNguoiSuaTrenHaiMayHoiTuVeBanSauCung() {
        val a = saveMember(base, mem("a", "Nguyễn Văn An", 1, 1, 0, who = "Chú Ba"), 2000)
        val b = saveMember(base, mem("a", "Nguyễn Văn An", 2, 1, 0, who = "Cô Tư"), 3000)
        val a2 = FamilyBook.merge(a, viaChat(b)).group
        val b2 = FamilyBook.merge(b, viaChat(a)).group
        assertEquals(a2.members, b2.members)
        assertEquals("Cô Tư", a2.active.single().assignee)
        assertEquals(2, a2.active.single().lunarDay)
    }

    @Test
    fun ok_xoaTrenMayNaySuaTruocDoTrenMayKiaThiXoaThang() {
        val b = saveMember(base, mem("a", "Nguyễn Văn An", 1, 1, 0, who = "Chú Ba"), 2000)
        val a = deleteMember(base, "a", 3000)
        assertTrue(FamilyBook.merge(a, viaChat(b)).group.active.isEmpty())
        assertTrue(FamilyBook.merge(b, viaChat(a)).group.active.isEmpty())
        // Sửa SAU khi xóa thì người đó sống lại (last-writer-wins, chấp nhận được)
        val b3 = saveMember(base, mem("a", "Nguyễn Văn An", 1, 1, 0, who = "Chú Ba"), 4000)
        assertEquals(1, FamilyBook.merge(a, viaChat(b3)).group.active.size)
    }

    @Test
    fun nit_dauXoaBiDemLaCapNhat() {
        val a = deleteMember(base, "a", 3000)
        val r = FamilyBook.merge(base, viaChat(a))
        // UI hiện "cập nhật 1" trong khi thật ra là đã xóa 1 người
        assertEquals("số người được 'cập nhật' khi chỉ có một dấu xóa", 0, r.updated)
    }

    @Test
    fun nit_haiBanCungThoiDiemKhongHoiTu() {
        val a = base.copy(members = listOf(mem("a", "Nguyễn Văn An", 1, 1, 500, who = "Chú Ba")))
        val b = base.copy(members = listOf(mem("a", "Nguyễn Văn An", 1, 1, 500, who = "Cô Tư")))
        val a2 = FamilyBook.merge(a, viaChat(b)).group
        val b2 = FamilyBook.merge(b, viaChat(a)).group
        assertEquals("hai máy phải giống nhau sau khi trao mã", a2.members.single().assignee, b2.members.single().assignee)
    }

    @Test
    fun design_dongHoMaySaiNamTuongLaiThangMaiMai() {
        // Một máy đặt nhầm đồng hồ năm 2036 sửa người "a"; các máy khác sửa lại ở hiện tại đều bị đè mỗi lần nhập lại mã cũ.
        val future = LocalDate.of(2036, 1, 1).toEpochDay() * 86_400_000L
        val now = LocalDate.of(2026, 11, 25).toEpochDay() * 86_400_000L
        val wrongClock = saveMember(base, mem("a", "Nguyễn Văn An", 9, 9, 0), future)
        val mine = saveMember(FamilyBook.merge(base, viaChat(wrongClock)).group, mem("a", "Nguyễn Văn An", 1, 1, 0), now)
        val again = FamilyBook.merge(mine, viaChat(wrongClock)).group
        assertEquals("bản sửa hôm nay bị bản 'năm 2036' đè lại", 1, again.active.single().lunarDay)
    }

    // ---------- Giải mã từ văn bản bất kỳ ----------

    @Test
    fun ok_khuHoiVoiUnicodeNgay30VaDauXoa() {
        val g = FamilyGroup(
            "Zx9", "Nhà ngoại 🏠 – Lê",
            listOf(
                FamilyMember("m1", "Đặng Thị Ánh 👵", "cụ bà \"Tư\"", 30, 12, 1945, "Quê: Nam Định\\Hải Hậu", "Chú Út\nanh Hai", "Mâm cỗ chay; không 🐓", 123456789L),
                FamilyMember("m2", "", "", 29, 1, null, "", "", "", 5L, deleted = true),   // dấu xóa không mang thông tin cá nhân
                // Tên gõ kiểu tổ hợp (NFD) vẫn giữ nguyên
                FamilyMember("m3", java.text.Normalizer.normalize("Trần Thị Thúy", java.text.Normalizer.Form.NFD), "", 15, 7, 2001, "", "", "", 6L),
            ),
            0, 999,
        )
        val chat = "Bố gửi:\r\n\t" + FamilyBook.encode(g) + "…Nhớ nhập nha! LICHGIAPHA1 (mã ở trên)"
        assertEquals(g, FamilyBook.decode(chat))
        assertEquals(g, FamilyBook.decode(FamilyBook.encode(FamilyBook.decode(chat)!!)))
        // Mã dính liền sau chữ/dấu câu vẫn đọc được
        assertEquals(g, FamilyBook.decode("mã:" + FamilyBook.encode(g) + "."))
        assertEquals(g, FamilyBook.decode("x".repeat(2_000_000) + " " + FamilyBook.encode(g)))
    }

    @Test
    fun ok_maHongDeuTraNullKhongNem() {
        val good = FamilyBook.encode(base)
        val inputs = listOf(
            "", "LICHGIAPHA1:", "LICHGIAPHA1:#####", "lichgiapha1:" + good.removePrefix(FamilyBook.PREFIX),
            good.dropLast(good.length / 2),                                        // bị cắt nửa
            good.substring(0, FamilyBook.PREFIX.length + 5),                       // độ dài base64 lẻ (mod 4 = 1)
            FamilyBook.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString("không phải zlib".toByteArray()),
            rawCode("không phải JSON"), rawCode("[1,2,3]"), rawCode("{\"members\":[1,2]}"), rawCode("{\"members\":[null]}"),
            rawCode("{\"id\":\"x\""),
        )
        for (s in inputs) {
            try {
                assertNull(s, FamilyBook.decode(s))
            } catch (t: Throwable) {
                if (t is AssertionError) throw t
                fail("decode ném ${t.javaClass.simpleName} với: ${s.take(40)}")
            }
        }
        // "members" không phải mảng → sổ rỗng, không lỗi
        assertEquals(0, FamilyBook.decode(rawCode("{\"id\":\"x\",\"members\":{}}"))!!.members.size)
    }

    @Test
    fun ok_bomNenBiChan() {
        val bomb = rawCode("{\"id\":\"x\",\"name\":\"" + " ".repeat(20_000_000) + "\"}")
        assertTrue("mã bom nén chỉ " + bomb.length + " ký tự", bomb.length < 40_000)
        val t0 = System.nanoTime()
        assertNull(FamilyBook.decode(bomb))
        assertTrue((System.nanoTime() - t0) < 3_000_000_000L)
    }

    @Test
    fun deviceOnly_maLongSauKhongDuocLamTranNganXep() {
        // LƯU Ý: org.json 20240303 trên JVM tự chặn độ sâu 512 (JSONException) nên test này XANH ở đây;
        // org.json của Android (libcore JSONTokener) không có giới hạn → StackOverflowError, không bị catch (e: Exception).
        // Chạy lại dưới Robolectric / instrumented test để thấy lỗi.
        // ~800 ký tự mã → JSON lồng 200 000 tầng "[" (nhỏ hơn giới hạn 512 KB). Android org.json không giới hạn độ sâu.
        val deep = rawCode("{\"members\":" + "[".repeat(200_000) + "]".repeat(200_000) + "}")
        assertTrue("độ dài mã " + deep.length, deep.length < 2_000)
        try {
            assertNull(FamilyBook.decode("Dán thử mã này: $deep"))
        } catch (t: StackOverflowError) {
            fail("decode ném StackOverflowError (không bị catch (e: Exception) bắt) → app crash khi dán mã")
        }
    }

    @Test
    fun bug_maNhoTaoHangVanNguoiVaSuKien() {
        // 40 000 phần tử "{}" chỉ nén thành vài trăm ký tự
        val code = rawCode("{\"id\":\"g\",\"members\":[" + List(40_000) { "{}" }.joinToString(",") + "]}")
        assertTrue("độ dài mã " + code.length, code.length < 1_500)
        val g = FamilyBook.decode(code)!!
        val events = FamilyBook.toEvents(g)
        // Mong đợi: có giới hạn số người (vd ≤ 500) và bỏ người không tên. Thực tế: 40 000 sự kiện "Giỗ " vào EventStore.
        assertTrue("số sự kiện tạo ra: ${events.size}, tiêu đề '${events.firstOrNull()?.title}'", events.size <= 500)
    }

    @Test
    fun bug_nguoiKhongTenKhongQuanHeVanThanhSuKien() {
        val g = FamilyBook.decode(rawCode("{\"id\":\"g\",\"members\":[{\"id\":\"m1\",\"d\":5,\"m\":3}]}"))!!
        // familySaveMember từ chối người thiếu cả tên lẫn quan hệ, nhưng nhập mã thì không
        assertEquals("tiêu đề: '" + FamilyBook.toEvents(g).firstOrNull()?.title + "'", 0, g.active.size)
    }

    @Test
    fun nit_thieuIdThiMoiLanNhapLaiSinhBanSao() {
        val code = rawCode("{\"id\":\"g\",\"members\":[{\"name\":\"An\",\"d\":3,\"m\":4,\"u\":1}]}")
        val first = FamilyBook.merge(null, FamilyBook.decode(code)!!).group
        val second = FamilyBook.merge(first, FamilyBook.decode(code)!!).group
        assertEquals("nhập cùng một mã hai lần", 1, second.active.size)
    }

    @Test
    fun nit_thieuIdSoThiMoiLanNhapThanhSoMoi() {
        val code = rawCode("{\"name\":\"Nhà\",\"members\":[]}")
        assertEquals(FamilyBook.decode(code)!!.id, FamilyBook.decode(code)!!.id)
    }

    @Test
    fun nit_hoiNhieuMaThiLayMaDauTien() {
        // Copy cả đoạn chat có mã cũ (trên) và mã mới (dưới): lẽ ra lấy mã mới nhất / gộp cả hai.
        val old = base
        val newer = saveMember(base, mem("b", "Trần Thị Bình", 2, 2, 0), 2000)
        val chat = FamilyBook.shareText(old, LocalDate.of(2026, 1, 1)) + "\n\n" + FamilyBook.shareText(newer, LocalDate.of(2026, 11, 25))
        assertEquals(2, FamilyBook.decode(chat)!!.active.size)
    }

    @Test
    fun nit_tenNguoiChuaMaLamLechTinNhanChiaSe() {
        val evil = rawCode("{\"id\":\"x\"}")
        assertTrue("mã chèn dài " + evil.length + " ≤ 60 ký tự (vừa ô tên)", evil.length <= 60)
        val g = saveMember(base, mem("b", evil, 2, 2, 0), 2000)
        assertEquals("mã trong tin nhắn chia sẻ phải là của chính sổ", "g1", FamilyBook.decode(FamilyBook.shareText(g, LocalDate.of(2026, 11, 25)))!!.id)
    }

    @Test
    fun nit_catChuoiLamVoEmoji() {
        val j = org.json.JSONObject().put("id", "m").put("name", "A".repeat(59) + "😀").put("d", 1).put("m", 1)
        val name = FamilyMember.fromJson(j).name
        assertFalse("tên bị cắt còn nửa cặp surrogate: ...${name.takeLast(2)}", Character.isHighSurrogate(name.last()))
    }

    @Test
    fun nit_tinNhanNhacTruoc0Ngay() {
        val text = FamilyBook.shareText(base.copy(remind = 0), LocalDate.of(2026, 11, 25))
        assertFalse(text.lines().first { it.contains("nhắc") }, text.contains("nhắc trước 0 ngày"))
    }

    // ---------- Giới hạn, id, ngày giỗ ----------

    @Test
    fun ok_gioiHanTruongVaId() {
        val g = FamilyBook.decode(
            rawCode(
                "{\"id\":\"ab-c/../d\",\"remind\":999,\"members\":[{\"id\":\"x-1\",\"name\":\"An\",\"d\":31,\"m\":13,\"y\":1700}," +
                    "{\"id\":\"x 2\",\"name\":\"Bình\",\"d\":0,\"m\":0,\"y\":\"1975\"}]}",
            ),
        )!!
        assertEquals("abcd", g.id)
        assertEquals(30, g.remind)
        assertEquals(listOf("x1", "x2"), g.members.map { it.id })
        assertEquals(listOf(30, 1), g.members.map { it.lunarDay })
        assertEquals(listOf(12, 1), g.members.map { it.lunarMonth })
        assertEquals(listOf(null, 1975), g.members.map { it.deathYear })
        assertEquals(0, FamilyBook.decode(rawCode("{\"id\":\"q\",\"remind\":-5}"))!!.remind)
        // Tiền tố sự kiện không lẫn giữa sổ "ab" và "abc"
        val e = FamilyBook.eventId("abc", "m1")
        assertFalse(e.startsWith("${FamilyBook.EVENT_PREFIX}ab-"))
    }

    @Test
    fun ok_ngayGioToiKhopVoiLichSuKien() {
        // nextDate (hiện trong sổ) phải trùng lần xuất hiện đầu tiên theo EventStore.occursOn (nhắc/lịch)
        val froms = (0 until 14).map { LocalDate.of(2026, 1, 3).plusDays(it * 151L) }
        for (mo in 1..12) for (d in listOf(1, 15, 29, 30)) {
            val m = mem("m", "X", d, mo, 1)
            val ev = FamilyBook.toEvents(FamilyGroup("g", "G", listOf(m), 3, 1)).single()
            for (from in froms) {
                val next = FamilyBook.nextDate(m, from)
                assertNotNull("$d/$mo từ $from", next)
                var x = from
                while (!EventStore.occursOn(ev, x)) x = x.plusDays(1)
                assertEquals("$d/$mo âm từ $from", x, next)
            }
        }
    }

    @Test
    fun ok_ngay30ThangThieuLamNgay29() {
        var checked = 0
        for (y in 2026..2030) for (mo in 1..12) {
            if (LunarCalendar.toSolar(30, mo, y) != null) continue
            val d29 = LunarCalendar.toSolar(29, mo, y)!!
            val next = FamilyBook.nextDate(mem("m", "X", 30, mo, 1), d29.minusDays(3))
            assertEquals("30/$mo/$y thiếu", d29, next)
            assertEquals(1, LunarCalendar.convert(next!!.plusDays(1)).day)
            checked++
        }
        assertTrue(checked > 10)
    }
}
