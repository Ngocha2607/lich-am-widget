package vn.lichthoitiet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64
import java.util.zip.Deflater
import kotlin.random.Random

/**
 * QA tester B, vòng 3: sổ giỗ. "bug_…"/"nit_…" khẳng định hành vi ĐÚNG mong đợi nên FAIL để chứng minh lỗi;
 * "ok_…" là kiểm tra hồi quy (đang đúng).
 */
class QaB3FamilyBookTest {
    private val MIN = 60_000L
    private val DAY = 86_400_000L
    private val today = LocalDate.of(2026, 11, 25)

    private fun rawCode(json: String): String {
        val d = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(json.toByteArray(Charsets.UTF_8)); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return FamilyBook.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    private fun mem(id: String, name: String, d: Int, mo: Int, u: Long = 0, who: String = "", rel: String = "bà nội") =
        FamilyMember(id, name, rel, d, mo, null, "", who, "", u)

    private fun reload(g: FamilyGroup) = FamilyGroup.fromJson(JSONObject(g.toJson().toString()))   // như FamilyStore.load

    private fun send(g: FamilyGroup) = FamilyBook.decode("Gửi cả nhà\n" + FamilyBook.shareText(g, today) + "\n👍")!!

    private fun book(id: String, n: Int, prefix: String, u: Long) = FamilyGroup(
        id, "Họ Nguyễn",
        List(n) { i -> FamilyMember("$prefix$i", "Nguyễn Văn $prefix $i", "cụ", i % 29 + 1, i % 12 + 1, null, "", "", "", u + i) },
        3, u,
    )

    // ======================= Giới hạn 300 / 600 khi gộp rồi đọc lại =======================

    @Test
    fun ok_gopHaiNhanhVuot300ThiDocLaiKhongMatThem() {
        // (Bản 20:16 của repo: merge ra 400 người, đọc lại còn 300 — mất người tháng 9–12. Bản 20:30 đã chặn trong merge.)
        val t = System.currentTimeMillis() - DAY
        val a = book("g1", 200, "a", t)
        val b = book("g1", 200, "b", t)
        val r = FamilyBook.merge(a, send(b))
        assertEquals(300, r.group.active.size)
        assertEquals(100, r.added)
        assertTrue(a.active.all { m -> r.group.active.any { it.id == m.id } })   // người đã có trên máy không bị bỏ
        assertEquals(r.group.active.size, reload(r.group).active.size)
        assertEquals(300, FamilyBook.toEvents(r.group).size)
    }

    @Test
    fun ok_nhieuDauXoaKhongDayNguoiConSongKhiDocLai() {
        // 100 người còn sống; mỗi máy giữ 300 dấu xóa (khác nhau). Gộp xong sổ có 100 người + 600 dấu xóa, xếp theo tháng âm.
        // fromJson chỉ đọc 600 phần tử ĐẦU mảng ⇒ người còn sống ở tháng 11–12 bị bỏ dù chỉ có 100 người.
        val t = System.currentTimeMillis() - 10 * DAY
        val live = book("g1", 100, "m", t).members
        fun tombs(p: String) = List(300) { i -> FamilyMember("$p$i", "", "", i % 29 + 1, i % 12 + 1, null, "", "", "", t + 1000 + i, deleted = true) }
        val a = FamilyGroup("g1", "Họ", live + tombs("x"), 3, t)
        val b = FamilyGroup("g1", "Họ", live + tombs("y"), 3, t)
        assertEquals(400, reload(a).members.size)
        val merged = FamilyBook.merge(a, send(b)).group
        assertEquals(100, merged.active.size)
        assertEquals("người còn sống sau khi lưu rồi đọc lại", 100, reload(merged).active.size)
    }

    @Test
    fun ok_saveMemberTaiGioiHan300() {
        val t = System.currentTimeMillis()
        val g0 = book("g1", 300, "m", t - DAY)
        val g = FamilyBook.deleteMember(g0, "m5", t)
        assertEquals(299, g.active.size)
        // Thêm một người mới khi còn 299: được; sau đó đủ 300 thì không hồi sinh được dấu xóa, không thêm được nữa
        val g2 = FamilyBook.saveMember(g, mem("new1", "Người mới", 3, 3), t + 1)!!
        assertEquals(300, g2.active.size)
        assertNull(FamilyBook.saveMember(g2, mem("m5", "Hồi sinh", 3, 3), t + 2))
        assertNull(FamilyBook.saveMember(g2, mem("new2", "Người mới 2", 3, 3), t + 2))
        // Sửa người đang có khi đủ 300 vẫn được, và đọc lại không mất ai
        val g3 = FamilyBook.saveMember(g2, g2.active.first().copy(assignee = "Chú Ba"), t + 3)!!
        assertEquals(300, reload(g3).active.size)
        assertEquals(301, reload(g3).members.size)
        // Xóa một người rồi hồi sinh dấu xóa m5: được
        val g4 = FamilyBook.saveMember(FamilyBook.deleteMember(g3, "new1", t + 4), mem("m5", "Hồi sinh", 3, 3), t + 5)!!
        assertEquals(300, g4.active.size)
        assertTrue(g4.active.any { it.id == "m5" && it.name == "Hồi sinh" })
    }

    // ======================= decodeAll: nhiều phiên bản trong một lần dán =======================

    @Test
    fun bug_haiBanTuMayNhanhGioGopTheoThuTuTinNhan() {
        // Máy W nhanh 1 ngày: thêm "x" (ngày 5/3), 5 phút sau sửa thành 6/3. Cả nhà dán đoạn chat có cả hai tin của W.
        // Tài liệu decodeAll: "nhiều phiên bản của cùng một sổ được gộp với nhau (không phụ thuộc thứ tự tin nhắn)".
        val now = System.currentTimeMillis()
        val base = FamilyGroup("g1", "Họ Nguyễn", listOf(mem("a", "Nguyễn Văn An", 1, 1, now - DAY)), 3, now - DAY)
        val f1 = FamilyBook.saveMember(base, mem("x", "Trần Văn Xuân", 5, 3), now + DAY)!!
        val f2 = FamilyBook.saveMember(f1, f1.members.first { it.id == "x" }.copy(lunarDay = 6), now + DAY + 5 * MIN)!!
        val chronological = FamilyBook.shareText(f1, today) + "\n\n" + FamilyBook.shareText(f2, today)
        val reversed = FamilyBook.shareText(f2, today) + "\n\n" + FamilyBook.shareText(f1, today)
        val dayChrono = FamilyBook.merge(base, FamilyBook.decodeAll(chronological).single()).group.active.first { it.id == "x" }.lunarDay
        val dayRev = FamilyBook.merge(base, FamilyBook.decodeAll(reversed).single()).group.active.first { it.id == "x" }.lunarDay
        assertEquals("ngày giỗ x sau khi dán (đúng thứ tự chat, đảo thứ tự)", listOf(6, 6), listOf(dayChrono, dayRev))
    }

    @Test
    fun nit_maTrongTenNguoiTaoThemMotSoRongKhiNhap() {
        // Tên người (≤ 60 ký tự) chứa một mã ngắn hợp lệ: shareText in tên ra danh sách ⇒ decodeAll thấy 2 sổ,
        // FamilyStore.importText nhập cả sổ rỗng "Gia đình" lạ vào máy người nhận.
        val evil = rawCode("{\"id\":\"zz\"}")
        assertTrue(evil.length <= 60)
        val now = System.currentTimeMillis()
        val g = FamilyBook.saveMember(FamilyGroup("g1", "Họ Nguyễn", emptyList(), 3, now - DAY), mem("b", evil, 2, 2), now)!!
        val books = FamilyBook.decodeAll(FamilyBook.shareText(g, today))
        assertEquals("các sổ nhập được: ${books.map { it.id + "/" + it.name }}", listOf("g1"), books.map { it.id })
    }

    // ---- Mã bị ngắt dòng ----

    private fun realBook(n: Int, seed: Int = n): FamilyGroup {
        // Dữ liệu đa dạng như sổ thật (tên, nơi, người lo khác nhau) để độ nén sát thực tế
        val rnd = Random(seed)
        val now = 1_795_500_000_000L   // cố định để kết quả lặp lại được
        fun id() = "%012x".format(rnd.nextLong(1L shl 47))
        val ho = listOf("Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi", "Đỗ", "Ngô")
        val dem = listOf("Văn", "Thị", "Đình", "Hữu", "Ngọc", "Minh", "Xuân", "Thu", "Quang", "Kim")
        val ten = listOf("An", "Bình", "Cường", "Dung", "Giang", "Hạnh", "Khánh", "Lan", "Mạnh", "Nga", "Phúc", "Quý", "Sơn", "Thảo", "Uyên", "Vinh", "Xuyến", "Yến", "Tâm", "Hòa")
        val rels = listOf("ông nội", "bà nội", "ông ngoại", "bà ngoại", "bác cả", "chú út", "cô Tư", "cụ ông", "cụ bà", "ông chú", "bà cô")
        val places = listOf("Hải Hậu, Nam Định", "Gia Lâm, Hà Nội", "nhà thờ họ", "Thủy Nguyên, Hải Phòng", "Quận 3, TP.HCM", "", "", "Kiến Xương, Thái Bình")
        val who = listOf("nhà bác Cả", "chú Út", "cô Tư", "anh Hai", "", "chị Ba", "nhà ông Trưởng")
        val notes = listOf("", "", "Cỗ chay", "nhớ mời họ ngoại", "mua hoa cúc vàng", "", "cúng trước 1 ngày")
        return FamilyGroup(
            id(), "Họ ${ho[rnd.nextInt(ho.size)]} chi ${rnd.nextInt(1, 5)}",
            List(n) { i ->
                FamilyMember(
                    id(), "${ho[rnd.nextInt(ho.size)]} ${dem[rnd.nextInt(dem.size)]} ${ten[rnd.nextInt(ten.size)]}",
                    rels[rnd.nextInt(rels.size)], rnd.nextInt(1, 31), rnd.nextInt(1, 13), if (rnd.nextBoolean()) 1940 + rnd.nextInt(80) else null,
                    places[rnd.nextInt(places.size)], who[rnd.nextInt(who.size)], notes[rnd.nextInt(notes.size)], now - DAY + rnd.nextLong(1_000_000),
                )
            },
            3, now - DAY,
        )
    }

    @Test
    fun ok_doDaiTinNhanChiaSeThucTe() {
        val sizes = listOf(5, 10, 20, 50, 100, 300).map { n ->
            val g = realBook(n)
            "$n người: mã ${FamilyBook.encode(g).length} ký tự, tin nhắn ${FamilyBook.shareText(g, today).length} ký tự"
        }
        System.err.println("QAB3 độ dài chia sẻ: " + sizes.joinToString("; "))
    }

    @Test
    fun ok_maNgatDong76KyTuSoThucTe() {
        // Email / ứng dụng ngắt dòng cứng 76 ký tự: decodeWrapped chỉ nối tối đa 12 đoạn ⇒ mã > 13 dòng (~1 000 ký tự) không đọc được.
        val results = (1..40).map { n ->
            val g = realBook(n)
            val code = FamilyBook.encode(g)
            val wrapped = "Bố gửi:\n" + code.chunked(76).joinToString("\n") + "\nNhớ nhập nhé"
            Triple(n, code.chunked(76).size, FamilyBook.decode(wrapped) == g)
        }
        val failed = results.filter { !it.third }
        assertEquals("sổ n người → số dòng mã, không đọc được: ${failed.map { "${it.first} người/${it.second} dòng" }}", 0, failed.size)
    }

    @Test
    fun nit_maNgatDongDai500KyTuKhongNoiDuoc() {
        // Mỗi đoạn chỉ đọc trong cửa sổ 400 ký tự: mã ngắt thành dòng dài hơn 400 ký tự (vd 2 dòng × 600) không nối được
        val g = realBook(10)
        val code = FamilyBook.encode(g)
        assertTrue("tiền đề: mã ${code.length} ký tự > 600", code.length > 600)
        val wrapped = code.chunked(code.length / 2 + 1).joinToString("\n")
        assertEquals(2, wrapped.lines().size)
        assertEquals(g, FamilyBook.decode(wrapped))
    }

    @Test
    fun nit_chuDinhLienSauMaDocDuocHayKhongTuyDoDaiMa() {
        // "…<mã>Cảm ơn" (không có khoảng trắng): chữ 'C' dính vào mã. Đọc được khi độ dài base64 mod 4 ≠ 0, hỏng khi = 0.
        val outcomes = mutableMapOf<Int, Boolean>()
        var i = 0
        while (outcomes.size < 3 && i < 200) {
            val g = FamilyGroup("g1", "Sổ $i", listOf(mem("a", "An", 1, 1, 5)), 3, 5)
            val code = FamilyBook.encode(g)
            val r = (code.length - FamilyBook.PREFIX.length) % 4
            outcomes.getOrPut(r) { FamilyBook.decode(code + "Cảm ơn") == g }
            i++
        }
        assertEquals("đọc được theo (độ dài mod 4): $outcomes", setOf(true), outcomes.values.toSet())
    }

    @Test
    fun ok_noiDongKhongBaoGioRaSoSaiNoiDung() {
        // Ngắt mã ở chỗ ngẫu nhiên, chèn khoảng trắng/xuống dòng ngẫu nhiên, thêm chữ rác (cả chữ base64) phía sau,
        // hoặc làm mất đoạn cuối: kết quả chỉ được là đúng sổ gốc hoặc null, không bao giờ là một sổ khác.
        val rnd = Random(7)
        val g = realBook(3)
        val code = FamilyBook.encode(g)
        val body = code.removePrefix(FamilyBook.PREFIX)
        val words = listOf("ok", "Cảm", "ơn", "bố", "Nhớ", "nhập", "nha", "A", "QQ", "x_y", "abc-def", "LICHGIAPHA1", "👍", "1234")
        var wrongs = 0; var ok = 0; var nulls = 0
        repeat(1500) {
            val cuts = (0 until rnd.nextInt(1, 40)).map { rnd.nextInt(1, body.length) }.toSortedSet().toList()
            val pieces = mutableListOf<String>()
            var prev = 0
            for (c in cuts) { pieces += body.substring(prev, c); prev = c }
            pieces += body.substring(prev)
            if (rnd.nextInt(4) == 0 && pieces.size > 1) pieces.removeAt(pieces.size - 1)   // mất đoạn cuối
            val sep = { listOf("\n", " ", "\r\n", "\n\n", " \n ", "\t")[rnd.nextInt(6)] }
            val tail = (0 until rnd.nextInt(0, 4)).joinToString("") { sep() + words[rnd.nextInt(words.size)] }
            val text = "Mã: " + FamilyBook.PREFIX + pieces.joinToString("") { it + sep() }.trimEnd() + tail
            val d = FamilyBook.decode(text)
            when {
                d == null -> nulls++
                d == g -> ok++
                else -> wrongs++
            }
        }
        System.err.println("QAB3 nối dòng ngẫu nhiên: đúng $ok, null $nulls, SAI $wrongs")
        assertEquals(0, wrongs)
        assertTrue(ok > 500)
    }

    @Test
    fun ok_danHonHopNhieuSoNhieuBanVaRac() {
        val now = System.currentTimeMillis()
        val g1v1 = FamilyGroup("g1", "Họ Nguyễn", listOf(mem("a", "An", 1, 1, now - 3 * DAY)), 3, now - 3 * DAY)
        val g1v2 = FamilyBook.saveMember(g1v1, mem("b", "Bình", 2, 2), now - 2 * DAY)!!
        val g1v3 = FamilyBook.deleteMember(FamilyBook.updateMeta(g1v2, "Nhà nội", 5, now - DAY)!!, "a", now - DAY + 1)
        val g2 = FamilyGroup("g2", "Họ Lê", listOf(mem("z", "Lê Thị Zung", 4, 4, now - DAY)), 3, now - DAY)
        val small = FamilyGroup("g3", "Nhỏ", listOf(mem("q", "Quý", 9, 9, now - DAY)), 3, now - DAY)
        val wrapped3 = FamilyBook.encode(small).chunked(30).joinToString("\n")
        val chat = listOf(
            "[10:01] Bố: " + FamilyBook.shareText(g1v3, today),
            "[10:02] Mẹ: LICHGIAPHA1:hỏng-mất-rồi ok",
            "[10:03] Cô: " + FamilyBook.shareText(g2, today),
            "[10:04] Chú: $wrapped3",
            "rác giữa các đoạn: LICHGIAPHA1:AAAA BBBB CCCC",
            "[10:05] Bố (tin cũ chuyển tiếp): " + FamilyBook.shareText(g1v1, today),
            "[10:06] Mẹ: " + FamilyBook.shareText(g1v2, today),
        ).joinToString("\n")
        val all = FamilyBook.decodeAll(chat)
        assertEquals(listOf("g2", "g3", "g1"), all.map { it.id })
        val g1 = all.last()
        assertEquals("Nhà nội", g1.name)
        assertEquals(5, g1.remind)
        assertEquals(listOf("b"), g1.active.map { it.id })
        assertTrue(g1.members.first { it.id == "a" }.deleted)
        assertEquals(small, all[1])
    }

    // ---- Hiệu năng nối dòng (bản mới: tối đa 1000 đoạn, mỗi lần nối lại giải nén từ đầu) ----

    @Test
    fun perf_so300NguoiNgatDong64KyTu() {
        val g = realBook(300)
        val code = FamilyBook.encode(g)
        val wrapped = "Bố gửi:\n" + code.chunked(64).joinToString("\n") + "\nNhớ nhập nhé"
        FamilyBook.decode(FamilyBook.encode(realBook(5)).chunked(64).joinToString("\n"))   // làm nóng
        val t0 = System.nanoTime()
        val d = FamilyBook.decode(wrapped)
        val ms = (System.nanoTime() - t0) / 1_000_000
        System.err.println("QAB3 sổ 300 người, mã ${code.length} ký tự bẻ ${wrapped.lines().size} dòng: giải mã $ms ms trên JVM")
        assertEquals(g, d)
        assertTrue("$ms ms", ms < 1500)
    }

    @Test
    fun perf_doanDanCoMaRacNhieuDongKhongTreo() {
        // Đầu mã thật (60% đầu của một sổ lớn) + hàng trăm dòng chữ base64 rác: mỗi lần nối lại giải nén lại toàn bộ
        val code = FamilyBook.encode(realBook(300, seed = 99))
        val head = code.substring(0, code.length * 6 / 10)
        val rnd = Random(3)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val junk = (1..900).joinToString("") { "\n" + String(CharArray(76) { alphabet[rnd.nextInt(64)] }) }
        val zeros = (1..900).joinToString("") { "\n" + "A".repeat(76) }
        for ((label, text) in listOf("rác ngẫu nhiên" to head + junk, "rác chữ A" to head + zeros, "3 khối rác" to (head + zeros + "\n\n").repeat(3))) {
            val t0 = System.nanoTime()
            val r = FamilyBook.decodeAll(text)
            val ms = (System.nanoTime() - t0) / 1_000_000
            System.err.println("QAB3 dán $label (${text.length} ký tự): ${r.size} sổ, $ms ms trên JVM")
            assertTrue("$label: $ms ms", ms < 3000)
        }
    }

    // ---- Giới hạn 300 trong merge (bản mới: capMembers ưu tiên người đã có trên máy) ----

    @Test
    fun ok_vuot300MoiMayGiuNguoiCuaMinh() {
        val t = System.currentTimeMillis() - DAY
        val a = book("g1", 200, "a", t)
        val b = book("g1", 200, "b", t)
        var pa = a; var pb = b
        repeat(3) {
            pa = reload(FamilyBook.merge(pa, send(pb)).group)
            pb = reload(FamilyBook.merge(pb, send(pa)).group)
        }
        assertEquals(300, pa.active.size)
        assertEquals(300, pb.active.size)
        val onlyA = pa.active.map { it.id }.toSet() - pb.active.map { it.id }.toSet()
        // Hai máy cùng một sổ nhưng mãi khác nhau 100 người; lần nhập không báo có người bị bỏ qua
        val r = FamilyBook.merge(a, send(b))
        System.err.println("QAB3 vượt 300: máy A có ${onlyA.size} người máy B không có; lần nhập đầu báo added=${r.added} (bỏ qua ${400 - 300} người không báo)")
        // Thiết kế: vượt 300 người thì mỗi máy ưu tiên giữ người đã có trên máy mình (không xoá người của chủ máy),
        // nên hai máy có thể khác nhau phần dư; điều cần giữ là không máy nào mất người của chính nó
        assertTrue(a.active.map { it.id }.all { id -> pa.active.any { it.id == id } })
        assertTrue(b.active.map { it.id }.all { id -> pb.active.any { it.id == id } })
    }

    @Test
    fun ok_capMembersGiuNguoiDaCoVaDauXoaMoiNhat() {
        val t = System.currentTimeMillis() - DAY
        val local = book("g1", 300, "m", t)
        // Bản nhận được: xóa m0 (mới hơn) + thêm 5 người mới ⇒ còn 299 + 5 = 304 > 300
        val inc = FamilyBook.deleteMember(local, "m0", t + 10_000).let { g -> g.copy(members = g.members + List(5) { i -> mem("n$i", "Mới $i", 1, 12, t + 20_000 + i) }) }
        val r = FamilyBook.merge(local, inc)
        assertEquals(300, r.group.active.size)
        assertTrue(r.group.members.first { it.id == "m0" }.deleted)
        assertEquals(1, r.added)
        assertEquals(r.group, reload(r.group))
    }

    // ======================= Mô phỏng nhiều máy, đồng hồ lệch lớn, dán nhiều mã =======================

    private fun simulate(seed: Int): List<String> {
        val rnd = Random(seed)
        // Thời gian mô phỏng kết thúc trước giờ thật vài giờ: decodeAll (gộp bên trong theo giờ thật) thấy cùng một "tương lai"
        val t0 = System.currentTimeMillis() - 45 * 3_600_000L
        val base = FamilyGroup("g1", "Họ Nguyễn", listOf(mem("a", "Nguyễn Văn An", 1, 1, t0 - DAY)), 3, t0 - DAY)
        val n = 4
        val phones = MutableList(n) { base }
        val history = mutableListOf<String>()   // mọi mã từng được gửi
        var t = t0
        var nextId = 0
        // Đồng hồ từng máy lệch theo pha: máy 3 nhanh 2 ngày ở nửa đầu; máy 2 chậm 30 phút; máy 1 nhanh 12 phút
        fun skew(p: Int, step: Int) = when (p) {
            3 -> if (step < 60) 2 * DAY else 0L
            2 -> -30 * MIN
            1 -> 12 * MIN
            else -> 0L
        }
        for (step in 0 until 120) {
            t += rnd.nextLong(10_000, 20 * MIN)
            val p = rnd.nextInt(n)
            val now = t + skew(p, step)
            val g = phones[p]
            val live = g.active
            when (rnd.nextInt(12)) {
                0, 1 -> phones[p] = FamilyBook.saveMember(g, mem("n${seed}x${nextId++}", "Người ${rnd.nextInt(1000)}", rnd.nextInt(1, 31), rnd.nextInt(1, 13)), now) ?: g
                2, 3 -> if (live.isNotEmpty()) phones[p] = FamilyBook.saveMember(g, live[rnd.nextInt(live.size)].copy(assignee = "Ai ${rnd.nextInt(5)}", lunarDay = rnd.nextInt(1, 31)), now) ?: g
                4 -> if (live.isNotEmpty()) phones[p] = FamilyBook.deleteMember(g, live[rnd.nextInt(live.size)].id, now)
                5 -> if (rnd.nextBoolean()) phones[p] = FamilyBook.updateMeta(g, "Sổ ${rnd.nextInt(100)}", g.remind, now) ?: g
                    else phones[p] = FamilyBook.updateMeta(g, g.name, rnd.nextInt(0, 8), now) ?: g
                6 -> { // dán cả đoạn chat: nhiều mã cũ + mới của máy khác, thứ tự ngẫu nhiên, có khi ngắt dòng
                    if (history.isNotEmpty()) {
                        val picks = List(rnd.nextInt(1, 5)) { history[rnd.nextInt(history.size)] }
                        val text = picks.joinToString("\n...\n") { c -> if (c.length < 300 && rnd.nextBoolean()) c.chunked(60).joinToString("\n") else c }
                        val inc = FamilyBook.decodeAll(text).singleOrNull()
                        if (inc != null) phones[p] = FamilyBook.merge(g, inc, now).group
                    }
                }
                7 -> phones[p] = reload(g)
                else -> {
                    val src = (p + 1 + rnd.nextInt(n - 1)) % n
                    val code = FamilyBook.encode(phones[src])
                    history += code
                    phones[p] = FamilyBook.merge(g, FamilyBook.decode(code)!!, now).group
                }
            }
            phones[p] = reload(phones[p])   // FamilyStore luôn lưu rồi đọc lại
        }
        // Mọi đồng hồ đã đúng; 1 giờ sau, gửi chéo nhiều lượt
        t += 3_600_000L
        repeat(4) {
            for (s in 0 until n) for (d in 0 until n) if (s != d) {
                t += 1000
                phones[d] = reload(FamilyBook.merge(phones[d], send(phones[s]), t).group)
            }
        }
        val problems = mutableListOf<String>()
        fun key(g: FamilyGroup) = g.members.sortedBy { it.id }
        for (i in 1 until n) if (key(phones[i]) != key(phones[0])) {
            val a = phones[0].members.associateBy { it.id }; val b = phones[i].members.associateBy { it.id }
            val diff = (a.keys + b.keys).filter { a[it] != b[it] }.take(3).map { "$it: ${a[it]?.let { m -> "${m.lunarDay}/${m.deleted}@${m.updatedAt - t0}" }} vs ${b[it]?.let { m -> "${m.lunarDay}/${m.deleted}@${m.updatedAt - t0}" }}" }
            problems += "seed $seed: máy 0 và $i khác nhau: $diff"
        }
        if (phones.map { it.name to it.remind }.toSet().size != 1) problems += "seed $seed: tên/nhắc không hội tụ ${phones.map { it.name to it.remind }}"
        return problems
    }

    @Test
    fun ok_bonMayLechGioLonDanNhieuMaVanHoiTu() {
        val problems = (1..60).flatMap { simulate(it) }
        assertEquals(problems.take(8).joinToString("\n"), 0, problems.size)
    }

    @Test
    fun ok_doiTenVaDoiNhacDocLapGiuaBaMay() {
        val now = System.currentTimeMillis()
        val base = FamilyGroup("g1", "Họ Nguyễn", emptyList(), 3, now - DAY)
        // A đổi tên (T+1'), B đổi nhắc (T+2'), C đổi tên lần nữa (T+3') nhưng C đồng hồ chậm 5' ⇒ mốc C = T−2' (thua A)
        val a = FamilyBook.updateMeta(base, "Nhà nội", 3, now + MIN)!!
        val b = FamilyBook.updateMeta(base, "Họ Nguyễn", 9, now + 2 * MIN)!!
        val c = FamilyBook.updateMeta(base, "Nhà nội Nam Định", 3, now - 2 * MIN)!!
        val phones = mutableListOf(a, b, c)
        repeat(3) { for (s in 0..2) for (d in 0..2) if (s != d) phones[d] = FamilyBook.merge(phones[d], send(phones[s]), now + 10 * MIN).group }
        assertEquals(setOf("Nhà nội" to 9), phones.map { it.name to it.remind }.toSet())
        // C đã nhận tên của A rồi mới đổi: dù đồng hồ chậm, mốc mới vẫn lớn hơn (stamp = mốc cũ + 1) ⇒ tên của C thắng
        val c2 = FamilyBook.updateMeta(phones[2], "Nhà nội Nam Định", 9, now - 2 * MIN)!!
        val a2 = FamilyBook.merge(phones[0], send(c2), now + 11 * MIN).group
        assertEquals("Nhà nội Nam Định" to 9, a2.name to a2.remind)
    }

    // ======================= Sổ lớn sát 512 KB =======================

    @Test
    fun ok_soLonNhatHopLeVanGuiNhanDuoc() {
        // 300 người, mọi trường dài tối đa bằng chữ 3 byte UTF-8 (ngoài dải \u2000–\u20FF mà org.json JVM thoát thành \\uXXXX),
        // cộng 300 dấu xóa: JSON phải < 512 KB và khứ hồi nguyên vẹn
        val now = System.currentTimeMillis()
        fun s(n: Int, i: Int) = ("ệ".repeat(n - 4) + "%04d".format(i))
        val g = FamilyGroup(
            "big", s(40, 0),
            List(300) { i -> FamilyMember("m$i" + "x".repeat(20), s(60, i), s(40, i), i % 30 + 1, i % 12 + 1, 1900 + i, s(80, i), s(40, i), s(120, i), now - i) } +
                List(300) { i -> FamilyMember("t$i" + "y".repeat(20), "", "", i % 30 + 1, i % 12 + 1, null, "", "", "", now - 1000 - i, deleted = true) },
            30, now,
        )
        val json = g.toJson().toString().toByteArray().size
        val t0 = System.nanoTime()
        val code = FamilyBook.encode(g)
        val back = FamilyBook.decode(FamilyBook.shareText(g, today))
        val ms = (System.nanoTime() - t0) / 1_000_000
        System.err.println("QAB3 sổ lớn nhất: JSON $json B, mã ${code.length} ký tự, mã hóa+giải mã $ms ms")
        assertTrue("JSON $json B", json < 512 * 1024)
        assertEquals(g, back)
    }
}
