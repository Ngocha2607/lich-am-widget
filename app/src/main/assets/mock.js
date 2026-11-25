// Giả lập cầu nối `Android` để xem giao diện trên trình duyệt (mở index.html trực tiếp).
// Trong app thật file này không được tải. Ngày âm tính thật (thuật toán Hồ Ngọc Đức, GMT+7);
// thời tiết, trực, giờ tốt... là số liệu mẫu.
(function () {
  const TZ = 7, PI = Math.PI, INT = Math.floor;
  const CAN = ["Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"];
  const CHI = ["Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"];
  const THU = ["Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"];
  const SDOW = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
  const TU = ["Giác", "Cang", "Đê", "Phòng", "Tâm", "Vĩ", "Cơ", "Đẩu", "Ngưu", "Nữ", "Hư", "Nguy", "Thất", "Bích",
    "Khuê", "Lâu", "Vị", "Mão", "Tất", "Chủy", "Sâm", "Tỉnh", "Quỷ", "Liễu", "Tinh", "Trương", "Dực", "Chẩn"];

  function jdFromDate(dd, mm, yy) {
    const a = INT((14 - mm) / 12), y = yy + 4800 - a, m = mm + 12 * a - 3;
    let jd = dd + INT((153 * m + 2) / 5) + 365 * y + INT(y / 4) - INT(y / 100) + INT(y / 400) - 32045;
    if (jd < 2299161) jd = dd + INT((153 * m + 2) / 5) + 365 * y + INT(y / 4) - 32083;
    return jd;
  }
  function newMoon(k) {
    const T = k / 1236.85, T2 = T * T, T3 = T2 * T, dr = PI / 180;
    let Jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3;
    Jd1 += 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
    const M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
    const Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
    const F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
    let C1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2 * dr * M);
    C1 = C1 - 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(dr * 2 * Mpr) - 0.0004 * Math.sin(dr * 3 * Mpr);
    C1 = C1 + 0.0104 * Math.sin(dr * 2 * F) - 0.0051 * Math.sin(dr * (M + Mpr));
    C1 = C1 - 0.0074 * Math.sin(dr * (M - Mpr)) + 0.0004 * Math.sin(dr * (2 * F + M));
    C1 = C1 - 0.0004 * Math.sin(dr * (2 * F - M)) - 0.0006 * Math.sin(dr * (2 * F + Mpr));
    C1 = C1 + 0.0010 * Math.sin(dr * (2 * F - Mpr)) + 0.0005 * Math.sin(dr * (2 * Mpr + M));
    const dt = T < -11 ? 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3 : -0.000278 + 0.000265 * T + 0.000262 * T2;
    return INT(Jd1 + C1 - dt + 0.5 + TZ / 24);
  }
  function sunSector(jdn) {
    const T = (jdn - 2451545.5 - TZ / 24) / 36525, T2 = T * T, dr = PI / 180;
    const M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
    const L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
    let DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M);
    DL = DL + (0.019993 - 0.000101 * T) * Math.sin(dr * 2 * M) + 0.000290 * Math.sin(dr * 3 * M);
    let L = (L0 + DL) * dr; L = L - PI * 2 * INT(L / (PI * 2));
    return INT(L / PI * 6);
  }
  function month11(yy) {
    const k = INT((jdFromDate(31, 12, yy) - 2415021) / 29.530588853);
    const nm = newMoon(k);
    return sunSector(nm) >= 9 ? newMoon(k - 1) : nm;
  }
  function leapOffset(a11) {
    const k = INT((a11 - 2415021.076998695) / 29.530588853 + 0.5);
    let last, i = 1, arc = sunSector(newMoon(k + i));
    do { last = arc; i++; arc = sunSector(newMoon(k + i)); } while (arc !== last && i < 14);
    return i - 1;
  }
  function toLunar(dd, mm, yy) {
    const day = jdFromDate(dd, mm, yy), k = INT((day - 2415021.076998695) / 29.530588853);
    let start = newMoon(k + 1); if (start > day) start = newMoon(k);
    let a11 = month11(yy), b11 = a11, ly;
    if (a11 >= start) { ly = yy; a11 = month11(yy - 1); } else { ly = yy + 1; b11 = month11(yy + 1); }
    const diff = INT((start - a11) / 29);
    let leap = false, lm = diff + 11;
    if (b11 - a11 > 365) { const lo = leapOffset(a11); if (diff >= lo) { lm = diff + 10; if (diff === lo) leap = true; } }
    if (lm > 12) lm -= 12;
    if (lm >= 11 && diff < 4) ly -= 1;
    return { d: day - start + 1, m: lm, y: ly, leap, jd: day };
  }
  function toSolar(ld, lm, ly) {           // dò tìm trong khoảng năm dương tương ứng (đủ cho xem trước)
    for (let t = new Date(ly, 0, 1); t.getFullYear() <= ly + 1; t.setDate(t.getDate() + 1)) {
      const l = toLunar(t.getDate(), t.getMonth() + 1, t.getFullYear());
      if (l.d === ld && l.m === lm && l.y === ly && !l.leap) return t;
    }
    return null;
  }
  const iso = t => `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, "0")}-${String(t.getDate()).padStart(2, "0")}`;
  const canChiYear = y => `${CAN[(y + 6) % 10]} ${CHI[(y + 8) % 12]}`;
  const moon = d => d <= 2 ? "🌑" : d <= 6 ? "🌒" : d <= 9 ? "🌓" : d <= 13 ? "🌔" : d <= 16 ? "🌕" : d <= 20 ? "🌖" : d <= 23 ? "🌗" : d <= 27 ? "🌘" : "🌑";
  const hoangDao = l => [0, 1, 4, 5, 7, 10].includes(((l.jd + 1) % 12 - ((l.m - 1) % 6) * 2 + 12) % 12);

  const now = new Date(), today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  let events = [
    { id: "e1", title: "Giỗ ông nội", icon: "🕯️", lunar: true, day: 18, month: 10, year: null, yearly: true, time: "", note: "Về quê từ chiều hôm trước", remind: 3 },
    { id: "e2", title: "Sinh nhật mẹ", icon: "🎂", lunar: false, day: 20, month: 12, year: null, yearly: true, time: "19:00", note: "", remind: 1 },
    { id: "e3", title: "Họp lớp", icon: "🎉", lunar: false, day: 26, month: 11, year: null, yearly: true, time: "18:30", note: "", remind: 1 },
    { id: "e4", title: "Khám sức khỏe", icon: "🏥", lunar: false, day: 3, month: 12, year: null, yearly: false, time: "08:00", note: "", remind: 1 },
  ];
  const profile = { name: "", birth: null, birthYear: 0, birthLabel: "" };
  const notes = { [iso(today)]: [{ id: "n1", text: "Mua hoa quả cúng rằm", done: false }, { id: "n2", text: "Gọi điện cho bà", done: true }] };
  const noteKey = (y, m, d) => iso(new Date(y, m - 1, d));
  const dateLabel = e => (e.lunar ? `${e.day}/${e.month} âm` : `${e.day}/${e.month}`) + (e.yearly ? " · hằng năm" : `/${e.year}`);
  function occurs(e, t) {
    if (!e.lunar) return t.getDate() === e.day && t.getMonth() + 1 === e.month && (e.yearly || e.year === t.getFullYear());
    const l = toLunar(t.getDate(), t.getMonth() + 1, t.getFullYear());
    return !l.leap && l.m === e.month && l.d === e.day && (e.yearly || e.year === l.y);
  }
  function withBirthday() {                // giống Profile.eventsWithBirthday bên Kotlin
    const b = profile.birth;
    if (!b) return events;
    return events.concat({ id: "profile-birthday", title: "Sinh nhật của bạn", icon: "🎂", lunar: b.lunarInput,
      day: b.lunarInput ? b.ld : b.sd, month: b.lunarInput ? b.lm : b.sm, year: null, yearly: true, time: "", note: "", remind: 1 });
  }
  const on = t => withBirthday().filter(e => occurs(e, t)).map(e => ({ ...e, dateLabel: dateLabel(e) }));

  const DAILY = [[3, 24, 31, 10], [2, 23, 30, 20], [61, 22, 27, 70], [80, 21, 26, 80], [3, 20, 28, 30], [1, 19, 29, 10], [0, 20, 30, 0]];
  const ICON = { 0: "☀️", 1: "🌤️", 2: "⛅", 3: "☁️", 61: "🌧️", 80: "🌧️" }, TEXT = { 0: "trời quang", 1: "ít mây", 2: "có mây", 3: "nhiều mây", 61: "mưa nhẹ", 80: "mưa nhẹ" };
  const weather = {
    city: "Hà Nội", updated: "07:30", tempNow: 26, feelsLike: 27, humidity: 78, iconNow: "⛅", labelNow: "có mây", codeNow: 2,
    windKmh: 12, windDir: "Gió đông bắc", beaufort: 3, pressure: 1014, visibility: 10, uv: 5, uvLabel: "Trung bình",
    daily: DAILY.map(([c, mn, mx, rain], i) => {
      const t = new Date(today); t.setDate(t.getDate() + i);
      return { date: iso(t), label: i === 0 ? "Hôm nay" : SDOW[t.getDay()], icon: ICON[c], text: TEXT[c], max: mx, min: mn, rain, sunrise: "06:15", sunset: "17:18" };
    }),
  };

  // Tuổi xông đất: cùng cách chấm với Horoscope.kt (mô hình thientue.vn)
  const HANH = ["Kim", "Thủy", "Mộc", "Hỏa", "Thổ"];
  const NAP_HANH = [0, 3, 2, 4, 0, 3, 1, 4, 0, 2, 1, 4, 3, 2, 1, 0, 3, 2, 4, 0, 3, 1, 4, 0, 2, 1, 4, 3, 2, 1];
  const md = (a, n) => ((a % n) + n) % n;
  const hanhOf = y => NAP_HANH[INT(md(y - 4, 60) / 2)], canOf = y => md(y - 4, 10), chiOf = y => md(y + 8, 12);
  const napSc = (a, b) => [0.5, 1, 0, 0, 1][md(b - a, 5)];
  const canSc = (a, b) => Math.abs(a - b) === 5 ? 1 : Math.abs(a - b) === 6 ? 0 : 0.5;
  const inSame = (g, a, b) => g.some(x => x.includes(a) && x.includes(b));
  const LUC_HOP = [[0, 1], [2, 11], [3, 10], [4, 9], [5, 8], [6, 7]], LUC_HAI = [[0, 7], [1, 6], [2, 5], [3, 4], [8, 11], [9, 10]];
  const TAM_HOP = [[8, 0, 4], [11, 3, 7], [2, 6, 10], [5, 9, 1]], TU_XUNG = [[0, 6, 3, 9], [2, 8, 5, 11], [4, 10, 1, 7]];
  const chiSc = (a, b) => a === b ? 0.5 : inSame(LUC_HOP, a, b) || inSame(TAM_HOP, a, b) ? 1
    : md(a - b, 12) === 6 || inSame(LUC_HAI, a, b) || inSame(TU_XUNG, a, b) ? 0 : 0.5;
  const chiRel = (a, b) => a === b ? null : inSame(LUC_HOP, a, b) ? "Lục hợp" : inSame(TAM_HOP, a, b) ? "Tam hợp" : null;
  const xdLabel = v => v >= 88 ? "Rất nên mời" : v >= 75 ? "Hợp để ưu tiên" : v >= 62 ? "Có thể cân nhắc" : "Nên tránh ưu tiên";
  function xongDat(owner, year) {
    const oc = canOf(owner), oz = chiOf(owner), oh = hanhOf(owner), yc = canOf(year), yz = chiOf(year), yh = hanhOf(year);
    const list = [];
    for (let c = year - 65; c <= year - 18; c++) {
      const cc = canOf(c), cz = chiOf(c), ch = hanhOf(c);
      const score = Math.round(20 * canSc(cc, oc) + 22 * chiSc(cz, oz) + 14 * canSc(cc, yc) + 16 * chiSc(cz, yz) + 14 * napSc(ch, oh) + 14 * napSc(ch, yh));
      const reasons = [], warnings = [], r1 = chiRel(cz, oz), r2 = chiRel(cz, yz);
      if (r1) reasons.push(`${r1} với gia chủ`);
      if (r2) reasons.push(`${r2} với năm`);
      if (canSc(cc, oc) === 1) reasons.push("Can hợp gia chủ");
      if (canSc(cc, yc) === 1) reasons.push("Can hợp năm");
      if (napSc(ch, oh) === 1) reasons.push(`Mệnh ${HANH[ch]} hợp gia chủ (${HANH[oh]})`);
      if (napSc(ch, yh) === 1) reasons.push(`Mệnh ${HANH[ch]} hợp năm (${HANH[yh]})`);
      const tt = md(yz - [2, 11, 8, 5][cz % 4], 12), tue = md(yz - cz, 12);
      if (tt <= 2) warnings.push(`phạm Tam Tai ${["năm đầu", "năm giữa", "năm cuối"][tt]}`);
      if (tue === 0) warnings.push("năm tuổi"); else if (tue === 6) warnings.push("xung Thái Tuế");
      list.push({ year: c, canChi: canChiYear(c), score, label: xdLabel(score), reasons, warnings });
    }
    list.sort((a, b) => b.score - a.score || a.warnings.length - b.warnings.length || b.year - a.year);
    const avoid = [...new Set([md(oz + 6, 12), md(yz + 6, 12)])].map(i => CHI[i]);
    const good = list.filter(x => x.score >= 62).slice(0, 8);
    return { year, canChi: canChiYear(year), avoid, list: good.length ? good : list.slice(0, 3) };
  }
  window.__xongDat = xongDat;   // cho kiểm thử

  // Câu thơ Zen mẫu (trên máy lấy từ ZenVerses.kt)
  const ZEN = [
    { text: "Mưa phùn đầu ngõ\nai phơi chiếc nón lá\nướt một vành trăng", by: "" },
    { text: "Chuông chùa buổi sớm\nsương còn đọng trên lá\nchưa ai đánh thức", by: "" },
    { text: "Sương giăng mặt hồ\ncây cầu son mất dấu\nchỉ còn tiếng chuông", by: "" },
    { text: "Thu về rất khẽ\nmột sớm mở cửa ra\nthấy trời cao hơn", by: "" },
    { text: "Không cần nói gì\nhai tách trà bên nhau\nhơi ấm đủ đầy", by: "" },
  ];

  // Sổ giỗ gia đình (giả lập, có tính ngày dương lần giỗ tới)
  let fams = [{ id: "f1", name: "Nhà nội", remind: 3, u: 1, members: [
    { id: "m1", name: "Nguyễn Văn An", rel: "Ông nội", d: 18, m: 10, y: 1998, place: "Nghĩa trang Văn Điển", who: "Chú Ba", note: "", u: 1 },
    { id: "m2", name: "Trần Thị Bình", rel: "Bà nội", d: 5, m: 3, y: 2005, place: "", who: "", note: "", u: 1 }] }];
  const famTitle = m => m.rel && m.name ? `Giỗ ${m.rel.toLowerCase()} (${m.name})` : `Giỗ ${(m.rel || m.name).toLowerCase()}`;
  function famNext(m) {
    const ly = toLunar(today.getDate(), today.getMonth() + 1, today.getFullYear()).y;
    for (let y = ly; y <= ly + 2; y++) { const t = toSolar(m.d, m.m, y) || (m.d === 30 ? toSolar(29, m.m, y) : null); if (t && t >= today) return t; }
    return null;
  }
  function famSync(g) {
    events = events.filter(e => !String(e.id).startsWith(`fam-${g.id}-`));
    if (fams.includes(g)) g.members.forEach(m => events.push({ id: `fam-${g.id}-${m.id}`, title: famTitle(m), icon: "🕯️", lunar: true, day: m.d, month: m.m,
      year: null, yearly: true, time: "", note: [m.who ? "Lo đồ cúng: " + m.who : "", m.place, "Sổ giỗ " + g.name].filter(Boolean).join(" · "), remind: g.remind }));
  }
  famSync(fams[0]);

  window.Android = {
    month(y, m) {
      const n = new Date(y, m, 0).getDate(), days = [];
      for (let d = 1; d <= n; d++) {
        const t = new Date(y, m - 1, d), l = toLunar(d, m, y), ev = on(t);
        const nt = (notes[iso(t)] || []).filter(n => !n.done).length;
        days.push({ d, ld: l.d, lm: l.m, leap: l.leap, hd: hoangDao(l), ev: ev.length ? ev[0].icon : "", evn: ev.length, nt });
      }
      return JSON.stringify({ firstWeekday: new Date(y, m - 1, 1).getDay() || 7, days });
    },
    day(y, m, d) {
      const t = new Date(y, m - 1, d), l = toLunar(d, m, y), tu = TU[(l.jd + 11) % 28];
      const isToday = +t === +today, tom = new Date(today); tom.setDate(tom.getDate() + 1);
      return JSON.stringify({
        y, m, d, weekday: THU[t.getDay()], ld: l.d, lm: l.m, ly: l.y, leap: l.leap, moon: moon(l.d),
        tuoi: profile.birthYear ? 0 : null, events: on(t),
        truc: "Thành", trucNen: ["Khai trương", "Cưới hỏi", "Nhập học"], trucKy: ["Kiện tụng"],
        napAm: "Thiên Hà Thủy", nguHanh: "Thủy", xung: CHI[((l.jd + 1) % 12 + 6) % 12], hyThan: "Chính Nam", taiThan: "Chính Tây",
        lucDieuDay: "Tiểu Cát", lucDieu: [{ name: "Đại An", hours: "23–1h (Tý), 11–13h (Ngọ)", good: true, desc: "Mọi việc đều tốt (số liệu mẫu)." }],
        quote: "Một cây làm chẳng nên non,\nBa cây chụm lại nên hòn núi cao.", quoteKind: "Ca dao",
        zen: ZEN[((l.jd % ZEN.length) + ZEN.length) % ZEN.length],
        tu, tuFull: tu + " (mẫu)", tuGood: true, tuNen: "khai trương, xuất hành", tuKy: "",
        ngocHap: [{ name: "Thiên Quan", good: true, desc: "Tốt mọi việc (mẫu)" }, { name: "Tục Thế", good: true, desc: "Tốt mọi việc, nhất là cưới hỏi (mẫu)" },
          { name: "Hoang Vu", good: false, desc: "Xấu mọi việc (mẫu)" }, { name: "Thiên Hỏa", good: false, desc: "Xấu về lợp nhà (mẫu)" }],
        canChiYear: canChiYear(l.y), canChiMonth: "Kỷ Hợi", canChiDay: `${CAN[(l.jd + 9) % 10]} ${CHI[(l.jd + 1) % 12]}`,
        canChiHour: `${CAN[0]} ${CHI[INT((now.getHours() + 1) / 2) % 12]}`, tietKhi: "Tiểu tuyết",
        hoangDao: hoangDao(l), trucThan: "Kim Quỹ", gioTot: [{ chi: "Tý", from: 23, to: 1 }, { chi: "Sửu", from: 1, to: 3 }, { chi: "Mão", from: 5, to: 7 }],
        tips: [{ short: "✅ Ngày hoàng đạo, hợp việc lớn", detail: "Số liệu mẫu khi xem trên trình duyệt.", kind: "NORMAL" }],
        isToday, isTomorrow: +t === +tom,
      });
    },
    goodDays: () => JSON.stringify([{ date: iso(new Date(today.getTime() + 5 * 864e5)), d: new Date(today.getTime() + 5 * 864e5).getDate(), m: today.getMonth() + 1, y: today.getFullYear(), dow: "T2", lunar: "22/10 âm", canChi: "Mậu Thân", truc: "Thành", hoangDao: true, score: 5, reasons: ["Hoàng đạo", "Trực Thành", "Sao Vị"] }]),
    upcoming(n) {
      const out = [];
      for (let i = 0; i <= n; i++) {
        const t = new Date(today); t.setDate(t.getDate() + i);
        on(t).forEach(e => out.push({ ...e, date: iso(t), daysLeft: i, solarLabel: `${THU[t.getDay()]} ${t.getDate()}/${t.getMonth() + 1}` }));
      }
      return JSON.stringify(out);
    },
    tuoi(by) {                              // cùng công thức với Almanac.checkYear
      const ly = toLunar(today.getDate(), today.getMonth() + 1, today.getFullYear()).y, out = [];
      const KL = { 1: "Kim Lâu Thân (hại bản thân)", 3: "Kim Lâu Thê (hại vợ/chồng)", 6: "Kim Lâu Tử (hại con cái)", 8: "Kim Lâu Lục Súc (hại vật nuôi, tài sản)" };
      const HO = ["Nhất Cát", "Nhì Nghi", "Tam Địa Sát", "Tứ Tấn Tài", "Ngũ Thọ Tử", "Lục Hoang Ốc"];
      for (let y = ly; y < ly + 5; y++) {
        const t = y - by + 1, bc = (by + 8) % 12, yc = (y + 8) % 12, ho = t >= 10 ? ((Math.floor(t / 10) - 1 + t % 10) % 6 + 6) % 6 : -1;
        const tt = (yc - [2, 11, 8, 5][bc % 4] + 12) % 12, tamTai = tt <= 2 ? "Tam Tai " + ["năm đầu", "năm giữa", "năm cuối"][tt] : "";
        const kimLau = KL[t % 9] || "", bad = [2, 4, 5].includes(ho), dd = (yc - bc + 12) % 12;
        out.push({ year: y, canChi: canChiYear(y), tuoi: t, kimLau, hoangOc: ho >= 0 ? HO[ho] : "", hoangOcBad: bad, tamTai,
          thaiTue: dd === 0 ? "Năm tuổi (phạm Thái Tuế)" : dd === 6 ? "Xung Thái Tuế" : "", badForHouse: !!kimLau || bad || !!tamTai });
      }
      return JSON.stringify(out);
    },
    calendarList: () => JSON.stringify([{ id: "1", name: "Cá nhân", account: "ban@gmail.com", color: "#4285F4", writable: true },
      { id: "2", name: "Ngày lễ ở Việt Nam", account: "ban@gmail.com", color: "#0B8043", writable: false }]),
    importCalendar() { window.onStatus && window.onStatus("(xem trước) Đã nhập 3 sự kiện từ lịch trên máy."); },
    exportCalendar() { window.onStatus && window.onStatus("(xem trước) Đã xuất 12 mục sang lịch đã chọn."); },
    tuvi: () => profile.birth ? JSON.stringify({ sign: "Song Ngư", symbol: "♓", element: "Nước", range: "19/2 – 20/3",
      traits: "Nhạy cảm, giàu trí tưởng tượng, dễ đồng cảm với người khác.", canChi: profile.birthLabel || "Canh Ngọ",
      animal: "Ngựa", menh: "Lộ Bàng Thổ", hanh: "Thổ" }) : "null",
    xongDat: owner => { const l = toLunar(today.getDate(), today.getMonth() + 1, today.getFullYear()); return JSON.stringify(xongDat(owner, l.y + 1)); },
    notes: (y, m, d) => JSON.stringify(notes[noteKey(y, m, d)] || []),
    addNote(y, m, d, text) { const k = noteKey(y, m, d); (notes[k] = notes[k] || []).push({ id: "n" + Date.now(), text: text.trim().slice(0, 200), done: false }); return JSON.stringify(notes[k]); },
    toggleNote(y, m, d, id) { const k = noteKey(y, m, d); (notes[k] || []).forEach(n => { if (n.id === id) n.done = !n.done; }); return JSON.stringify(notes[k] || []); },
    deleteNote(y, m, d, id) { const k = noteKey(y, m, d); notes[k] = (notes[k] || []).filter(n => n.id !== id); return JSON.stringify(notes[k]); },
    lunarToSolar(d, m, y) { const t = toSolar(d, m, y); return t ? iso(t) : ""; },
    saveEvent(json) { const e = JSON.parse(json); e.id = e.id || "e" + Date.now(); events = events.filter(x => x.id !== e.id).concat(e); return e.id; },
    deleteEvent(id) { events = events.filter(e => e.id !== id); },
    setProfile(name, lunar, d, m, y) {
      const t = lunar ? toSolar(d, m, y) : new Date(y, m - 1, d);
      if (!t || t > today) return "Ngày sinh không hợp lệ.";
      const l = toLunar(t.getDate(), t.getMonth() + 1, t.getFullYear());
      Object.assign(profile, { name, birthYear: l.y, birthLabel: canChiYear(l.y),
        birth: { sd: t.getDate(), sm: t.getMonth() + 1, sy: t.getFullYear(), ld: l.d, lm: l.m, ly: l.y, leap: l.leap, lunarInput: lunar } });
      setTimeout(push); return "";
    },
    clearProfile() { Object.assign(profile, { name: "", birth: null, birthYear: 0, birthLabel: "" }); setTimeout(push); },
    setTheme(v) { prefs.theme = v; setTimeout(push); },
    setBackground(k) { prefs.bg = k; setTimeout(push); },
    pickBackground() { window.onStatus && window.onStatus("(xem trước) Trên máy sẽ mở trình chọn ảnh."); },
    backgroundPhoto: () => "",
    setSpiritual(v) { prefs.spiritual = v; },
    families: () => JSON.stringify(fams.map(g => ({ ...g, members: g.members.slice().sort((a, b) => a.m - b.m || a.d - b.d).map(m => {
      const t = famNext(m);
      return { ...m, title: famTitle(m), ...(t ? { next: `${t.getDate()}/${t.getMonth() + 1}/${t.getFullYear()}`, daysLeft: Math.round((t - today) / 864e5) } : {}) };
    }) }))),
    familyCreate(name) { const g = { id: "f" + Date.now(), name: name.trim() || "Gia đình", remind: 3, u: Date.now(), members: [] }; fams.push(g); return g.id; },
    familyUpdate(id, name, remind) { const g = fams.find(x => x.id === id); if (g) { g.name = name.trim() || g.name; g.remind = remind; famSync(g); } },
    familySaveMember(gid, json) {
      const g = fams.find(x => x.id === gid); if (!g) return false;
      const m = JSON.parse(json); m.id = m.id || "m" + Date.now(); m.u = Date.now();
      g.members = g.members.filter(x => x.id !== m.id).concat(m); famSync(g); return true;
    },
    familyDeleteMember(gid, mid) { const g = fams.find(x => x.id === gid); if (g) { g.members = g.members.filter(x => x.id !== mid); famSync(g); } },
    familyDelete(id) { const g = fams.find(x => x.id === id); fams = fams.filter(x => x.id !== id); if (g) famSync(g); },
    familyShare() { window.onStatus && window.onStatus("(xem trước) Trên máy sẽ mở Zalo, Messenger… với tin nhắn có mã LICHGIAPHA1:…"); },
    familyImport(text) {
      if (!/LICHGIAPHA1:[A-Za-z0-9_-]+/.test(text)) return JSON.stringify({ error: "Không tìm thấy mã sổ giỗ hợp lệ trong nội dung đã dán." });
      const g = { id: "f" + Date.now(), name: "Nhà ngoại (mẫu)", remind: 3, u: Date.now(), members: [
        { id: "m9", name: "Lê Văn Cường", rel: "Ông ngoại", d: 2, m: 1, y: 2010, place: "", who: "Cậu Tư", note: "", u: 1 }] };
      fams.push(g); famSync(g);
      return JSON.stringify({ id: g.id, name: g.name, added: 1, updated: 0, isNew: true });
    },
    clipboardText: () => "Sổ giỗ \"Nhà ngoại\"\nLICHGIAPHA1:eJxLyEzJTM1LSVWyUkpKLMpUMlSyUsrJz0tXqgUAeQ8IyA",
    openTearOff(iso) { window.onStatus && window.onStatus(`(xem trước) Trên máy sẽ mở lịch xé từ ngày ${iso}.`); },
    openCompass() { window.onStatus && window.onStatus("(xem trước) Trên máy sẽ mở la bàn phong thủy AR (camera + cảm biến)."); },
    setLiveWallpaper() { prefs.wpActive = true; window.onStatus && window.onStatus("(xem trước) Trên máy sẽ mở màn hình đặt hình nền động."); setTimeout(push); },
    setWallpaperText(v) { prefs.wpText = v; },
    setLockNote(v) { prefs.lockNote = v; return true; },
    setSystemBars() {},
    openNotificationSettings() { window.onStatus && window.onStatus("(xem trước) Trên máy sẽ mở cài đặt thông báo của app."); },
    setZen(start, paper) { prefs.zenStart = start; prefs.zenPaper = paper; },
    searchCity(q) { setTimeout(() => window.onCityResults([{ name: q, region: "Việt Nam (mẫu)", lat: 21, lon: 105.8 }]), 200); },
  };
  ["setSections", "setNotify", "testNotify", "setNotifyWeather", "addCity", "setActiveCity", "removeCity", "exportEvents",
    "exportIcs", "importEvents", "shareToday", "setCity", "refresh", "pinWidget", "pinIconWidget", "pinBlocWidget", "openUrl"].forEach(k => {
    window.Android[k] = () => window.onStatus && window.onStatus(`(xem trước) Android.${k}()`);
  });

  const prefs = { city: "Hà Nội", spiritual: true, theme: "system", showAlmanac: true, showLucDieu: true, showHuong: true, showGoodDays: true,
    notifyEvents: true, notifyLunar: true, notifyHour: 7, notifyWeather: true, notifyWeatherHour: 20, bg: "none", bgVer: 0,
    lockNote: false, notifAllowed: true, wpText: true, wpActive: false, zenStart: false, zenPaper: "auto" };
  function push() {
    const n = new Date(), l = toLunar(today.getDate(), today.getMonth() + 1, today.getFullYear());
    const tet = toSolar(1, 1, l.y + 1), dark = prefs.theme === "dark" || (prefs.theme === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
    window.onState({
      today: { y: today.getFullYear(), m: today.getMonth() + 1, d: today.getDate() }, hour: n.getHours(), minute: n.getMinutes(),
      dark, zenLaunch: true, version: "xem trước", tet: { date: iso(tet), daysLeft: Math.round((tet - today) / 864e5), name: "Tết " + canChiYear(l.y + 1) },
      cities: [{ name: "Hà Nội", lat: 21.03, lon: 105.85 }, { name: "Đà Nẵng", lat: 16.05, lon: 108.2 }],
      prefs: { ...prefs, ...profile }, weather,
    });
  }
  push();
})();
