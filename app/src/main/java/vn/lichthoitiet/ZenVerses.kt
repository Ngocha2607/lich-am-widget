package vn.lichthoitiet

import java.time.LocalDate

/**
 * Một câu thơ cho chế độ Zen; [by] là tác giả và tác phẩm, rỗng với thơ ba dòng của app.
 * [tag]: rỗng = hợp mọi ngày; mùa theo tiết khí ("xuan" từ Lập xuân, "he" từ Lập hạ, "thu" từ Lập thu, "dong" từ Lập đông;
 * "xuanmuon" Xuân phân → hết Cốc vũ, "thumuon" Hàn lộ → hết Sương giáng, "daudong" Lập đông → hết Tiểu tuyết);
 * theo tháng âm ("thang7", "thang12", "cuoinam" = tháng 11–12 âm,
 * "trangnon" = mùng 2–6); theo ngày ("tet1" mùng 1, "tet2" mùng 2, "tet" mùng 3–4 Tết, "giaothua", "ram1", "ram8").
 */
data class Verse(val text: String, val by: String, val tag: String = "")

/**
 * Thơ cho chế độ Zen ("Lịch Mù"): thơ ba dòng kiểu haiku viết riêng cho app và trích đoạn thơ cổ
 * Việt Nam đã hết thời hạn bảo hộ (tác giả mất trước 1975), đối chiếu nguyên văn ở ít nhất hai nguồn.
 */
object ZenVerses {
    private fun h(text: String, tag: String = "") = Verse(text, "", tag)

    /** Thơ ba dòng viết riêng cho app. */
    val HAIKU = listOf(
        h("Mưa phùn đầu ngõ\nai phơi chiếc nón lá\nướt một vành trăng", "xuan"),
        h("Chuông chùa buổi sớm\nsương còn đọng trên lá\nchưa ai đánh thức"),
        h("Tách trà nguội dần\nngoài hiên một chiếc lá\nchậm rãi lìa cành", "thu"),
        h("Gió heo may về\nhàng cây trút lá vàng\nphố bỗng rộng ra", "heomay"),
        h("Đêm rằm tháng Tám\ntrẻ con rước đèn qua\nánh trăng theo sau", "ram8"),
        h("Đồng lúa chín vàng\nmột cánh cò chao nghiêng\ntrời bỗng thấp xuống"),
        h("Khói bếp chiều quê\nquyện vào sương lam mỏng\nmẹ gọi ăn cơm"),
        h("Hoa sữa nồng nàn\nđêm cuối thu Hà Nội\nai đi chậm lại", "thumuon"),
        h("Hồ sen buổi sớm\ngiọt sương lăn trên lá\nchẳng để lại vết", "he"),
        h("Tiếng ve đầu hè\nsân trường vắng bóng người\nphượng đỏ một mình", "dauhe"),
        h("Nắng xiên qua mái\nhạt bụi bay chậm rãi\ncăn phòng thở đều"),
        h("Bến sông mùa nước\ncon đò chờ người sang\nmây cũng chờ theo"),
        h("Mùng Một đầu năm\nnhành đào bên cửa sổ\nnở thêm một bông", "tet1"),
        h("Trăng non đầu tháng\nmảnh như nét chì mờ\ntrên tờ giấy dó", "trangnon"),
        h("Mưa rào mùa hạ\nmái tôn gõ từng hồi\nrồi im như cũ", "he"),
        h("Ruộng bậc thang chiều\nnước soi từng bậc trời\nmây bước xuống chơi"),
        h("Đêm đông gió bấc\nbếp than hồng lách tách\nbà kể chuyện xưa", "dong"),
        h("Phiên chợ sớm mai\nrau còn ướt sương đêm\nngười bán khẽ cười"),
        h("Một chiếc lá rơi\nmặt hồ gợn vòng tròn\nrồi lại phẳng lặng"),
        h("Mặc áo len cũ\nmùi long não năm xưa\nmùa đông trở về", "daudong"),
        h("Mưa ngâu tháng Bảy\nhiên nhà ướt hai bên\nmột chiếc ô nghiêng", "thang7"),
        h("Sóng vỗ bờ cát\nxóa dấu chân hôm qua\nbiển chẳng nhớ ai"),
        h("Ngọn nến đêm giỗ\nlay lắt giữa gian thờ\nkhói hương nối người"),
        h("Đèn đường vừa bật\nphố còn chút nắng hồng\nngày chưa muốn tắt"),
        h("Đầu non mây trắng\nchú tiểu quét lá đa\nquét cả tiếng chim"),
        h("Cánh diều chiều gió\nbuộc trời vào tay em\ndây chùng lại căng", "he"),
        h("Bát cơm gạo mới\nhương lúa còn trên tay\nnhớ mùa gặt cũ"),
        h("Sương giăng mặt hồ\ncây cầu son mất dấu\nchỉ còn tiếng chuông", "dong"),
        h("Mưa tạnh hiên nhà\ngiọt cuối cùng còn treo\nchưa nỡ rơi xuống"),
        h("Chiều ba mươi Tết\nnồi bánh chưng sôi đều\nlửa thức cùng người", "bamuoi"),
        h("Sáng mùng Hai Tết\nphố vắng như tờ giấy\nchưa ai viết lên", "tet2"),
        h("Đêm khuya trang sách\ncon mèo cuộn bên chân\nngọn đèn không ngủ"),
        h("Cây gạo tháng Ba\nhoa đỏ rụng đầu làng\nchim sáo kéo về", "xuanmuon"),
        h("Nước triều lên chậm\nrễ đước níu bùn non\nbiển gần thêm chút"),
        h("Chiếc thuyền giấy nhỏ\ntrôi qua rãnh nước mưa\nmang theo tuổi thơ", "he"),
        h("Gốc đa đầu đình\nbóng mát trăm năm rồi\nvẫn ngồi đợi ai"),
        h("Mây vắt ngang đèo\nxe chậm qua khúc quanh\nnúi cũng ngoảnh nhìn"),
        h("Quả hồng chín đỏ\ntrên cành trơ trụi lá\nmột ngọn đèn treo", "thu"),
        h("Ấm nước reo sôi\nngoài trời mưa lâm thâm\nchẳng cần đi đâu"),
        h("Nắng hanh tháng Chạp\nchăn bông phơi trên dây\ngiữ mùi nắng cũ", "thang12"),
        h("Hạt mưa đầu mùa\nđất khô thơm ngai ngái\ncỏ khẽ ngẩng đầu", "dauhe"),
        h("Đom đóm bờ ao\nmang sao xuống thật thấp\ncho trẻ con đếm", "he"),
        h("Bậc thềm rêu xanh\nbao nhiêu năm bước chân\nmòn thành im lặng"),
        h("Rằm tháng Giêng sáng\nđường lên chùa đông người\nhương trầm bay xa", "ram1"),
        h("Hơi thở mùa đông\nphả thành mây trắng nhỏ\ntan trước lời chào", "dong"),
        h("Lá bàng đỏ thắm\nrơi xuống vỉa hè khuya\nnhư lá thư muộn", "dong"),
        h("Giọt cà phê rơi\nchậm hơn cả buổi sáng\nphố chưa vội gì"),
        h("Bãi bồi ven sông\nngô non vừa trổ cờ\ngió đếm từng hàng"),
        h("Tiếng sáo diều xa\nthả trôi trên đồng trưa\nngười thôi vội vã", "he"),
        h("Đêm không trăng sao\nchỉ có tiếng ếch nhái\ntrời đất gần hơn", "hetoi"),
        h("Hoa ban nở trắng\ntrên triền núi Tây Bắc\nnúi khoác áo xuân", "xuan"),
        h("Dòng chữ nghiêng nghiêng\ntrên phong bì đã cũ\nmực vẫn chưa phai"),
        h("Một đóa sen tàn\ngương sen còn ở lại\ngiữ hạt cho mai", "thu"),
        h("Cầu ao buổi trưa\nbóng tre rung trên nước\ncá đớp mây trôi"),
        h("Thu về rất khẽ\nmột sớm mở cửa ra\nthấy trời cao hơn", "dauthu"),
        h("Gió Lào miền Trung\ncát bay qua ô cửa\nmẹ khép rồi mở", "he"),
        h("Bông lau trắng đồi\nlay theo gió cuối năm\nnhư tóc ai bạc", "cuoinam"),
        h("Cơn mưa vừa qua\nvũng nước giữ bầu trời\nem bước nhẹ thôi"),
        h("Đêm giao thừa lặng\nhương trầm lan khắp nhà\nnăm cũ ngồi lại", "giaothua"),
        h("Đường về nhà quen\nhàng cau thêm một tuổi\nmình thêm một mùa"),
        h("Con mèo ngáp dài\ntrong nắng chiều đầu đông\nthời gian chậm lại", "daudong"),
        h("Không cần nói gì\nhai tách trà bên nhau\nhơi ấm đủ đầy"),
        h("Câu đối đỏ tươi\nkhách vào nhà chúc Tết\ntrà ấm tay người", "tet"),
        h("Pháo hoa đã tắt\nmâm ngũ quả còn thơm\nnhà vẫn đông người", "tet"),
    )

    /** Trích đoạn thơ cổ đã đối chiếu nguồn. */
    val CLASSICS: List<Verse> = listOf(
        Verse("Ao thu lạnh lẽo nước trong veo,\nMột chiếc thuyền câu bé tẻo teo.", "Nguyễn Khuyến · Thu điếu", "thu"),
        Verse("Sóng biếc theo làn hơi gợn tí,\nLá vàng trước gió khẽ đưa vèo.", "Nguyễn Khuyến · Thu điếu", "thu"),
        Verse("Trời thu xanh ngắt mấy tầng cao,\nCần trúc lơ phơ gió hắt hiu.", "Nguyễn Khuyến · Thu vịnh", "thu"),
        Verse("Nước biếc trông như tầng khói phủ,\nSong thưa để mặc bóng trăng vào.", "Nguyễn Khuyến · Thu vịnh", "thu"),
        Verse("Mấy chùm trước giậu hoa năm ngoái,\nMột tiếng trên không ngỗng nước nào?", "Nguyễn Khuyến · Thu vịnh", "thu"),
        Verse("Năm gian nhà cỏ thấp le te,\nNgõ tối đêm sâu đóm lập lòe.", "Nguyễn Khuyến · Thu ẩm", "thu"),
        Verse("Lưng giậu phất phơ màu khói nhạt,\nLàn ao lóng lánh bóng trăng loe.", "Nguyễn Khuyến · Thu ẩm", "thu"),
        Verse("Bước tới Đèo Ngang, bóng xế tà,\nCỏ cây chen đá, lá chen hoa.", "Bà Huyện Thanh Quan · Qua Đèo Ngang"),
        Verse("Dừng chân đứng lại: trời, non, nước,\nMột mảnh tình riêng, ta với ta.", "Bà Huyện Thanh Quan · Qua Đèo Ngang"),
        Verse("Gác mái, ngư ông về viễn phố,\nGõ sừng, mục tử lại cô thôn.", "Bà Huyện Thanh Quan · Chiều hôm nhớ nhà"),
        Verse("Lối xưa xe ngựa hồn thu thảo,\nNền cũ lâu đài bóng tịch dương.", "Bà Huyện Thanh Quan · Thăng Long thành hoài cổ"),
        Verse("Một mai, một cuốc, một cần câu,\nThơ thẩn dầu ai vui thú nào.", "Nguyễn Bỉnh Khiêm · Nhàn"),
        Verse("Thu ăn măng trúc, đông ăn giá,\nXuân tắm hồ sen, hạ tắm ao.", "Nguyễn Bỉnh Khiêm · Nhàn"),
        Verse("Rồi hóng mát thuở ngày trường,\nHòe lục đùn đùn tán rợp trương.", "Nguyễn Trãi · Cảnh ngày hè (Bảo kính cảnh giới, bài 43)", "he"),
        Verse("Lao xao chợ cá làng ngư phủ,\nDắng dỏi cầm ve lầu tịch dương.", "Nguyễn Trãi · Cảnh ngày hè (Bảo kính cảnh giới, bài 43)", "he"),
        Verse("Chén rượu hương đưa say lại tỉnh,\nVầng trăng bóng xế khuyết chưa tròn.", "Hồ Xuân Hương · Tự tình (bài II)"),
        Verse("Sông kia rày đã nên đồng,\nChỗ làm nhà cửa, chỗ trồng ngô khoai.", "Trần Tế Xương · Sông Lấp"),
        Verse("Vẳng nghe tiếng ếch bên tai,\nGiật mình còn tưởng tiếng ai gọi đò.", "Trần Tế Xương · Sông Lấp"),
        Verse("Nước non nặng một nhời thề,\nNước đi đi mãi không về cùng non.", "Tản Đà · Thề non nước"),
        Verse("Cung quế đã ai ngồi đó chửa?\nCành đa xin chị nhắc lên chơi.", "Tản Đà · Muốn làm thằng Cuội"),
        Verse("Sao anh không về chơi thôn Vĩ?\nNhìn nắng hàng cau nắng mới lên.", "Hàn Mặc Tử · Đây thôn Vĩ Dạ"),
        Verse("Gió theo lối gió, mây đường mây,\nDòng nước buồn thiu, hoa bắp lay.", "Hàn Mặc Tử · Đây thôn Vĩ Dạ"),
        Verse("Thuyền ai đậu bến sông trăng đó,\nCó chở trăng về kịp tối nay?", "Hàn Mặc Tử · Đây thôn Vĩ Dạ"),
        Verse("Vườn ai mướt quá, xanh như ngọc\nLá trúc che ngang mặt chữ điền.", "Hàn Mặc Tử · Đây thôn Vĩ Dạ"),
        Verse("Trong làn nắng ửng: khói mơ tan,\nĐôi mái nhà tranh lấm tấm vàng.", "Hàn Mặc Tử · Mùa xuân chín", "xuan"),
        Verse("Sột soạt gió trêu tà áo biếc,\nTrên giàn thiên lý. Bóng xuân sang.", "Hàn Mặc Tử · Mùa xuân chín", "xuan"),
        Verse("Bữa ấy mưa xuân phơi phới bay,\nHoa xoan lớp lớp rụng vơi đầy.", "Nguyễn Bính · Mưa xuân", "xuan"),
        Verse("Hoa chanh nở giữa vườn chanh,\nThầy u mình với chúng mình chân quê.", "Nguyễn Bính · Chân quê"),
        Verse("Chiều ấm mùi hương thoảng gió đưa\nTà tà mưa bụi rắc thưa thưa", "Nguyễn Bính · Mưa xuân (II)", "xuan"),
        Verse("Hoa giãi nguyệt, nguyệt in một tấm,\nNguyệt lồng hoa, hoa thắm từng bông", "Đặng Trần Côn (Đoàn Thị Điểm dịch) · Chinh phụ ngâm"),
        Verse("Gà eo óc gáy sương năm trống,\nHòe phất phơ rủ bóng bốn bên.", "Đặng Trần Côn (Đoàn Thị Điểm dịch) · Chinh phụ ngâm"),
        Verse("Cỏ non xanh tận chân trời,\nCành lê trắng điểm một vài bông hoa.", "Nguyễn Du · Truyện Kiều", "xuan"),
        Verse("Tà tà bóng ngả về tây,\nChị em thơ thẩn dan tay ra về.", "Nguyễn Du · Truyện Kiều"),
        Verse("Nao nao dòng nước uốn quanh,\nDịp cầu nho nhỏ cuối ghềnh bắc ngang.", "Nguyễn Du · Truyện Kiều"),
        Verse("Dưới trăng quyên đã gọi hè,\nĐầu tường lửa lựu lập lòe đâm bông.", "Nguyễn Du · Truyện Kiều", "he"),
        Verse("Vầng trăng ai xẻ làm đôi,\nNửa in gối chiếc, nửa soi dặm trường.", "Nguyễn Du · Truyện Kiều"),
        Verse("Đêm thu gió lọt song đào,\nNửa vành trăng khuyết, ba sao giữa trời.", "Nguyễn Du · Truyện Kiều", "thu"),
        Verse("Sen tàn cúc lại nở hoa,\nSầu dài ngày ngắn đông đà sang xuân.", "Nguyễn Du · Truyện Kiều", "thu"),
        Verse("Buồn trông cửa bể chiều hôm,\nThuyền ai thấp thoáng cánh buồm xa xa?", "Nguyễn Du · Truyện Kiều"),
        Verse("Mục đồng sáo vẳng trâu về hết,\nCò trắng từng đôi liệng xuống đồng.", "Trần Nhân Tông (Ngô Tất Tố dịch) · Thiên Trường vãn vọng"),
        Verse("Đừng bảo xuân tàn hoa rụng hết,\nĐêm qua sân trước một nhành mai.", "Mãn Giác thiền sư (Ngô Tất Tố dịch) · Cáo tật thị chúng", "xuanmuon"),
        Verse("Chúa xuân nay đã thành quen mặt,\nNệm cỏ ngồi yên, ngó rụng hồng.", "Trần Nhân Tông (Ngô Tất Tố dịch) · Xuân vãn", "xuanmuon"),
        Verse("Tựa hiên, nâng sáo ngọc,\nĐầy ngực ánh trăng trong.", "Trần Nhân Tông (Ngô Tất Tố dịch) · Đăng Bảo Đài sơn"),
        Verse("Dưới bóng đa già chùa vắng vẻ,\nTiếng ve khơi động tứ thu xa.", "Trần Nhân Tông (Ngô Tất Tố dịch) · Đề Phổ Minh tự thủy tạ", "he"),
    )

    val ALL: List<Verse> = HAIKU + CLASSICS

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun stride(n: Int): Int = listOf(41, 43, 47, 53, 59).first { gcd(it, n) == 1 }

    /** Ngày đặc biệt có câu riêng: Tết (mùng 1–4), giao thừa (ngày cuối năm âm), rằm tháng Giêng, rằm tháng Tám. */
    private fun dayTag(l: LunarDate, next: LunarDate): String? = when {
        l.month == 12 && next.month == 1 && next.day == 1 && !next.leap -> "giaothua"
        !l.leap && l.month == 1 && l.day == 1 -> "tet1"
        !l.leap && l.month == 1 && l.day == 2 -> "tet2"
        !l.leap && l.month == 1 && l.day in 3..4 -> "tet"
        !l.leap && l.month == 1 && l.day == 15 -> "ram1"
        !l.leap && l.month == 8 && l.day == 15 -> "ram8"
        else -> null
    }

    /** Mùa theo tiết khí (chỉ số theo LunarCalendar.TIET_KHI, 0 = Xuân phân): Lập xuân 21, Lập hạ 3, Lập thu 9, Lập đông 15. */
    private fun season(tk: Int) = when (tk) {
        21, 22, 23, 0, 1, 2 -> "xuan"
        in 3..8 -> "he"
        in 9..14 -> "thu"
        else -> "dong"
    }

    /** Câu [v] có hợp với ngày dương [date] (ngày âm [l]) không, ngoài các ngày đặc biệt. */
    fun fits(v: Verse, date: LocalDate, l: LunarDate): Boolean = fitsTag(v, date, l) && moonOk(v, l)

    /** Câu tả trăng sáng không hiện vào những ngày không trăng (cuối, đầu tháng âm). */
    private fun moonOk(v: Verse, l: LunarDate): Boolean {
        if (v.tag == "trangnon" || v.tag == "hetoi") return true
        val t = v.text.lowercase()
        return !(t.contains("trăng") || t.contains("nguyệt")) || l.day in 4..27
    }

    private fun fitsTag(v: Verse, date: LocalDate, l: LunarDate): Boolean = when (v.tag) {
        "" -> true
        "xuan", "he", "thu", "dong" -> v.tag == season(LunarCalendar.tietKhiIndexAt(l.jd))
        "xuanmuon" -> LunarCalendar.tietKhiIndexAt(l.jd) in 0..2
        "thumuon" -> LunarCalendar.tietKhiIndexAt(l.jd) in 13..14
        "daudong" -> LunarCalendar.tietKhiIndexAt(l.jd) in 15..16
        "dauhe" -> LunarCalendar.tietKhiIndexAt(l.jd) in 3..4
        "dauthu" -> LunarCalendar.tietKhiIndexAt(l.jd) in 9..10
        "heomay" -> LunarCalendar.tietKhiIndexAt(l.jd) in 11..14
        "hetoi" -> season(LunarCalendar.tietKhiIndexAt(l.jd)) == "he" && (l.day <= 6 || l.day >= 24)   // đêm hè ít trăng
        "cuoinam" -> l.month >= 11   // cả tháng 11 nhuận
        "thang7" -> !l.leap && l.month == 7
        "thang12" -> !l.leap && l.month == 12
        "trangnon" -> l.day in 2..6
        else -> false
    }

    private fun raw(date: LocalDate): Verse {
        val l = LunarCalendar.convert(date)
        dayTag(l, LunarCalendar.convert(date.plusDays(1)))?.let { t ->
            val special = ALL.filter { it.tag == t || (t == "giaothua" && it.tag == "bamuoi" && l.day == 30) }
            if (special.isNotEmpty()) return special[Math.floorMod(date.toEpochDay() + l.year, special.size.toLong()).toInt()]
        }
        val pool = ALL.filter { fits(it, date, l) }
        val narrow = pool.filter { it.tag in NARROW }
        val day = date.toEpochDay()
        if (narrow.isNotEmpty() && Math.floorMod(day, 3L) == 0L) return narrow[Math.floorMod(day / 3, narrow.size.toLong()).toInt()]
        return pool[Math.floorMod(day * stride(pool.size) + 7, pool.size.toLong()).toInt()]
    }

    /** Nhãn chỉ hợp một quãng ngắn trong năm. */
    private val NARROW = setOf("thang7", "thang12", "cuoinam", "trangnon", "xuanmuon", "thumuon", "daudong", "dauhe", "dauthu", "heomay", "hetoi")

    /**
     * Câu của ngày: ngày đặc biệt dùng câu riêng; ngày thường chọn xoay vòng trong các câu hợp mùa, hợp tháng âm
     * (bước nhảy nguyên tố cùng nhau với số câu: đi hết vòng mới lặp); không trùng câu của hôm trước.
     */
    fun forDate(date: LocalDate): Verse = pick(date, 4)

    private val SPECIAL = setOf("tet1", "tet2", "tet", "giaothua", "bamuoi", "ram1", "ram8")

    /** Chọn có so với câu thực sự đã hiện hôm trước (nhìn lùi tối đa [depth] ngày); câu ngày đặc biệt luôn giữ nguyên. */
    private fun pick(date: LocalDate, depth: Int): Verse {
        val v = raw(date)
        if (v.tag in SPECIAL || depth == 0) return v
        if (v != pick(date.minusDays(1), depth - 1)) return v
        val l = LunarCalendar.convert(date)
        val pool = ALL.filter { fits(it, date, l) }
        return pool[(pool.indexOf(v) + 1) % pool.size]
    }
}
