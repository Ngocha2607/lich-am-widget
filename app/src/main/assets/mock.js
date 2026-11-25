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
  ];
  const profile = { name: "", birth: null, birthYear: 0, birthLabel: "" };
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
    city: "Hà Nội", updated: "07:30", tempNow: 26, feelsLike: 27, humidity: 78, iconNow: "⛅", labelNow: "có mây",
    windKmh: 12, windDir: "Gió đông bắc", beaufort: 3, pressure: 1014, visibility: 10, uv: 5, uvLabel: "Trung bình",
    daily: DAILY.map(([c, mn, mx, rain], i) => {
      const t = new Date(today); t.setDate(t.getDate() + i);
      return { date: iso(t), label: i === 0 ? "Hôm nay" : SDOW[t.getDay()], icon: ICON[c], text: TEXT[c], max: mx, min: mn, rain, sunrise: "06:15", sunset: "17:18" };
    }),
  };

  window.Android = {
    month(y, m) {
      const n = new Date(y, m, 0).getDate(), days = [];
      for (let d = 1; d <= n; d++) {
        const t = new Date(y, m - 1, d), l = toLunar(d, m, y), ev = on(t);
        days.push({ d, ld: l.d, lm: l.m, leap: l.leap, hd: hoangDao(l), ev: ev.length ? ev[0].icon : "" });
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
    setSpiritual(v) { prefs.spiritual = v; },
    searchCity(q) { setTimeout(() => window.onCityResults([{ name: q, region: "Việt Nam (mẫu)", lat: 21, lon: 105.8 }]), 200); },
  };
  ["setSections", "setNotify", "testNotify", "setNotifyWeather", "addCity", "setActiveCity", "removeCity", "exportEvents",
    "exportIcs", "importEvents", "shareToday", "setCity", "refresh", "pinWidget", "openUrl"].forEach(k => {
    window.Android[k] = () => window.onStatus && window.onStatus(`(xem trước) Android.${k}()`);
  });

  const prefs = { city: "Hà Nội", spiritual: true, theme: "system", showAlmanac: true, showLucDieu: true, showHuong: true, showGoodDays: true,
    notifyEvents: true, notifyLunar: true, notifyHour: 7, notifyWeather: true, notifyWeatherHour: 20 };
  function push() {
    const n = new Date(), l = toLunar(today.getDate(), today.getMonth() + 1, today.getFullYear());
    const tet = toSolar(1, 1, l.y + 1), dark = prefs.theme === "dark" || (prefs.theme === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
    window.onState({
      today: { y: today.getFullYear(), m: today.getMonth() + 1, d: today.getDate() }, hour: n.getHours(), minute: n.getMinutes(),
      dark, version: "xem trước", tet: { date: iso(tet), daysLeft: Math.round((tet - today) / 864e5), name: "Tết " + canChiYear(l.y + 1) },
      cities: [{ name: "Hà Nội", lat: 21.03, lon: 105.85 }, { name: "Đà Nẵng", lat: 16.05, lon: 108.2 }],
      prefs: { ...prefs, ...profile }, weather,
    });
  }
  push();
})();
