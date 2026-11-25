package vn.lichthoitiet

import kotlin.math.floor
import kotlin.math.round

/** Tám hướng; [deg] là góc phương vị (0 = Bắc, theo chiều kim đồng hồ), [que] là quẻ Hậu thiên tương ứng. */
enum class Huong(val label: String, val deg: Float, val que: Int, val queName: String) {
    BAC("Bắc", 0f, 1, "Khảm"),
    DONG_BAC("Đông Bắc", 45f, 8, "Cấn"),
    DONG("Đông", 90f, 3, "Chấn"),
    DONG_NAM("Đông Nam", 135f, 4, "Tốn"),
    NAM("Nam", 180f, 9, "Ly"),
    TAY_NAM("Tây Nam", 225f, 2, "Khôn"),
    TAY("Tây", 270f, 7, "Đoài"),
    TAY_BAC("Tây Bắc", 315f, 6, "Càn");

    companion object {
        fun ofQue(que: Int): Huong = values().first { it.que == que }

        /** Đọc tên hướng như "Chính Nam", "Đông Bắc" (bảng Hỷ thần, Tài thần); null nếu không nhận ra. */
        fun parse(s: String): Huong? {
            val t = s.trim().removePrefix("Chính ").trim()
            return values().firstOrNull { it.label.equals(t, ignoreCase = true) }
        }

        /** Hướng gần nhất với góc [deg]. */
        fun nearest(deg: Float): Huong = values()[Math.floorMod(floor((PhongThuy.normD(deg) + 22.5) / 45.0).toInt(), 8)]
    }
}

/** Một sao Bát trạch ứng với một hướng của gia chủ. */
data class SaoBatTrach(val name: String, val meaning: String, val good: Boolean, val rank: Int, val huong: Huong)

/** Cung phi (quẻ mệnh) của gia chủ. */
data class CungPhi(val que: Int, val name: String, val group: String, val home: Huong)

/** Phong thủy Bát trạch: cung phi theo năm sinh âm lịch và giới tính, tám hướng tốt xấu. */
object PhongThuy {
    /**
     * Cung phi theo năm sinh âm lịch [lunarYear]: gọi s = năm mod 9 (0 → 9);
     * nam (11 − s) mod 9, nữ (s + 4) mod 9 (0 → 9); số 5 thì nam là Khôn (2), nữ là Cấn (8).
     * Ví dụ nam 1990 cung Khảm, nữ 1990 cung Cấn; nam 1985 Càn, nữ 1985 Ly.
     */
    fun cungPhi(lunarYear: Int, male: Boolean): CungPhi {
        val s = Math.floorMod(lunarYear, 9).let { if (it == 0) 9 else it }
        var k = if (male) Math.floorMod(11 - s, 9) else Math.floorMod(s + 4, 9)
        if (k == 0) k = 9
        if (k == 5) k = if (male) 2 else 8
        val home = Huong.ofQue(k)
        val dong = k in setOf(1, 3, 4, 9)
        return CungPhi(k, home.queName, if (dong) "Đông tứ mệnh" else "Tây tứ mệnh", home)
    }

    // Các cặp quẻ theo từng sao (mỗi cặp không thứ tự); 7 sao × 4 cặp = đủ 28 cặp của 8 quẻ, cộng Phục Vị là chính cung.
    private val PAIRS = linkedMapOf(
        "Sinh Khí" to listOf(1 to 4, 2 to 8, 3 to 9, 6 to 7),
        "Thiên Y" to listOf(1 to 3, 2 to 7, 4 to 9, 6 to 8),
        "Diên Niên" to listOf(1 to 9, 2 to 6, 3 to 4, 7 to 8),
        "Tuyệt Mệnh" to listOf(1 to 2, 3 to 7, 4 to 8, 6 to 9),
        "Ngũ Quỷ" to listOf(1 to 8, 2 to 4, 3 to 6, 7 to 9),
        "Lục Sát" to listOf(1 to 6, 2 to 9, 3 to 8, 4 to 7),
        "Họa Hại" to listOf(1 to 7, 2 to 3, 4 to 6, 8 to 9),
    )

    private val MEANING = mapOf(
        "Sinh Khí" to "Tài lộc, danh tiếng",
        "Thiên Y" to "Sức khỏe, quý nhân",
        "Diên Niên" to "Gia đạo hòa thuận",
        "Phục Vị" to "Bình yên, vững vàng",
        "Tuyệt Mệnh" to "Hao tổn, bệnh tật",
        "Ngũ Quỷ" to "Thị phi, tai họa",
        "Lục Sát" to "Xáo trộn, kiện tụng",
        "Họa Hại" to "Phiền nhiễu, thất bại",
    )

    private val ORDER = listOf("Sinh Khí", "Thiên Y", "Diên Niên", "Phục Vị", "Họa Hại", "Lục Sát", "Ngũ Quỷ", "Tuyệt Mệnh")

    /** Tên sao Bát trạch của hướng thuộc quẻ [que] với gia chủ cung [cung]. */
    fun star(cung: Int, que: Int): String {
        if (cung == que) return "Phục Vị"
        return PAIRS.entries.first { (_, pairs) -> pairs.any { (a, b) -> (a == cung && b == que) || (a == que && b == cung) } }.key
    }

    /** Tám hướng của gia chủ, xếp theo mức tốt: Sinh Khí, Thiên Y, Diên Niên, Phục Vị rồi bốn hướng xấu. */
    fun batTrach(cung: CungPhi): List<SaoBatTrach> = Huong.values().map { h ->
        val name = star(cung.que, h.que)
        val rank = ORDER.indexOf(name)
        SaoBatTrach(name, MEANING.getValue(name), rank < 4, rank, h)
    }.sortedBy { it.rank }

    // 24 sơn, mỗi sơn 15°, Tý ở chính Bắc (352,5° – 7,5°)
    val SON = listOf(
        "Tý", "Quý", "Sửu", "Cấn", "Dần", "Giáp", "Mão", "Ất", "Thìn", "Tốn", "Tỵ", "Bính",
        "Ngọ", "Đinh", "Mùi", "Khôn", "Thân", "Canh", "Dậu", "Tân", "Tuất", "Càn", "Hợi", "Nhâm",
    )

    /** Sơn (trong 24 sơn) của góc phương vị [deg]. */
    fun son(deg: Float): String = SON[Math.floorMod(floor((normD(deg) + 7.5) / 15.0).toInt(), 24)]

    /** Góc về [0, 360) bằng số thực đôi, không làm tròn góc đã nằm sẵn trong khoảng. */
    internal fun normD(deg: Float): Double {
        val d = deg.toDouble()
        return if (d >= 0.0 && d < 360.0) d else ((d % 360.0) + 360.0) % 360.0
    }

    /** Đưa góc về [0, 360). */
    fun norm(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    /** Độ lệch có dấu từ [from] tới [to], trong (−180, 180]. */
    fun delta(from: Float, to: Float): Float {
        val d = norm(to - from)
        return if (d > 180f) d - 360f else d
    }

    /** Tên hướng kèm độ, ví dụ "135° Đông Nam". */
    fun describe(deg: Float): String = "${Math.floorMod(floor(normD(deg) + 0.5).toInt(), 360)}° ${Huong.nearest(deg).label}"
}

/**
 * Phép chiếu cho lớp phủ AR. Ma trận xoay [r] (3×3, hàng trước) đổi tọa độ máy sang tọa độ thế giới
 * (x Đông, y Bắc, z lên trời) như SensorManager.getRotationMatrixFromVector.
 * Camera sau nhìn theo trục −z của máy.
 */
object ArMath {
    /** Phương vị (độ) mà camera sau đang nhìn. */
    fun cameraHeading(r: FloatArray): Float =
        PhongThuy.norm(Math.toDegrees(kotlin.math.atan2(-r[2].toDouble(), -r[5].toDouble())).toFloat())

    /** Góc ngẩng của camera (độ): 0 nhìn ngang, âm là chúc xuống đất. */
    fun cameraPitch(r: FloatArray): Float = Math.toDegrees(kotlin.math.asin((-r[8]).coerceIn(-1f, 1f).toDouble())).toFloat()

    /** Phương vị của cạnh trên màn hình khi đặt máy nằm ngang (la bàn phẳng). */
    fun topHeading(r: FloatArray): Float =
        PhongThuy.norm(Math.toDegrees(kotlin.math.atan2(r[1].toDouble(), r[4].toDouble())).toFloat())

    /**
     * Chiếu một hướng nằm ngang có phương vị [azimuth] lên màn hình [w]×[h] với tiêu cự [f] (pixel).
     * Trả về (x, y) hoặc null nếu hướng đó ở sau lưng camera.
     */
    fun project(r: FloatArray, azimuth: Float, w: Float, h: Float, f: Float): Pair<Float, Float>? {
        val a = Math.toRadians(azimuth.toDouble())
        val wx = kotlin.math.sin(a).toFloat()
        val wy = kotlin.math.cos(a).toFloat()
        // Tọa độ máy = Rᵀ · thế giới (vector nằm ngang nên bỏ thành phần z)
        val dx = r[0] * wx + r[3] * wy
        val dy = r[1] * wx + r[4] * wy
        val dz = r[2] * wx + r[5] * wy
        if (dz > -0.05f) return null
        return Pair(w / 2 + f * dx / -dz, h / 2 - f * dy / -dz)
    }

    /**
     * Tiêu cự hiển thị (pixel) khi khung camera dọc lấp đầy khung xem [viewW]×[viewH] (FILL_CENTER).
     * [focalMm] tiêu cự thấu kính, [sensorW]×[sensorH] kích thước cảm biến (mm, cạnh dài trước).
     */
    fun displayFocal(viewW: Float, viewH: Float, focalMm: Float, sensorW: Float, sensorH: Float): Float {
        val imageAspect = sensorH / sensorW            // ảnh dọc: rộng / cao
        val viewAspect = viewW / viewH
        return if (viewAspect < imageAspect) focalMm / sensorW * viewH else focalMm / sensorH * viewW
    }

    /** Tiêu cự mặc định khi không đọc được thông số camera: góc nhìn dọc khoảng 65°. */
    fun fallbackFocal(viewH: Float): Float = (viewH / 2f) / kotlin.math.tan(Math.toRadians(32.5)).toFloat()
}
