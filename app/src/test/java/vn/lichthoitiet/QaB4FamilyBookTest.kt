package vn.lichthoitiet

import org.json.JSONObject
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
 * QA tester B, vòng 4 (kiểm tra cuối): sổ giỗ. "bug_…"/"nit_…" khẳng định hành vi ĐÚNG mong đợi nên FAIL để chứng minh lỗi;
 * "ok_…"/"perf_…" là hồi quy (đang đúng).
 */
class QaB4FamilyBookTest {
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

    private fun reload(g: FamilyGroup) = FamilyGroup.fromJson(JSONObject(g.toJson().toString()))

    private fun share(g: FamilyGroup) = FamilyBook.shareText(g, today)

    private fun newer(a: FamilyMember, b: FamilyMember) =
        if (b.updatedAt > a.updatedAt || (b.updatedAt == a.updatedAt && b != a && b.toJson().toString() > a.toJson().toString())) b else a

    // ======================= decodeAll: bản từ máy nhanh giờ trong cùng một lần dán =======================

    @Test
    fun bug_danCaBanMayNhanhGioVaBanSuaThatThiBanSuaThatBiBo() {
        // W nhanh 1 ngày: sửa x (ngày 6), đổi tên sổ "Nhà W". R (đúng giờ) nhận mã của W (bị chặn vì giờ tương lai, đúng),
        // 5 phút sau sửa x thành ngày 9, xóa y, đổi tên "Nhà nội". Máy L (đúng giờ, còn bản gốc) dán đoạn chat có cả hai tin.
        val now = System.currentTimeMillis()
        val base = FamilyGroup(
            "g1", "Họ Nguyễn",
            listOf(mem("x", "Trần Văn Xuân", 5, 3, now - DAY), mem("y", "Lê Thị Yến", 7, 7, now - DAY)), 3, now - DAY,
        )
        val w = FamilyBook.updateMeta(FamilyBook.saveMember(base, base.members[0].copy(lunarDay = 6), now + DAY)!!, "Nhà W", 3, now + DAY)!!
        val rRecv = FamilyBook.merge(base, FamilyBook.decode(share(w))!!, now).group
        assertEquals("tiền đề: R chặn bản tương lai của W", 5, rRecv.members.first { it.id == "x" }.lunarDay)
        var r = FamilyBook.saveMember(rRecv, rRecv.members.first { it.id == "x" }.copy(lunarDay = 9), now + 5 * MIN)!!
        r = FamilyBook.deleteMember(r, "y", now + 5 * MIN)
        r = FamilyBook.updateMeta(r, "Nhà nội", 3, now + 5 * MIN)!!
        val at = now + 6 * MIN
        fun st(g: FamilyGroup) = "x=${g.members.first { it.id == "x" }.lunarDay}, y xóa=${g.members.firstOrNull { it.id == "y" }?.deleted}, tên=${g.name}"
        // Nhập hai tin riêng lẻ (W rồi R, hoặc R rồi W): bản sửa thật của R được áp
        val sep = FamilyBook.merge(FamilyBook.merge(base, FamilyBook.decode(share(w))!!, at).group, FamilyBook.decode(share(r))!!, at).group
        val sep2 = FamilyBook.merge(FamilyBook.merge(base, FamilyBook.decode(share(r))!!, at).group, FamilyBook.decode(share(w))!!, at).group
        assertEquals("x=9, y xóa=true, tên=Nhà nội", st(sep))
        assertEquals(st(sep), st(sep2))
        // Dán cả đoạn chat (hai thứ tự): decodeAll gộp không theo luật giờ tương lai ⇒ bản W (mốc +1 ngày) đè bản thật của R,
        // rồi lúc nhập vào máy thì bị chặn ⇒ x vẫn là 5 (không phải 6, cũng không phải 9), tên giữ "Họ Nguyễn"
        val p1 = FamilyBook.merge(base, FamilyBook.decodeAll(share(w) + "\n\n" + share(r)).single(), at)
        val p2 = FamilyBook.merge(base, FamilyBook.decodeAll(share(r) + "\n\n" + share(w)).single(), at)
        System.err.println("QAB4 dán W+R: ${st(p1.group)} (added ${p1.added}, updated ${p1.updated}, removed ${p1.removed}, metaChanged ${p1.metaChanged}); R+W: ${st(p2.group)}")
        assertEquals("dán cả hai tin (W trước, R trước) so với nhập riêng", listOf(st(sep), st(sep)), listOf(st(p1.group), st(p2.group)))
    }

    @Test
    fun bug_mayMoiDanDoanChatNhanNgayGioSaiCuaMayNhanhGio() {
        // W (nhanh 1 ngày) thêm cụ x ngày 5/3 (nhập nhầm). R (đúng giờ) đã nhận và sửa thành 9/3. Máy mới N dán cả đoạn chat
        // (tin của W + tin sửa của R): N nhận ngày của W (5/3) dù tin sửa của R nằm ngay trong đoạn dán.
        val now = System.currentTimeMillis()
        val base = FamilyGroup("g1", "Họ Nguyễn", listOf(mem("a", "Nguyễn Văn An", 1, 1, now - DAY)), 3, now - DAY)
        val w = FamilyBook.saveMember(base, mem("x", "Trần Văn Xuân", 5, 3), now + DAY)!!
        val rRecv = FamilyBook.merge(base, FamilyBook.decode(share(w))!!, now).group          // x được thêm với mốc 1
        val r = FamilyBook.saveMember(rRecv, rRecv.members.first { it.id == "x" }.copy(lunarDay = 9), now + 5 * MIN)!!
        assertEquals(9, FamilyBook.merge(null, FamilyBook.decode(share(r))!!, now + 6 * MIN).group.active.first { it.id == "x" }.lunarDay)
        val days = listOf(share(w) + "\n" + share(r), share(r) + "\n" + share(w)).map { text ->
            FamilyBook.merge(null, FamilyBook.decodeAll(text).single(), now + 6 * MIN).group.active.first { it.id == "x" }.lunarDay
        }
        assertEquals("ngày giỗ x trên máy mới (W+R, R+W)", listOf(9, 9), days)
    }

    @Test
    fun nit_dauXoaBiCatKhiVuot300ThiNhapLaiCungMaHoiSinhNguoiDaXoa() {
        // Máy L đã xóa Y (mốc t+5) sau bản sửa Y của máy P (t+1). Gộp có tổng dấu xóa > 300 ⇒ capMembers bỏ dấu xóa cũ nhất (của Y),
        // Y biến mất; dán lại CÙNG mã của P lần nữa ⇒ Y (bản cũ, đã bị xóa) quay lại, báo "thêm 1".
        val t = System.currentTimeMillis() - DAY
        fun tomb(id: String, u: Long) = FamilyMember(id, "", "", 1, 1, null, "", "", "", u, deleted = true)
        val live = List(10) { mem("L$it", "Người $it", it + 1, 1, t) }
        val local = FamilyGroup("g1", "Họ", live + tomb("Y", t + 5) + List(150) { tomb("a$it", t + 100 + it) }, 3, t)
        val p = FamilyGroup("g1", "Họ", live + mem("Y", "Lê Văn Y", 3, 3, t + 1) + List(200) { tomb("b$it", t + 1000 + it) }, 3, t)
        val r1 = FamilyBook.merge(local, FamilyBook.decode(share(p))!!)
        val r2 = FamilyBook.merge(r1.group, FamilyBook.decode(share(p))!!)
        System.err.println("QAB4 dấu xóa >300: lần 1 thêm ${r1.added}, Y còn dấu xóa: ${r1.group.members.any { it.id == "Y" }}; lần 2 (cùng mã) thêm ${r2.added}, Y sống: ${r2.group.active.any { it.id == "Y" }}")
        assertEquals("Y đã xóa (mốc sau) không được sống lại khi dán lại cùng mã", false, r2.group.active.any { it.id == "Y" })
    }

    @Test
    fun ok_danHaiBanCungTuMayNhanhGioVanLayBanSau() {
        // Hồi quy của lỗi vòng 3: hai bản đều từ W ⇒ lấy bản sau, không phụ thuộc thứ tự
        val now = System.currentTimeMillis()
        val base = FamilyGroup("g1", "Họ Nguyễn", listOf(mem("a", "An", 1, 1, now - DAY)), 3, now - DAY)
        val f1 = FamilyBook.saveMember(base, mem("x", "Xuân", 5, 3), now + DAY)!!
        val f2 = FamilyBook.saveMember(f1, f1.members.first { it.id == "x" }.copy(lunarDay = 6), now + DAY + 5 * MIN)!!
        for (text in listOf(share(f1) + "\n" + share(f2), share(f2) + "\n" + share(f1))) {
            val g = FamilyBook.merge(base, FamilyBook.decodeAll(text).single()).group
            assertEquals(6, g.active.first { it.id == "x" }.lunarDay)
        }
    }

    @Test
    fun nit_danNhieuBanCungSoVuot300PhuThuocThuTuTinNhan() {
        // Hai máy mỗi máy 200 người khác nhau (cùng sổ): đoạn chat có cả hai mã ⇒ hợp 400 > 300.
        // capMembers ưu tiên "bản trước" trong lần dán ⇒ máy thứ ba nhận 300 người nào là tùy thứ tự tin nhắn.
        val t = System.currentTimeMillis() - DAY
        val a = FamilyGroup("g1", "Họ", List(200) { mem("a$it", "A $it", it % 29 + 1, it % 12 + 1, t + it) }, 3, t)
        val b = FamilyGroup("g1", "Họ", List(200) { mem("b$it", "B $it", it % 29 + 1, it % 12 + 1, t + it) }, 3, t)
        val ab = FamilyBook.decodeAll(share(a) + "\n" + share(b)).single().active.map { it.id }.toSet()
        val ba = FamilyBook.decodeAll(share(b) + "\n" + share(a)).single().active.map { it.id }.toSet()
        System.err.println("QAB4 dán A+B: ${ab.count { it.startsWith("a") }} người của A; dán B+A: ${ba.count { it.startsWith("a") }} người của A")
        assertEquals(ab, ba)
    }

    // ======================= shareText: lọc tiền tố mã khỏi dòng chữ =======================

    @Test
    fun ok_tenSoChuaMaDongTieuDeDaLoc() {
        // Dòng đầu "📜 Sổ giỗ "<tên>"" không qua replace(PREFIX): tên sổ (≤ 40) là một mã ngắn hợp lệ ⇒ nhập thêm sổ "Gia đình" rỗng
        val tiny = rawCode("{}")
        assertTrue("mã '{}' dài ${tiny.length}", tiny.length <= 40)
        val now = System.currentTimeMillis()
        val g = FamilyBook.updateMeta(FamilyGroup("g1", "Họ Nguyễn", listOf(mem("a", "An", 1, 1, now - DAY)), 3, now - DAY), tiny, 3, now)!!
        assertEquals(tiny, g.name)
        val books = FamilyBook.decodeAll(share(g))
        assertEquals("sổ đọc được: ${books.map { it.id + "/" + it.name }}", listOf("g1"), books.map { it.id })
    }

    @Test
    fun nit_tenSoChuaTienToLongNhauVuotBoLocDongTieuDe() {
        // Bản 21:09 đã lọc dòng tiêu đề, nhưng vẫn một lượt replace: "LICHGIAPHA" + "LICHGIAPHA1:" + "1:" + thân mã (38 ký tự ≤ 40)
        val body = rawCode("{}").removePrefix(FamilyBook.PREFIX)
        val evil = "LICHGIAPHA" + FamilyBook.PREFIX + "1:" + body
        assertTrue("tên ${evil.length} ký tự", evil.length <= 40)
        val now = System.currentTimeMillis()
        val g = FamilyBook.updateMeta(FamilyGroup("g1", "Họ Nguyễn", listOf(mem("a", "An", 1, 1, now - DAY)), 3, now - DAY), evil, 3, now)!!
        assertEquals(evil, g.name)
        val books = FamilyBook.decodeAll(share(g))
        assertEquals("sổ đọc được: ${books.map { it.id + "/" + it.name }}", listOf("g1"), books.map { it.id })
    }

    @Test
    fun nit_tenNguoiChuaTienToLongNhauVuotBoLoc() {
        // replace(PREFIX, "") chỉ chạy một lượt: "LICHGIAPHA" + "LICHGIAPHA1:" + "1:" + thân mã ⇒ sau khi lọc còn "LICHGIAPHA1:" + thân mã
        val body = rawCode("{}").removePrefix(FamilyBook.PREFIX)
        val evil = "LICHGIAPHA" + FamilyBook.PREFIX + "1:" + body
        assertTrue("tên ${evil.length} ký tự", evil.length <= 60)
        val now = System.currentTimeMillis()
        val g = FamilyBook.saveMember(FamilyGroup("g1", "Họ Nguyễn", emptyList(), 3, now - DAY), mem("b", evil, 2, 2), now)!!
        val books = FamilyBook.decodeAll(share(g))
        assertEquals("sổ đọc được: ${books.map { it.id + "/" + it.name }}", listOf("g1"), books.map { it.id })
    }

    // ======================= Mã ngắt dòng + chữ dính =======================

    @Test
    fun nit_maNgatDongCoChuDinhCuoiKhongDocDuoc() {
        // Một dòng: "<mã>Cảm ơn" đã đọc được (vòng 3). Mã bị ngắt dòng mà dòng cuối dính chữ: decodeWrapped không thử bỏ 1–3 ký tự cuối
        val now = 1_795_500_000_000L
        var i = 0
        var g: FamilyGroup
        var code: String
        do {
            g = FamilyGroup("g$i", "Họ Nguyễn $i", List(4) { k -> mem("m$k", "Người số $k nhà $i", k + 1, k + 2, now) }, 3, now)
            code = FamilyBook.encode(g)
            i++
        } while ((code.length - FamilyBook.PREFIX.length) % 4 != 0)
        assertEquals("một dòng + chữ dính", g, FamilyBook.decode("Mã: " + code + "Cảm ơn cả nhà"))
        val wrapped = "Mã:\n" + code.chunked(76).joinToString("\n") + "Cảm ơn cả nhà"
        assertTrue(wrapped.lines().size > 2)
        assertEquals("ngắt dòng 76 + chữ dính ở dòng cuối", g, FamilyBook.decode(wrapped))
    }

    @Test
    fun ok_moiDiemCatMaChiRaNullHoacDungSo() {
        // Cắt mã ở mọi vị trí, có / không dính 1–3 ký tự base64 phía sau, có / không ngắt dòng: chỉ được null hoặc đúng sổ gốc
        val now = 1_795_500_000_000L
        val g = FamilyGroup("gx", "Họ Lê", List(12) { k -> FamilyMember("m$k", "Lê Văn $k", "cụ", k + 1, k % 12 + 1, 1950 + k, "Nam Định", "chú $k", "", now - k) }, 3, now)
        val code = FamilyBook.encode(g)
        var wrong = 0; var ok = 0
        for (cut in FamilyBook.PREFIX.length + 1..code.length) {
            val c = code.substring(0, cut)
            for (tail in listOf("", "A", "ok", "Cam", " Cảm ơn", "\nok nha")) {
                val r = FamilyBook.decode("Mã: " + c + tail)
                if (r != null && r != g) wrong++
                if (r == g) ok++
            }
        }
        System.err.println("QAB4 cắt mã ${code.length} ký tự ở mọi vị trí: đúng $ok, SAI $wrong")
        assertEquals(0, wrong)
    }

    // ======================= Gộp: kiểm tra bất biến ngẫu nhiên (cả vượt 300) =======================

    private fun randomBook(rnd: Random, pool: Int, live: Int, tombs: Int, t: Long): FamilyGroup {
        val ids = (0 until pool).shuffled(rnd)
        val l = ids.take(live).map { i ->
            FamilyMember("p$i", "Người $i v${rnd.nextInt(3)}", "cụ", rnd.nextInt(1, 31), rnd.nextInt(1, 13), null, "", if (rnd.nextBoolean()) "Ai ${rnd.nextInt(3)}" else "", "", t - rnd.nextLong(30) * 1000)
        }
        val d = ids.drop(live).take(tombs).map { i -> FamilyMember("p$i", "", "", rnd.nextInt(1, 31), rnd.nextInt(1, 13), null, "", "", "", t - rnd.nextLong(30) * 1000, deleted = true) }
        return reload(FamilyGroup("g1", "Họ", (l + d).shuffled(rnd), 3, t))
    }

    @Test
    fun ok_fuzzGopKhongMatNguoiTrenMayVaDemDung() {
        val bad = mutableListOf<String>()
        var reimport = 0
        var reimportTombCap = 0
        val t = System.currentTimeMillis() - DAY
        for (seed in 1..400) {
            val rnd = Random(seed)
            val pool = listOf(350, 500, 800)[seed % 3]
            val local = randomBook(rnd, pool, rnd.nextInt(0, 301), rnd.nextInt(0, 301), t)
            val inc = randomBook(rnd, pool, rnd.nextInt(0, 301), rnd.nextInt(0, 301), t)
            val r = FamilyBook.merge(local, inc, t + 3_600_000L)
            val res = r.group.members.associateBy { it.id }
            val lo = local.members.associateBy { it.id }
            val im = inc.members.associateBy { it.id }
            fun e(s: String) { if (bad.size < 15) bad += "seed $seed: $s" }
            if (r.group.active.size > 300) e("${r.group.active.size} người")
            if (r.group.members.count { it.deleted } > 300) e("${r.group.members.count { it.deleted }} dấu xóa")
            if (reload(r.group) != r.group) e("đọc lại khác")
            if (r.group.members.size != res.size) e("trùng id")
            for ((id, v) in res) {
                val win = listOfNotNull(lo[id], im[id]).reduce { a, b -> newer(a, b) }
                if (v != win) e("$id không phải bản sau cùng")
            }
            for (m in local.active) {
                val v = res[m.id]
                val win = im[m.id]?.let { newer(m, it) } ?: m
                if (!win.deleted && (v == null || v.deleted)) e("mất người trên máy ${m.id}")
                if (win.deleted && v != null && !v.deleted) e("không xóa ${m.id}")
            }
            val localLive = local.active.map { it.id }.toSet()
            val added = r.group.active.count { it.id !in localLive }
            val removed = local.active.count { m -> res[m.id]?.deleted != false }
            val updated = local.active.count { m -> res[m.id]?.let { !it.deleted && it != m } == true }
            if (listOf(r.added, r.removed, r.updated) != listOf(added, removed, updated)) e("đếm (thêm, xóa, sửa) ${listOf(r.added, r.removed, r.updated)} ≠ thực tế ${listOf(added, removed, updated)}")
            // Nhập lại cùng mã: không có gì mới — trừ khi dấu xóa bị cắt vì tổng > 300 (xem nit_dauXoaBiCat…)
            val again = FamilyBook.merge(r.group, inc, t + 3_600_000L)
            if (again.added + again.updated + again.removed != 0) {
                reimport++
                val tombsUnion = (local.members + inc.members).filter { it.deleted }.map { it.id }.toSet().size
                if (tombsUnion > 300) reimportTombCap++ else e("nhập lại vẫn báo thay đổi ${listOf(again.added, again.updated, again.removed)} (không do cắt dấu xóa)")
            }
        }
        System.err.println("QAB4 fuzz gộp: $reimport/400 lần nhập lại cùng mã vẫn báo thay đổi, $reimportTombCap trong đó có tổng dấu xóa > 300")
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    // ======================= Mô phỏng 4 máy, vượt 300, lệch giờ, dán nhiều mã =======================

    private fun simulate(seed: Int): List<String> {
        val rnd = Random(seed)
        val t0 = System.currentTimeMillis() - 60 * 3_600_000L
        val base = reload(FamilyGroup("g1", "Họ Nguyễn", List(150) { i -> mem("b$i", "Cụ $i", i % 29 + 1, i % 12 + 1, t0 - DAY + i) }, 3, t0 - DAY))
        val n = 4
        val phones = MutableList(n) { base }
        val history = mutableListOf<String>()
        val problems = mutableListOf<String>()
        var t = t0
        var nextId = 0
        fun skew(p: Int, step: Int) = when (p) { 3 -> if (step < 150) 2 * DAY else 0L; 2 -> -30 * MIN; 1 -> 12 * MIN; else -> 0L }
        fun checkMerge(local: FamilyGroup, inc: FamilyGroup, r: FamilyImport, step: Int) {
            val live = r.group.active.map { it.id }.toSet()
            val incDel = inc.members.filter { it.deleted }.map { it.id }.toSet()
            val lost = local.active.filter { it.id !in live && it.id !in incDel }
            if (lost.isNotEmpty() && problems.size < 5) problems += "seed $seed bước $step: mất ${lost.size} người trên máy (${lost.take(3).map { it.id }})"
            if (r.group.active.size > 300 && problems.size < 5) problems += "seed $seed bước $step: ${r.group.active.size} người"
        }
        for (step in 0 until 300) {
            t += rnd.nextLong(10_000, 15 * MIN)
            val p = rnd.nextInt(n)
            val now = t + skew(p, step)
            val g = phones[p]
            val live = g.active
            when (rnd.nextInt(14)) {
                0, 1, 2, 3, 4 -> repeat(rnd.nextInt(1, 30)) {
                    phones[p] = FamilyBook.saveMember(phones[p], mem("n${p}x${nextId++}", "Người ${rnd.nextInt(1000)}", rnd.nextInt(1, 31), rnd.nextInt(1, 13)), now) ?: phones[p]
                }
                5 -> if (live.isNotEmpty()) phones[p] = FamilyBook.saveMember(g, live[rnd.nextInt(live.size)].copy(assignee = "Ai ${rnd.nextInt(5)}", lunarDay = rnd.nextInt(1, 31)), now) ?: g
                6 -> repeat(rnd.nextInt(1, 6)) { val l2 = phones[p].active; if (l2.isNotEmpty()) phones[p] = FamilyBook.deleteMember(phones[p], l2[rnd.nextInt(l2.size)].id, now) }
                7 -> phones[p] = FamilyBook.updateMeta(g, "Sổ ${rnd.nextInt(100)}", rnd.nextInt(0, 8), now) ?: g
                8 -> if (history.isNotEmpty()) {
                    val text = List(rnd.nextInt(1, 4)) { history[rnd.nextInt(history.size)] }.joinToString("\n...\n")
                    val inc = FamilyBook.decodeAll(text).singleOrNull()
                    if (inc != null) { val r = FamilyBook.merge(g, inc, now); checkMerge(g, inc, r, step); phones[p] = r.group }
                }
                else -> {
                    val src = (p + 1 + rnd.nextInt(n - 1)) % n
                    val code = FamilyBook.encode(phones[src])
                    history += code
                    val inc = FamilyBook.decode(code)!!
                    val r = FamilyBook.merge(g, inc, now)
                    checkMerge(g, inc, r, step)
                    phones[p] = r.group
                }
            }
            phones[p] = reload(phones[p])
        }
        t += 3_600_000L
        repeat(4) {
            for (s in 0 until n) for (d in 0 until n) if (s != d) {
                t += 1000
                val inc = FamilyBook.decode(share(phones[s]))!!
                val r = FamilyBook.merge(phones[d], inc, t)
                checkMerge(phones[d], inc, r, 9999)
                phones[d] = reload(r.group)
            }
        }
        // Hai máy cùng có một id thì phải cùng một bản; tên / nhắc hội tụ
        for (i in 0 until n) for (j in i + 1 until n) {
            val a = phones[i].members.associateBy { it.id }; val b = phones[j].members.associateBy { it.id }
            val diff = a.keys.intersect(b.keys).filter { a[it] != b[it] }
            if (diff.isNotEmpty() && problems.size < 8) problems += "seed $seed: máy $i/$j khác ${diff.size} id chung, ví dụ ${diff.first()}: ${a[diff.first()]} vs ${b[diff.first()]}"
        }
        if (phones.map { it.name to it.remind }.toSet().size != 1) problems += "seed $seed: tên/nhắc không hội tụ"
        return problems
    }

    @Test
    fun ok_bonMayVuot300LechGioKhongMatNguoiVaIdChungHoiTu() {
        val problems = (1..25).flatMap { simulate(it) }
        assertEquals(problems.take(10).joinToString("\n"), 0, problems.size)
    }

    // ======================= Hiệu năng decodeAll trên đoạn dán dài =======================

    private fun bigBook(seed: Int): FamilyGroup {
        val rnd = Random(seed)
        val now = 1_795_500_000_000L
        return FamilyGroup(
            "%012x".format(rnd.nextLong(1L shl 47)), "Họ $seed",
            List(300) { i -> FamilyMember("%012x".format(rnd.nextLong(1L shl 47)), "Nguyễn Văn ${rnd.nextInt(100000)}", listOf("ông", "bà", "cụ")[i % 3], rnd.nextInt(1, 31), rnd.nextInt(1, 13), null, listOf("Nam Định", "Hà Nội", "")[i % 3], "", "", now - rnd.nextLong(1_000_000)) },
            3, now,
        )
    }

    private fun timed(label: String, text: String): Long {
        val t0 = System.nanoTime()
        val r = FamilyBook.decodeAll(text)
        val ms = (System.nanoTime() - t0) / 1_000_000
        System.err.println("QAB4 perf $label (${text.length / 1000} K ký tự): ${r.size} sổ, $ms ms trên JVM")
        return ms
    }

    @Test
    fun perf_decodeAllDoanDanDai() {
        FamilyBook.decodeAll(FamilyBook.encode(bigBook(0)))   // làm nóng
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val rnd = Random(5)
        fun junkLines(k: Int, w: Int) = (1..k).joinToString("") { "\n" + String(CharArray(w) { alphabet[rnd.nextInt(64)] }) }
        val results = mutableMapOf<String, Long>()
        // 1) 10 mã 300 người bị cắt 60%, mỗi mã có ~1000 dòng chữ base64 phía sau
        results["10 mã cụt + 1000 dòng rác"] = timed("10 mã cụt + 1000 dòng rác mỗi mã", (1..10).joinToString("\n\n") { s -> FamilyBook.encode(bigBook(s)).let { it.substring(0, it.length * 6 / 10) } + junkLines(1000, 76) })
        // 2) Mã cụt + đoạn chat gõ không dấu rất dài (chữ base64 cách nhau bởi dấu cách)
        val words = listOf("bo", "gui", "ma", "so", "gio", "nha", "ca", "nho", "nhap", "nhe", "ok", "roi", "con", "da", "nhan", "duoc")
        val chat = (1..20000).joinToString(" ") { words[rnd.nextInt(words.size)] }
        results["mã cụt + chat không dấu"] = timed("mã cụt + 20000 từ không dấu", FamilyBook.encode(bigBook(11)).let { it.substring(0, it.length / 2) } + " " + chat)
        // 3) Một mã cụt + các khối 3999 ký tự (mỗi lần nối giải base64 lại tới ~1 MB)
        results["khối 3999"] = timed("mã cụt + 300 khối 3999 ký tự", FamilyBook.encode(bigBook(12)).let { it.substring(0, it.length / 2) } + junkLines(300, 3999))
        // 4) 50 mã đầy đủ khác nhau + chat tiếng Việt xen giữa (đoạn chat thật dài)
        val vn = "Cả nhà ơi, bố gửi sổ giỗ, mọi người nhập vào máy nhé. Cảm ơn bố ạ! "
        results["50 mã đủ"] = timed("50 mã đầy đủ + chat", (1..50).joinToString("\n") { s -> vn.repeat(20) + "\n" + FamilyBook.shareText(bigBook(100 + s), today) })
        // 5) 2000 mã rác ngắn có đầu zlib hợp lệ, mỗi mã theo sau là 300 từ ASCII
        results["2000 mã rác"] = timed("2000 mã rác 'eNo…' + 300 từ", (1..2000).joinToString("\n") { "LICHGIAPHA1:eNoAbc " + (1..300).joinToString(" ") { words[it % words.size] } })
        System.err.println("QAB4 perf tổng: $results")
        assertTrue(results.toString(), results.values.all { it < 20_000 })
    }

    // ======================= Ngày giỗ tới, chia sẻ, mốc sửa =======================

    @Test
    fun ok_nextDateMoiThangMoiNgayNhieuNam() {
        val bad = mutableListOf<String>()
        var from = LocalDate.of(1990, 1, 1)
        val rnd = Random(1)
        while (from.year < 2150) {
            for (m in 1..12) for (d in listOf(1, 15, 29, 30)) {
                val next = FamilyBook.nextDate(mem("x", "X", d, m), from)
                if (next == null) { bad += "$from $d/$m: null"; continue }
                val l = LunarCalendar.convert(next)
                val okDay = l.day == d || (d == 30 && l.day == 29 && LunarCalendar.toSolar(30, m, l.year) == null)
                if (next.isBefore(from) || l.month != m || l.leap || !okDay || next.isAfter(from.plusDays(400))) bad += "$from giỗ $d/$m → $next (${l.day}/${l.month}${if (l.leap) "N" else ""})"
                // Không bỏ sót lần sớm hơn: ngày trước đó của cùng tháng âm (năm âm trước) phải trước from
                val prevY = LunarCalendar.toSolar(minOf(d, 29), m, l.year - 1)
                if (prevY != null && !prevY.isBefore(from) && prevY.isBefore(next)) bad += "$from giỗ $d/$m bỏ sót $prevY"
            }
            from = from.plusDays(rnd.nextLong(5, 40))
            if (bad.size > 10) break
        }
        assertEquals(bad.take(10).joinToString("\n"), 0, bad.size)
    }

    @Test
    fun ok_shareTextKhuHoiVaKhongLoMa() {
        val rnd = Random(9)
        repeat(60) { s ->
            val now = 1_795_500_000_000L
            val g = FamilyGroup(
                "g$s", "Họ ${rnd.nextInt(100)}",
                List(rnd.nextInt(0, 40)) { i -> FamilyMember("m$i", if (i % 7 == 0) "LICHGIAPHA1:${"A".repeat(i)}" else "Tên $i", "cụ", rnd.nextInt(1, 31), rnd.nextInt(1, 13), null, "", if (i % 5 == 0) "LICHGIAPHA1:x" else "", "", now) },
                rnd.nextInt(0, 31), now,
            )
            val text = share(g)
            val lines = text.lines()
            assertTrue(lines.dropLast(1).none { it.contains(FamilyBook.PREFIX) })
            assertEquals(g, FamilyBook.decode(text))
            assertEquals(listOf(g.id), FamilyBook.decodeAll(text).map { it.id })
        }
    }

    @Test
    fun ok_mocSuaLuonTangVaMayTuongLai() {
        val now = 1_795_500_000_000L
        assertEquals(now, FamilyBook.stamp(0, now))
        assertEquals(now + 6 * MIN, FamilyBook.stamp(now + 6 * MIN - 1, now))   // máy hơi chậm: vẫn sau mốc cũ
        assertEquals(now, FamilyBook.stamp(now + 11 * MIN, now))               // mốc cũ ở tương lai xa: lấy giờ hiện tại
        val g = FamilyGroup("g1", "Họ", listOf(mem("a", "An", 1, 1, now - DAY)), 3, now - DAY)
        assertEquals(null, FamilyBook.updateMeta(g, "   ", 3, now))
        assertEquals(null, FamilyBook.updateMeta(g, "Họ", 99.coerceAtMost(3), now))
        val g2 = FamilyBook.updateMeta(g, "  Họ mới  ", 99, now)!!
        assertEquals("Họ mới" to 30, g2.name to g2.remind)
        assertEquals(g.metaAt, FamilyBook.updateMeta(g, "Họ", 5, now)!!.metaAt)   // chỉ đổi nhắc thì mốc tên giữ nguyên
        val d = FamilyBook.deleteMember(g, "a", now)
        assertTrue(d.members.single().deleted)
        assertEquals("", d.members.single().name)
        assertNotNull(FamilyBook.saveMember(d, mem("a", "An lại", 1, 1), now + 1))
        assertEquals(0, FamilyBook.toEvents(d).size)
    }
}
