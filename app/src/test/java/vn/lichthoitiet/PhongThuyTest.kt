package vn.lichthoitiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PhongThuyTest {
    @Test
    fun cungPhiTheoNamSinhVaGioiTinh() {
        // Bảng cung phi phổ biến
        assertEquals("Khảm", PhongThuy.cungPhi(1990, true).name)
        assertEquals("Cấn", PhongThuy.cungPhi(1990, false).name)
        assertEquals("Càn", PhongThuy.cungPhi(1985, true).name)
        assertEquals("Ly", PhongThuy.cungPhi(1985, false).name)
        assertEquals("Ly", PhongThuy.cungPhi(2000, true).name)
        assertEquals("Càn", PhongThuy.cungPhi(2000, false).name)
        assertEquals("Cấn", PhongThuy.cungPhi(2001, true).name)
        assertEquals("Đoài", PhongThuy.cungPhi(2001, false).name)
        assertEquals("Tốn", PhongThuy.cungPhi(1987, true).name)
        assertEquals("Khôn", PhongThuy.cungPhi(1987, false).name)
    }

    @Test
    fun soNamCungTrungCung() {
        // Năm có quẻ số 5: nam (11 − s) mod 9 = 5 ⇔ s = 6 (ví dụ 1995), nữ (s + 4) mod 9 = 5 ⇔ s = 1 (ví dụ 1990)
        assertEquals(2, PhongThuy.cungPhi(1995, true).que)
        assertEquals(8, PhongThuy.cungPhi(1990, false).que)
        assertEquals("Tây tứ mệnh", PhongThuy.cungPhi(1995, true).group)
        assertEquals("Đông tứ mệnh", PhongThuy.cungPhi(1990, true).group)
    }

    @Test
    fun batTrachDuTamSaoMoiCung() {
        for (que in listOf(1, 2, 3, 4, 6, 7, 8, 9)) {
            val home = Huong.ofQue(que)
            val cung = CungPhi(que, home.queName, "", home)
            val list = PhongThuy.batTrach(cung)
            assertEquals(8, list.map { it.name }.toSet().size)
            assertEquals(8, list.map { it.huong }.toSet().size)
            assertEquals("Phục Vị", list.first { it.huong == home }.name)
            // Quan hệ đối xứng: sao của A nhìn B trùng sao của B nhìn A
            for (other in listOf(1, 2, 3, 4, 6, 7, 8, 9)) assertEquals(PhongThuy.star(que, other), PhongThuy.star(other, que))
        }
    }

    @Test
    fun batTrachCungKham() {
        val k = PhongThuy.batTrach(PhongThuy.cungPhi(1990, true))
        fun at(n: String) = k.first { it.name == n }.huong
        assertEquals(Huong.DONG_NAM, at("Sinh Khí"))
        assertEquals(Huong.DONG, at("Thiên Y"))
        assertEquals(Huong.NAM, at("Diên Niên"))
        assertEquals(Huong.BAC, at("Phục Vị"))
        assertEquals(Huong.TAY_NAM, at("Tuyệt Mệnh"))
        assertEquals(Huong.DONG_BAC, at("Ngũ Quỷ"))
        assertEquals(Huong.TAY_BAC, at("Lục Sát"))
        assertEquals(Huong.TAY, at("Họa Hại"))
        assertEquals(listOf("Sinh Khí", "Thiên Y", "Diên Niên", "Phục Vị"), k.filter { it.good }.map { it.name })
    }

    @Test
    fun haiMuoiTuSonVaDocHuong() {
        assertEquals("Tý", PhongThuy.son(0f))
        assertEquals("Tý", PhongThuy.son(359f))
        assertEquals("Nhâm", PhongThuy.son(345f))
        assertEquals("Ngọ", PhongThuy.son(180f))
        assertEquals("Tốn", PhongThuy.son(135f))
        assertEquals(Huong.NAM, Huong.parse("Chính Nam"))
        assertEquals(Huong.DONG_BAC, Huong.parse("Đông Bắc"))
        assertNull(Huong.parse("Giữa"))
        assertEquals(Huong.BAC, Huong.nearest(350f))
        assertEquals(-20f, PhongThuy.delta(10f, 350f), 1e-3f)
        assertEquals(20f, PhongThuy.delta(350f, 10f), 1e-3f)
    }

    // Máy đứng thẳng, camera sau nhìn về hướng Bắc: trục x máy = Đông, y máy = lên trời, z máy = Nam
    private val uprightNorth = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)

    @Test
    fun chieuArKhiNhinVeBac() {
        assertEquals(0f, ArMath.cameraHeading(uprightNorth), 1e-3f)
        assertEquals(0f, ArMath.cameraPitch(uprightNorth), 1e-3f)
        val center = ArMath.project(uprightNorth, 0f, 1000f, 2000f, 1500f)
        assertNotNull(center)
        assertEquals(500f, center!!.first, 0.5f)
        assertEquals(1000f, center.second, 0.5f)
        // Hướng lệch phải 20° nằm bên phải tâm
        val right = ArMath.project(uprightNorth, 20f, 1000f, 2000f, 1500f)!!
        assertTrue(right.first > 500f)
        assertEquals(500f + 1500f * Math.tan(Math.toRadians(20.0)).toFloat(), right.first, 1f)
        // Hướng Nam ở sau lưng
        assertNull(ArMath.project(uprightNorth, 180f, 1000f, 2000f, 1500f))
    }

    @Test
    fun laBanPhangVaTieuCu() {
        // Máy nằm ngửa, cạnh trên chỉ hướng Đông: trục x máy = Nam, y máy = Đông, z máy = lên trời
        val flatEast = floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
        assertEquals(90f, ArMath.topHeading(flatEast), 1e-3f)
        assertTrue(ArMath.cameraPitch(flatEast) < -80f)
        // Cảm biến 4:3 (6,4 × 4,8 mm), tiêu cự 4,8 mm, màn 1080 × 2400: lấp theo chiều cao
        val f = ArMath.displayFocal(1080f, 2400f, 4.8f, 6.4f, 4.8f)
        assertEquals(4.8f / 6.4f * 2400f, f, 0.5f)
        assertTrue(abs(ArMath.fallbackFocal(2400f) - 1884f) < 5f)
    }
}
