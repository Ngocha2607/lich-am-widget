package vn.lichthoitiet

import java.time.LocalDate

data class LucDieuHour(val name: String, val hours: String, val good: Boolean, val desc: String)

/** Một sao trong Nhị thập bát tú. [nen]/[ky] rỗng nghĩa là "không hợp việc gì" / "không kỵ gì". */
data class Tu(val name: String, val fullName: String, val good: Boolean, val nen: String, val ky: String)

/** Thông tin xem ngày tính theo trực (thập nhị kiến trừ), can chi và phép Lục Diệu. */
data class DayAlmanac(
    val truc: String,
    val nen: List<String>,
    val ky: List<String>,
    val napAm: String,
    val nguHanh: String,
    val xung: String,
    val hyThan: String,
    val taiThan: String,
    val lucDieuDay: String,
    val lucDieu: List<LucDieuHour>,
    val tu: Tu,
)

data class GoodDayTask(val key: String, val name: String, val icon: String, val good: Set<Int>, val bad: Set<Int>)
data class GoodDay(val date: LocalDate, val score: Int, val reasons: List<String>, val truc: String, val hoangDao: Boolean)

object Almanac {
    val TRUC = listOf("Kiến", "Trừ", "Mãn", "Bình", "Định", "Chấp", "Phá", "Nguy", "Thành", "Thu", "Khai", "Bế")

    private val TRUC_NEN = listOf(
        listOf("Xuất hành", "Cầu tài", "Gặp người lớn", "Nhậm chức", "Trồng cây"),
        listOf("Cúng tế", "Chữa bệnh", "Dọn dẹp, tẩy uế", "Trừ sâu bệnh"),
        listOf("Cúng tế", "Cầu phúc", "Khai trương", "Ký kết", "Xuất hành"),
        listOf("Cưới hỏi", "Sửa nhà", "Động thổ", "Dọn nhà", "Nhậm chức"),
        listOf("Cưới hỏi", "Ký hợp đồng", "Nhậm chức", "Mua bán", "Động thổ"),
        listOf("Xây dựng", "Sửa chữa", "Trồng cây", "Tuyển người", "Cưới hỏi"),
        listOf("Phá dỡ", "Dọn dẹp", "Chữa bệnh"),
        listOf("Cúng tế", "Cầu an", "Tu sửa nhỏ"),
        listOf("Khai trương", "Cưới hỏi", "Nhập học", "Nhậm chức", "Động thổ", "Xuất hành"),
        listOf("Thu hoạch", "Nhập kho", "Thu nợ", "Cưới hỏi", "Mua tài sản"),
        listOf("Khai trương", "Cưới hỏi", "Nhậm chức", "Xuất hành", "Nhập học"),
        listOf("Đắp đê", "Xây tường", "Lấp hố", "An táng"),
    )
    private val TRUC_KY = listOf(
        listOf("Động thổ", "Đào giếng", "An táng"),
        listOf("Cưới hỏi", "Nhậm chức", "Xuất hành xa"),
        listOf("An táng", "Kiện tụng", "Nhậm chức"),
        listOf("Đào mương", "Trồng cây lớn"),
        listOf("Kiện tụng", "Xuất hành xa"),
        listOf("Xuất hành", "Khai trương", "Mở kho"),
        listOf("Cưới hỏi", "Khai trương", "Ký kết", "Nhậm chức", "Động thổ"),
        listOf("Xuất hành", "Leo cao", "Động thổ", "Cưới hỏi"),
        listOf("Kiện tụng"),
        listOf("Xuất hành", "Mở kho", "An táng"),
        listOf("An táng", "Động thổ"),
        listOf("Khai trương", "Xuất hành", "Chữa bệnh"),
    )

    val LUC_DIEU = listOf("Đại An", "Lưu Niên", "Tốc Hỷ", "Xích Khẩu", "Tiểu Cát", "Không Vong")
    private val LUC_DIEU_GOOD = setOf(0, 2, 4)
    private val LUC_DIEU_DESC = listOf(
        "Mọi việc đều tốt, cầu tài đi hướng Tây Nam. Nhà cửa yên lành, người xuất hành bình an.",
        "Việc khó thành, cầu tài mờ mịt, kiện tụng nên hoãn. Làm chậm nhưng chắc.",
        "Vui sắp tới. Cầu tài đi hướng Nam, đi việc quan nhiều may mắn, xuất hành bình an.",
        "Hay cãi cọ, tranh chấp, đề phòng miệng tiếng; người đi nên hoãn lại.",
        "Rất tốt lành, đi thường gặp may; buôn bán có lời, mọi việc hòa hợp.",
        "Xấu, cầu tài không lợi, hay bị trái ý; việc quan không thuận, kiện cáo bất lợi.",
    )

    /**
     * Nhị thập bát tú theo thứ tự, bắt đầu từ sao Giác. Tốt/xấu theo xemlicham.com và lichngaytot.com
     * (hai nguồn khớp nhau cả 28 sao); việc nên/kỵ tóm tắt từ xemlicham.com.
     */
    val NHI_THAP_BAT_TU = listOf(
        Tu("Giác", "Giác Mộc Giao", true, "tạo tác, cưới hỏi, cầu công danh, thi cử", "chôn cất, sửa mộ"),
        Tu("Cang", "Cang Kim Long", false, "cắt may", "chôn cất, cưới gả, kiện tụng"),
        Tu("Đê", "Đê Thổ Lạc", false, "", "khởi công, chôn cất, cưới gả, xuất hành, đi đường thủy"),
        Tu("Phòng", "Phòng Nhật Thố", true, "mọi việc: cưới gả, xuất hành, xây nhà, chôn cất, mua bán", ""),
        Tu("Tâm", "Tâm Nguyệt Hồ", false, "", "cưới gả, đóng giường, xây cất, chôn cất, kiện tụng"),
        Tu("Vĩ", "Vĩ Hỏa Hổ", true, "khởi tạo, cưới gả, xây cất, chôn cất, thủy lợi", "đóng giường, đi thuyền, mua sắm"),
        Tu("Cơ", "Cơ Thủy Báo", true, "khai trương, xuất hành, chôn cất, tu bổ mộ, thủy lợi", "đóng giường, đi thuyền"),
        Tu("Đẩu", "Đẩu Mộc Giải", true, "xây đắp, sửa mộ, thủy lợi, may mặc, kinh doanh", "đi thuyền"),
        Tu("Ngưu", "Ngưu Kim Ngưu", false, "đi thuyền, cắt may", "khởi công, xây nhà, cưới gả, xuất hành"),
        Tu("Nữ", "Nữ Thổ Bức", false, "may áo, kết màn", "khởi công, tháo nước, trổ cửa, kiện cáo, chôn cất"),
        Tu("Hư", "Hư Nhật Thử", false, "", "xây nhà, khai trương, cưới gả, trổ cửa"),
        Tu("Nguy", "Nguy Nguyệt Yến", false, "lót giường, chôn cất", "dựng nhà, gác đòn dông, tháo nước, đi thuyền"),
        Tu("Thất", "Thất Hỏa Trư", true, "mọi việc: xây nhà, cưới gả, chôn cất, thủy lợi", ""),
        Tu("Bích", "Bích Thủy Du", true, "mọi việc: khai trương, xuất hành, xây nhà, cưới gả, chôn cất", ""),
        Tu("Khuê", "Khuê Mộc Lang", false, "nhập học, cắt áo, dựng nhà, cầu công danh", "chôn cất, khai trương, trổ cửa, đào giếng, kiện tụng"),
        Tu("Lâu", "Lâu Kim Cẩu", true, "dựng cột, cưới gả, cất lầu, thủy lợi", "đóng giường, đi đường thủy"),
        Tu("Vị", "Vị Thổ Trĩ", true, "cưới gả, xây cất, gieo trồng", "đi thuyền"),
        Tu("Mão", "Mão Nhật Kê", false, "xây dựng", "chôn cất, cưới gả, khai trương, xuất hành"),
        Tu("Tất", "Tất Nguyệt Ô", true, "trổ cửa, thủy lợi, cưới gả, chôn cất, đào giếng", "đi thuyền"),
        Tu("Chủy", "Chủy Hỏa Hầu", false, "", "chôn cất, sửa mộ"),
        Tu("Sâm", "Sâm Thủy Viên", true, "xây nhà, trổ cửa, nhập học, thủy lợi, đi thuyền", "cưới gả, đóng giường, chôn cất"),
        Tu("Tỉnh", "Tỉnh Mộc Hãn", true, "trổ cửa, đào giếng, xây cất, nhậm chức, nhập học", "chôn cất, tu bổ mộ"),
        Tu("Quỷ", "Quỷ Kim Dương", false, "chôn cất, cắt áo", "xây nhà, cưới gả, động thổ, trổ cửa"),
        Tu("Liễu", "Liễu Thổ Chướng", false, "", "thủy lợi, chôn cất, trổ cửa, xây đắp"),
        Tu("Tinh", "Tinh Nhật Mã", false, "xây phòng mới", "chôn cất, cưới gả, khơi đường nước"),
        Tu("Trương", "Trương Nguyệt Lộc", true, "mọi việc: xây nhà, cưới gả, chôn cất, làm ruộng", "sửa thuyền, hạ thủy thuyền"),
        Tu("Dực", "Dực Hỏa Xà", false, "cắt áo", "chôn cất, xây nhà, cưới gả, trổ cửa, thủy lợi"),
        Tu("Chẩn", "Chẩn Thủy Dẫn", true, "cưới gả, xây lầu, chôn cất, xuất hành", "đi thuyền"),
    )

    /**
     * Sao Nhị thập bát tú của ngày: chu kỳ 28 ngày liên tục gắn với tuần (sao Giác luôn vào thứ Năm).
     * Mốc đối chiếu: 26/11/2026 sao Khuê, 1/1/2000 sao Vị (lichngaytot, xemlicham, hoàng lịch 值日星宿).
     */
    fun tuIndex(jd: Int): Int = ((jd + 11) % 28 + 28) % 28

    fun tu(jd: Int): Tu = NHI_THAP_BAT_TU[tuIndex(jd)]

    /** Nạp âm 60 hoa giáp, mỗi mục ứng với hai can chi liên tiếp. */
    val NAP_AM = listOf(
        "Hải Trung Kim", "Lư Trung Hỏa", "Đại Lâm Mộc", "Lộ Bàng Thổ", "Kiếm Phong Kim",
        "Sơn Đầu Hỏa", "Giản Hạ Thủy", "Thành Đầu Thổ", "Bạch Lạp Kim", "Dương Liễu Mộc",
        "Tuyền Trung Thủy", "Ốc Thượng Thổ", "Tích Lịch Hỏa", "Tùng Bách Mộc", "Trường Lưu Thủy",
        "Sa Trung Kim", "Sơn Hạ Hỏa", "Bình Địa Mộc", "Bích Thượng Thổ", "Kim Bạch Kim",
        "Phú Đăng Hỏa", "Thiên Hà Thủy", "Đại Trạch Thổ", "Thoa Xuyến Kim", "Tang Đố Mộc",
        "Đại Khê Thủy", "Sa Trung Thổ", "Thiên Thượng Hỏa", "Thạch Lựu Mộc", "Đại Hải Thủy",
    )

    // Theo can ngày: Giáp, Ất, Bính, Đinh, Mậu, Kỷ, Canh, Tân, Nhâm, Quý
    private val HY_THAN = listOf("Đông Bắc", "Tây Bắc", "Tây Nam", "Chính Nam", "Đông Nam", "Đông Bắc", "Tây Bắc", "Tây Nam", "Chính Nam", "Đông Nam")
    private val TAI_THAN = listOf("Đông Nam", "Đông Nam", "Chính Tây", "Chính Tây", "Chính Bắc", "Chính Bắc", "Chính Đông", "Chính Đông", "Chính Nam", "Chính Nam")

    private fun t(vararg names: String): Set<Int> = names.map { TRUC.indexOf(it) }.toSet()

    val TASKS = listOf(
        GoodDayTask("cuoi", "Cưới hỏi", "💍", t("Thành", "Định", "Bình", "Khai", "Mãn"), t("Phá", "Nguy", "Bế", "Trừ")),
        GoodDayTask("khaitruong", "Khai trương", "✂️", t("Thành", "Khai", "Mãn", "Định"), t("Phá", "Nguy", "Bế", "Chấp", "Thu")),
        GoodDayTask("khoicong", "Khởi công", "🏗️", t("Thành", "Định", "Bình", "Chấp"), t("Phá", "Nguy", "Kiến", "Bế", "Khai")),
        GoodDayTask("nhaptrach", "Nhập trạch", "🏠", t("Thành", "Định", "Bình", "Khai", "Mãn"), t("Phá", "Nguy", "Bế", "Trừ")),
        GoodDayTask("hopdong", "Ký hợp đồng", "🤝", t("Thành", "Định", "Mãn", "Khai", "Kiến"), t("Phá", "Nguy", "Bế")),
        GoodDayTask("muasam", "Mua xe, tài sản", "🚗", t("Thành", "Định", "Mãn", "Khai", "Thu"), t("Phá", "Nguy", "Bế")),
        GoodDayTask("xuathanh", "Xuất hành", "✈️", t("Kiến", "Thành", "Khai", "Bình", "Mãn"), t("Phá", "Nguy", "Bế", "Chấp", "Thu")),
        GoodDayTask("cungle", "Cúng lễ, cầu an", "🙏", t("Mãn", "Nguy", "Trừ", "Thành", "Bình"), t("Phá", "Bế")),
    )

    private val TAM_HOP = listOf(setOf(8, 0, 4), setOf(5, 9, 1), setOf(2, 6, 10), setOf(11, 3, 7))
    private val LUC_HOP = mapOf(0 to 1, 1 to 0, 2 to 11, 11 to 2, 3 to 10, 10 to 3, 4 to 9, 9 to 4, 5 to 8, 8 to 5, 6 to 7, 7 to 6)

    /**
     * Ngày sinh dương lịch từ ngày người dùng nhập theo âm ([lunar] = true) hoặc dương lịch.
     * Trả về null nếu ngày không tồn tại (31/2, 30 của tháng âm thiếu) hoặc ở sau [today].
     */
    fun birthToSolar(lunar: Boolean, d: Int, m: Int, y: Int, today: LocalDate): LocalDate? {
        val date = if (lunar) LunarCalendar.toSolar(d, m, y)
        else try { LocalDate.of(y, m, d) } catch (e: Exception) { null }
        return date?.takeIf { !it.isAfter(today) }
    }

    /** Chi của tuổi theo năm sinh âm lịch. */
    fun birthChi(lunarYear: Int): Int = ((lunarYear + 8) % 12 + 12) % 12

    /** Quan hệ chi ngày với chi tuổi: 1 hợp (tam hợp, lục hợp), -1 xung (lục xung), 0 bình thường. */
    fun tuoiRelation(dayChi: Int, birthChi: Int): Int = when {
        (dayChi + 6) % 12 == birthChi -> -1
        LUC_HOP[dayChi] == birthChi -> 1
        TAM_HOP.any { dayChi in it && birthChi in it } -> 1
        else -> 0
    }

    /** Trực của ngày: Kiến tại ngày có chi trùng chi tháng tiết khí. */
    fun trucIndex(l: LunarDate): Int =
        (LunarCalendar.dayChiIndex(l.jd) - LunarCalendar.tietMonthChi(l.jd) + 12) % 12

    /** Sao Lục Diệu của ngày: tháng Giêng khởi Đại An, đếm tháng rồi đếm ngày. */
    fun lucDieuDayIndex(l: LunarDate): Int = ((l.month - 1) + (l.day - 1)) % 6

    fun napAm(jd: Int): String {
        val c = (jd + 9) % 10
        val z = (jd + 1) % 12
        val n = ((6 * c - 5 * z) % 60 + 60) % 60
        return NAP_AM[n / 2]
    }

    fun of(l: LunarDate): DayAlmanac {
        val t = trucIndex(l)
        val c = (l.jd + 9) % 10
        val z = LunarCalendar.dayChiIndex(l.jd)
        val ld = lucDieuDayIndex(l)
        fun h(ci: Int) = "${(ci * 2 + 23) % 24}–${(ci * 2 + 25) % 24}h (${LunarCalendar.CHI[ci]})"
        val hours = (0 until 6).map { i ->
            val s = (ld + i) % 6
            LucDieuHour(LUC_DIEU[s], "${h(i)}, ${h(i + 6)}", s in LUC_DIEU_GOOD, LUC_DIEU_DESC[s])
        }
        val nap = napAm(l.jd)
        return DayAlmanac(
            truc = TRUC[t], nen = TRUC_NEN[t], ky = TRUC_KY[t],
            napAm = nap, nguHanh = nap.substringAfterLast(' '),
            xung = LunarCalendar.CHI[(z + 6) % 12],
            hyThan = HY_THAN[c], taiThan = TAI_THAN[c],
            lucDieuDay = LUC_DIEU[ld], lucDieu = hours,
            tu = tu(l.jd),
        )
    }

    /** Những ngày hợp với việc [taskKey] trong [horizon] ngày tới, điểm cao xếp trước. */
    fun goodDays(taskKey: String, from: LocalDate, horizon: Int = 60, limit: Int = 10, birthChi: Int? = null): List<GoodDay> {
        val task = TASKS.find { it.key == taskKey } ?: return emptyList()
        val out = mutableListOf<GoodDay>()
        for (i in 1..horizon) {
            val d = from.plusDays(i.toLong())
            val l = LunarCalendar.convert(d)
            val t = trucIndex(l)
            if (t in task.bad) continue
            val rel = birthChi?.let { tuoiRelation(LunarCalendar.dayChiIndex(l.jd), it) } ?: 0
            if (rel < 0) continue
            val hd = LunarCalendar.isHoangDao(l)
            val ld = lucDieuDayIndex(l)
            var score = 0
            val reasons = mutableListOf<String>()
            if (hd) { score += 2; reasons += "Hoàng đạo" }
            if (t in task.good) { score += 2; reasons += "Trực ${TRUC[t]}" }
            if (ld in LUC_DIEU_GOOD) { score += 1; reasons += LUC_DIEU[ld] }
            val tu = tu(l.jd)
            if (tu.good) { score += 1; reasons += "Sao ${tu.name}" }
            if (rel > 0) { score += 1; reasons += "Hợp tuổi" }
            if (score >= 3) out += GoodDay(d, score, reasons, TRUC[t], hd)
        }
        return out.sortedWith(compareByDescending<GoodDay> { it.score }.thenBy { it.date }).take(limit)
    }
}
