package vn.lichthoitiet

import java.time.LocalDate
import java.time.MonthDay

/** Cung hoàng đạo (Tây) theo ngày sinh dương lịch; khoảng ngày theo bảng phổ biến của lichngaytot, hoanghamobile. */
data class ZodiacSign(val name: String, val symbol: String, val element: String, val range: String, val traits: String)

/** Một tuổi xông đất: điểm 0–100, nhãn mức, lý do và lưu ý (Tam Tai, Thái Tuế trong năm). */
data class XongDatPick(val year: Int, val canChi: String, val score: Int, val label: String, val reasons: List<String>, val warnings: List<String>)

/** Kết quả xem tuổi xông đất cho một gia chủ trong năm âm [year]. */
data class XongDat(val year: Int, val canChi: String, val avoid: List<String>, val picks: List<XongDatPick>)

/** Tử vi cơ bản theo ngày sinh: cung hoàng đạo, con giáp, mệnh ngũ hành nạp âm. */
data class BasicHoroscope(val sign: ZodiacSign, val canChi: String, val animal: String, val menh: String, val hanh: String)

object Horoscope {
    // (ngày bắt đầu, cung) theo thứ tự trong năm; cung trước ngày 20/1 là Ma Kết
    private val SIGNS = listOf(
        MonthDay.of(1, 20) to ZodiacSign("Bảo Bình", "♒", "Khí", "20/1 – 18/2", "Độc lập, giàu ý tưởng, thích điều mới và quan tâm đến cộng đồng."),
        MonthDay.of(2, 19) to ZodiacSign("Song Ngư", "♓", "Nước", "19/2 – 20/3", "Nhạy cảm, giàu trí tưởng tượng, dễ đồng cảm với người khác."),
        MonthDay.of(3, 21) to ZodiacSign("Bạch Dương", "♈", "Lửa", "21/3 – 19/4", "Năng nổ, thẳng thắn, thích dẫn đầu và dám thử thách."),
        MonthDay.of(4, 20) to ZodiacSign("Kim Ngưu", "♉", "Đất", "20/4 – 20/5", "Kiên nhẫn, thực tế, coi trọng sự ổn định và cái đẹp."),
        MonthDay.of(5, 21) to ZodiacSign("Song Tử", "♊", "Khí", "21/5 – 21/6", "Linh hoạt, hoạt ngôn, tò mò và học nhanh."),
        MonthDay.of(6, 22) to ZodiacSign("Cự Giải", "♋", "Nước", "22/6 – 22/7", "Chu đáo, giàu tình cảm, gắn bó với gia đình."),
        MonthDay.of(7, 23) to ZodiacSign("Sư Tử", "♌", "Lửa", "23/7 – 22/8", "Tự tin, hào phóng, thích được ghi nhận."),
        MonthDay.of(8, 23) to ZodiacSign("Xử Nữ", "♍", "Đất", "23/8 – 22/9", "Tỉ mỉ, ngăn nắp, cầu toàn và hay giúp đỡ."),
        MonthDay.of(9, 23) to ZodiacSign("Thiên Bình", "♎", "Khí", "23/9 – 23/10", "Hòa nhã, công bằng, khéo léo trong giao tiếp."),
        MonthDay.of(10, 24) to ZodiacSign("Bọ Cạp", "♏", "Nước", "24/10 – 21/11", "Sâu sắc, quyết đoán, trung thành và kín đáo."),
        MonthDay.of(11, 22) to ZodiacSign("Nhân Mã", "♐", "Lửa", "22/11 – 21/12", "Lạc quan, thích tự do, ham khám phá."),
        MonthDay.of(12, 22) to ZodiacSign("Ma Kết", "♑", "Đất", "22/12 – 19/1", "Kỷ luật, có trách nhiệm, bền bỉ với mục tiêu."),
    )

    fun zodiac(date: LocalDate): ZodiacSign {
        val md = MonthDay.from(date)
        return SIGNS.lastOrNull { !md.isBefore(it.first) }?.second ?: SIGNS.last().second
    }

    private val ANIMALS = listOf("Chuột", "Trâu", "Hổ", "Mèo", "Rồng", "Rắn", "Ngựa", "Dê", "Khỉ", "Gà", "Chó", "Lợn")

    /** Nạp âm của năm âm lịch [year], ví dụ 1990 → "Lộ Bàng Thổ". */
    fun napAmYear(year: Int): String = Almanac.NAP_AM[Math.floorMod(year - 4, 60) / 2]

    // ---------- Xông đất ----------
    private val HANH = listOf("Kim", "Thủy", "Mộc", "Hỏa", "Thổ")   // theo vòng tương sinh: Kim → Thủy → Mộc → Hỏa → Thổ → Kim

    private fun hanh(year: Int) = napAmYear(year).substringAfterLast(' ')
    private fun can(year: Int) = Math.floorMod(year - 4, 10)

    /** 1 tương sinh (hai chiều), 0.5 cùng hành, 0 tương khắc. */
    private fun napAmScore(a: String, b: String): Double {
        val d = Math.floorMod(HANH.indexOf(b) - HANH.indexOf(a), 5)
        return when (d) { 0 -> .5; 1, 4 -> 1.0; else -> 0.0 }
    }

    // Can xung theo thientue (4 cặp): Giáp–Canh, Ất–Tân, Bính–Nhâm, Đinh–Quý
    private val CAN_XUNG = setOf(0 to 6, 1 to 7, 2 to 8, 3 to 9)

    /** 1 can hợp (Giáp–Kỷ, Ất–Canh, Bính–Tân, Đinh–Nhâm, Mậu–Quý), 0 can xung, 0.5 còn lại. */
    private fun canScore(a: Int, b: Int): Double = when {
        Math.abs(a - b) == 5 -> 1.0
        (minOf(a, b) to maxOf(a, b)) in CAN_XUNG -> 0.0
        else -> .5
    }

    private val LUC_HOP = setOf(setOf(0, 1), setOf(2, 11), setOf(3, 10), setOf(4, 9), setOf(5, 8), setOf(6, 7))
    private val TAM_HOP = listOf(setOf(8, 0, 4), setOf(11, 3, 7), setOf(2, 6, 10), setOf(5, 9, 1))
    private val LUC_HAI = setOf(setOf(0, 7), setOf(1, 6), setOf(2, 5), setOf(3, 4), setOf(8, 11), setOf(9, 10))
    private val TU_HANH_XUNG = listOf(setOf(0, 6, 3, 9), setOf(2, 8, 5, 11), setOf(4, 10, 1, 7))

    /** 1 tam hợp / lục hợp (hợp được ưu tiên), 0 lục xung / lục hại / cùng nhóm tứ hành xung, 0.5 còn lại (kể cả cùng chi). */
    private fun chiScore(a: Int, b: Int): Double = when {
        a != b && (setOf(a, b) in LUC_HOP || TAM_HOP.any { a in it && b in it }) -> 1.0
        a != b && ((a - b + 12) % 12 == 6 || setOf(a, b) in LUC_HAI || TU_HANH_XUNG.any { a in it && b in it }) -> 0.0
        else -> .5
    }

    private fun chiRel(a: Int, b: Int): String? = when {
        a == b -> null
        setOf(a, b) in LUC_HOP -> "lục hợp"
        TAM_HOP.any { a in it && b in it } -> "tam hợp"
        (a - b + 12) % 12 == 6 -> "xung"
        else -> null
    }

    /**
     * Xem tuổi xông đất Tết năm âm [year] cho gia chủ sinh năm âm [owner], theo cách chấm của thientue.vn
     * (dựng lại khớp 224/224 điểm trang này công bố): thiên can với gia chủ 20, địa chi với gia chủ 22,
     * thiên can với năm 14, địa chi với năm 16, nạp âm với gia chủ 14, nạp âm với năm 14; mỗi yếu tố
     * tốt = đủ điểm, chấp nhận được = nửa, không tốt = 0. Người xông 18–65 tuổi.
     * Thêm lưu ý Tam Tai, Thái Tuế của người xông trong năm (các trang khác hay xét).
     */
    fun xongDat(owner: Int, year: Int, top: Int = 8): XongDat {
        val oCan = can(owner); val oChi = Almanac.birthChi(owner); val oHanh = hanh(owner)
        val yCan = can(year); val yChi = Almanac.birthChi(year); val yHanh = hanh(year)
        val picks = (year - 65..year - 18).map { c ->
            val cCan = can(c); val cChi = Almanac.birthChi(c); val cHanh = hanh(c)
            val score = score(cCan, cChi, cHanh, oCan, oChi, oHanh, yCan, yChi, yHanh)
            val reasons = mutableListOf<String>()
            chiRel(cChi, oChi)?.let { if (it != "xung") reasons += "${it.replaceFirstChar { c0 -> c0.uppercase() }} với gia chủ" }
            chiRel(cChi, yChi)?.let { if (it != "xung") reasons += "${it.replaceFirstChar { c0 -> c0.uppercase() }} với năm" }
            if (canScore(cCan, oCan) == 1.0) reasons += "Can hợp gia chủ"
            if (canScore(cCan, yCan) == 1.0) reasons += "Can hợp năm"
            if (napAmScore(cHanh, oHanh) == 1.0) reasons += "Mệnh $cHanh hợp gia chủ ($oHanh)"
            if (napAmScore(cHanh, yHanh) == 1.0) reasons += "Mệnh $cHanh hợp năm ($yHanh)"
            val check = Almanac.checkYear(c, year)
            val warnings = listOfNotNull(
                check.tamTai?.let { "phạm $it" },
                check.thaiTue?.let { if (it.startsWith("Năm tuổi")) "năm tuổi" else "xung Thái Tuế" },
            )
            val s = Math.round(score).toInt()
            XongDatPick(c, LunarCalendar.canChiYear(c), s, label(s), reasons, warnings)
        }.sortedWith(compareByDescending<XongDatPick> { it.score }.thenBy { it.warnings.size }.thenByDescending { it.year })
        // Tuổi nên tránh: chi xung với gia chủ hoặc với năm
        val avoid = listOf((oChi + 6) % 12, (yChi + 6) % 12).distinct().map { LunarCalendar.CHI[it] }
        // Chỉ gợi ý tuổi từ mức "có thể cân nhắc" (62) trở lên; không có thì đưa 3 tuổi cao điểm nhất
        val shown = picks.filter { it.score >= 62 }.take(top).ifEmpty { picks.take(3) }
        return XongDat(year, LunarCalendar.canChiYear(year), avoid, shown)
    }

    /** Nhãn mức theo thientue: 88+ rất nên mời, 75–87 hợp để ưu tiên, 62–74 có thể cân nhắc, dưới 62 nên tránh ưu tiên. */
    fun label(score: Int): String = when {
        score >= 88 -> "Rất nên mời"
        score >= 75 -> "Hợp để ưu tiên"
        score >= 62 -> "Có thể cân nhắc"
        else -> "Nên tránh ưu tiên"
    }

    /** Điểm xông đất của một người sinh năm âm [candidate] (dùng cho kiểm thử). */
    fun xongDatScore(owner: Int, year: Int, candidate: Int): Int {
        if (candidate !in year - 65..year - 18) return -1
        val oCan = can(owner); val oChi = Almanac.birthChi(owner); val oHanh = hanh(owner)
        val yCan = can(year); val yChi = Almanac.birthChi(year); val yHanh = hanh(year)
        val cCan = can(candidate); val cChi = Almanac.birthChi(candidate); val cHanh = hanh(candidate)
        return Math.round(score(cCan, cChi, cHanh, oCan, oChi, oHanh, yCan, yChi, yHanh)).toInt()
    }

    private fun score(cCan: Int, cChi: Int, cHanh: String, oCan: Int, oChi: Int, oHanh: String, yCan: Int, yChi: Int, yHanh: String) =
        20 * canScore(cCan, oCan) + 22 * chiScore(cChi, oChi) + 14 * canScore(cCan, yCan) +
            16 * chiScore(cChi, yChi) + 14 * napAmScore(cHanh, oHanh) + 14 * napAmScore(cHanh, yHanh)

    /** Tử vi cơ bản cho người sinh ngày dương [birth]; con giáp và mệnh theo năm âm của ngày sinh. */
    fun basic(birth: LocalDate): BasicHoroscope {
        val ly = LunarCalendar.convert(birth).year
        val menh = napAmYear(ly)
        return BasicHoroscope(zodiac(birth), LunarCalendar.canChiYear(ly), ANIMALS[Almanac.birthChi(ly)], menh, menh.substringAfterLast(' '))
    }
}
