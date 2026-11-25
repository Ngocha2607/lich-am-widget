package vn.lichthoitiet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64
import java.util.zip.Deflater
import kotlin.random.Random

/**
 * QA tester B, vòng 5 (cổng phát hành): sổ giỗ. "bug_…" khẳng định hành vi ĐÚNG mong đợi nên FAIL để chứng minh lỗi;
 * "ok_…"/"perf_…" là hồi quy (đang đúng).
 */
class QaB5FamilyBookTest {
    private val MIN = 60_000L
    private val DAY = 86_400_000L
    private val SKEW = 10 * MIN
    private val today = LocalDate.of(2026, 11, 25)

    private fun mem(id: String, name: String, d: Int, mo: Int, u: Long = 0, who: String = "", rel: String = "cụ") =
        FamilyMember(id, name, rel, d, mo, null, "", who, "", u)

    private fun reload(g: FamilyGroup) = FamilyGroup.fromJson(JSONObject(g.toJson().toString()))
    private fun share(g: FamilyGroup) = FamilyBook.shareText(g, today)
    private fun body(m: FamilyMember) = m.copy(updatedAt = 0).toJson().toString()

    private fun rawCode(json: String): String {
        val d = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(json.toByteArray(Charsets.UTF_8)); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return FamilyBook.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    // ======================= Máy mới dán nhiều phiên bản: giới hạn 300 =======================

    @Test
    fun bug_mayMoiDanHaiBanHopLaiVuot300KhongCatRoiDocLaiMatNguoi() {
        // combine (vòng 4) không cắt 300; merge(local = null) cũng không cắt ⇒ máy mới lưu sổ 400 người, báo "thêm 400",
        // tạo 400 sự kiện; FamilyStore.load (fromJson ⇒ capMembers) chỉ còn 300 ⇒ 100 người (tháng cuối năm) biến mất sau khi đọc lại.
        // Đây đúng là lỗi vòng 2/3 (ok_gopHaiNhanhVuot300ThiDocLaiKhongMatThem) tái xuất ở đường "chưa có sổ".
        val t = System.currentTimeMillis() - DAY
        val shared = List(100) { i -> mem("c$i", "Cụ chung $i", i % 29 + 1, i % 12 + 1, t + i) }
        val a = FamilyGroup("g1", "Họ Nguyễn", shared + List(150) { i -> mem("a$i", "Nhánh A $i", i % 29 + 1, i % 12 + 1, t + i) }, 3, t)
        val b = FamilyGroup("g1", "Họ Nguyễn", shared + List(150) { i -> mem("b$i", "Nhánh B $i", i % 29 + 1, i % 12 + 1, t + i) }, 3, t)
        val r = FamilyBook.merge(null, FamilyBook.decodeAll(share(a) + "\n\nBác gửi bản mới:\n" + share(b)).single())
        val stored = reload(r.group)
        val lostMonths = r.group.active.filter { m -> stored.active.none { it.id == m.id } }.map { it.lunarMonth }.toSortedSet()
        System.err.println("QAB5 máy mới dán A+B: nhập ${r.group.active.size} người, báo added=${r.added}, ${FamilyBook.toEvents(r.group).size} sự kiện; " +
            "đọc lại còn ${stored.active.size} (mất người tháng $lostMonths)")
        assertTrue("máy mới: ${r.group.active.size} người > 300", r.group.active.size <= FamilyBook.MAX_MEMBERS)
        assertEquals("lưu rồi đọc lại phải giữ nguyên", r.group.active.map { it.id }.toSet(), stored.active.map { it.id }.toSet())
    }

    @Test
    fun ok_mayDaCoSoDanHaiBanVuot300GiuNguoiTrenMay() {
        // Cùng tình huống nhưng máy đã có bản A: merge cắt 300, ưu tiên người trên máy; đọc lại không mất thêm
        val t = System.currentTimeMillis() - DAY
        val a = FamilyGroup("g1", "Họ", List(200) { i -> mem("a$i", "A $i", i % 29 + 1, i % 12 + 1, t + i) }, 3, t)
        val b = FamilyGroup("g1", "Họ", List(200) { i -> mem("b$i", "B $i", i % 29 + 1, i % 12 + 1, t + i) }, 3, t)
        for (text in listOf(share(a) + "\n" + share(b), share(b) + "\n" + share(a))) {
            val r = FamilyBook.merge(a, FamilyBook.decodeAll(text).single())
            assertEquals(300, r.group.active.size)
            assertEquals(100, r.added)
            assertTrue(a.active.all { m -> r.group.active.any { it.id == m.id } })
            assertEquals(r.group, reload(r.group))
        }
    }

    // ======================= combine: giao hoán / kết hợp, nhiều phiên bản lệch giờ =======================

    private fun randomVersion(rnd: Random, ids: List<String>, now: Long): FamilyGroup {
        val skews = listOf(0L, 3 * MIN, -4 * MIN, 9 * MIN, 11 * MIN, 2 * DAY, -DAY, 3 * DAY)
        val ms = ids.filter { rnd.nextInt(3) > 0 }.map { id ->
            val u = now - rnd.nextLong(0, 3 * DAY) + skews[rnd.nextInt(skews.size)]
            if (rnd.nextInt(5) == 0) FamilyMember(id, "", "", 1, 1, null, "", "", "", u, deleted = true)
            else FamilyMember(id, "Người $id v${rnd.nextInt(4)}", "cụ", rnd.nextInt(1, 31), rnd.nextInt(1, 13), null, "", "Ai ${rnd.nextInt(3)}", "", u)
        }
        val mu = now - rnd.nextLong(0, DAY) + skews[rnd.nextInt(skews.size)]
        val ru = now - rnd.nextLong(0, DAY) + skews[rnd.nextInt(skews.size)]
        return reload(FamilyGroup("g1", "Sổ ${rnd.nextInt(4)}", ms, rnd.nextInt(0, 4), maxOf(mu, ru), mu, ru))
    }

    @Test
    fun ok_combineKhongPhuThuocThuTuTinNhan() {
        val bad = mutableListOf<String>()
        val now = 1_795_500_000_000L
        for (seed in 1..300) {
            val rnd = Random(seed)
            val ids = List(rnd.nextInt(3, 25)) { "p$it" }
            val versions = List(rnd.nextInt(2, 6)) { randomVersion(rnd, ids, now) }
            val codes = versions.map { share(it) }
            val ref = FamilyBook.decodeAll(codes.joinToString("\n…\n"), now).single()
            repeat(6) {
                val got = FamilyBook.decodeAll(codes.shuffled(rnd).joinToString("\n…\n"), now).single()
                if (got != ref && bad.size < 10) bad += "seed $seed: $got\n   vs $ref"
            }
            // Từng cặp rồi gộp tiếp (kết hợp): (v1+v2)+v3 qua máy trung gian không sinh ra bản khác dán một lần
            val viaPairs = FamilyBook.decodeAll(share(FamilyBook.decodeAll(codes.take(2).joinToString("\n"), now).single()) + "\n" + codes.drop(2).joinToString("\n"), now).single()
            if (viaPairs.members.map { body(it) to it.updatedAt }.toSet() != ref.members.map { body(it) to it.updatedAt }.toSet() || viaPairs.name != ref.name || viaPairs.remind != ref.remind)
                if (bad.size < 10) bad += "seed $seed: gộp theo cặp khác gộp một lần"
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    @Test
    fun ok_danLaiCungDoanChatLanHaiKhongDoiGi() {
        val bad = mutableListOf<String>()
        val now = 1_795_500_000_000L
        for (seed in 1..300) {
            val rnd = Random(seed * 7)
            val ids = List(rnd.nextInt(3, 25)) { "p$it" }
            val local = if (rnd.nextBoolean()) null else randomVersion(rnd, ids, now)
            val text = List(rnd.nextInt(1, 5)) { share(randomVersion(rnd, ids, now)) }.joinToString("\n")
            val r1 = FamilyBook.merge(local, FamilyBook.decodeAll(text, now).single(), now)
            val g1 = reload(r1.group)
            val r2 = FamilyBook.merge(g1, FamilyBook.decodeAll(text, now).single(), now)
            if ((r2.added + r2.updated + r2.removed != 0 || r2.metaChanged || reload(r2.group).members.toSet() != g1.members.toSet()) && bad.size < 10)
                bad += "seed $seed: dán lại báo ${listOf(r2.added, r2.updated, r2.removed, r2.metaChanged)}"
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    // ======================= Mô phỏng nhiều máy: dán gộp vs nhập từng tin, lệch giờ, hội tụ =======================

    private class Stats {
        val cat = sortedMapOf<String, Int>()
        val problems = mutableListOf<String>()
        fun inc(k: String) { cat[k] = (cat[k] ?: 0) + 1 }
    }

    /** So kết quả dán gộp [c] với nhập lần lượt [s] (cùng máy, cùng giờ [now]); phân loại khác biệt. */
    private fun compare(seed: Int, step: Int, local: FamilyGroup, versions: List<FamilyGroup>, c: FamilyGroup, s: FamilyGroup, now: Long, st: Stats, label: String) {
        val skew = now + SKEW
        val cm = c.members.associateBy { it.id }
        val sm = s.members.associateBy { it.id }
        for (id in cm.keys + sm.keys) {
            val a = cm[id]; val b = sm[id]
            if (a == b) continue
            val vers = versions.mapNotNull { g -> g.members.firstOrNull { it.id == id } }
            val l = local.members.firstOrNull { it.id == id }
            val k = when {
                a != null && b != null && body(a) == body(b) -> "người: cùng nội dung, khác mốc"
                l == null && vers.isNotEmpty() && vers.all { it.updatedAt > skew } -> "người mới chỉ có bản 'tương lai' (theo thiết kế: dán gộp lấy bản sau, nhập lần lượt lấy bản đầu)"
                else -> "NGƯỜI KHÁC (không giải thích)"
            }
            st.inc("$label · $k")
            if (k.startsWith("NGƯỜI KHÁC") && st.problems.size < 8)
                st.problems += "seed $seed bước $step [$label] $id: gộp=$a\n      lần lượt=$b\n      máy=$l\n      các bản=${vers.map { "${it.updatedAt - now}ms ${body(it).take(60)}" }}"
        }
        fun meta(what: String, ca: Any, sa: Any, stamps: List<Long>) {
            if (ca == sa) return
            val k = if (stamps.any { it > now && it <= skew }) "$what: khác do kẹp mốc 'hơi tương lai' (≤ 10 phút) về bây giờ" else "${what.uppercase()} KHÁC (không giải thích)"
            st.inc("$label · $k")
            if (k.contains("KHÁC (") && st.problems.size < 8) st.problems += "seed $seed bước $step [$label] $what gộp=$ca lần lượt=$sa máy=${local.name}/${local.remind} mốc=${stamps.map { it - now }}"
        }
        meta("tên", c.name, s.name, versions.map { it.metaAt })
        meta("nhắc", c.remind, s.remind, versions.map { it.remindAt })
    }

    private fun simulateMix(seed: Int, st: Stats) {
        val rnd = Random(seed)
        val t0 = System.currentTimeMillis() - 20 * DAY
        val base = reload(FamilyGroup("g1", "Họ Nguyễn", List(40) { i -> mem("b$i", "Cụ $i", i % 29 + 1, i % 12 + 1, t0 - DAY + i) }, 3, t0 - DAY))
        // 0 đúng giờ, 1 nhanh 2 ngày, 2 chậm 5 phút, 3 nhanh 7 phút, 4 chậm 1 ngày
        val offs = longArrayOf(0, 2 * DAY, -5 * MIN, 7 * MIN, -DAY)
        val n = offs.size
        val phones = MutableList(n) { base }
        val history = mutableListOf<String>()
        val everDeleted = mutableSetOf<String>()
        val added = mutableSetOf<String>()
        var t = t0
        var nextId = 0
        for (step in 0 until 350) {
            t += rnd.nextLong(5_000, 20 * MIN)
            val p = rnd.nextInt(n)
            val now = t + offs[p]
            val g = phones[p]
            val live = g.active
            when (rnd.nextInt(12)) {
                0, 1 -> repeat(rnd.nextInt(1, 4)) {
                    val id = "n${p}x${nextId++}"
                    FamilyBook.saveMember(phones[p], mem(id, "Người ${rnd.nextInt(1000)}", rnd.nextInt(1, 31), rnd.nextInt(1, 13)), now)?.let { phones[p] = it; added += id }
                }
                2, 3 -> if (live.isNotEmpty()) phones[p] = FamilyBook.saveMember(g, live[rnd.nextInt(live.size)].copy(assignee = "Ai ${rnd.nextInt(5)}", lunarDay = rnd.nextInt(1, 31)), now) ?: g
                4 -> if (live.isNotEmpty()) { val id = live[rnd.nextInt(live.size)].id; everDeleted += id; phones[p] = FamilyBook.deleteMember(g, id, now) }
                5 -> phones[p] = FamilyBook.updateMeta(g, "Sổ ${rnd.nextInt(50)}", rnd.nextInt(0, 8), now) ?: g
                6, 7 -> history += share(g)
                else -> if (history.isNotEmpty()) {
                    val msgs = List(rnd.nextInt(1, 5)) { history[rnd.nextInt(history.size)] }
                    val versions = msgs.map { FamilyBook.decodeAll(it, now).single() }
                    val combined = FamilyBook.merge(g, FamilyBook.decodeAll(msgs.joinToString("\n\n— tin khác —\n"), now).single(), now).group
                    if (msgs.size > 1) {
                        for ((label, order) in listOf("xuôi" to versions, "ngược" to versions.reversed())) {
                            val seq = order.fold(g) { acc, v -> FamilyBook.merge(acc, v, now).group }
                            compare(seed, step, g, versions, combined, seq, now, st, label)
                        }
                    }
                    phones[p] = combined
                }
            }
            phones[p] = reload(phones[p])
        }
        // Sửa giờ mọi máy; mỗi máy dán cả đoạn chat gồm sổ của các máy khác (thứ tự ngẫu nhiên), vài vòng
        t += 3_600_000L
        repeat(4) {
            for (d in 0 until n) {
                t += 1000
                val text = (0 until n).filter { it != d }.shuffled(rnd).joinToString("\n\n") { share(phones[it]) }
                phones[d] = reload(FamilyBook.merge(phones[d], FamilyBook.decodeAll(text, t).single(), t).group)
            }
        }
        val ref = phones[0]
        for (i in 1 until n) {
            val a = ref.members.associateBy { it.id }; val b = phones[i].members.associateBy { it.id }
            val diff = (a.keys + b.keys).filter { a[it]?.let(::body) != b[it]?.let(::body) }
            if (diff.isNotEmpty() && st.problems.size < 12) st.problems += "seed $seed: máy 0/$i không hội tụ ${diff.size} người, vd ${diff.first()}: ${a[diff.first()]} vs ${b[diff.first()]}"
            if ((ref.name to ref.remind) != (phones[i].name to phones[i].remind) && st.problems.size < 12) st.problems += "seed $seed: tên/nhắc không hội tụ ${ref.name}/${ref.remind} vs ${phones[i].name}/${phones[i].remind}"
            if (diff.isEmpty() && a != b) st.inc("hội tụ nội dung nhưng khác mốc")
        }
        // Người được thêm mà chưa từng bị ai xóa thì phải còn trên mọi máy
        for (i in 0 until n) {
            val miss = added.filter { it !in everDeleted && phones[i].active.none { m -> m.id == it } }
            if (miss.isNotEmpty() && st.problems.size < 12) st.problems += "seed $seed: máy $i mất ${miss.size} người chưa từng bị xóa: ${miss.take(5)}"
        }
    }

    @Test
    fun ok_nhieuMayDanGopVaNhapLanLuotKhopVaHoiTu() {
        val st = Stats()
        for (seed in 1..40) simulateMix(seed, st)
        System.err.println("QAB5 mô phỏng 40×5 máy (dán gộp vs nhập lần lượt, cùng giờ): " + st.cat.entries.joinToString("; ") { "${it.key}: ${it.value}" })
        assertEquals(st.problems.joinToString("\n"), 0, st.problems.size)
    }

    @Test
    fun ok_mayNhanTungTinVaMayDanCaDoanChatCuoiCungGiongNhau() {
        // Không có máy lệch giờ: máy X nhập từng tin khi tin tới, máy Y dán cả đoạn chat ở cuối (nhiều phiên bản) ⇒ cùng kết quả.
        val bad = mutableListOf<String>()
        for (seed in 1..30) {
            val rnd = Random(seed + 1000)
            val t0 = System.currentTimeMillis() - 5 * DAY
            val base = reload(FamilyGroup("g1", "Họ", List(20) { i -> mem("b$i", "Cụ $i", i % 29 + 1, i % 12 + 1, t0 - DAY) }, 3, t0 - DAY))
            val phones = MutableList(3) { base }
            var x = base
            val chat = StringBuilder()
            var t = t0
            for (step in 0 until 200) {
                t += rnd.nextLong(1000, 30 * MIN)
                val p = rnd.nextInt(3)
                val g = phones[p]
                phones[p] = when (rnd.nextInt(7)) {
                    0 -> FamilyBook.saveMember(g, mem("n${seed}x$step", "Mới $step", rnd.nextInt(1, 31), rnd.nextInt(1, 13)), t) ?: g
                    1 -> g.active.randomOrNull(rnd)?.let { FamilyBook.saveMember(g, it.copy(note = "sửa $step"), t) } ?: g
                    2 -> g.active.randomOrNull(rnd)?.let { FamilyBook.deleteMember(g, it.id, t) } ?: g
                    3 -> FamilyBook.updateMeta(g, "Sổ $step", rnd.nextInt(0, 6), t) ?: g
                    4 -> { val o = rnd.nextInt(3); FamilyBook.merge(g, FamilyBook.decode(share(phones[o]))!!, t).group }
                    else -> {
                        val msg = share(g)
                        chat.append("Máy $p lúc $step:\n").append(msg).append("\n\n")
                        x = FamilyBook.merge(x, FamilyBook.decode(msg)!!, t).group
                        g
                    }
                }
                phones[p] = reload(phones[p])
            }
            val y = FamilyBook.merge(base, FamilyBook.decodeAll(chat.toString(), t).single(), t).group
            if (x.members.associateBy { it.id } != y.members.associateBy { it.id } || x.name != y.name || x.remind != y.remind)
                bad += "seed $seed: từng tin ${x.active.size} người '${x.name}'/${x.remind} vs cả đoạn ${y.active.size} người '${y.name}'/${y.remind}"
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    // ======================= Giải mã: ngân sách 3000 lần thử dùng chung cho cả đoạn dán =======================

    private fun bigBook(seed: Int, n: Int = 300): FamilyGroup {
        val rnd = Random(seed)
        val now = 1_795_500_000_000L
        return FamilyGroup(
            "%012x".format(rnd.nextLong(1L shl 47)), "Họ $seed",
            List(n) { i -> FamilyMember("%012x".format(rnd.nextLong(1L shl 47)), "Nguyễn Văn ${rnd.nextInt(100000)}", listOf("ông", "bà", "cụ")[i % 3], rnd.nextInt(1, 31), rnd.nextInt(1, 13), null, listOf("Nam Định", "Hà Nội", "")[i % 3], "", "", now - rnd.nextLong(1_000_000)) },
            3, now,
        )
    }

    /** Tin nhắn chia sẻ có mã bị ngắt dòng cứng [w] ký tự (email, SMS dài). */
    private fun wrappedShare(g: FamilyGroup, w: Int = 76): String {
        val lines = share(g).lines()
        return (lines.dropLast(1) + lines.last().chunked(w)).joinToString("\n")
    }

    @Test
    fun bug_nhieuBanSoLonNgatDongTrongMotLanDanThiBanMoiNhatBiBoQua() {
        // Sổ 300 người gửi qua email (ngắt dòng 76): mỗi phiên bản tốn ~4 lần thử cho MỖI dòng mã (~120 dòng) ⇒ ~480 lần thử/bản;
        // từ phiên bản thứ 7 trong một lần dán, ngân sách 3000 đã hết ⇒ bản mới nhất (cuối đoạn dán) bị bỏ qua, nhập "thành công" nhưng thiếu sửa đổi mới.
        val now = 1_795_500_000_000L
        var g = bigBook(1)
        val ids = g.active.map { it.id }
        val texts = mutableListOf<String>()
        val outcome = mutableListOf<String>()
        var firstMiss = -1
        for (v in 1..10) {
            val target = g.active.first { it.id == ids[v * 10] }
            g = FamilyBook.saveMember(g, target.copy(note = "sửa lần $v"), now + v * MIN)!!
            texts += wrappedShare(g)
            val got = FamilyBook.decodeAll(texts.joinToString("\n\nRe: Sổ giỗ\n\n"), now + 20 * MIN).singleOrNull()
            val ok = got?.active?.firstOrNull { it.id == ids[v * 10] }?.note == "sửa lần $v"
            outcome += "$v bản: ${if (ok) "đủ" else "THIẾU bản mới nhất"}"
            if (!ok && firstMiss < 0) firstMiss = v
        }
        val codeLines = wrappedShare(g).lines().count { it.matches(Regex("[A-Za-z0-9_:-]{60,}")) }
        System.err.println("QAB5 nhiều phiên bản sổ 300 người ngắt dòng 76 (~$codeLines dòng mã mỗi bản): $outcome")
        assertEquals("số phiên bản đầu tiên bị bỏ qua", -1, firstMiss)
    }

    @Test
    fun bug_haiMaDinhLienNhauMatMaSau() {
        // "<mã A>LICHGIAPHA1:<mã B>" (hai tin dán liền không xuống dòng): "LICHGIAPHA1" là chữ base64 nên bị nuốt vào mã A ⇒ mã B mất
        val t = System.currentTimeMillis() - DAY
        val a = FamilyGroup("ga", "Sổ A", listOf(mem("x", "X", 1, 1, t)), 3, t)
        val b = FamilyGroup("gb", "Sổ B", listOf(mem("y", "Y", 2, 2, t)), 3, t)
        val r = FamilyBook.decodeAll(FamilyBook.encode(a) + FamilyBook.encode(b)).map { it.id }
        System.err.println("QAB5 hai mã dính liền: $r")
        assertEquals(listOf("ga", "gb"), r)
    }

    @Test
    fun bug_maCutDauDoanChatKhongDauLamMaHopLePhiaSauKhongDocDuoc() {
        // Một mã cụt ở đầu đoạn chat + ~800 từ không dấu, không dấu câu (mỗi từ là một "đoạn nối" ⇒ 4 lần thử) ⇒ hết 3000 lần thử;
        // mã HỢP LỆ, đầy đủ ở cuối đoạn dán bị bỏ qua ⇒ "Không tìm thấy mã sổ giỗ hợp lệ".
        val good = bigBook(7, 20)
        val broken = FamilyBook.encode(bigBook(8, 20)).let { it.substring(0, it.length * 2 / 3) }
        val words = listOf("ok", "da", "vang", "con", "nhan", "duoc", "roi", "bo", "oi", "nhe", "ca", "nha", "nho", "nhap", "vao", "may")
        val rnd = Random(3)
        val chat = (1..160).joinToString("\n") { (1..5).joinToString(" ") { words[rnd.nextInt(words.size)] } }
        val text = "Bo gui ma cu\n$broken\n$chat\n" + share(good)
        val r = FamilyBook.decodeAll(text)
        System.err.println("QAB5 mã cụt + 160 dòng chat không dấu (${chat.split(Regex("\\s+")).size} từ) rồi mã đúng: đọc được ${r.size} sổ")
        assertEquals(listOf(good.id), r.map { it.id })
    }

    @Test
    fun perf_doanDan1MBToiDaLanThu() {
        // Kịch bản xấu nhất cho ngân sách: một mã cụt rồi 750 khối base64 1 390 ký tự (≈1 MB) ⇒ 3000 lần thử, mỗi lần giải lại tới ~1 MB
        FamilyBook.decodeAll(FamilyBook.encode(bigBook(0)))
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val rnd = Random(5)
        val junk = (1..750).joinToString("") { "\n" + String(CharArray(1390) { alphabet[rnd.nextInt(64)] }) }
        val text = FamilyBook.encode(bigBook(11)).let { it.substring(0, it.length / 2) } + junk
        val t0 = System.nanoTime()
        val r = FamilyBook.decodeAll(text)
        val ms = (System.nanoTime() - t0) / 1_000_000
        System.err.println("QAB5 perf dán ${text.length / 1000} K ký tự (mã cụt + 750 khối 1390): ${r.size} sổ, $ms ms trên JVM")
        assertTrue("$ms ms", ms < 20_000)
    }

    // ======================= Giải mã: biến thể khoảng trắng, chữ dính, ngắt dòng =======================

    @Test
    fun ok_bienTheNgatDongVaChuDinh() {
        val now = 1_795_500_000_000L
        val bad = mutableListOf<String>()
        for (n in listOf(1, 3, 8, 25, 60)) {
            val g = bigBook(n, n).copy(updatedAt = now)
            val code = FamilyBook.encode(g)
            val bodyPart = code.removePrefix(FamilyBook.PREFIX)
            val cases = mapOf(
                "dấu cách sau tiền tố" to FamilyBook.PREFIX + " " + bodyPart,
                "xuống dòng sau tiền tố" to FamilyBook.PREFIX + "\n" + bodyPart,
                "CRLF 64" to FamilyBook.PREFIX + bodyPart.chunked(64).joinToString("\r\n"),
                "dòng trống giữa các đoạn" to code.chunked(70).joinToString("\n\n"),
                "khoảng trắng cuối dòng" to code.chunked(76).joinToString("  \n"),
                "tab" to code.chunked(50).joinToString("\t"),
                "ngắt 76 + dính 'ok'" to code.chunked(76).joinToString("\n") + "ok",
                "ngắt 76 + dính 'Cảm ơn'" to code.chunked(76).joinToString("\n") + "Cảm ơn",
                "ngắt 76 + dính 'Thanks!'" to code.chunked(76).joinToString("\n") + "Thanks!",
                "một dòng + dính 'abc'" to code + "abc",
                "trong ngoặc" to "(" + code + ")",
                "ngắt 1 ký tự cuối sang dòng mới" to code.dropLast(1) + "\n" + code.takeLast(1),
            )
            for ((label, text) in cases) {
                val r = FamilyBook.decode("Bố gửi: " + text + "\nNhớ nhập nhé")
                if (r != g) bad += "$n người, $label: ${if (r == null) "null" else "SAI"}"
            }
        }
        System.err.println("QAB5 biến thể dán không đọc được: $bad")
        // "Thanks!" dài 6 chữ base64 dính sau mã ngắt dòng: chỉ bỏ 1–3 ký tự — ghi nhận, không tính lỗi
        assertEquals(bad.joinToString("\n"), 0, bad.count { !it.contains("Thanks") })
    }

    @Test
    fun ok_khoangTrangUnicodeSauTienTo() {
        // NBSP (dấu cách không ngắt, hay gặp khi chép từ web/Messenger) sau tiền tố: regex \s không khớp — ghi nhận
        val g = bigBook(2, 5)
        val bodyPart = FamilyBook.encode(g).removePrefix(FamilyBook.PREFIX)
        val r = mapOf(
            "NBSP sau tiền tố" to FamilyBook.decode(FamilyBook.PREFIX + "\u00A0" + bodyPart),
            "NBSP giữa hai đoạn" to FamilyBook.decode(FamilyBook.PREFIX + bodyPart.take(40) + "\u00A0" + bodyPart.drop(40)),
            "U+2028 giữa hai đoạn" to FamilyBook.decode(FamilyBook.PREFIX + bodyPart.take(40) + "\u2028" + bodyPart.drop(40)),
            "zero-width space giữa hai đoạn" to FamilyBook.decode(FamilyBook.PREFIX + bodyPart.take(40) + "\u200B" + bodyPart.drop(40)),
        ).mapValues { it.value == g }
        System.err.println("QAB5 khoảng trắng Unicode: $r")
    }

    @Test
    fun ok_thuTuSoTheoLanXuatHienCuoiVaDecodeLaySoCuoi() {
        val now = System.currentTimeMillis() - DAY
        val a1 = FamilyGroup("ga", "Sổ A", listOf(mem("x", "X", 1, 1, now)), 3, now)
        val a2 = FamilyBook.saveMember(a1, mem("y", "Y", 2, 2), now + MIN)!!
        val b1 = FamilyGroup("gb", "Sổ B", listOf(mem("z", "Z", 3, 3, now)), 3, now)
        val all = FamilyBook.decodeAll(share(a2) + "\n" + share(b1) + "\n" + share(a1))
        assertEquals(listOf("gb", "ga"), all.map { it.id })
        assertEquals(setOf("x", "y"), all.last().active.map { it.id }.toSet())
        assertEquals("ga", FamilyBook.decode(share(a2) + "\n" + share(b1) + "\n" + share(a1))!!.id)
    }

    // ======================= shareText: safe() =======================

    @Test
    fun ok_shareTextMoiTruongChuaTienToKhongTaoMaPhu() {
        val tiny = rawCode("{\"id\":\"evil\",\"members\":[{\"id\":\"e\",\"name\":\"Kẻ lạ\",\"d\":1,\"m\":1}]}")
        val evils = listOf(tiny, "LICHGIAPHA" + tiny, " $tiny ", tiny.replace(":", ": "), tiny.replace(":", ":\n"), "LICHGIAPHA1", "LICHGIAPHA1:", "LICH" + tiny)
        val now = System.currentTimeMillis()
        val bad = mutableListOf<String>()
        for (e in evils) {
            val e40 = e.takeSafe(40)
            val g = FamilyGroup(
                "g1", e40,
                listOf(
                    FamilyMember("a", e.takeSafe(60), "", 1, 1, null, e.takeSafe(80), e40, e.takeSafe(120), now),
                    FamilyMember("b", "Tên", e40, 2, 2, null, "", "", "", now),
                    FamilyMember("c", e.takeSafe(60), e40, 3, 3, null, "", e40, "", now),
                ),
                3, now,
            )
            val gg = reload(g)
            val books = FamilyBook.decodeAll(share(gg))
            if (books.map { it.id } != listOf("g1") || books.single() != gg) bad += "'$e' → ${books.map { it.id }}"
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    // ======================= capMembers =======================

    @Test
    fun ok_capMembersUuTienDungThuTu() {
        val t = 1_795_500_000_000L
        val live = List(320) { i -> mem("L$i", "Người $i", i % 29 + 1, i % 12 + 1, t + i) }
        val tombs = List(320) { i -> FamilyMember("D$i", "", "", 1, 1, null, "", "", "", t + i, deleted = true) }
        val prefer = (300 until 320).map { "L$it" }.toSet()
        val keepDel = setOf("D0", "D1", "D2")
        val r = FamilyBook.capMembers(live + tombs, prefer, keepDel)
        assertEquals(300, r.count { !it.deleted })
        assertEquals(300, r.count { it.deleted })
        assertTrue(prefer.all { id -> r.any { it.id == id } })
        assertTrue(keepDel.all { id -> r.any { it.id == id } })
        // dấu xóa giữ: 3 cái ưu tiên + 297 cái mới nhất
        assertEquals(((23 until 320).map { "D$it" } + keepDel).toSet(), r.filter { it.deleted }.map { it.id }.toSet())
        // giữ thứ tự gốc
        assertEquals((live + tombs).filter { m -> r.any { it.id == m.id } }, r)
    }
}
