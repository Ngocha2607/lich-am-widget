package vn.lichthoitiet

/** Một sao theo Ngọc Hạp thông thư. */
data class NgocHapStar(val name: String, val good: Boolean, val desc: String)

/**
 * Sao tốt, sao xấu theo Ngọc Hạp thông thư (91 sao).
 *
 * Quy tắc từng sao được suy ra từ danh sách sao hằng ngày của lichngaytot.com và xemlicham.com
 * cả năm 2026 (khớp 100% ở cả hai trang), rồi kiểm lại trên 7–9/2025 (có tháng 6 nhuận) cũng khớp 100%.
 * Các sao hai trang ghi khác nhau (Nguyệt Phá, Thiên Đức Hợp, Thiên Xá...) không đưa vào.
 * Tháng nhuận tính như tháng thường cùng số. Mười hai sao trực thần (Thanh Long, Bạch Hổ...)
 * đã có ở phần hoàng đạo/hắc đạo nên không lặp lại.
 *
 * Mã hóa quy tắc: [mode] 'M' theo tháng âm (12 ô), 'S' theo mùa âm lịch (4 ô: tháng 1–3, 4–6, 7–9, 10–12),
 * 'G' mọi tháng (1 ô). Mỗi ô: "" không có; "z.." các chi ngày; "c.." các can ngày; "k.." các can chi ngày (0..59).
 * File này sinh tự động bằng script, sửa tay thì nhớ chạy lại test.
 */
object NgocHap {
    private class Rule(name: String, good: Boolean, desc: String, val mode: Char, cells: String) {
        val star = NgocHapStar(name, good, desc)
        val cells: List<Pair<Char, Set<Int>>?> = cells.split('|').map { c ->
            if (c.isEmpty()) null else c[0] to c.substring(1).split(',').map { it.toInt() }.toSet()
        }
    }

    private val RULES = listOf(
        Rule("Cát Khánh", true, "Tốt mọi việc", 'M', "z9|z2|z11|z4|z1|z6|z3|z8|z5|z10|z7|z0"),
        Rule("Dân Nhật", true, "Tốt mọi việc", 'M', "z6|z6|z6|z9|z9|z9|z0|z0|z0|z3|z3|z3"),
        Rule("Dịch Mã", true, "Tốt mọi việc, nhất là xuất hành", 'M', "z8|z5|z2|z11|z8|z5|z2|z11|z8|z5|z2|z11"),
        Rule("Giải Thần", true, "Đại cát: Tốt cho việc tế tự, tố tụng, giải oan (trừ được các sao xấu)", 'M', "z8|z8|z10|z10|z0|z0|z2|z2|z4|z4|z6|z6"),
        Rule("Hoàng Ân", true, "Tốt mọi việc", 'M', "z10|z1|z2|z5|z9|z3|z0|z6|z11|z4|z8|z7"),
        Rule("Hoạt Điệu", true, "Tốt, nhưng gặp Thụ tử thì xấu", 'M', "z5|z10|z7|z0|z9|z2|z11|z4|z1|z6|z3|z8"),
        Rule("Kính Tâm", true, "Tốt đối với an táng", 'M', "z7|z1|z8|z2|z9|z3|z10|z4|z11|z5|z0|z6"),
        Rule("Lộc Khố", true, "Tốt cho việc cầu tài lộc, khai trương, giao dịch", 'M', "z4|z5|z6|z7|z8|z9|z10|z11|z0|z1|z2|z3"),
        Rule("Lục Hợp", true, "Tốt mọi việc", 'M', "z11|z10|z9|z8|z7|z6|z5|z4|z3|z2|z1|z0"),
        Rule("Minh Tinh", true, "Tốt mọi việc", 'M', "z8|z10|z0|z2|z4|z6|z8|z10|z0|z2|z4|z6"),
        Rule("Mãn Đức Tinh", true, "Tốt mọi việc", 'M', "z2|z7|z4|z9|z6|z11|z8|z1|z10|z3|z0|z5"),
        Rule("Mẫu Thương", true, "Tốt về cầu tài lộc, khai trương, mở kho", 'M', "z0,11|z0,11|z0,11|z2,3|z2,3|z2,3|z1,4|z1,4|z1,4|z8,9|z8,9|z8,9"),
        Rule("Nguyệt Giải", true, "Tốt mọi việc", 'M', "z8|z8|z9|z9|z10|z10|z11|z11|z6|z6|z7|z7"),
        Rule("Nguyệt Không", true, "Tốt cho việc sửa chữa nhà cửa, đặt giường", 'M', "c8|c6|c2|c0|c8|c6|c2|c0|c8|c6|c2|c0"),
        Rule("Nguyệt Tài", true, "Tốt cho việc cầu tài lộc, khai trương, mở kho, nhập kho, xuất hành, di chuyển, giao dịch, mua bán, ký kết", 'M', "z6|z5|z5|z7|z9|z11|z6|z5|z5|z7|z9|z11"),
        Rule("Nguyệt Ân", true, "Tốt mọi việc", 'M', "c2|c3|c6|c5|c4|c7|c8|c9|c6|c1|c0|c7"),
        Rule("Nguyệt Đức", true, "Tốt mọi việc", 'M', "c2|c0|c8|c6|c2|c0|c8|c6|c2|c0|c8|c6"),
        Rule("Nguyệt Đức Hợp", true, "Tốt mọi việc, kỵ tố tụng", 'M', "c7|c5|c3|c1|c7|c5|c3|c1|c7|c5|c3|c1"),
        Rule("Ngũ Phú", true, "Tốt mọi việc", 'M', "z11|z2|z5|z8|z11|z2|z5|z8|z11|z2|z5|z8"),
        Rule("Phúc Hậu", true, "Tốt về cầu tài lộc, khai trương, mở kho", 'M', "z2|z2|z2|z5|z5|z5|z8|z8|z8|z11|z11|z11"),
        Rule("Phúc Sinh", true, "Tốt mọi việc", 'M', "z9|z3|z10|z4|z11|z5|z0|z6|z1|z7|z2|z8"),
        Rule("Phổ Hộ", true, "Tốt mọi việc, cưới hỏi, xuất hành", 'M', "z8|z2|z9|z3|z10|z4|z11|z5|z0|z6|z1|z7"),
        Rule("Quan Nhật", true, "Tốt mọi việc", 'M', "|z3|||z6|||z9|||z0|"),
        Rule("Sinh Khí", true, "Tốt mọi việc, nhất là xây dựng nhà cửa, tu tạo, động thổ ban nền, trồng cây", 'M', "z0|z1|z2|z3|z4|z5|z6|z7|z8|z9|z10|z11"),
        Rule("Tam Hợp", true, "Tốt mọi việc", 'M', "z6,10|z7,11|z0,8|z1,9|z2,10|z3,11|z0,4|z1,5|z2,6|z3,7|z4,8|z5,9"),
        Rule("Thiên Hỷ", true, "Tốt mọi việc, nhất là cưới hỏi", 'M', "z10|z11|z0|z1|z2|z3|z4|z5|z6|z7|z8|z9"),
        Rule("Thiên Mã", true, "Tốt cho việc xuất hành, giao dịch, mua bán, ký kết, cầu tài lộc", 'M', "z6|z8|z10|z0|z2|z4|z6|z8|z10|z0|z2|z4"),
        Rule("Thiên Phú", true, "Tốt mọi việc, nhất là xây dựng nhà cửa, khởi công, động thổ, khai trương, mở kho, nhập kho, an táng", 'M', "z4|z5|z6|z7|z8|z9|z10|z11|z0|z1|z2|z3"),
        Rule("Thiên Quan", true, "Tốt mọi việc", 'M', "z10|z0|z2|z4|z6|z8|z10|z0|z2|z4|z6|z8"),
        Rule("Thiên Quý", true, "Tốt mọi việc", 'M', "c0,1|c0,1|c0,1|c2,3|c2,3|c2,3|c6,7|c6,7|c6,7|c8,9|c8,9|c8,9"),
        Rule("Thiên Tài", true, "Tốt cho việc cầu tài lộc, khai trương", 'M', "z4|z6|z8|z10|z0|z2|z4|z6|z8|z10|z0|z2"),
        Rule("Thiên Đức", true, "Tốt mọi việc", 'M', "c3|z8|c8|c7|z11|c0|c9|z2|c2|c1|z5|c6"),
        Rule("Thánh Tâm", true, "Tốt mọi việc, nhất là cầu phúc, tế tự", 'M', "z11|z5|z0|z6|z1|z7|z2|z8|z3|z9|z4|z10"),
        Rule("Tuế Hợp", true, "Tốt mọi việc", 'M', "z1|z0|z11|z10|z9|z8|z7|z6|z5|z4|z3|z2"),
        Rule("Tục Thế", true, "Tốt mọi việc, nhất là cưới hỏi", 'M', "z1|z7|z2|z8|z3|z9|z4|z10|z5|z11|z6|z0"),
        Rule("U Vi Tinh", true, "Tốt mọi việc", 'M', "z11|z4|z1|z6|z3|z8|z5|z10|z7|z0|z9|z2"),
        Rule("Yếu Yên", true, "Tốt mọi việc, nhất là cưới hỏi", 'M', "z2|z8|z3|z9|z4|z10|z5|z11|z6|z0|z7|z1"),
        Rule("Âm Đức", true, "Tốt mọi việc", 'M', "z9|z7|z5|z3|z1|z11|z9|z7|z5|z3|z1|z11"),
        Rule("Ích Hậu", true, "Tốt mọi việc, nhất là cưới hỏi", 'M', "z0|z6|z1|z7|z2|z8|z3|z9|z4|z10|z5|z11"),
        Rule("Đại Hồng Sa", true, "Tốt mọi việc", 'M', "z0,1|z0,1|z0,1|z4,5|z4,5|z4,5|z6,7|z6,7|z6,7|z8,10|z8,10|z8,10"),
        Rule("Địa Tài", true, "Tốt cho việc cầu tài lộc, khai trương", 'M', "z5|z7|z9|z11|z1|z3|z5|z7|z9|z11|z1|z3"),
        Rule("Băng Tiêu Ngọa Hãm", false, "Xấu mọi việc", 'M', "z5|z0|z1|z2|z3|z10|z11|z6|z7|z8|z9|z4"),
        Rule("Cô Thần", false, "Xấu với cưới hỏi", 'M', "z10|z11|z0|z1|z2|z3|z4|z5|z6|z7|z8|z9"),
        Rule("Cẩu Giảo", false, "Xấu mọi việc, nhất là khởi công, động thổ, xây dựng nhà cửa", 'M', "z11|z6|z1|z8|z3|z10|z5|z0|z7|z2|z9|z4"),
        Rule("Cửu Không", false, "Kỵ xuất hành, cầu tài lộc, khai trương", 'M', "z4|z1|z10|z7|z3|z0|z9|z6|z2|z11|z8|z5"),
        Rule("Hoang Vu", false, "Xấu mọi việc", 'M', "z1,5,9|z1,5,9|z1,5,9|z0,4,8|z0,4,8|z0,4,8|z3,7,11|z3,7,11|z3,7,11|z2,6,10|z2,6,10|z2,6,10"),
        Rule("Hoàng Sa", false, "Xấu đối với xuất hành", 'M', "z6|z2|z0|z6|z2|z0|z6|z2|z0|z6|z2|z0"),
        Rule("Hà Khôi", false, "Xấu mọi việc, nhất là khởi công, động thổ, xây dựng nhà cửa", 'M', "z11|z6|z1|z8|z3|z10|z5|z0|z7|z2|z9|z4"),
        Rule("Hỏa Tai", false, "Xấu đối với xây dựng nhà cửa, đổ mái, sửa sang nhà cửa", 'M', "z1|z7|z2|z8|z3|z9|z4|z10|z5|z11|z6|z0"),
        Rule("Không Phòng", false, "Kỵ cưới hỏi", 'M', "z4|z5|z0|z10|z11|z7|z2|z3|z6|z8|z9|z1"),
        Rule("Kiếp Sát", false, "Kỵ xuất hành, cưới hỏi, an táng, xây dựng nhà cửa", 'M', "z11|z8|z5|z2|z11|z8|z5|z2|z11|z8|z5|z2"),
        Rule("Ly Sàng", false, "Kỵ cưới hỏi", 'M', "z9|z9|z9|z2,6|z2,6|z2,6|z10|z10|z10|z5|z5|z5"),
        Rule("Lôi Công", false, "Xấu với xây dựng nhà cửa", 'M', "z2|z11|z5|z8|z2|z11|z5|z8|z2|z11|z5|z8"),
        Rule("Lỗ Ban Sát", false, "Kỵ khởi công, động thổ", 'M', "z0|z0|z0|z3|z3|z3|z6|z6|z6|z9|z9|z9"),
        Rule("Lục Bất Thành", false, "Xấu đối với xây dựng", 'M', "z2|z6|z10|z5|z9|z1|z8|z0|z4|z11|z3|z7"),
        Rule("Nguyệt Hình", false, "Xấu mọi việc", 'M', "z5|z0|z4|z8|z6|z1|z2|z9|z7|z11|z3|z10"),
        Rule("Nguyệt Hư", false, "Xấu đối với việc giá thú, mở cửa, mở hàng", 'M', "z1|z10|z7|z4|z1|z10|z7|z4|z1|z10|z7|z4"),
        Rule("Nguyệt Hỏa", false, "Xấu đối với sửa sang nhà cửa, đổ mái, xây bếp", 'M', "z5|z4|z3|z2|z1|z0|z11|z10|z9|z8|z7|z6"),
        Rule("Nguyệt Kiến Chuyển Sát", false, "Kỵ khởi công, động thổ", 'M', "z3|z3|z3|z6|z6|z6|z9|z9|z9|z0|z0|z0"),
        Rule("Nguyệt Yếm Đại Họa", false, "Xấu đối với xuất hành, giá thú", 'M', "z10|z9|z8|z7|z6|z5|z4|z3|z2|z1|z0|z11"),
        Rule("Ngũ Hư", false, "Kỵ khởi tạo, cưới hỏi, an táng", 'M', "z5|z9|z1|z8|z0|z4|z11|z3|z7|z2|z6|z10"),
        Rule("Ngũ Quỷ", false, "Kỵ xuất hành", 'M', "z6|z2|z4|z9|z3|z8|z1|z5|z0|z11|z7|z10"),
        Rule("Nhân Cách", false, "Xấu đối với giá thú, khởi tạo", 'M', "z9|z7|z5|z3|z1|z11|z9|z7|z5|z3|z1|z11"),
        Rule("Phi Ma Sát", false, "Kỵ giá thú nhập trạch", 'M', "z0|z9|z6|z3|z0|z9|z6|z3|z0|z9|z6|z3"),
        Rule("Phủ Đầu Dát", false, "Kỵ khởi công, động thổ", 'M', "z4|z4|z4|z7|z7|z7|z9|z9|z9|z0|z0|z0"),
        Rule("Quả Tú", false, "Xấu với cưới hỏi", 'M', "z4|z5|z6|z7|z8|z9|z10|z11|z0|z1|z2|z3"),
        Rule("Quỷ Khốc", false, "Xấu với tế tự, an táng", 'G', "k10,22,34,46,58"),
        Rule("Tam Tang", false, "Kỵ khởi tạo, cưới hỏi, an táng", 'M', "z4|z4|z4|z7|z7|z7|z10|z10|z10|z1|z1|z1"),
        Rule("Thiên Cương", false, "Xấu mọi việc", 'M', "z5|z0|z7|z2|z9|z4|z11|z6|z1|z8|z3|z10"),
        Rule("Thiên Hỏa", false, "Xấu về lợp nhà", 'M', "z0|z3|z6|z9|z0|z3|z6|z9|z0|z3|z6|z9"),
        Rule("Thiên Lại", false, "Xấu mọi việc", 'M', "z9|z6|z3|z0|z9|z6|z3|z0|z9|z6|z3|z0"),
        Rule("Thiên Ngục", false, "Xấu mọi việc", 'M', "z0|z3|z6|z9|z0|z3|z6|z9|z0|z3|z6|z9"),
        Rule("Thiên Tặc", false, "Xấu đối với khởi tạo, động thổ, về nhà mới, khai trương", 'M', "z4|z9|z2|z7|z0|z5|z10|z3|z8|z1|z6|z11"),
        Rule("Thiên Địa Chuyển Sát", false, "Kỵ khởi công, động thổ", 'S', "k51|k42|k57|k48"),
        Rule("Thiên Địa Chính Chuyển", false, "Kỵ khởi công, động thổ", 'S', "k39|k42|k33|k36"),
        Rule("Thần Cách", false, "Kỵ tế tự", 'M', "z5|z3|z1|z11|z9|z7|z5|z3|z1|z11|z9|z7"),
        Rule("Thổ Cẩm", false, "Kỵ xây dựng nhà cửa, an táng", 'M', "z11|z11|z11|z2|z2|z2|z5|z5|z5|z8|z8|z8"),
        Rule("Thổ Phủ", false, "Kỵ xây dựng nhà cửa, khởi công, động thổ", 'M', "z2|z3|z4|z5|z6|z7|z8|z9|z10|z11|z0|z1"),
        Rule("Thổ Ôn", false, "Kỵ xây dựng nhà cửa, đào ao, đào giếng, tế tự", 'M', "z4|z5|z6|z7|z8|z9|z10|z11|z0|z1|z2|z3"),
        Rule("Thụ Tử", false, "Xấu mọi việc (trừ săn bắn tốt)", 'M', "z10|z4|z11|z5|z0|z6|z1|z7|z2|z8|z3|z9"),
        Rule("Tiểu Hao", false, "Xấu về giao dịch, mua bán, cầu tài lộc", 'M', "z5|z6|z7|z8|z9|z10|z11|z0|z1|z2|z3|z4"),
        Rule("Tiểu Hồng Sa", false, "Xấu mọi việc", 'M', "z5|z9|z1|z5|z9|z1|z5|z9|z1|z5|z9|z1"),
        Rule("Trùng Phục", false, "Kỵ giá thú, an táng", 'M', "c6|c7|c5|c8|c9|c4|c0|c1|c5|c8|c9|c5"),
        Rule("Trùng Tang", false, "Kỵ cưới hỏi, an táng, khởi công, động thổ, xây dựng nhà cửa", 'M', "c0|c1|c5|c2|c3|c5|c6|c7|c5|c8|c9|c5"),
        Rule("Tội Chỉ", false, "Xấu với tế tự, tố tụng", 'M', "z6|z0|z7|z1|z8|z2|z9|z3|z10|z4|z11|z5"),
        Rule("Tứ Thời Cô Quả", false, "Kỵ cưới hỏi", 'M', "z1|z1|z1|z4|z4|z4|z7|z7|z7|z10|z10|z10"),
        Rule("Tứ Thời Đại Mộ", false, "Kỵ an táng", 'S', "k31|k22|k37|k28"),
        Rule("Vãng Vong", false, "Kỵ xuất hành, cưới hỏi, cầu tài lộc, khởi công, động thổ", 'M', "z2|z5|z8|z11|z3|z6|z9|z0|z4|z7|z10|z1"),
        Rule("Đại Hao", false, "Xấu mọi việc", 'M', "z6|z7|z8|z9|z10|z11|z0|z1|z2|z3|z4|z5"),
        Rule("Địa Phá", false, "Kỵ xây dựng nhà cửa, khởi công, động thổ", 'M', "z11|z0|z1|z2|z3|z4|z5|z6|z7|z8|z9|z10"),
        Rule("Địa Tặc", false, "Xấu đối với khởi tạo, an táng, khởi công động thổ, xuất hành", 'M', "z1|z0|z11|z10|z9|z8|z7|z6|z5|z4|z3|z2"),
    )

    /** Các sao của ngày âm [l]: sao tốt trước, sao xấu sau. */
    fun of(l: LunarDate): List<NgocHapStar> {
        val can = (l.jd + 9) % 10
        val chi = LunarCalendar.dayChiIndex(l.jd)
        val canChi = Math.floorMod(6 * can - 5 * chi, 60)
        return RULES.filter { r ->
            val cell = when (r.mode) {
                'M' -> r.cells[l.month - 1]
                'S' -> r.cells[(l.month - 1) / 3]
                else -> r.cells[0]
            } ?: return@filter false
            when (cell.first) {
                'z' -> chi in cell.second
                'c' -> can in cell.second
                'k' -> canChi in cell.second
                else -> l.day in cell.second
            }
        }.map { it.star }
    }
}
