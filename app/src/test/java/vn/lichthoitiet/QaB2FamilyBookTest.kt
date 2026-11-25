package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64
import java.util.zip.Deflater
import kotlin.random.Random

/**
 * QA tester B, vòng 2: sổ giỗ. "bug_…"/"nit_…"/"compat_…" khẳng định hành vi ĐÚNG mong đợi nên đang FAIL để chứng minh lỗi;
 * "ok_…" là phần đã kiểm tra và đúng (hồi quy).
 */
class QaB2FamilyBookTest {
    private val T0 = LocalDate.of(2026, 11, 25).toEpochDay() * 86_400_000L + 8 * 3_600_000L
    private val SEC = 1_000L
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

    private fun mem(id: String, name: String, d: Int, mo: Int, u: Long, who: String = "", rel: String = "bà nội") =
        FamilyMember(id, name, rel, d, mo, null, "", who, "", u)

    // Mô phỏng đúng các hàm cầu nối trong MainActivity (familySaveMember / familyDeleteMember / familyUpdate)
    // Dùng đúng các hàm thuần mà cầu nối trong MainActivity gọi (familySaveMember / familyDeleteMember / familyUpdate)
    private fun save(g: FamilyGroup, m: FamilyMember, now: Long) = FamilyBook.saveMember(g, m, now)!!

    private fun del(g: FamilyGroup, id: String, now: Long) = FamilyBook.deleteMember(g, id, now)

    private fun meta(g: FamilyGroup, name: String, remind: Int, now: Long) = FamilyBook.updateMeta(g, name, remind, now) ?: g

    /** Gửi sổ qua tin nhắn (cả đoạn chia sẻ) rồi máy kia dán vào. */
    private fun send(g: FamilyGroup) = FamilyBook.decode("Gửi cả nhà\n" + FamilyBook.shareText(g, today) + "\n👍")!!

    private fun recv(dst: FamilyGroup, src: FamilyGroup, now: Long) = FamilyBook.merge(dst, send(src), now)

    private val base = FamilyGroup("g1", "Họ Nguyễn", listOf(mem("a", "Nguyễn Văn An", 1, 1, T0 - DAY)), 3, T0 - DAY)

    // ---------- Ba máy, đồng hồ lệch ít ----------

    /** Khóa so sánh cố định của một phiên bản người (giống phép chọn trong merge). */
    private fun newer(a: FamilyMember, b: FamilyMember) =
        if (a.updatedAt != b.updatedAt) a.updatedAt > b.updatedAt else a.toJson().toString() > b.toJson().toString()

    private fun simulate(seed: Int, skews: List<Long>, steps: Int): List<String> {
        val rnd = Random(seed)
        val phones = MutableList(3) { base }
        val versions = mutableMapOf<String, MutableList<FamilyMember>>()
        base.members.forEach { versions.getOrPut(it.id) { mutableListOf() } += it }
        // Tên sổ và số ngày nhắc là hai giá trị độc lập, mỗi thứ theo lần sửa (thật sự đổi giá trị) sau cùng
        var nameBest = base.metaAt to base.name
        var remindBest = base.remindAt to base.remind
        var t = T0
        var nextId = 0
        val problems = mutableListOf<String>()
        repeat(steps) {
            t += rnd.nextLong(5 * SEC, 20 * MIN)
            val p = rnd.nextInt(3)
            val now = t + skews[p]
            val g = phones[p]
            val live = g.active
            when (val op = rnd.nextInt(10)) {
                0, 1 -> {
                    val m = mem("n${seed}x${nextId++}", "Người ${rnd.nextInt(1000)}", rnd.nextInt(1, 31), rnd.nextInt(1, 13), 0, who = "Ai ${rnd.nextInt(5)}")
                    phones[p] = save(g, m, now)
                    versions.getOrPut(m.id) { mutableListOf() } += phones[p].members.first { it.id == m.id }
                }
                2, 3 -> if (live.isNotEmpty()) {
                    val m = live[rnd.nextInt(live.size)].copy(assignee = "Ai ${rnd.nextInt(5)}", lunarDay = rnd.nextInt(1, 31))
                    phones[p] = save(g, m, now)
                    versions.getOrPut(m.id) { mutableListOf() } += phones[p].members.first { it.id == m.id }
                }
                4 -> if (live.isNotEmpty()) {
                    val id = live[rnd.nextInt(live.size)].id
                    phones[p] = del(g, id, now)
                    versions.getOrPut(id) { mutableListOf() } += phones[p].members.first { it.id == id }
                }
                5 -> {
                    val name = "Sổ ${rnd.nextInt(100)}"
                    val remind = rnd.nextInt(0, 8)
                    phones[p] = meta(g, name, remind, now)
                    val after = phones[p]
                    if (after.name != g.name && (after.metaAt > nameBest.first || (after.metaAt == nameBest.first && after.name > nameBest.second)))
                        nameBest = after.metaAt to after.name
                    if (after.remind != g.remind && (after.remindAt > remindBest.first || (after.remindAt == remindBest.first && after.remind > remindBest.second)))
                        remindBest = after.remindAt to after.remind
                }
                else -> {
                    val src = (p + 1 + rnd.nextInt(2)) % 3
                    phones[p] = recv(g, phones[src], now).group
                    if (op == 9) phones[p] = FamilyGroup.fromJson(org.json.JSONObject(phones[p].toJson().toString())) // lưu rồi đọc lại
                }
            }
        }
        // Một giờ sau, mọi người gửi chéo cho nhau đủ nhiều lượt
        t += 3_600_000L
        repeat(3) {
            for (s in 0 until 3) for (d in 0 until 3) if (s != d) {
                t += SEC
                phones[d] = recv(phones[d], phones[s], t + skews[d]).group
            }
        }
        fun norm(g: FamilyGroup) = g.members.sortedBy { it.id }
        if (norm(phones[0]) != norm(phones[1]) || norm(phones[1]) != norm(phones[2])) problems += "seed $seed: danh sách người không hội tụ"
        if (phones.map { it.name to it.remind }.toSet().size != 1) problems += "seed $seed: tên/nhắc không hội tụ ${phones.map { it.name to it.remind }}"
        // Mỗi người phải là bản ghi có mốc lớn nhất từng được ghi (last-writer-wins)
        for ((id, vs) in versions) {
            val expect = vs.reduce { a, b -> if (newer(b, a)) b else a }
            val got = phones[0].members.firstOrNull { it.id == id }
            if (got != expect) problems += "seed $seed: người $id = ${got?.let { "${it.lunarDay}/${it.assignee}/del=${it.deleted}@${it.updatedAt - T0}" }}, mong đợi ${expect.lunarDay}/${expect.assignee}/del=${expect.deleted}@${expect.updatedAt - T0}"
        }
        if (phones[0].name to phones[0].remind != nameBest.second to remindBest.second)
            problems += "seed $seed: tên/nhắc ${phones[0].name}/${phones[0].remind}, mong đợi ${nameBest.second}/${remindBest.second}"
        return problems
    }

    @Test
    fun ok_baMayLechGioItHoiTuVaDungBanSauCung() {
        // Lệch tương đối tối đa 8 phút (< 10 phút): không bản nào bị coi là "tương lai"
        val problems = (1..40).flatMap { simulate(it, listOf(0L, 4 * MIN, -4 * MIN), 80) }
        assertEquals(problems.take(10).joinToString("\n"), 0, problems.size)
    }

    @Test
    fun ok_baMayLechGio11PhutVanHoiTu() {
        // Lệch tương đối 11 phút: vài bản sửa bị kẹp mốc; chỉ đòi hội tụ (không đòi đúng bản sau cùng)
        val problems = (1..40).flatMap { simulate(it, listOf(0L, 4 * MIN, -7 * MIN), 80) }.filter { "không hội tụ" in it }
        assertEquals(problems.take(10).joinToString("\n"), 0, problems.size)
    }

    @Test
    fun bug_kepMocTuongLaiVeBayGioDeMatBanSuaThat() {
        // Máy W để đồng hồ nhanh 1 ngày, thêm người "x" (ngày 5/3). C nhập mã lúc t1, sửa x lúc t1+1' (ngày 6/3, thật).
        // B nhập mã của W muộn hơn (t1+2'): x trên B mang mốc "bây giờ" của B = t1+2' > bản sửa thật của C.
        val w = save(base, mem("x", "Trần Văn Xuân", 5, 3, 0), T0 + DAY)
        val c1 = recv(base, w, T0).group
        val c2 = save(c1, c1.members.first { it.id == "x" }.copy(lunarDay = 6), T0 + 1 * MIN)
        val b1 = recv(base, w, T0 + 2 * MIN).group
        val b2 = recv(b1, c2, T0 + 3 * MIN).group
        val c3 = recv(c2, b2, T0 + 4 * MIN).group
        assertEquals(
            "ngày giỗ của x trên (B, C) — bản sửa thật của C bị bản kẹp mốc của B đè",
            listOf(6, 6),
            listOf(b2.active.first { it.id == "x" }.lunarDay, c3.active.first { it.id == "x" }.lunarDay),
        )
    }

    @Test
    fun bug_maMayChayNhanhDayMocTenSoLenBayGioLamMatDoiTenThat() {
        // C đổi tên sổ lúc T0−1h (thật). W (đồng hồ nhanh 1 năm) đổi tên → bị bỏ qua đúng thiết kế,
        // nhưng merge vẫn kéo metaAt của L lên "bây giờ" mà không đổi tên ⇒ tên của C (cũ hơn "bây giờ") bị từ chối.
        val c = meta(base, "Nhà nội", 7, T0 - 3_600_000L)
        val w = meta(base, "Tên từ máy sai giờ", 1, T0 + 365 * DAY)
        val l1 = recv(base, w, T0).group
        assertEquals("Họ Nguyễn", l1.name)          // đúng: bản tương lai không đè
        val l2 = recv(l1, c, T0 + MIN).group
        val c2 = recv(c, l2, T0 + 2 * MIN).group      // L gửi lại cho C
        assertEquals("tên sổ trên (L, C)", listOf("Nhà nội", "Nhà nội"), listOf(l2.name, c2.name))
    }

    @Test
    fun nit_doiTenVaDoiNgayNhacDungChungMotMoc() {
        // A đổi tên lúc +1', C (chưa nhận) chỉ đổi số ngày nhắc lúc +2' → familyUpdate ghi cả tên cũ với mốc mới
        val a = meta(base, "Nhà nội", 3, T0 + 1 * MIN)
        val c = meta(base, base.name, 7, T0 + 2 * MIN)
        val a2 = recv(a, c, T0 + 3 * MIN).group
        val c2 = recv(c, a, T0 + 3 * MIN).group
        assertEquals(listOf("Nhà nội", 7, "Nhà nội", 7), listOf(a2.name, a2.remind, c2.name, c2.remind))
    }

    @Test
    fun data_suaNgaySauKhiNhanTuMayNhanhGioBiMat() {
        // B nhanh 4 phút (trong ngưỡng 10'), thêm "x" ghi nhầm ngày 5/3. A nhận rồi sửa ngay thành 6/3 (1 phút sau).
        // Bản sửa của A mang mốc nhỏ hơn mốc của B ⇒ khi trao mã, cả hai máy quay về 5/3.
        val b = save(base, mem("x", "Trần Văn Xuân", 5, 3, 0), T0 + 4 * MIN)
        val a1 = recv(base, b, T0 + 30 * SEC).group
        val a2 = save(a1, a1.members.first { it.id == "x" }.copy(lunarDay = 6), T0 + 90 * SEC)
        val a3 = recv(a2, b, T0 + 5 * MIN).group
        val b2 = recv(b, a2, T0 + 9 * MIN).group
        assertEquals("ngày giỗ x trên (A, B) sau khi A sửa", listOf(6, 6), listOf(a3.active.first { it.id == "x" }.lunarDay, b2.active.first { it.id == "x" }.lunarDay))
    }

    @Test
    fun design_mayTungSaiGioTuongLaiKhongBaoGioNhanBanSuaMoi() {
        // W từng chạy nhanh 1 năm và sửa "a"; sau đó chỉnh lại giờ. B sửa "a" thật (T0+1h) và gửi cho W.
        val w = save(base, base.members.first().copy(assignee = "Chú Ba"), T0 + 365 * DAY)
        val b = save(recv(base, w, T0).group, base.members.first().copy(assignee = "Cô Tư"), T0 + 3_600_000L)
        val w2 = recv(w, b, T0 + 2 * 3_600_000L).group
        val b2 = recv(b, w2, T0 + 3 * 3_600_000L).group
        assertEquals("người lo đồ cúng trên (W, B)", w2.active.single().assignee, b2.active.single().assignee)
    }


    @Test
    fun ok_maKhongCoMuVanDocVaDoiTenTuBanCuVanNhan() {
        // Bản cũ đổi tên (u tăng) thì máy mới vẫn nhận tên đó
        val oldJson = org.json.JSONObject().put("id", "g1").put("name", "Nhà ngoại").put("remind", 5).put("u", T0 + MIN)
            .put("members", org.json.JSONArray().put(base.members.first().toJson()))
        val r = FamilyBook.merge(base, FamilyBook.decode(rawCode(oldJson.toString()))!!, T0 + 2 * MIN).group
        assertEquals("Nhà ngoại" to 5, r.name to r.remind)
    }

    @Test
    fun ok_xoaSuaDoiTenXenKeHaiChieuHoiTu() {
        // A xóa a; B sửa a trước đó; C đổi tên; vòng gửi A→B→C→A rồi C→B
        val a = del(base, "a", T0 + 3 * MIN)
        val b = save(base, base.members.first().copy(assignee = "Chú Út"), T0 + 2 * MIN)
        val c = meta(save(base, mem("c", "Lê Văn Cường", 9, 9, 0), T0 + MIN), "Họ Nguyễn – Lê", 5, T0 + 4 * MIN)
        val b1 = recv(b, a, T0 + 5 * MIN).group
        val c1 = recv(c, b1, T0 + 6 * MIN).group
        val a1 = recv(a, c1, T0 + 7 * MIN).group
        val b2 = recv(b1, c1, T0 + 8 * MIN).group
        for (g in listOf(a1, b2, c1)) {
            assertEquals(listOf("c"), g.active.map { it.id })
            assertEquals("Họ Nguyễn – Lê", g.name)
            assertEquals(5, g.remind)
            assertTrue(g.members.first { it.id == "a" }.deleted)
        }
        // Dấu xóa đã lột nội dung vẫn đi qua mã và vẫn thắng bản sửa cũ hơn
        val tomb = send(a).members.first { it.id == "a" }
        assertTrue(tomb.deleted && tomb.name.isEmpty() && tomb.relation.isEmpty())
    }

    // ---------- Giới hạn kích thước ----------

    // Ghi chú, nơi thờ dài nhưng vẫn trong giới hạn ô nhập (120, 80 ký tự)
    private val longNote = (
        "Cúng mâm cỗ chay: xôi gấc, chè đỗ đen, bánh chưng, giò lụa; nhờ chú Út ở Hải Phòng và cô Tư ở Nam Định về sớm phụ bếp, " +
            "nhớ mua hương trầm"
        ).take(120)
    private val longPlace = "Nhà thờ họ, thôn Đông Thượng, xã Hải Đường, huyện Hải Hậu, tỉnh Nam Định (cạnh đình)".take(80)

    private fun bigBook(n: Int, tombs: Int = 0, place: String = longPlace, note: String = longNote) =
        FamilyGroup(
            "big", "Họ Nguyễn Đình – chi trưởng",
            List(n) { i ->
                FamilyMember(
                    FamilyBook.newId() + i, "Nguyễn Đình Thượng Nghiễm $i", "cụ ông đời thứ ${i % 12 + 1}", i % 30 + 1, i % 12 + 1,
                    1800 + i % 200, place, "Chi ${i % 7} – nhà bác Cả", note, T0 - i,
                )
            } + List(tombs) { i -> FamilyMember("t$i", "", "", 1, 1, null, "", "", "", T0 - i, deleted = true) },
            7, T0,
        )

    @Test
    fun ok_so300NguoiBinhThuongKhuHoiVaKichThuocTinNhan() {
        val g = bigBook(300, place = "Hải Hậu, Nam Định", note = "Cỗ chay")
        val text = FamilyBook.shareText(g, today)
        val code = FamilyBook.encode(g)
        System.err.println("QAB2 300 người (ghi chú ngắn): JSON ${g.toJson().toString().toByteArray().size} B, mã ${code.length} ký tự, tin nhắn ${text.length} ký tự")
        assertEquals(g, FamilyBook.decode(text))
    }

    @Test
    fun bug_soHopLeNhieuGhiChuVuot128KBThiMaKhongNhapDuoc() {
        // 300 người, mọi trường đều trong giới hạn nhập (ghi chú < 120, nơi < 80 ký tự): máy gửi lưu, xem bình thường,
        // nhưng mọi máy nhận báo "Không tìm thấy mã" vì JSON giải nén > 128 KB.
        val g = bigBook(300)
        assertTrue(g.members.all { it.note.length <= 120 && it.place.length <= 80 && it.name.length <= 60 && it.relation.length <= 40 })
        val json = g.toJson().toString().toByteArray().size
        assertTrue("tiền đề: JSON $json B phải > 128 KB", json > 128 * 1024)
        val text = FamilyBook.shareText(g, today)
        System.err.println("QAB2 300 người (ghi chú dài): JSON $json B, tin nhắn ${text.length} ký tự")
        assertNotNull("JSON $json B > 131072 ⇒ decode trả null", FamilyBook.decode(text))
    }

    @Test
    fun bug_dauXoaTinhVaoGioiHan300NguoiMoiBiMatKhiDocLai() {
        // Sổ 280 người còn sống + 20 dấu xóa (đã xóa 20 lần). Thêm 1 người: lưu xong đọc lại (FamilyStore.load) thì mất người mới.
        val g = bigBook(280, tombs = 20, place = "", note = "")
        val added = save(g, mem("moi", "Phạm Thị Mới", 3, 4, 0), T0 + MIN)
        assertEquals(281, added.active.size)
        val reloaded = FamilyGroup.fromJson(org.json.JSONObject(added.toJson().toString()))   // như FamilyStore.load
        val viaCode = FamilyBook.decode(FamilyBook.encode(added))!!
        assertEquals("số người còn sống sau khi lưu/đọc lại, sau khi gửi mã", listOf(281, 281), listOf(reloaded.active.size, viaCode.active.size))
    }

    // ---------- Mã trong tin nhắn ----------

    @Test
    fun nit_maBiXuongDongGiuaChungThiNhanNhamBanCu() {
        // Ứng dụng nhắn tin / email ngắt dòng mã dài (76 ký tự/dòng). Đoạn dán có bản cũ ở trên, bản mới (bị ngắt) ở dưới.
        val old = base
        val newer = save(base, mem("b", "Trần Thị Bình", 2, 2, 0), T0)
        val code = FamilyBook.encode(newer)
        val wrapped = code.chunked(76).joinToString("\n")
        val chat = FamilyBook.shareText(old, today) + "\n\n" + wrapped
        // Mong đợi [2, 2]. Thực tế [null, 1]: bản mới không đọc được, và có bản cũ phía trên thì lặng lẽ nhập bản cũ
        assertEquals(
            "(chỉ bản mới bị ngắt dòng, bản cũ + bản mới bị ngắt dòng)",
            listOf(2, 2), listOf(FamilyBook.decode(wrapped)?.active?.size, FamilyBook.decode(chat)?.active?.size),
        )
    }

    @Test
    fun nit_banCuDanSauBanMoiThiNhapBanCu() {
        // Cả nhà chuyển tiếp lại tin cũ sau tin mới: mã cuối là bản CŨ ⇒ nhập bản cũ (bỏ qua bản mới ngay phía trên)
        val newer = save(base, mem("b", "Trần Thị Bình", 2, 2, 0), T0)
        val chat = FamilyBook.shareText(newer, today) + "\n\n[Chuyển tiếp]\n" + FamilyBook.shareText(base, today)
        val local = FamilyBook.merge(null, FamilyBook.decode(chat)!!, T0 + MIN).group
        assertEquals(2, local.active.size)
    }

    @Test
    fun nit_haiSoKhacNhauTrongMotLanDanChiNhapSoCuoi() {
        val noi = base
        val ngoai = FamilyGroup("g2", "Họ Lê", listOf(mem("z", "Lê Thị Zung", 4, 4, T0)), 3, T0)
        val chat = FamilyBook.shareText(noi, today) + "\n\n" + FamilyBook.shareText(ngoai, today)
        val found = Regex("""LICHGIAPHA1:[A-Za-z0-9_-]+""").findAll(chat).mapNotNull { FamilyBook.decode(it.value)?.id }.toSet()
        assertEquals(setOf("g1", "g2"), found)
        // decodeAll trả mọi sổ trong một lần dán (FamilyStore.importText nhập tất cả)
        assertEquals("số sổ nhập được từ một lần dán", 2, FamilyBook.decodeAll(chat).size)
    }

    @Test
    fun ok_kyTuDacBietVaNgoacTrongChuoiKhongBiChanDoSau() {
        val weird = "[[[[{{{{\\\"]]]}}}\\"
        val g = FamilyGroup(
            "w", "{[ \"Sổ\" ]}\\",
            List(40) { i -> FamilyMember("m$i", weird + i, "\\\\[\"{", 1 + i % 29, 1 + i % 12, null, "[".repeat(80), "]".repeat(40), "\"".repeat(120), T0) },
            2, T0,
        )
        assertEquals(g, FamilyBook.decode("x " + FamilyBook.encode(g) + " y"))
    }

    // ---------- Số liệu báo cho người dùng ----------

    @Test
    fun nit_doiTenSoMaUiBaoKhongCoGiMoi() {
        val renamed = meta(base, "Nhà nội", 7, T0)
        val r = FamilyBook.merge(base, send(renamed), T0 + MIN)
        assertEquals("Nhà nội", r.group.name)
        // Đổi tên / số ngày nhắc được báo qua metaChanged (UI không còn nói "không có gì mới")
        assertTrue("added=${r.added} updated=${r.updated} removed=${r.removed} metaChanged=${r.metaChanged}", r.metaChanged)
    }

    @Test
    fun nit_nguoiSongLaiDemLaSuaKhongPhaiThem() {
        val a = del(base, "a", T0)
        val b = save(base, base.members.first().copy(assignee = "Chú Ba"), T0 + MIN)  // sửa sau khi xóa ⇒ sống lại
        val r = FamilyBook.merge(a, send(b), T0 + 2 * MIN)
        assertEquals(1, r.group.active.size)
        assertEquals("người hiện lại trong sổ: added=${r.added} updated=${r.updated}", 1, r.added)
    }

    @Test
    fun nit_tenSoCatGiuaEmojiDoiThanhDauHoi() {
        // FamilyStore.create / updateMeta cắt bằng takeSafe(40): không để nửa emoji, gửi mã giữ nguyên tên
        val g = FamilyBook.updateMeta(base, "Nhà A" + "🌸".repeat(30), base.remind, T0)!!
        assertTrue(!Character.isHighSurrogate(g.name.last()))
        assertEquals("tên sổ sau khi gửi mã", g.name, FamilyBook.decode(FamilyBook.encode(g))!!.name)
    }
}
