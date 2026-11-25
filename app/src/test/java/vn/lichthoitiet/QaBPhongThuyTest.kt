package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** QA tester B: cung phi, Bát trạch, 24 sơn, hình học AR. */
class QaBPhongThuyTest {
    private val Q = mapOf(1 to "Khảm", 2 to "Khôn", 3 to "Chấn", 4 to "Tốn", 6 to "Càn", 7 to "Đoài", 8 to "Cấn", 9 to "Ly")

    @Test
    fun ok_cungPhiTheoBangTraPhoBien() {
        // (năm âm, cung nam, cung nữ) theo bảng tra cung mệnh phổ biến
        val table = listOf(
            Triple(1954, "Khảm", "Cấn"), Triple(1964, "Ly", "Càn"), Triple(1968, "Khôn", "Khảm"),
            Triple(1975, "Đoài", "Cấn"), Triple(1984, "Đoài", "Cấn"), Triple(1986, "Khôn", "Khảm"),
            Triple(1988, "Chấn", "Chấn"), Triple(1992, "Cấn", "Đoài"), Triple(1993, "Đoài", "Cấn"),
            Triple(1996, "Tốn", "Khôn"), Triple(2004, "Khôn", "Khảm"), Triple(2020, "Đoài", "Cấn"),
        )
        for ((y, nam, nu) in table) {
            assertEquals("nam $y", nam, PhongThuy.cungPhi(y, true).name)
            assertEquals("nữ $y", nu, PhongThuy.cungPhi(y, false).name)
        }
    }

    @Test
    fun ok_cungPhiTheoTamNguyen() {
        // Thượng nguyên 1864: nam khởi Khảm đi nghịch, nữ khởi Trung cung (5 → Cấn) đi thuận; Trung nguyên 1924, Hạ nguyên 1984 nối tiếp
        for (y in 1864..2043) {
            val k = y - 1864
            var nam = Math.floorMod(-k, 9) + 1
            var nu = Math.floorMod(4 + k, 9) + 1
            if (nam == 5) nam = 2
            if (nu == 5) nu = 8
            assertEquals("nam $y", Q[nam], PhongThuy.cungPhi(y, true).name)
            assertEquals("nữ $y", Q[nu], PhongThuy.cungPhi(y, false).name)
            val g = PhongThuy.cungPhi(y, true)
            assertEquals(if (g.que in setOf(1, 3, 4, 9)) "Đông tứ mệnh" else "Tây tứ mệnh", g.group)
            assertEquals(g.que, g.home.que)
        }
    }

    @Test
    fun ok_batTrachDuTamCung() {
        // Sinh Khí, Thiên Y, Diên Niên, Phục Vị, Tuyệt Mệnh, Ngũ Quỷ, Lục Sát, Họa Hại (bảng Bát trạch chuẩn)
        val expected = mapOf(
            1 to "ĐN Đ N B TN ĐB TB T",
            9 to "Đ ĐN B N TB T TN ĐB",
            3 to "N B ĐN Đ T TB ĐB TN",
            4 to "B N Đ ĐN ĐB TN T TB",
            6 to "T ĐB TN TB N Đ B ĐN",
            2 to "ĐB T TB TN B ĐN N Đ",
            7 to "TB TN ĐB T Đ N ĐN B",
            8 to "TN TB T ĐB ĐN B Đ N",
        )
        val abbr = mapOf(
            "B" to Huong.BAC, "ĐB" to Huong.DONG_BAC, "Đ" to Huong.DONG, "ĐN" to Huong.DONG_NAM,
            "N" to Huong.NAM, "TN" to Huong.TAY_NAM, "T" to Huong.TAY, "TB" to Huong.TAY_BAC,
        )
        val names = listOf("Sinh Khí", "Thiên Y", "Diên Niên", "Phục Vị", "Tuyệt Mệnh", "Ngũ Quỷ", "Lục Sát", "Họa Hại")
        for ((que, row) in expected) {
            val home = Huong.ofQue(que)
            val list = PhongThuy.batTrach(CungPhi(que, home.queName, "", home))
            row.split(" ").forEachIndexed { i, a ->
                assertEquals("${Q[que]} ${names[i]}", abbr.getValue(a), list.first { it.name == names[i] }.huong)
            }
            assertEquals(listOf("Sinh Khí", "Thiên Y", "Diên Niên", "Phục Vị", "Họa Hại", "Lục Sát", "Ngũ Quỷ", "Tuyệt Mệnh"), list.map { it.name })
        }
    }

    @Test
    fun ok_haiMuoiTuSonGanBien() {
        val son = PhongThuy.SON
        for (i in 0 until 24) {
            val center = i * 15f
            assertEquals(son[i], PhongThuy.son(center))
            assertEquals(son[i], PhongThuy.son(center - 7.4f))
            assertEquals(son[i], PhongThuy.son(center + 7.4f))
            assertEquals(son[(i + 1) % 24], PhongThuy.son(center + 7.6f))
            assertEquals(son[i], PhongThuy.son(center + 360f * 3))
            assertEquals(son[i], PhongThuy.son(center - 360f))
        }
        assertEquals("Nhâm", PhongThuy.son(352.4f))
        assertEquals("Tý", PhongThuy.son(352.6f))
        assertEquals("0° Bắc", PhongThuy.describe(359.6f))
        assertEquals("0° Bắc", PhongThuy.describe(-0.4f))
    }

    @Test
    fun nit_dungBienSonLucTheoTruocLucTheoSau() {
        // round() của Kotlin làm tròn nửa về số chẵn: đúng biên 7,5° → Tý (sơn trước), 22,5° → Sửu (sơn sau), 37,5° → Sửu (sơn trước)
        val sides = listOf(7.5f, 22.5f, 37.5f, 52.5f).map { b -> if (PhongThuy.son(b) == PhongThuy.son(b + 0.01f)) "sau" else "trước" }
        assertEquals("biên thuộc sơn nào", 1, sides.toSet().size)
    }

    @Test
    fun ok_docHuongHyThanTaiThan() {
        for (s in listOf("Đông Bắc", "Tây Bắc", "Tây Nam", "Chính Nam", "Đông Nam", "Chính Tây", "Chính Bắc", "Chính Đông")) {
            assertNotNull(s, Huong.parse(s))
        }
        val start = LunarCalendar.convert(java.time.LocalDate.of(2026, 11, 25))
        for (i in 0 until 60) {
            val l = LunarCalendar.convert(java.time.LocalDate.of(2026, 11, 25).plusDays(i.toLong()))
            val a = Almanac.of(l)
            assertNotNull(a.hyThan, Huong.parse(a.hyThan))
            assertNotNull(a.taiThan, Huong.parse(a.taiThan))
        }
        assertNotNull(start)
    }

    // ---------- ArMath ----------

    private fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    /** Ma trận máy → thế giới (hàng trước) cho máy dọc, camera sau nhìn phương vị [h], ngẩng [p], nghiêng (roll) [r] độ. */
    private fun rot(h: Double, p: Double, r: Double): FloatArray {
        val hr = Math.toRadians(h); val pr = Math.toRadians(p); val rr = Math.toRadians(r)
        val f = doubleArrayOf(sin(hr) * cos(pr), cos(hr) * cos(pr), sin(pr))
        val u0 = doubleArrayOf(-sin(hr) * sin(pr), -cos(hr) * sin(pr), cos(pr))
        val x0 = cross(f, u0)
        val x = DoubleArray(3) { x0[it] * cos(rr) + u0[it] * sin(rr) }
        val y = DoubleArray(3) { u0[it] * cos(rr) - x0[it] * sin(rr) }
        val z = DoubleArray(3) { -f[it] }
        return floatArrayOf(
            x[0].toFloat(), y[0].toFloat(), z[0].toFloat(),
            x[1].toFloat(), y[1].toFloat(), z[1].toFloat(),
            x[2].toFloat(), y[2].toFloat(), z[2].toFloat(),
        )
    }

    @Test
    fun ok_huongVaGocNgangCuaCamera() {
        for (h in listOf(0.0, 37.0, 90.0, 181.0, 270.0, 359.0)) for (p in listOf(-50.0, -10.0, 0.0, 25.0, 60.0)) for (r in listOf(-90.0, -20.0, 0.0, 15.0, 90.0)) {
            val m = rot(h, p, r)
            assertEquals("h=$h p=$p r=$r", 0f, PhongThuy.delta(h.toFloat(), ArMath.cameraHeading(m)), 0.05f)
            assertEquals("h=$h p=$p r=$r", p.toFloat(), ArMath.cameraPitch(m), 0.05f)
        }
    }

    @Test
    fun ok_chieuLenManHinh() {
        val w = 1080f; val hh = 2400f; val f = 1800f
        // Ngẩng camera lên p°: đường chân trời ngay phía trước nằm dưới tâm f·tan(p)
        for (p in listOf(-40.0, -15.0, 0.0, 20.0, 45.0)) {
            val pt = ArMath.project(rot(120.0, p, 0.0), 120f, w, hh, f)!!
            assertEquals(w / 2, pt.first, 0.5f)
            assertEquals(hh / 2 + f * tan(Math.toRadians(p)).toFloat(), pt.second, 1f)
        }
        // Lệch α sang phải (đứng thẳng): x = w/2 + f·tan α
        for (a in listOf(-40f, -10f, 10f, 40f)) {
            val pt = ArMath.project(rot(300.0, 0.0, 0.0), 300f + a, w, hh, f)!!
            assertEquals(w / 2 + f * tan(Math.toRadians(a.toDouble())).toFloat(), pt.first, 1f)
            assertEquals(hh / 2, pt.second, 0.5f)
        }
        // Nghiêng máy (roll) không đổi khoảng cách tới tâm, chỉ xoay
        for (r in listOf(-60.0, 30.0, 90.0)) {
            val pt = ArMath.project(rot(10.0, 20.0, r), 10f, w, hh, f)!!
            assertEquals(f * tan(Math.toRadians(20.0)).toFloat(), hypot(pt.first - w / 2, pt.second - hh / 2), 1f)
        }
        // Sau lưng, ngang hông → null
        assertNull(ArMath.project(rot(10.0, 0.0, 0.0), 190f, w, hh, f))
        assertNull(ArMath.project(rot(10.0, 0.0, 0.0), 100f, w, hh, f))
    }

    @Test
    fun ok_laBanPhang() {
        for (h in listOf(0.0, 45.0, 135.0, 270.0, 333.0)) {
            val hr = Math.toRadians(h)
            val y = floatArrayOf(sin(hr).toFloat(), cos(hr).toFloat(), 0f)
            val x = floatArrayOf(cos(hr).toFloat(), -sin(hr).toFloat(), 0f)
            val m = floatArrayOf(x[0], y[0], 0f, x[1], y[1], 0f, 0f, 0f, 1f)
            assertEquals(0f, PhongThuy.delta(h.toFloat(), ArMath.topHeading(m)), 0.01f)
            assertEquals(-90f, ArMath.cameraPitch(m), 0.01f)
        }
    }

    @Test
    fun ok_tieuCuHienThi() {
        // Màn rộng hơn ảnh (view 1080×1200, ảnh dọc 3:4): lấp theo chiều rộng, góc nhìn ngang = góc nhìn cạnh ngắn cảm biến
        val f = ArMath.displayFocal(1080f, 1200f, 4.8f, 6.4f, 4.8f)
        val fovH = 2 * Math.toDegrees(Math.atan((1080 / 2.0) / f))
        assertEquals(2 * Math.toDegrees(Math.atan(2.4 / 4.8)), fovH, 0.1)
        // Màn cao (1080×2400): lấp theo chiều cao, góc nhìn dọc = góc nhìn cạnh dài cảm biến
        val f2 = ArMath.displayFocal(1080f, 2400f, 4.8f, 6.4f, 4.8f)
        assertEquals(2 * Math.toDegrees(Math.atan(3.2 / 4.8)), 2 * Math.toDegrees(Math.atan(1200.0 / f2)), 0.1)
        assertTrue(abs(ArMath.fallbackFocal(2000f) - 1000f / tan(Math.toRadians(32.5)).toFloat()) < 1f)
    }
}
