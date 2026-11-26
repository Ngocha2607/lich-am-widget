// Toàn bộ mã giao diện của app (tách khỏi index.html để chính sách bảo mật chặn mọi script nội tuyến)
const $ = id => document.getElementById(id);
// Địa chi viết chữ Hán như lịch treo tường (giờ hoàng đạo, tuổi)
const CHI_HAN = {Tý:"子",Sửu:"丑",Dần:"寅",Mão:"卯",Thìn:"辰",Tỵ:"巳",Ngọ:"午",Mùi:"未",Thân:"申",Dậu:"酉",Tuất:"戌",Hợi:"亥"};
// Dữ liệu (sự kiện, thời tiết, gợi ý) vẫn mang emoji cho widget và thông báo; trong app vẽ bằng icon nét Lucide (icons.js) kèm màu nhấn
const EMO = {
  "📌": ["pin", "blue"], "🕯": ["flame", "gold"], "🎂": ["cake", "red"], "💍": ["gem", "violet"], "🙏": ["hand-heart", "orange"],
  "🎉": ["party-popper", "red"], "💼": ["briefcase", "blue"], "🏥": ["hospital", "teal"], "✈": ["plane", "blue"], "📚": ["book-open", "orange"],
  "🏠": ["house", "green"], "❤": ["heart", "red"], "📅": ["calendar", "blue"], "📍": ["map-pin", "red"], "📜": ["book-heart", "gold"],
  "☀": ["sun", "gold"], "🌤": ["cloud-sun", "gold"], "⛅": ["cloud-sun", "gold"], "☁": ["cloud", "gray"], "🌫": ["cloud-fog", "gray"],
  "🌦": ["cloud-drizzle", "blue"], "🌧": ["cloud-rain", "blue"], "❄": ["snowflake", "teal"], "⛈": ["cloud-lightning", "violet"], "🌡": ["thermometer", "orange"],
  "☔": ["umbrella", "blue"], "✅": ["circle-check", "green"], "🌿": ["sprout", "green"], "💧": ["droplets", "blue"], "💨": ["wind", "teal"],
  "🔸": ["diamond", "orange"], "🥵": ["thermometer-sun", "red"], "🥶": ["thermometer-snowflake", "teal"], "🧧": ["gift", "red"],
};
const emoKey = e => String(e ?? "").replace(/[︎️]/g, "").trim();
const emoIcon = e => (EMO[emoKey(e)] || ["sparkle", "gray"])[0];
const emoTint = e => (EMO[emoKey(e)] || ["sparkle", "gray"])[1];
/** Bỏ emoji khỏi chữ máy gửi sang (ví dụ nhãn tuổi kèm hình con giáp). */
const noEmo = s => String(s ?? "").replace(/[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}️‍]/gu, "").replace(/\s{2,}/g, " ").trim();
/** Gắn icon cho các phần tử tĩnh có data-ic. */
function hydrateIcons(root) { (root || document).querySelectorAll("[data-ic]").forEach(el => { if (!el.querySelector("svg")) el.innerHTML = ic(el.dataset.ic); }); }
hydrateIcons();

let state = null;               // {today:{y,m,d}, hour, minute, prefs:{city,spiritual,...}, weather, dark, tet, cities}
let view = { y: 0, m: 0, sel: 0 };
const DOW_S = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
function ls(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
function lsSet(k, v) { try { localStorage.setItem(k, v); } catch (e) { } }
let weekMode = ls("weekMode") === "1";
const COL_DEFAULT = { almCard: false, gdCard: true, hoursCard: false, ageCard: true, tetCard: true };   // mục Xem ngày mở sẵn phần xem ngày
function isCollapsed(id) { const v = ls("col_" + id); return v === null ? COL_DEFAULT[id] : v === "1"; }

// ART3D-BEGIN — Hình 3D dùng chung: trăng hình cầu và mây khối. Cùng một mã chạy trong app (index.html) và
// trong script sinh VectorDrawable cho widget. Hình mô tả bằng danh sách {d, fill} (màu #AARRGGBB, gradient toạ độ tuyệt đối).
const ART3D = (() => {
  const f = n => +n.toFixed(2);
  const circle = (cx, cy, r) => `M${f(cx - r)},${f(cy)} A${f(r)},${f(r)} 0 1,0 ${f(cx + r)},${f(cy)} A${f(r)},${f(r)} 0 1,0 ${f(cx - r)},${f(cy)} Z`;
  const rad = (cx, cy, r, ...stops) => ({ rad: [f(cx), f(cy), f(r)], stops });
  const lin = (x1, y1, x2, y2, ...stops) => ({ lin: [x1, y1, x2, y2], stops });

  /** Viền phần sáng của trăng ngày âm ld; rỗng khi trăng mới. */
  function moonLitPath(ld, cx, cy, r) {
    const p = ((ld - 1) / 29.53) % 1, k = Math.cos(2 * Math.PI * p);
    if (k > 0.985) return "";
    const top = `${f(cx)},${f(cy - r)}`, bottom = `${f(cx)},${f(cy + r)}`;
    if (k < -0.985) return `M${top} A${r},${r} 0 1,1 ${bottom} A${r},${r} 0 1,1 ${top} Z`;
    const w = p < 0.5, rx = f(Math.abs(k) * r);
    return `M${top} A${r},${r} 0 0,${w ? 1 : 0} ${bottom} A${rx},${r} 0 0,${w ? (k > 0 ? 0 : 1) : (k > 0 ? 1 : 0)} ${top} Z`;
  }

  /** Trăng hình cầu cho nền trời (khung size×size): quầng sáng, phần tối hắt sáng, phần sáng tô khối, vết trăng, điểm bóng. */
  function moon(ld, size) {
    const c = size / 2, r = size * .3, lit = moonLitPath(ld, c, c, r);
    const p = ((ld - 1) / 29.53) % 1, side = Math.abs(p - .5) < .08 ? -.3 : (p < .5 ? .35 : -.35);  // nguồn sáng lệch về phía sáng
    const lx = c + side * r, ly = c - .38 * r;
    const s = [
      { d: circle(c, c, c * .98), fill: rad(c, c, c * .98, [0.38, "#8CFFE7A3"], [1, "#00FFE7A3"]), cls: "halo" },
      { d: circle(c, c, r), fill: rad(c - .3 * r, c - .35 * r, r * 1.35, [0, "#3DFFFFFF"], [1, "#12FFFFFF"]) },
    ];
    if (lit) {
      s.push({ d: lit, fill: rad(lx, ly, r * 1.6, [0, "#FFFFFBEA"], [0.32, "#FFF9D77A"], [0.78, "#FFE0A33A"], [1, "#FFA86A16"]) });
      s.push({ clip: lit, children: [
        { d: `${circle(c - r * .3, c - r * .2, r * .17)} ${circle(c + r * .32, c + r * .22, r * .23)} ${circle(c - r * .02, c + r * .5, r * .11)}`, fill: "#3DA86A16" },
        { d: circle(lx, ly, r * .42), fill: rad(lx, ly, r * .42, [0, "#CCFFFFFF"], [1, "#00FFFFFF"]) },
      ] });
    }
    return s;
  }

  /** Mây cát tường dạng khối (khung 200×80): đáy đổ bóng, các múi mây tô sáng tối, nét cuộn truyền thống. */
  const LOBES = [[44, 50, 17], [72, 40, 23], [106, 35, 26], [138, 44, 20], [161, 52, 13], [88, 50, 18], [122, 52, 17]];
  const SWIRLS = "M30,60 C22,58 22,48 30,47 C36,46 37,53 32,53 M70,30 C66,40 78,44 83,37 C86,32 81,29 78,32 M104,24 C100,34 113,39 118,31 C121,26 116,23 113,26 M166,62 C178,62 184,56 192,58 C197,59 198,55 195,52";
  function cloud() {
    const s = [{ group: { scale: [1, .22], pivot: [100, 70] }, children: [{ d: circle(100, 70, 78), fill: rad(100, 70, 78, [0, "#59101A40"], [1, "#00101A40"]) }] }];
    LOBES.forEach(([x, y, r]) => s.push({ d: circle(x, y, r), fill: rad(x - r * .35, y - r * .45, r * 1.45, [0, "#FFFFFFFF"], [0.55, "#FFF0F4FB"], [1, "#FFB9C7E0"]) }));
    s.push({ d: "M42,55 H158 A6,6 0 0,1 158,67 H42 A6,6 0 0,1 42,55 Z", fill: lin(0, 55, 0, 67, [0, "#FFE8EEF8"], [1, "#FFAFBFDB"]) });
    s.push({ d: SWIRLS, stroke: "#B3FFFFFF", strokeWidth: 2 });
    s.push({ d: SWIRLS, stroke: "#4D8FA3C8", strokeWidth: 1, dy: 1 });
    return s;
  }

  // ---------- Xuất SVG (app) ----------
  const argb = c => { const h = c.slice(1); return { a: +(parseInt(h.slice(0, 2), 16) / 255).toFixed(3), rgb: "#" + h.slice(2) }; };
  function toSvg(shapes, idp) {
    let defs = "", n = 0;
    const one = s => {
      if (s.clip) { const id = idp + "c" + (++n); defs += `<clipPath id="${id}"><path d="${s.clip}"/></clipPath>`; return `<g clip-path="url(#${id})">${s.children.map(one).join("")}</g>`; }
      if (s.group) { const [sx, sy] = s.group.scale, [px, py] = s.group.pivot; return `<g transform="translate(${px} ${py}) scale(${sx} ${sy}) translate(${-px} ${-py})">${s.children.map(one).join("")}</g>`; }
      if (s.stroke) { const c = argb(s.stroke); return `<path d="${s.d}" fill="none" stroke="${c.rgb}" stroke-opacity="${c.a}" stroke-width="${s.strokeWidth}" stroke-linecap="round"${s.dy ? ` transform="translate(0 ${s.dy})"` : ""}/>`; }
      let fill;
      if (typeof s.fill === "string") { const c = argb(s.fill); fill = `fill="${c.rgb}" fill-opacity="${c.a}"`; }
      else {
        const id = idp + (++n), st = s.fill.stops.map(([o, c]) => { const k = argb(c); return `<stop offset="${o}" stop-color="${k.rgb}" stop-opacity="${k.a}"/>`; }).join("");
        defs += s.fill.lin ? `<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${s.fill.lin[0]}" y1="${s.fill.lin[1]}" x2="${s.fill.lin[2]}" y2="${s.fill.lin[3]}">${st}</linearGradient>`
          : `<radialGradient id="${id}" gradientUnits="userSpaceOnUse" cx="${s.fill.rad[0]}" cy="${s.fill.rad[1]}" r="${s.fill.rad[2]}">${st}</radialGradient>`;
        fill = `fill="url(#${id})"`;
      }
      return `<path d="${s.d}" ${fill}${s.cls ? ` class="${s.cls}"` : ""}/>`;
    };
    const body = shapes.map(one).join("");
    return `<defs>${defs}</defs>${body}`;
  }
  return { moonLitPath, moon, cloud, toSvg };
})();
// ART3D-END

// ---- Hình vẽ "Trăng & mây" bản 3D (cùng hình với widget: res/drawable/moon_*, art_cloud) ----
const ART = (() => {
  const f = n => +n.toFixed(2);
  /** Trăng hình cầu đúng tuần trăng của ngày âm ld. */
  const moon = (ld, size, id) => `<svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" class="moon" aria-hidden="true">${ART3D.toSvg(ART3D.moon(ld, size), id)}</svg>`;
  /** Mây cát tường dạng khối; độ mờ áp cho cả nhóm để các múi chồng nhau không bị đậm lên. */
  const cloud = (w, opacity, cls) => `<svg viewBox="0 0 200 80" width="${w}" height="${f(w * .4)}" class="cloud ${cls}" aria-hidden="true"><g opacity="${opacity}">${ART3D.toSvg(ART3D.cloud(), "cl" + cls)}</g></svg>`;
  /** Sao: chấm tròn và sao bốn cánh, vị trí cố định theo hạt giống. */
  function stars(w, h, n, seed) {
    let s = seed; const rnd = () => (s = (s * 9301 + 49297) % 233280) / 233280;
    let out = "";
    for (let i = 0; i < n; i++) {
      const x = f(rnd() * w), y = f(rnd() * h), big = rnd() > .82, d = f(1.5 + rnd() * 3);
      out += big
        ? `<path class="tw" style="animation-delay:-${d}s" d="M${x},${f(y - 4)} L${f(x + 1)},${f(y - 1)} L${f(x + 4)},${y} L${f(x + 1)},${f(y + 1)} L${x},${f(y + 4)} L${f(x - 1)},${f(y + 1)} L${f(x - 4)},${y} L${f(x - 1)},${f(y - 1)} Z" fill="#FFF6D8"/>`
        : `<circle class="tw" style="animation-delay:-${d}s" cx="${x}" cy="${y}" r="${f(.6 + rnd() * .9)}" fill="#fff" fill-opacity="${f(.5 + rnd() * .5)}"/>`;
    }
    return `<svg viewBox="0 0 ${w} ${h}" width="100%" height="100%" preserveAspectRatio="xMidYMid slice" class="stars" aria-hidden="true">${out}</svg>`;
  }
  /** Thời điểm trong ngày như widget: night, morning, day, evening, rain. */
  function sky(hour, minute, sunrise, sunset, rainy) {
    const m = s => { const [a, b] = String(s).split(":").map(Number); return a * 60 + b; };
    const t = hour * 60 + minute, rise = m(sunrise || "06:00"), set = m(sunset || "18:00");
    if (t < rise - 30 || t >= set + 30) return "night";
    if (rainy) return "rain";
    return t < rise + 150 ? "morning" : t < set - 90 ? "day" : "evening";
  }
  return { moon, cloud, stars, sky };
})();

// Chiều sâu khung trời: nghiêng máy (hoặc rê ngón tay) thì tấm trời nghiêng nhẹ, sao (xa), trăng (giữa), mây (gần) dịch khác nhau
(() => {
  if (matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  const card = document.getElementById("dayCard");
  let tx = 0, ty = 0, x = 0, y = 0, raf = 0;
  const loop = () => {
    x += (tx - x) * .12; y += (ty - y) * .12;
    card.style.setProperty("--px", x.toFixed(3)); card.style.setProperty("--py", y.toFixed(3));
    raf = Math.abs(tx - x) + Math.abs(ty - y) > .002 ? requestAnimationFrame(loop) : 0;
  };
  const aim = (nx, ny) => { tx = Math.max(-1, Math.min(1, nx)); ty = Math.max(-1, Math.min(1, ny)); if (!raf) raf = requestAnimationFrame(loop); };
  window.addEventListener("deviceorientation", e => { if (e.gamma != null && e.beta != null) aim(e.gamma / 25, (e.beta - 45) / 25); });
  card.addEventListener("pointermove", e => { const r = card.getBoundingClientRect(); aim((e.clientX - r.left) / r.width * 2 - 1, (e.clientY - r.top) / r.height * 2 - 1); });
  card.addEventListener("pointerleave", () => aim(0, 0));
})();

function pad(n) { return (n < 10 ? "0" : "") + n; }
// Thoát cả dấu nháy: chuỗi được chèn cả vào nội dung lẫn thuộc tính (value="…", data-id="…")
function esc(s) { return String(s ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c])); }
function monthName(m) { return m === 1 ? "Giêng" : m === 12 ? "Chạp" : String(m); }
function dateKey(y, m, d) { return `${y}-${pad(m)}-${pad(d)}`; }
function forecastFor(y, m, d) { const w = state.weather; return w ? w.daily.find(x => x.date === dateKey(y, m, d)) : null; }

// ---- Hình nền lịch ----
const BG_LIST = [["none", "Không"], ["sen", "Hoa sen"], ["halong", "Hạ Long"], ["dao", "Hoa đào"], ["ruong", "Ruộng bậc thang"], ["cachep", "Cá chép (dân gian)"], ["photo", "Ảnh của bạn"]];
const bgUrl = k => `url("data:image/svg+xml;utf8,${encodeURIComponent(window.BG_ART[k])}")`;
let bgShownVer = -1;
function applyBackground() {
  const kind = (state.prefs.bg && (state.prefs.bg === "photo" || window.BG_ART && window.BG_ART[state.prefs.bg])) ? state.prefs.bg : "none";
  const el = $("bgArt");
  document.body.classList.toggle("has-bg", kind !== "none");
  if (kind === "none") { el.style.backgroundImage = ""; bgShownVer = -1; return; }
  if (kind === "photo") {
    if (bgShownVer !== state.prefs.bgVer) { bgShownVer = state.prefs.bgVer; const u = Android.backgroundPhoto(); el.style.backgroundImage = u ? `url("${u}")` : ""; }
    return;
  }
  bgShownVer = -1;
  el.style.backgroundImage = bgUrl(kind);
}
function renderBgPick() {
  const cur = state.prefs.bg || "none";
  $("bgPick").innerHTML = BG_LIST.map(([k, n]) => {
    const th = k === "none" ? `<span class="th">∅</span>` : k === "photo" ? `<span class="th">${ic("plus")}</span>` : `<span class="th" style='background-image:${bgUrl(k)}'></span>`;
    return `<button class="${k === cur ? "on" : ""}" data-k="${k}">${th}<span>${n}</span></button>`;
  }).join("");
  $("bgPick").querySelectorAll("button").forEach(b => b.onclick = () => {
    if (b.dataset.k === "photo") { toast("Chọn một ảnh trong máy…"); Android.pickBackground(); }
    else Android.setBackground(b.dataset.k);
  });
}

window.onState = function (s) {
  const first = !state;
  state = s;
  document.documentElement.classList.toggle("dark", !!s.dark);
  document.documentElement.classList.toggle("light", s.dark === false);
  if (first) {
    view = { y: s.today.y, m: s.today.m, sel: s.today.d };
    // Lần mở đầu: khung trời hiện dần, trăng nhô lên, mây trôi vào, các thẻ lần lượt hiện
    document.body.classList.add("intro");
    setTimeout(() => document.body.classList.remove("intro"), 1800);
  }
  render();
  renderSettings();
  applyBackground();
  if (first && s.prefs.zenStart && s.zenLaunch) openZen();
  else if (!$("zen").hidden) renderZen();
  if (!$("famSheet").hidden && famView === "list") famList();
};
function toast(msg) { const t = $("toast"); t.textContent = msg; t.classList.add("show"); clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove("show"), 2800); }
window.onStatus = toast;

function render() {
  if (!state) return;
  renderGrid();
  renderDay();
  renderNotes();
  renderWeather();
  renderGoodDays();
  renderAge();
  renderTet();
  renderXongDat();
  renderTuvi();
}

function renderGrid() {
  const data = JSON.parse(Android.month(view.y, view.m));
  $("monthTitle").textContent = `Tháng ${view.m}, ${view.y}`;
  const grid = $("grid");
  grid.innerHTML = "";
  const t = state.today, n = data.days.length, lead = data.firstWeekday - 1;
  let from = 0, to = lead + n;                     // vị trí ô, 0 = Thứ Hai tuần đầu
  if (weekMode) { from = Math.floor((lead + view.sel - 1) / 7) * 7; to = from + 7; }
  for (let pos = from; pos < to; pos++) {
    const dayNum = pos - lead + 1;
    if (dayNum < 1 || dayNum > n) { grid.appendChild(document.createElement("div")); continue; }
    const d = data.days[dayNum - 1];
    const el = document.createElement("div");
    const isToday = view.y === t.y && view.m === t.m && d.d === t.d;
    el.className = "cell" + (isToday ? " today" : "") + (d.d === view.sel ? " sel" : "") + (pos % 7 === 6 ? " sun" : "") + (d.ev ? " has-ev" : "");
    const special = d.ld === 1 || d.ld === 15;
    const lunarLabel = special ? `${d.ld}/${d.lm}` : String(d.ld);
    el.innerHTML = `<div class="s">${d.d}</div><div class="l${special ? " first" : ""}">${lunarLabel}</div>` +
      (state.prefs.spiritual && d.hd ? `<div class="dot"></div>` : "") +
      (d.ev ? `<div class="evb t-${emoTint(d.ev)}" title="${d.evn || 1} sự kiện">${ic(emoIcon(d.ev))}${d.evn > 1 ? `<i>${d.evn}</i>` : ""}</div>` : "") +
      (d.nt ? `<div class="nt" title="${d.nt} việc chưa xong"></div>` : "");
    el.onclick = () => { view.sel = d.d; render(); };
    grid.appendChild(el);
  }
  const wk = $("weekToggle");
  wk.classList.toggle("up", weekMode);
  wk.setAttribute("aria-label", weekMode ? "Xem cả tháng" : "Thu gọn còn một tuần");
}
function shiftWeek(n) {
  const dt = new Date(view.y, view.m - 1, view.sel + 7 * n);
  view = { y: dt.getFullYear(), m: dt.getMonth() + 1, sel: dt.getDate() };
  render();
}
$("weekToggle").onclick = () => { weekMode = !weekMode; lsSet("weekMode", weekMode ? "1" : "0"); render(); };

function renderDay() {
  const d = JSON.parse(Android.day(view.y, view.m, view.sel));
  curDay = d;
  renderEvents(d);
  $("wd").textContent = d.weekday;
  $("bigDay").textContent = d.d;
  $("solarSub").textContent = `Tháng ${d.m}, ${d.y}`;
  $("hourNow").textContent = d.isToday ? `Giờ ${d.canChiHour} · ${pad(state.hour)}:${pad(state.minute)}` : "";

  $("lunarMain").innerHTML = `<div class="mrow">${ART.moon(d.ld, 50, "dm")}<span class="lday serif">${d.ld}</span><span class="lmon serif">tháng ${monthName(d.lm)}${d.leap ? " nhuận" : ""}</span></div>
    <div class="cy serif">năm ${esc(d.canChiYear)}</div>`;
  heroSky();
  $("quote").innerHTML = d.quote ? `“${esc(d.quote)}”<small>— ${esc(d.quoteKind)} —</small>` : "";
  $("canChiDay").innerHTML = `Ngày <b>${esc(d.canChiDay)}</b>`;
  $("canChiMonth").innerHTML = `Tháng <b>${esc(d.canChiMonth)}</b>`;
  $("tiet").innerHTML = `Tiết <b>${esc(d.tietKhi)}</b>`;
  const badges = [];
  if (state.prefs.spiritual) badges.push(d.hoangDao ? `<span class="badge hd">${ic("sparkle")}Hoàng đạo · ${esc(d.trucThan)}</span>` : `<span class="badge hac">Hắc đạo · ${esc(d.trucThan)}</span>`);
  if (d.tuoi === 1) badges.push(`<span class="badge good">Hợp tuổi bạn</span>`);
  else if (d.tuoi === -1) badges.push(`<span class="badge bad">Xung tuổi bạn</span>`);
  $("hdBadge").innerHTML = badges.join("");

  // Thời tiết của ngày đang chọn
  const w = state.weather, wEl = $("weather"), f = forecastFor(view.y, view.m, view.sel);
  if (!w) {
    wEl.className = "weather empty"; wEl.textContent = "Chưa có dữ liệu thời tiết. Chạm để mở mục Thời tiết.";
  } else if (d.isToday) {
    wEl.className = "weather";
    wEl.innerHTML = `<div class="icon">${ic(emoIcon(w.iconNow))}</div><div><div class="temp">${w.tempNow}°</div>
      <div class="desc">${esc(w.labelNow)} · ${f ? `${f.min}–${f.max}° · mưa ${f.rain}%` : ""}</div></div>
      <div class="city"><b>${esc(w.city)}</b>cảm giác ${w.feelsLike}° · ẩm ${w.humidity}%</div>`;
  } else if (f) {
    wEl.className = "weather";
    wEl.innerHTML = `<div class="icon">${ic(emoIcon(f.icon))}</div><div><div class="temp">${f.min}–${f.max}°</div>
      <div class="desc">${esc(f.text)} · mưa ${f.rain}%</div></div>
      <div class="city"><b>${esc(w.city)}</b>dự báo ${f.label}</div>`;
  } else {
    wEl.className = "weather empty"; wEl.textContent = "Chỉ có dự báo thời tiết cho 7 ngày tới.";
  }

  // Đếm ngược Tết và sự kiện gần nhất
  const cnt = $("count");
  if (d.isToday && state.tet) {
    const tet = state.tet, tdt = new Date(tet.date + "T00:00:00");
    const rows = [tet.daysLeft === 0
      ? `<div class="tet">${ic("gift")}<b>Hôm nay là ${esc(tet.name)}</b>. Chúc mừng năm mới!</div>`
      : `<div class="tet">${ic("gift")}Còn <b>${tet.daysLeft} ngày</b> đến ${esc(tet.name)} · ${DOW_S[tdt.getDay()]} ${tdt.getDate()}/${tdt.getMonth() + 1}/${tdt.getFullYear()}</div>`];
    const u = lastUp[0];
    if (u) rows.push(`<div>${ic(emoIcon(u.icon))}Còn <b>${u.daysLeft} ngày</b>: ${esc(u.title)} · ${esc(u.solarLabel)}</div>`);
    cnt.innerHTML = rows.join(""); cnt.hidden = false;
  } else { cnt.innerHTML = ""; cnt.hidden = true; }

  // Gợi ý
  const tipsBase = d.isToday ? (state.hour >= 18 ? "Gợi ý cho ngày mai" : "Gợi ý hôm nay") : `Ngày ${d.d}/${d.m}`;
  const first = (state.prefs.name || "").trim().split(/\s+/).pop();
  const greet = state.hour < 11 ? "Chào buổi sáng" : state.hour < 13 ? "Chào buổi trưa" : state.hour < 18 ? "Chào buổi chiều" : "Chào buổi tối";
  $("tipsTitle").textContent = d.isToday && first ? `${greet}, ${first}! ${tipsBase}` : tipsBase;
  $("tips").innerHTML = d.tips.map(tp => {
    const sp = tp.short.indexOf(" ");
    const e = tp.short.slice(0, sp), txt = tp.short.slice(sp + 1);
    return `<div class="tip ${tp.kind.toLowerCase()}"><div class="ic t-${emoTint(e)}">${ic(emoIcon(e))}</div><div><div class="t">${esc(txt)}</div><div class="d">${esc(tp.detail)}</div></div></div>`;
  }).join("");

  renderAlmanac(d);

  // Giờ tốt
  $("hoursCard").style.display = state.prefs.spiritual ? "" : "none";
  const nowChi = d.isToday ? d.canChiHour.split(" ")[1] : "";
  $("hoursSum").textContent = d.gioTot.map(g => g.chi).join(", ");
  $("hours").innerHTML = d.gioTot.map(g =>
    `<div class="chip${g.chi === nowChi ? " now" : ""}"><div class="an">${CHI_HAN[g.chi] || ""}</div><div class="n">${g.chi}</div><div class="h">${g.from}h–${g.to}h</div></div>`
  ).join("");

  // Đầu mục Xem ngày: ngày đang xem, nút sang ngày trước / sau
  $("xemTitle").textContent = `${d.weekday}, ${d.d}/${d.m}/${d.y}`;
  $("xemSub").textContent = `${d.ld}/${d.lm}${d.leap ? " nhuận" : ""} âm lịch · ngày ${d.canChiDay}`;
  $("xemToday").hidden = !!d.isToday;
}

/** Màu trời của thẻ ngày theo giờ hiện tại và mặt trời mọc/lặn; chỉ vẽ lại khi đổi thời điểm. */
let lastSky = "";
function heroSky() {
  const w = state.weather, t = w && w.daily && w.daily[0];
  const name = ART.sky(state.hour, state.minute, t && t.sunrise, t && t.sunset, !!(w && w.codeNow >= 51));
  if (name === lastSky) return;
  lastSky = name;
  const card = $("dayCard");
  card.className = card.className.replace(/\bsky-\w+/, "sky-" + name);
  $("skyArt").innerHTML = name === "night" ? ART.stars(400, 170, 34, 3) : name === "rain" ? "" : `<div class="glow"></div>`;
  $("cloudBand").innerHTML = ART.cloud(190, .85, "c1") + ART.cloud(150, .62, "c2");
}

function renderWeather() {
  const w = state.weather, card = $("wxCard"), has = !!(w && w.daily.length);
  $("wxCity").textContent = w ? w.city : (state.prefs.city || "Chưa chọn thành phố");
  $("wxEmpty").hidden = has;
  if (!has) { card.style.display = "none"; return; }
  card.style.display = "";
  $("wxUpd").textContent = `Cập nhật lúc ${w.updated} · nguồn Open-Meteo`;

  // Chip thành phố
  const cities = state.cities || [], chips = $("cityChips");
  chips.hidden = cities.length < 2;
  chips.innerHTML = cities.map(c => `<button data-n="${esc(c.name)}" class="${c.name === w.city ? "on" : ""}">${esc(c.name)}</button>`).join("");
  chips.querySelectorAll("button").forEach(b => b.onclick = () => {
    if (b.dataset.n === w.city) return;
    toast(`Chuyển sang ${b.dataset.n}…`); Android.setActiveCity(b.dataset.n);
  });

  const selKey = dateKey(view.y, view.m, view.sel);
  const days = w.daily.slice(0, 7);
  $("wxTop").innerHTML = days.map(d =>
    `<div class="d${d.date === selKey ? " sel" : ""}" data-date="${esc(d.date)}"><div class="lb">${esc(d.label)}</div><div class="ic t-${emoTint(d.icon)}">${ic(emoIcon(d.icon))}</div><div class="v">${esc(d.max)}°</div></div>`).join("");
  $("wxBottom").innerHTML = days.map(d =>
    `<div class="d${d.date === selKey ? " sel" : ""}" data-date="${esc(d.date)}"><div class="v min">${esc(d.min)}°</div><div class="rain">${d.rain >= 20 ? ic("droplets") + esc(d.rain) + "%" : "&nbsp;"}</div></div>`).join("");
  document.querySelectorAll(".wx-days .d").forEach(el => el.onclick = () => {
    const [y, m, d] = el.dataset.date.split("-").map(Number);
    view = { y, m, sel: d }; render();
  });

  // Đường nhiệt độ cao nhất / thấp nhất
  const svg = $("wxCurve"), W = svg.clientWidth || 380, H = 96, padY = 10;
  svg.setAttribute("viewBox", `0 0 ${W} ${H}`);
  const all = days.flatMap(d => [d.max, d.min]);
  const lo = Math.min(...all), hi = Math.max(...all), span = Math.max(hi - lo, 4);
  const x = i => (i + 0.5) * W / days.length;
  const y = v => H - padY - (v - lo) / span * (H - 2 * padY);
  const ptsMax = days.map((d, i) => ({ x: x(i), y: y(d.max) }));
  const ptsMin = days.map((d, i) => ({ x: x(i), y: y(d.min) }));
  const dots = (pts, cls) => pts.map(p => `<circle class="${cls}" cx="${p.x}" cy="${p.y}" r="4"/>`).join("");
  svg.innerHTML = `<path class="mx" d="${smooth(ptsMax)}"/><path class="mn" d="${smooth(ptsMin)}"/>${dots(ptsMax, "mx")}${dots(ptsMin, "mn")}`;

  // Ô chỉ số
  const tile = (k, v, unit) => `<div class="tile"><div class="k">${k}</div><div class="v">${v}${unit ? `<small>${unit}</small>` : ""}</div></div>`;
  $("tiles").innerHTML =
    tile(ic("sun") + "UV", w.uv, w.uvLabel) +
    tile(ic("thermometer") + "Cảm giác", w.feelsLike + "°") +
    tile(ic("droplets") + "Độ ẩm", w.humidity, "%") +
    tile(ic("wind") + esc(w.windDir), "Cấp " + w.beaufort, w.windKmh + " km/h") +
    tile(ic("gauge") + "Áp suất", w.pressure, "hPa") +
    tile(ic("eye") + "Tầm nhìn", w.visibility, "km");

  // Bình minh / hoàng hôn
  const toMin = s => { const [h, m] = s.split(":").map(Number); return h * 60 + m; };
  const now = state.hour * 60 + state.minute, t0 = days[0], t1 = days[1] || days[0];
  const rise = toMin(t0.sunrise), set = toMin(t0.sunset);
  let left, right, start, end, mark;
  if (now >= rise && now < set) { left = ["Bình minh", t0.sunrise]; right = ["Hoàng hôn", t0.sunset]; start = rise; end = set; mark = ic("sun"); }
  else if (now >= set) { left = ["Hoàng hôn", t0.sunset]; right = ["Bình minh", t1.sunrise]; start = set; end = toMin(t1.sunrise) + 1440; mark = ic("moon"); }
  else { left = ["Hoàng hôn", t0.sunset]; right = ["Bình minh", t0.sunrise]; start = set - 1440; end = rise; mark = ic("moon"); }
  const pct = Math.max(0, Math.min(100, (now - start) / (end - start) * 100));
  $("sun").innerHTML = `<div class="lbl"><span>${left[0]}<b>${esc(left[1])}</b></span><span style="text-align:right">${right[0]}<b>${esc(right[1])}</b></span></div>
    <div class="track"><div class="fill" style="width:${pct}%"></div><div class="mk" style="left:${pct}%">${mark}</div></div>`;
}

function smooth(pts) {
  if (pts.length < 2) return "";
  let d = `M${pts[0].x},${pts[0].y}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i], p1 = pts[i], p2 = pts[i + 1], p3 = pts[i + 2] || p2;
    d += ` C${p1.x + (p2.x - p0.x) / 6},${p1.y + (p2.y - p0.y) / 6} ${p2.x - (p3.x - p1.x) / 6},${p2.y - (p3.y - p1.y) / 6} ${p2.x},${p2.y}`;
  }
  return d;
}

$("prev").onclick = () => { if (weekMode) return shiftWeek(-1); view.m--; if (view.m < 1) { view.m = 12; view.y--; } view.sel = 1; render(); };
$("next").onclick = () => { if (weekMode) return shiftWeek(1); view.m++; if (view.m > 12) { view.m = 1; view.y++; } view.sel = 1; render(); };
$("todayBtn").onclick = () => { view = { y: state.today.y, m: state.today.m, sel: state.today.d }; render(); };

// ---- Thẻ thu gọn được ----
Object.keys(COL_DEFAULT).forEach(id => {
  $(id).classList.toggle("collapsed", isCollapsed(id));
  $(id).querySelector("h2").onclick = () => {
    const c = !isCollapsed(id);
    lsSet("col_" + id, c ? "1" : "0");
    $(id).classList.toggle("collapsed", c);
    if (!c && id === "gdCard") renderGoodDays();
  };
});
/** Đóng mọi lớp đang mở (Zen, các bảng, Cài đặt) để thấy nội dung chính. */
function closeAllLayers() {
  for (let i = 0; i < 12 && window.closeSettingsIfOpen(); i++) { /* đóng lần lượt từ lớp trên cùng */ }
}
window.openToday = function () {
  closeAllLayers();
  if (!state) return;
  view = { y: state.today.y, m: state.today.m, sel: state.today.d };
  render();
  setTab("lich");
  window.scrollTo({ top: 0, behavior: "smooth" });
};
/** Mở mục chứa thẻ (widget chạm vùng thời tiết, sự kiện) rồi cuộn tới thẻ; thẻ ở đầu mục thì giữ cả đầu mục. */
window.scrollToCard = function (id) {
  closeAllLayers();
  const el = $(id); if (!el) return;
  const t = el.closest("[data-tab]");
  setTab(t ? t.dataset.tab : "lich");
  if (el.style.display === "none") return;
  const y = el.getBoundingClientRect().top + window.scrollY - 12;
  window.scrollTo({ top: y < 160 ? 0 : y, behavior: "smooth" });
};
// ---- Xem ngày, chọn ngày tốt ----
const TASKS = [["cuoi","Cưới hỏi","gem"],["khaitruong","Khai trương","scissors"],["khoicong","Khởi công","construction"],["nhaptrach","Nhập trạch","house"],["hopdong","Ký hợp đồng","handshake"],["muasam","Mua xe, tài sản","car"],["xuathanh","Xuất hành","plane"],["cungle","Cúng lễ","flame"]];
let curTask = "cuoi";

function renderAlmanac(d) {
  const p = state.prefs;
  const show = p.showAlmanac || p.showHuong || p.showLucDieu;
  $("almCard").style.display = show ? "" : "none";
  $("almSum").textContent = `Trực ${d.truc} · sao ${d.tu} · ${d.napAm} · xung ${d.xung}`;
  if (!show) return;
  $("almKv").innerHTML = `<span>Trực <b>${esc(d.truc)}</b></span><span>Ngũ hành <b>${esc(d.napAm)}</b></span><span>Tuổi xung <b>${esc(d.xung)}</b></span>`;
  $("almTruc").style.display = p.showAlmanac ? "" : "none";
  $("almNen").innerHTML = d.trucNen.map(x => `<span>${esc(x)}</span>`).join("");
  $("almKy").innerHTML = d.trucKy.map(x => `<span>${esc(x)}</span>`).join("");
  $("almTu").innerHTML = `<span class="nm">Sao ${esc(d.tuFull)}</span> · <span class="${d.tuGood ? "good" : "bad"}">${d.tuGood ? "sao tốt" : "sao xấu"}</span><br>` +
    (d.tuNen ? `Nên ${esc(d.tuNen)}. ` : "Không hợp việc lớn. ") +
    (d.tuKy ? `Kỵ ${esc(d.tuKy)}.` : "Không kỵ việc gì.");
  // Sao Ngọc Hạp: chạm vào tên sao để xem ý nghĩa
  const nh = d.ngocHap || [];
  const chip = (s, i) => `<button class="${s.good ? "g" : "b"}" data-i="${i}">${esc(s.name)}</button>`;
  $("nhGood").innerHTML = nh.map(chip).filter((_, i) => nh[i].good).join("") || `<span>Không có</span>`;
  $("nhBad").innerHTML = nh.map(chip).filter((_, i) => !nh[i].good).join("") || `<span>Không có</span>`;
  $("nhDesc").textContent = nh.length ? "Chạm vào tên sao để xem ý nghĩa." : "";
  document.querySelectorAll(".chips2.nh button").forEach(b => b.onclick = () => {
    const s = nh[+b.dataset.i];
    document.querySelectorAll(".chips2.nh button").forEach(x => x.classList.toggle("on", x === b));
    $("nhDesc").innerHTML = `<span class="nm">${esc(s.name)}</span> · <span class="${s.good ? "good" : "bad"}">${s.good ? "sao tốt" : "sao xấu"}</span>: ${esc(s.desc)}.`;
  });
  $("almHuongWrap").style.display = p.showHuong ? "" : "none";
  $("almHuong").innerHTML = `<span>Hỷ thần <b>${esc(d.hyThan)}</b></span><span>Tài thần <b>${esc(d.taiThan)}</b></span>`;
  $("almLdWrap").style.display = p.showLucDieu ? "" : "none";
  $("ldDay").textContent = d.lucDieuDay;
  $("almLd").innerHTML = d.lucDieu.map(h =>
    `<div class="ld"><div class="n${h.good ? " good" : ""}">${esc(h.name)}</div><div><div class="h">${esc(h.hours)}</div><div class="ds">${esc(h.desc)}</div></div></div>`).join("");
}

function renderGoodDays() {
  const on = !!state.prefs.showGoodDays;
  $("gdCard").style.display = on ? "" : "none";
  $("gdSum").textContent = "Cưới hỏi, khai trương, khởi công… 60 ngày tới";
  if (!on || $("gdCard").classList.contains("collapsed")) return;
  $("tasks").innerHTML = TASKS.map(([k, n, i]) => `<button data-k="${k}" class="${k === curTask ? "on" : ""}">${ic(i)}${n}</button>`).join("");
  $("tasks").querySelectorAll("button").forEach(b => b.onclick = () => { curTask = b.dataset.k; renderGoodDays(); });
  const list = JSON.parse(Android.goodDays(curTask, 60));
  $("gdList").innerHTML = list.length ? list.map(g =>
    `<button class="gd" data-date="${g.date}"><div class="dt"><b>${g.d}</b><small>${esc(g.dow)} ${g.m}/${String(g.y).slice(2)}</small></div>
     <div class="b"><div class="t">${esc(g.canChi)} · ${esc(g.lunar)}</div><div class="r">${g.reasons.map(esc).join(" · ")}</div></div>
     <div class="sc" aria-label="${Math.min(g.score, 5)} sao">${ic("star").repeat(Math.min(g.score, 5))}</div></button>`).join("")
    : `<div class="ev-empty">Không có ngày nổi bật cho việc này trong 60 ngày tới.</div>`;
  $("gdList").querySelectorAll(".gd").forEach(b => b.onclick = () => {
    const [y, m, d] = b.dataset.date.split("-").map(Number);
    view = { y, m, sel: d }; render(); window.scrollTo({ top: 0, behavior: "smooth" });
  });
}

// ---- Xem tuổi: Kim Lâu, Hoang Ốc, Tam Tai, Thái Tuế ----
const CAN_N = ["Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"];
const CHI_N = ["Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"];
let ageYear = 0, agePicked = false;   // agePicked: người dùng tự chọn năm khác năm trong hồ sơ
function renderAge() {
  const sel = $("ageYear");
  if (!agePicked) ageYear = state.prefs.birthYear || 0;
  if (!sel.options.length) {
    const o0 = document.createElement("option"); o0.value = 0; o0.textContent = "Chọn năm"; sel.appendChild(o0);
    for (let y = state.today.y; y >= 1930; y--) {
      const o = document.createElement("option"); o.value = y; o.textContent = `${y} · ${CAN_N[(y + 6) % 10]} ${CHI_N[(y + 8) % 12]}`; sel.appendChild(o);
    }
  }
  sel.value = String(ageYear);
  if (!ageYear) {
    $("ageSum").textContent = "Kim Lâu, Hoang Ốc, Tam Tai theo năm sinh";
    $("ageList").innerHTML = `<div class="ev-empty">Chọn năm sinh âm lịch ở trên, hoặc nhập ngày sinh trong <b>Cài đặt → Hồ sơ</b> để app tự điền.</div>`;
    return;
  }
  const list = JSON.parse(Android.tuoi(ageYear));
  const pham = c => [c.kimLau && "Kim Lâu", c.hoangOcBad && "Hoang Ốc", c.tamTai && "Tam Tai"].filter(Boolean);
  const now = list[0];
  $("ageSum").textContent = now ? `${now.year}: ${now.tuoi} tuổi · ${pham(now).length ? "phạm " + pham(now).join(", ") : "không phạm Kim Lâu, Hoang Ốc, Tam Tai"}` : "";
  $("ageList").innerHTML = list.map(c => {
    const chips = [
      c.kimLau ? `<span class="b">${esc(c.kimLau)}</span>` : `<span class="g">Không phạm Kim Lâu</span>`,
      c.hoangOc ? `<span class="${c.hoangOcBad ? "b" : "g"}">Hoang Ốc: ${esc(c.hoangOc)}</span>` : "",
      c.tamTai ? `<span class="b">${esc(c.tamTai)}</span>` : "",
      c.thaiTue ? `<span>${esc(c.thaiTue)}</span>` : "",
    ].join("");
    return `<div class="yr"><div class="hd"><span><b>${c.year} ${esc(c.canChi)}</b> <small>· ${c.tuoi} tuổi mụ</small></span>
      <span class="tag ${c.badForHouse ? "bad" : "good"}">${c.badForHouse ? "Không nên làm nhà" : "Hợp làm nhà"}</span></div>
      <div class="chips2">${chips}</div></div>`;
  }).join("");
}
$("ageYear").onchange = e => { ageYear = +e.target.value; agePicked = ageYear !== (state.prefs.birthYear || 0); renderAge(); };

// ---- Ghi chú theo ngày ----
function renderNotes() {
  const list = JSON.parse(Android.notes(view.y, view.m, view.sel));
  const t = state.today, isToday = view.y === t.y && view.m === t.m && view.sel === t.d;
  $("noteTitle").textContent = isToday ? "Ghi chú hôm nay" : `Ghi chú ngày ${view.sel}/${view.m}`;
  const open = list.filter(n => !n.done).length;
  $("noteSum").textContent = list.length ? (open ? `còn ${open}/${list.length} việc` : "đã xong hết") : "";
  $("noteList").innerHTML = list.length ? list.map(n => `<div class="note${n.done ? " done" : ""}">
      <button class="ck" data-id="${esc(n.id)}" aria-label="${n.done ? "Bỏ đánh dấu xong" : "Đánh dấu xong"}">${n.done ? ic("check") : ""}</button>
      <span class="tx">${esc(n.text)}</span><button class="del" data-id="${esc(n.id)}" aria-label="Xóa ghi chú">${ic("x")}</button></div>`).join("")
    : `<div class="ev-empty">Chưa có ghi chú cho ngày này. Gõ việc cần làm ở trên rồi bấm Thêm.</div>`;
  const redo = () => { renderNotes(); renderGrid(); };
  $("noteList").querySelectorAll(".ck").forEach(b => b.onclick = () => { Android.toggleNote(view.y, view.m, view.sel, b.dataset.id); redo(); });
  $("noteList").querySelectorAll(".del").forEach(b => b.onclick = () => { Android.deleteNote(view.y, view.m, view.sel, b.dataset.id); redo(); });
}
function addNote() {
  const v = $("noteInput").value.trim();
  if (!v) { $("noteInput").focus(); return; }
  Android.addNote(view.y, view.m, view.sel, v);
  $("noteInput").value = "";
  renderNotes(); renderGrid();
}
$("noteAdd").onclick = addNote;
$("noteInput").onkeydown = e => { if (e.key === "Enter") { e.preventDefault(); addNote(); } };

// ---- Tết & tử vi ----
let cdTimer = 0, xdPicked = false, xdOwner = 0;
function renderTet() {
  const tet = state.tet; if (!tet) return;
  const t = new Date(tet.date + "T00:00:00"), ms = Math.max(0, t - new Date());
  const d = Math.floor(ms / 864e5), h = Math.floor(ms % 864e5 / 36e5), mi = Math.floor(ms % 36e5 / 6e4);
  $("tetSum").textContent = tet.daysLeft > 0 ? `Còn ${tet.daysLeft} ngày đến ${tet.name}` : tet.name;
  $("tetCd").innerHTML = ms > 0 ? [[d, "ngày"], [h, "giờ"], [mi, "phút"]].map(([v, u]) => `<div class="u"><b>${v}</b><small>${u}</small></div>`).join("")
    : `<div class="u"><b>${ic("party-popper")}</b><small>Chúc mừng năm mới!</small></div>`;
  $("tetWhen").textContent = `${tet.name} · ${DOW_S[t.getDay()]} ${t.getDate()}/${t.getMonth() + 1}/${t.getFullYear()}`;
  clearTimeout(cdTimer); cdTimer = setTimeout(renderTet, 30000);
}
function renderXongDat() {
  const sel = $("xdOwner");
  if (!xdPicked) xdOwner = state.prefs.birthYear || 0;
  if (!sel.options.length) {
    const o0 = document.createElement("option"); o0.value = 0; o0.textContent = "Chọn năm"; sel.appendChild(o0);
    for (let y = state.today.y; y >= 1930; y--) { const o = document.createElement("option"); o.value = y; o.textContent = `${y} · ${CAN_N[(y + 6) % 10]} ${CHI_N[(y + 8) % 12]}`; sel.appendChild(o); }
  }
  sel.value = String(xdOwner);
  if (!xdOwner) { $("xdList").innerHTML = `<div class="ev-empty">Chọn năm sinh gia chủ, hoặc nhập ngày sinh trong <b>Cài đặt → Hồ sơ</b>.</div>`; return; }
  const r = JSON.parse(Android.xongDat(xdOwner));
  $("xdTitle").textContent = `Tuổi xông đất Tết ${r.canChi}`;
  const rows = r.list.map(c => `<div class="xd"><div class="yr"><b>${c.year}</b><small>${esc(c.canChi)}</small></div>
      <div class="why">${c.reasons.map(esc).join(" · ") || "Không xung khắc"}${c.warnings.length ? `<span class="warn">Lưu ý: ${c.warnings.map(esc).join(", ")}</span>` : ""}</div>
      <div class="sc ${c.score >= 75 ? "g" : c.score >= 62 ? "m" : "b"}"><b>${c.score}</b><small>${esc(c.label)}</small></div></div>`).join("");
  $("xdList").innerHTML = (rows || `<div class="ev-empty">Chưa tìm được tuổi phù hợp.</div>`)
    + (r.avoid && r.avoid.length ? `<div class="xd-avoid">Tránh mời tuổi ${r.avoid.map(esc).join(", ")}: xung với gia chủ hoặc với năm ${esc(r.canChi)}.</div>` : "");
}
$("xdOwner").onchange = e => { xdOwner = +e.target.value; xdPicked = xdOwner !== (state.prefs.birthYear || 0); renderXongDat(); };
function renderTuvi() {
  const raw = Android.tuvi();
  if (raw === "null") { $("tuvi").innerHTML = `<div class="ev-empty">Nhập ngày sinh trong <b>Cài đặt → Hồ sơ</b> để xem cung hoàng đạo và mệnh.</div>`; return; }
  const t = JSON.parse(raw);
  $("tuvi").innerHTML = `<div class="tuvi-row"><span class="big zod">${esc(t.symbol)}︎</span><div><b>${esc(t.sign)}</b> <span class="muted small">${esc(t.range)} · nhóm ${esc(t.element)}</span><p>${esc(t.traits)}</p></div></div>
    <div class="tuvi-row"><span class="big han">${CHI_HAN[t.canChi.split(" ")[1]] || ""}</span><div><b>Tuổi ${esc(t.canChi)}</b> <span class="muted small">con ${esc(t.animal)}</span><p>Mệnh ${esc(t.menh)} (hành ${esc(t.hanh)})</p></div></div>`;
}

// ---- Văn khấn cổ truyền ----
const SO_DEM = ["", "Một", "Hai", "Ba", "Bốn", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười"];
const THANG_AM = ["", "Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười", "Mười một", "Chạp"];
/** Ngày âm đọc bằng chữ như trong văn khấn: mùng Một, Mười hai, Rằm, Hai mươi mốt... */
function ngayAmChu(d) {
  if (d <= 10) return "mùng " + SO_DEM[d];
  if (d === 15) return "Rằm";
  if (d < 20) return "Mười " + (d === 11 ? "một" : SO_DEM[d - 10].toLowerCase());
  if (d === 20) return "Hai mươi";
  if (d < 30) { const u = d - 20; return "Hai mươi " + (u === 1 ? "mốt" : u === 4 ? "tư" : u === 5 ? "lăm" : SO_DEM[u].toLowerCase()); }
  return "Ba mươi";
}
/** Điền ngày âm (theo ngày đang chọn), tên tín chủ, năm sinh, tuổi, năm cũ / mới; chỗ còn lại để "……" cho người dùng tự điền. */
function vkFill(text) {
  const d = curDay, p = state.prefs, cc = y => `${CAN_N[(y + 6) % 10]} ${CHI_N[(y + 8) % 12]}`;
  const map = {
    ngay_am: `${ngayAmChu(d.ld)} tháng ${THANG_AM[d.lm]}${d.leap ? " nhuận" : ""} năm ${d.canChiYear}`,
    tin_chu: p.name || "", nam_moi: cc(d.ly + 1), nam_cu: cc(d.ly),
    nam_sinh: p.birthYear ? cc(p.birthYear) : "", tuoi: p.birthYear ? String(d.ly + 1 - p.birthYear + 1) : "",
  };
  return esc(text).replace(/\{(\w+)\}/g, (m, k) => `<b class="fill">${map[k] ? esc(map[k]) : "……"}</b>`);
}
function openVk() { $("vkBg").hidden = false; $("vkSheet").hidden = false; vkList(); }
function closeVk() { $("vkBg").hidden = true; $("vkSheet").hidden = true; }
function vkList() {
  const all = window.VAN_KHAN || [];
  $("vkTitle").textContent = "Văn khấn cổ truyền"; $("vkBack").hidden = true;
  const groups = [...new Set(all.map(v => v.group))];
  $("vkBody").innerHTML = all.length ? groups.map(g => `<div class="vk-group">${esc(g)}</div>` + all.filter(v => v.group === g)
      .map(v => `<button class="vk-item" data-id="${esc(v.id)}"><b>${esc(v.title)}</b><small>${esc(v.when)}</small></button>`).join("")).join("")
    : `<div class="ev-empty">Chưa có bài văn khấn.</div>`;
  $("vkBody").querySelectorAll(".vk-item").forEach(b => b.onclick = () => vkRead(b.dataset.id));
  $("vkSheet").scrollTop = 0;
}
function vkRead(id) {
  const v = (window.VAN_KHAN || []).find(x => x.id === id); if (!v) return;
  $("vkTitle").textContent = v.title; $("vkBack").hidden = false;
  $("vkBody").innerHTML = `<div class="vk-meta"><b>Khi nào dùng:</b> ${esc(v.when)}<br><b>Lễ vật:</b> ${esc(v.offerings)}<br>
    Ngày âm lấy theo ngày đang chọn trên lịch (${curDay.ld}/${curDay.lm}); chỗ <b class="fill">……</b> là phần bạn tự điền khi khấn.</div>
    <div class="vk-text">${vkFill(v.text)}</div>`;
  $("vkSheet").scrollTop = 0;
}
$("vkRow").onclick = openVk; $("vkClose").onclick = closeVk; $("vkBg").onclick = closeVk; $("vkBack").onclick = vkList;

// ---- Sự kiện ----
const ICONS = [["📌", "pin", "Ghim"], ["🕯️", "flame", "Giỗ"], ["🎂", "cake", "Sinh nhật"], ["💍", "gem", "Cưới hỏi"], ["🙏", "hand-heart", "Lễ, cúng"],
  ["🎉", "party-popper", "Liên hoan"], ["💼", "briefcase", "Công việc"], ["🏥", "hospital", "Khám bệnh"], ["✈️", "plane", "Đi xa"],
  ["📚", "book-open", "Học tập"], ["🏠", "house", "Nhà cửa"], ["❤️", "heart", "Kỷ niệm"]];
const DOW = ["Chủ nhật","Thứ Hai","Thứ Ba","Thứ Tư","Thứ Năm","Thứ Sáu","Thứ Bảy"];
let curDay = null, editing = null, lastUp = [];

function evRow(e, extra) {
  const meta = [e.dateLabel, e.time, e.note].filter(Boolean).map(esc).join(" · ");
  return `<button class="ev" data-id="${esc(e.id)}"><div class="ic t-${emoTint(e.icon)}">${ic(emoIcon(e.icon))}</div><div class="b"><div class="t">${esc(e.title)}</div><div class="d">${meta}</div></div>${extra || ""}</button>`;
}
function renderEvents(d) {
  $("evTitle").textContent = d.isToday ? "Sự kiện hôm nay" : `Sự kiện ngày ${d.d}/${d.m}`;
  const list = d.events || [];
  $("evList").innerHTML = list.length ? list.map(e => evRow(e)).join("")
    : `<div class="ev-empty">Chưa có sự kiện. Bấm <b>+ Thêm</b> để ghi giỗ, sinh nhật, lịch hẹn theo âm hoặc dương lịch. App sẽ nhắc trên widget khi gần đến ngày.</div>`;
  $("evList").querySelectorAll(".ev").forEach(b => b.onclick = () => openSheet(list.find(e => e.id === b.dataset.id)));
  let up = [];
  if (d.isToday) up = JSON.parse(Android.upcoming(30)).filter(u => u.daysLeft > 0);
  lastUp = up;
  $("evUpcoming").innerHTML = up.length
    ? `<div class="sub-h">Sắp tới</div>` + up.map(u => evRow({ ...u, dateLabel: `${u.solarLabel} · ${u.dateLabel}` }, `<span class="left">còn ${u.daysLeft} ngày</span>`)).join("")
    : "";
  $("evUpcoming").querySelectorAll(".ev").forEach(b => b.onclick = () => openSheet(up.find(u => u.id === b.dataset.id)));
}
function fillSelect(sel, from, to, val, prefix) {
  sel.innerHTML = "";
  for (let i = from; i <= to; i++) { const o = document.createElement("option"); o.value = i; o.textContent = (prefix || "") + i; if (i === val) o.selected = true; sel.appendChild(o); }
}
/** Ô chọn biểu tượng sự kiện: hiện icon nét, giá trị lưu vẫn là emoji (widget, thông báo dùng chung); biểu tượng lạ (lịch Google, sổ giỗ) được giữ nguyên. */
function setIcon(cur) {
  const box = $("evIcons"), k = emoKey(cur);
  box.dataset.orig = cur || "📌";
  box.innerHTML = ICONS.map(([e, n, l]) => `<button data-i="${e}" class="t-${emoTint(e)}${emoKey(e) === k ? " on" : ""}" aria-label="${l}" title="${l}">${ic(n)}</button>`).join("");
  box.querySelectorAll("button").forEach(b => b.onclick = () => setIcon(b.dataset.i));
}
function curIcon() { const b = $("evIcons").querySelector(".on"); return b ? b.dataset.i : ($("evIcons").dataset.orig || "📌"); }
function isLunar() { const b = $("evCal").querySelector(".on"); return !b || b.dataset.v === "lunar"; }
function setCal(lunar, e) {
  document.querySelectorAll("#evCal button").forEach(b => b.classList.toggle("on", (b.dataset.v === "lunar") === lunar));
  const d = e ? e.day : (lunar ? curDay.ld : curDay.d);
  const m = e ? e.month : (lunar ? curDay.lm : curDay.m);
  const y = e && e.year ? e.year : (lunar ? curDay.ly : curDay.y);
  fillSelect($("evDay"), 1, lunar ? 30 : 31, d);
  fillSelect($("evMonth"), 1, 12, m, "Tháng ");
  fillSelect($("evYear"), state.today.y - 10, state.today.y + 10, y);
  updateHint();
}
function toggleYear() { $("evYear").style.display = $("evYearly").checked ? "none" : ""; updateHint(); }
function updateHint() {
  const d = +$("evDay").value, m = +$("evMonth").value, yearly = $("evYearly").checked;
  const y = yearly ? state.today.y : +$("evYear").value;
  if (!isLunar()) { $("evHint").textContent = yearly ? `Lặp lại ngày ${d}/${m} dương lịch hằng năm.` : ""; return; }
  const s = Android.lunarToSolar(d, m, y);
  if (!s) { $("evHint").textContent = `Năm ${y} tháng này chỉ có 29 ngày, sẽ tính vào ngày 29.`; return; }
  const dt = new Date(s + "T00:00:00");
  $("evHint").textContent = `${yearly ? "Năm nay" : "Năm " + y} rơi vào ${DOW[dt.getDay()]}, ${dt.getDate()}/${dt.getMonth() + 1}/${dt.getFullYear()} dương lịch.`;
}
function openSheet(e) {
  if (e && e.id === "profile-birthday") return openProfile();   // sinh nhật lấy từ Hồ sơ
  if (e && String(e.id).startsWith("fam-")) return openFamily(String(e.id).split("-")[1]);   // giỗ trong sổ gia đình
  editing = e || null;
  $("sheetTitle").textContent = e ? "Sửa sự kiện" : "Thêm sự kiện";
  $("evDelete").hidden = !e; $("evDelete").textContent = "Xóa"; delete $("evDelete").dataset.arm;
  $("evName").value = e ? e.title : "";
  setIcon(e ? e.icon : "📌");
  $("evYearly").checked = e ? e.yearly : true;
  setCal(e ? e.lunar : true, e);
  $("evTime").value = e ? e.time : "";
  const rem = String(e ? e.remind : 1), rsel = $("evRemind");
  rsel.querySelectorAll("option[data-extra]").forEach(o => o.remove());
  if (![...rsel.options].some(o => o.value === rem)) {
    const o = document.createElement("option"); o.value = rem; o.textContent = rem + " ngày"; o.dataset.extra = "1"; rsel.appendChild(o);
  }
  rsel.value = rem;
  $("evNote").value = e ? e.note : "";
  toggleYear();
  $("sheetBg").hidden = false; $("sheet").hidden = false;
  if (!e) $("evName").focus();
}
function closeSheet() { $("sheet").hidden = true; $("sheetBg").hidden = true; editing = null; }
$("addEvent").onclick = () => openSheet(null);
$("evCancel").onclick = closeSheet; $("sheetBg").onclick = closeSheet;
$("evCal").onclick = ev => { const b = ev.target.closest("button"); if (b) setCal(b.dataset.v === "lunar", null); };
["evDay", "evMonth", "evYear"].forEach(id => $(id).onchange = updateHint);
$("evYearly").onchange = toggleYear;
$("evSave").onclick = () => {
  const title = $("evName").value.trim();
  if (!title) { toast("Nhập tên sự kiện trước đã"); $("evName").focus(); return; }
  const yearly = $("evYearly").checked;
  const ev = { id: editing ? editing.id : "", title, icon: curIcon(), lunar: isLunar(), day: +$("evDay").value, month: +$("evMonth").value,
    year: yearly ? null : +$("evYear").value, yearly, time: $("evTime").value || "", note: $("evNote").value.trim(), remind: +$("evRemind").value };
  Android.saveEvent(JSON.stringify(ev));
  const wasEditing = !!editing;
  closeSheet(); render();
  toast(wasEditing ? "Đã lưu thay đổi" : "Đã thêm sự kiện");
};
$("evDelete").onclick = () => {
  if (!editing) return;
  if (!$("evDelete").dataset.arm) { $("evDelete").dataset.arm = "1"; $("evDelete").textContent = "Xóa thật?"; return; }
  Android.deleteEvent(editing.id); closeSheet(); render(); toast("Đã xóa sự kiện");
};

// ---- Công cụ: đổi lịch, chia sẻ ----
let convMode = "s2l";
function openConv() {
  const t = state.today;
  fillSelect($("convDay"), 1, 31, t.d); fillSelect($("convMonth"), 1, 12, t.m, "Tháng "); fillSelect($("convYear"), t.y - 80, t.y + 20, t.y);
  setConvMode("s2l");
  $("convBg").hidden = false; $("convSheet").hidden = false;
}
function closeConv() { $("convBg").hidden = true; $("convSheet").hidden = true; }
function setConvMode(m) {
  convMode = m;
  document.querySelectorAll("#convMode button").forEach(b => b.classList.toggle("on", b.dataset.v === m));
  $("convLbl").textContent = m === "s2l" ? "Ngày dương lịch" : "Ngày âm lịch";
  const max = m === "s2l" ? 31 : 30, cur = +$("convDay").value || 1;
  fillSelect($("convDay"), 1, max, Math.min(cur, max));
  runConv();
}
function convOpenDay(y, m, d) { closeConv(); view = { y, m, sel: d }; render(); setTab("lich"); window.scrollTo({ top: 0, behavior: "smooth" }); }
function runConv() {
  const d = +$("convDay").value, m = +$("convMonth").value, y = +$("convYear").value, res = $("convRes");
  const hd = r => r.hoangDao ? "Hoàng đạo" : "Hắc đạo";
  if (convMode === "s2l") {
    if (new Date(y, m - 1, d).getDate() !== d) { res.innerHTML = `<div class="sub">Tháng ${m}/${y} không có ngày ${d}.</div>`; return; }
    const r = JSON.parse(Android.day(y, m, d));
    res.innerHTML = `<div class="big">${ART.moon(r.ld, 30, "cv")} ${r.ld} tháng ${monthName(r.lm)}${r.leap ? " (nhuận)" : ""}, ${esc(r.canChiYear)}</div>
      <div class="sub">${esc(r.weekday)} ${d}/${m}/${y} · ngày ${esc(r.canChiDay)}, tháng ${esc(r.canChiMonth)} · tiết ${esc(r.tietKhi)} · ${hd(r)}</div>
      <button class="pill" data-d="${y}-${m}-${d}">Mở ngày này trên lịch</button>`;
  } else {
    const s = Android.lunarToSolar(d, m, y);
    if (!s) { res.innerHTML = `<div class="sub">Năm ${y} không có ngày ${d}/${m} âm lịch (tháng thiếu chỉ có 29 ngày).</div>`; return; }
    const [sy, sm, sd] = s.split("-").map(Number);
    const r = JSON.parse(Android.day(sy, sm, sd));
    res.innerHTML = `<div class="big">${esc(r.weekday)}, ${sd}/${sm}/${sy}</div>
      <div class="sub">${ART.moon(r.ld, 22, "cv")} ${d}/${m} âm năm ${esc(r.canChiYear)} · ngày ${esc(r.canChiDay)} · tiết ${esc(r.tietKhi)} · ${hd(r)}</div>
      <button class="pill" data-d="${sy}-${sm}-${sd}">Mở ngày này trên lịch</button>`;
  }
  res.querySelectorAll("button").forEach(b => b.onclick = () => convOpenDay(...b.dataset.d.split("-").map(Number)));
}
$("convRow").onclick = openConv; $("convClose").onclick = closeConv; $("convBg").onclick = closeConv;
$("convMode").onclick = e => { const b = e.target.closest("button"); if (b) setConvMode(b.dataset.v); };
["convDay", "convMonth", "convYear"].forEach(id => $(id).onchange = runConv);
$("shareBtn").onclick = $("shareRow").onclick = () => { toast("Đang tạo ảnh…"); Android.shareToday(); };

// ---- Cài đặt ----
function renderCities() {
  const cities = (state.cities && state.cities.length) ? state.cities : [{ name: state.prefs.city }];
  $("cityList").innerHTML = cities.map(c => {
    const on = c.name === state.prefs.city;
    return `<button class="item city" data-n="${esc(c.name)}"><span class="ico t-red">${ic("map-pin")}</span><span class="body"><span class="lbl">${esc(c.name)}</span>
      <span class="val">${on ? "Đang chọn · dùng cho widget, gợi ý và thông báo" : "Chạm để chọn"}</span></span>
      ${on ? `<span class="act">${ic("check")}</span>` : `<span class="city-x" data-x="${esc(c.name)}" role="button" aria-label="Bỏ ${esc(c.name)}">${ic("x")}</span>`}</button>`;
  }).join("");
  $("cityList").querySelectorAll(".city").forEach(b => b.onclick = e => {
    const x = e.target.closest(".city-x");
    if (x) { Android.removeCity(x.dataset.x); toast(`Đã bỏ ${x.dataset.x}`); return; }
    if (b.dataset.n !== state.prefs.city) { Android.setActiveCity(b.dataset.n); toast(`Đã chọn ${b.dataset.n}`); }
  });
}
function renderSettings() {
  renderCities();
  profileSummary();
  renderBgPick();
  const wh = $("notifyWeatherHour");
  if (!wh.options.length) [18, 19, 20, 21].forEach(h => { const o = document.createElement("option"); o.value = h; o.textContent = h + ":00"; wh.appendChild(o); });
  wh.value = String(state.prefs.notifyWeatherHour ?? 20);
  $("notifyWeather").checked = !!state.prefs.notifyWeather;
  $("spiritual").checked = !!state.prefs.spiritual;
  const theme = state.prefs.theme || "system";
  document.querySelectorAll("#themeSeg button").forEach(b => b.classList.toggle("on", b.dataset.v === theme));
  $("updVal").textContent = state.weather ? `Cập nhật lúc ${state.weather.updated} · ${state.weather.city}` : "Chưa có dữ liệu";
  $("verVal").textContent = state.version ? `Phiên bản ${state.version}` : "";
  ["showAlmanac", "showHuong", "showLucDieu", "showGoodDays", "notifyEvents", "notifyLunar"].forEach(k => { $(k).checked = !!state.prefs[k]; });
  $("lockNote").checked = !!state.prefs.lockNote;
  $("lockFix").hidden = !(state.prefs.lockNote && state.prefs.notifAllowed === false);
  $("wpText").checked = state.prefs.wpText !== false;
  $("zenStart").checked = !!state.prefs.zenStart;
  document.querySelectorAll("#zenSeg button").forEach(b => b.classList.toggle("on", b.dataset.v === (state.prefs.zenPaper || "auto")));
  $("wpAct").textContent = state.prefs.wpActive ? "Đang dùng" : "Đặt";
  const hs = $("notifyHour");
  if (!hs.options.length) [6, 7, 8, 9, 12, 18, 19, 20, 21].forEach(h => { const o = document.createElement("option"); o.value = h; o.textContent = h + ":00"; hs.appendChild(o); });
  hs.value = String(state.prefs.notifyHour ?? 7);
}
["showAlmanac", "showHuong", "showLucDieu", "showGoodDays"].forEach(k => $(k).onchange = e => {
  state.prefs[k] = e.target.checked;
  Android.setSections(!!state.prefs.showAlmanac, !!state.prefs.showLucDieu, !!state.prefs.showHuong, !!state.prefs.showGoodDays);
  render();
});
function pushNotify() { Android.setNotify(!!state.prefs.notifyEvents, !!state.prefs.notifyLunar, +(state.prefs.notifyHour ?? 7)); }
["notifyEvents", "notifyLunar"].forEach(k => $(k).onchange = e => { state.prefs[k] = e.target.checked; pushNotify(); });
$("notifyHour").onchange = e => { state.prefs.notifyHour = +e.target.value; pushNotify(); toast(`Sẽ nhắc lúc ${e.target.value}:00 mỗi ngày`); };
$("testNotify").onclick = () => { Android.testNotify(); toast("Đã gửi thông báo thử, kéo thanh trạng thái xuống xem"); };
$("notifyWeather").onchange = e => { state.prefs.notifyWeather = e.target.checked; Android.setNotifyWeather(e.target.checked, +(state.prefs.notifyWeatherHour ?? 20)); };
$("notifyWeatherHour").onchange = e => { state.prefs.notifyWeatherHour = +e.target.value; Android.setNotifyWeather(!!state.prefs.notifyWeather, +e.target.value); toast(`Sẽ báo thời tiết lúc ${e.target.value}:00`); };
// ---- Hồ sơ: họ tên, ngày sinh (âm hoặc dương lịch) ----
let profLunar = false;
function profileSummary() {
  const p = state.prefs, b = p.birth;
  $("profName").textContent = p.name || "Thông tin của bạn";
  $("profVal").textContent = b
    ? `Sinh ${b.sd}/${b.sm}/${b.sy} (${b.ld}/${b.lm}${b.leap ? " nhuận" : ""} âm lịch) · tuổi ${noEmo(p.birthLabel)}`
    : p.birthYear ? `Năm sinh âm lịch ${p.birthYear} · tuổi ${noEmo(p.birthLabel)}. Chạm để nhập đủ ngày sinh`
    : "Họ tên, ngày sinh để xét hợp, xung tuổi";
}
function setProfCal(lunar, d, m, y) {
  profLunar = lunar;
  document.querySelectorAll("#profCal button").forEach(b => b.classList.toggle("on", (b.dataset.v === "lunar") === lunar));
  fillSelect($("profDay"), 1, lunar ? 30 : 31, Math.min(d, lunar ? 30 : 31));
  fillSelect($("profMonth"), 1, 12, m, "Tháng ");
  fillSelect($("profYear"), 1900, state.today.y, y);
  profHint();
}
/** Hiện ngày sinh quy sang lịch còn lại; trả về false nếu ngày không hợp lệ. */
function profHint() {
  const d = +$("profDay").value, m = +$("profMonth").value, y = +$("profYear").value, h = $("profHint");
  let sy = y, sm = m, sd = d;
  if (profLunar) {
    const s = Android.lunarToSolar(d, m, y);
    if (!s) { h.textContent = `Năm ${y} tháng ${m} âm lịch không có ngày ${d}.`; return false; }
    [sy, sm, sd] = s.split("-").map(Number);
  } else if (new Date(y, m - 1, d).getDate() !== d) { h.textContent = `Tháng ${m}/${y} không có ngày ${d}.`; return false; }
  const t = state.today;
  if (dateKey(sy, sm, sd) > dateKey(t.y, t.m, t.d)) { h.textContent = "Ngày sinh không được sau hôm nay."; return false; }
  const r = JSON.parse(Android.day(sy, sm, sd));
  h.textContent = profLunar
    ? `Tức ${r.weekday}, ${sd}/${sm}/${sy} dương lịch · tuổi ${r.canChiYear}`
    : `Tức ${r.ld}/${r.lm}${r.leap ? " nhuận" : ""} âm lịch · tuổi ${r.canChiYear}`;
  return true;
}
function openProfile() {
  const p = state.prefs, b = p.birth;
  $("profNameIn").value = p.name || "";
  if (b) b.lunarInput ? setProfCal(true, b.ld, b.lm, b.ly) : setProfCal(false, b.sd, b.sm, b.sy);
  else setProfCal(!!p.birthYear, 1, 1, p.birthYear || 1990);   // bản cũ chỉ có năm âm lịch
  $("profClear").hidden = !(p.name || b || p.birthYear); $("profClear").textContent = "Xóa"; delete $("profClear").dataset.arm;
  $("profBg").hidden = false; $("profSheet").hidden = false;
}
function closeProfile() { $("profSheet").hidden = true; $("profBg").hidden = true; }
$("profileRow").onclick = openProfile;
$("profCancel").onclick = closeProfile; $("profBg").onclick = closeProfile;
$("profCal").onclick = e => {
  const b = e.target.closest("button"); if (!b || (b.dataset.v === "lunar") === profLunar) return;
  // Đổi kiểu lịch: giữ nguyên ngày sinh, chỉ đổi cách nhập
  const ok = profHint(), d = +$("profDay").value, m = +$("profMonth").value, y = +$("profYear").value;
  if (!ok) return setProfCal(!profLunar, d, m, y);
  if (profLunar) { const [sy, sm, sd] = Android.lunarToSolar(d, m, y).split("-").map(Number); setProfCal(false, sd, sm, sy); }
  else { const r = JSON.parse(Android.day(y, m, d)); setProfCal(true, r.ld, r.lm, r.ly); }
};
["profDay", "profMonth", "profYear"].forEach(id => $(id).onchange = profHint);
$("profSave").onclick = () => {
  if (!profHint()) { toast($("profHint").textContent); return; }
  const err = Android.setProfile($("profNameIn").value.trim(), profLunar, +$("profDay").value, +$("profMonth").value, +$("profYear").value);
  if (err) { toast(err); return; }
  closeProfile(); toast("Đã lưu hồ sơ");
};
$("profClear").onclick = () => {
  if (!$("profClear").dataset.arm) { $("profClear").dataset.arm = "1"; $("profClear").textContent = "Xóa thật?"; return; }
  Android.clearProfile(); closeProfile(); toast("Đã xóa hồ sơ");
};
$("exportRow").onclick = () => Android.exportEvents();
$("importRow").onclick = () => Android.importEvents();
$("icsRow").onclick = () => Android.exportIcs();

// ---- Lịch Google trên máy: chọn lịch, nhập, xuất ----
let calSel = ls("calId"), calList = [];
function openCal() { $("calBg").hidden = false; $("calSheet").hidden = false; loadCalendars(); }
function closeCal() { $("calBg").hidden = true; $("calSheet").hidden = true; }
function loadCalendars() {
  const raw = Android.calendarList();
  if (raw === "null") { $("calList").innerHTML = `<div class="ev-empty">Đang xin quyền truy cập lịch trên máy…</div>`; return; }
  calList = JSON.parse(raw);
  if (!calList.length) {
    $("calList").innerHTML = `<div class="ev-empty">Máy chưa có lịch nào. Thêm tài khoản Google trong Cài đặt máy rồi bật đồng bộ Lịch.</div>`;
    $("calImport").disabled = $("calExport").disabled = true; return;
  }
  if (!calList.some(c => c.id === calSel)) calSel = (calList.find(c => c.writable) || calList[0]).id;
  $("calList").innerHTML = calList.map(c => `<button class="cal${c.id === calSel ? " on" : ""}" data-id="${esc(c.id)}"><i style="background:${esc(c.color)}"></i>
    <span><b>${esc(c.name)}</b><small>${esc(c.account)}${c.writable ? "" : " · chỉ đọc"}</small></span></button>`).join("");
  $("calList").querySelectorAll(".cal").forEach(b => b.onclick = () => { calSel = b.dataset.id; lsSet("calId", calSel); loadCalendars(); });
  const cur = calList.find(c => c.id === calSel);
  $("calImport").disabled = false;
  $("calExport").disabled = !(cur && cur.writable);
}
window.onCalendarPermission = ok => {
  if (ok) loadCalendars();
  else $("calList").innerHTML = `<div class="ev-empty">Chưa được cấp quyền truy cập lịch. Mở lại mục này để thử, hoặc cấp trong Cài đặt máy → Ứng dụng → Lịch &amp; Thời tiết → Quyền.</div>`;
};
$("gcalRow").onclick = openCal; $("calClose").onclick = closeCal; $("calBg").onclick = closeCal;
$("calImport").onclick = () => { if (!calSel) return; toast("Đang nhập sự kiện…"); Android.importCalendar(calSel); };
$("calExport").onclick = () => { if (!calSel) return; toast("Đang xuất sang lịch…"); Android.exportCalendar(calSel); };
// ---- Thanh menu dưới: Lịch, Thời tiết, Xem ngày, Tiện ích, Cài đặt; mỗi mục nhớ chỗ đang cuộn ----
const TABS = ["lich", "thoitiet", "xem", "tienich", "caidat"];
let curTab = "lich";
const tabScroll = {};
function setTab(t) {
  if (!TABS.includes(t)) t = "lich";
  if (t === curTab) return;
  tabScroll[curTab] = window.scrollY;
  if (curTab === "caidat") $("citySearch").hidden = true;
  curTab = t;
  document.body.dataset.cur = t;
  document.querySelectorAll("#tabbar button").forEach(b => { const on = b.dataset.t === t; b.setAttribute("aria-selected", on ? "true" : "false"); b.tabIndex = on ? 0 : -1; });
  if (state && t === "caidat") renderSettings();
  if (state && t === "thoitiet") renderWeather();   // vẽ lại đường nhiệt độ theo đúng bề rộng (lúc ẩn không đo được)
  window.scrollTo(0, tabScroll[t] || 0);
}
window.closeSettingsIfOpen = function () {
  if (!$("zen").hidden) { closeZen(); return true; }
  if (!$("famSheet").hidden) { if (famView !== "list") famList(); else closeFamily(); return true; }
  if (!$("sheet").hidden) { closeSheet(); return true; }
  if (!$("convSheet").hidden) { closeConv(); return true; }
  if (!$("calSheet").hidden) { closeCal(); return true; }
  if (!$("vkSheet").hidden) { if (!$("vkBack").hidden) vkList(); else closeVk(); return true; }
  if (!$("profSheet").hidden) { closeProfile(); return true; }
  if (curTab !== "lich") { setTab("lich"); return true; }
  return false;
};
$("tabbar").onclick = e => {
  const b = e.target.closest("button[data-t]"); if (!b) return;
  if (b.dataset.t === curTab) window.scrollTo({ top: 0, behavior: "smooth" });   // chạm lại mục đang mở: lên đầu trang
  else setTab(b.dataset.t);
};
// Đang gõ chữ thì ẩn thanh menu (bàn phím đẩy thanh lên che ô nhập)
document.addEventListener("focusin", e => { if (e.target.matches("input:not([type=checkbox]):not([type=time]), textarea")) document.body.classList.add("typing"); });
document.addEventListener("focusout", () => document.body.classList.remove("typing"));
function shiftDay(n) {
  const dt = new Date(view.y, view.m - 1, view.sel + n);
  view = { y: dt.getFullYear(), m: dt.getMonth() + 1, sel: dt.getDate() };
  render();
}
$("dPrev").onclick = () => shiftDay(-1);
$("dNext").onclick = () => shiftDay(1);
$("xemToday").onclick = () => { view = { y: state.today.y, m: state.today.m, sel: state.today.d }; render(); };
$("weather").onclick = () => setTab("thoitiet");
$("wxRefresh").onclick = $("wxEmptyBtn").onclick = () => { toast("Đang tải thời tiết..."); Android.refresh(); };

let searchTimer, lastResults = [];
$("cityRow").onclick = () => {
  const p = $("citySearch"); p.hidden = !p.hidden;
  if (!p.hidden) { $("cityInput").value = ""; $("cityResults").innerHTML = ""; $("cityInput").focus(); }
};
$("cityInput").oninput = e => {
  clearTimeout(searchTimer);
  const q = e.target.value.trim();
  if (q.length < 2) { $("cityResults").innerHTML = ""; return; }
  searchTimer = setTimeout(() => Android.searchCity(q), 350);
};
window.onCityResults = function (list) {
  lastResults = list || [];
  $("cityResults").innerHTML = lastResults.length
    ? lastResults.map((c, i) => `<button data-i="${i}"><div class="n">${esc(c.name)}</div><div class="r">${esc(c.region)}</div></button>`).join("")
    : `<div class="empty">Không tìm thấy. Thử gõ không dấu, ví dụ "Hai Phong".</div>`;
  $("cityResults").querySelectorAll("button").forEach(b => b.onclick = () => {
    const c = lastResults[+b.dataset.i];
    Android.addCity(c.lat, c.lon, c.name);
    $("citySearch").hidden = true;
    toast(`Đã thêm ${c.name}, đang tải thời tiết...`);
  });
};
$("spiritual").onchange = e => { Android.setSpiritual(e.target.checked); state.prefs.spiritual = e.target.checked; render(); };
$("themeSeg").onclick = e => { const b = e.target.closest("button"); if (!b) return; state.prefs.theme = b.dataset.v; Android.setTheme(b.dataset.v); renderSettings(); };
$("refreshRow").onclick = () => { toast("Đang tải thời tiết..."); Android.refresh(); };
$("pinRow").onclick = () => Android.pinWidget();
$("pinIconRow").onclick = () => Android.pinIconWidget();
$("pinBlocRow").onclick = () => Android.pinBlocWidget();
$("srcRow").onclick = () => Android.openUrl("https://github.com/Ngocha2607/lich-am-widget");

// ---- Lịch xé, la bàn AR, màn hình khóa, hình nền động ----
const pad2 = n => String(n).padStart(2, "0");
$("tearRow").onclick = () => Android.openTearOff(`${view.y}-${pad2(view.m)}-${pad2(view.sel)}`);
$("compassRow").onclick = () => Android.openCompass();
$("lockNote").onchange = e => {
  state.prefs.lockNote = e.target.checked;
  const allowed = Android.setLockNote(e.target.checked);
  toast(!e.target.checked ? "Đã tắt lịch trên màn hình khóa."
    : allowed ? "Đã bật lịch trên màn hình khóa." : "Đã bật. Hãy cho phép app gửi thông báo để lịch hiện trên màn hình khóa.");
};
$("wpRow").onclick = () => Android.setLiveWallpaper();
$("lockFix").onclick = () => Android.openNotificationSettings();
$("wpText").onchange = e => { state.prefs.wpText = e.target.checked; Android.setWallpaperText(e.target.checked); };
$("zenStart").onchange = e => { state.prefs.zenStart = e.target.checked; Android.setZen(e.target.checked, state.prefs.zenPaper || "auto"); };
$("zenSeg").onclick = e => { const b = e.target.closest("button"); if (!b) return; state.prefs.zenPaper = b.dataset.v;
  Android.setZen(!!state.prefs.zenStart, b.dataset.v); renderSettings(); };

// ---- Chế độ Zen ----
const LUNAR_MONTHS = ["Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười", "Mười Một", "Chạp"];
let zenDate = null, zenCtlTimer = 0, zenX = null;
function zenMode() { const p = state.prefs.zenPaper || "auto"; return p === "auto" ? (state.dark ? "ink" : "paper") : p; }
function openZen() {
  zenDate = new Date(state.today.y, state.today.m - 1, state.today.d);
  $("zen").hidden = false;
  document.body.classList.add("zen-open");
  document.documentElement.style.overflow = "hidden";
  renderZen();
  zenControls();
}
function closeZen() {
  $("zen").hidden = true; $("zen").classList.remove("show", "ctl"); document.body.classList.remove("zen-open"); document.documentElement.style.overflow = "";
  if (Android.setSystemBars) Android.setSystemBars("", false);
}
function renderZen() {
  if (!zenDate) return;
  const z = $("zen"), m = zenMode();
  z.classList.toggle("paper", m === "paper"); z.classList.toggle("ink", m === "ink");
  if (Android.setSystemBars) Android.setSystemBars(m === "paper" ? "#EEE5D0" : "#0B0B0C", m === "paper");
  const d = JSON.parse(Android.day(zenDate.getFullYear(), zenDate.getMonth() + 1, zenDate.getDate()));
  $("zNum").textContent = d.ld;
  $("zMon").textContent = `tháng ${LUNAR_MONTHS[d.lm - 1]}${d.leap ? " nhuận" : ""}`;
  const v = d.zen || { text: d.quote, by: d.quoteKind };
  $("zVerse").textContent = v.text;
  $("zBy").textContent = v.by ? "— " + v.by : "";
  z.setAttribute("aria-label", `Chế độ Zen. Ngày ${d.ld} tháng ${LUNAR_MONTHS[d.lm - 1]} âm lịch, ${d.d}/${d.m}/${d.y}. ${v.text.replace(/\n/g, ", ")}`);
  z.classList.remove("show"); void z.offsetWidth; z.classList.add("show");
}
function zenControls() {
  $("zen").classList.add("ctl");
  clearTimeout(zenCtlTimer);
  zenCtlTimer = setTimeout(() => $("zen").classList.remove("ctl"), 3500);
}
function zenShift(n) { zenDate.setDate(zenDate.getDate() + n); renderZen(); zenControls(); }
$("zenRow").onclick = openZen;
$("zClose").onclick = e => { e.stopPropagation(); closeZen(); };
$("zPrev").onclick = e => { e.stopPropagation(); zenShift(-1); };
$("zNext").onclick = e => { e.stopPropagation(); zenShift(1); };
$("zToday").onclick = e => { e.stopPropagation(); zenDate = new Date(state.today.y, state.today.m - 1, state.today.d); renderZen(); zenControls(); };
$("zInk").onclick = e => { e.stopPropagation(); const next = zenMode() === "ink" ? "paper" : "ink"; state.prefs.zenPaper = next;
  Android.setZen(!!state.prefs.zenStart, next); renderZen(); zenControls(); };
let zenCtlHidden = false;
$("zen").addEventListener("pointerdown", e => { zenX = e.clientX; zenCtlHidden = !$("zen").classList.contains("ctl"); });
$("zen").addEventListener("click", e => {
  if (zenCtlHidden && e.target.closest(".z-ctl")) { e.stopPropagation(); e.preventDefault(); zenControls(); }
}, true);
$("zen").addEventListener("pointerup", e => {
  if (zenX === null) return;
  const dx = e.clientX - zenX; zenX = null;
  if (e.target.closest(".z-ctl")) return;
  if (Math.abs(dx) > 50) zenShift(dx < 0 ? 1 : -1); else zenControls();
});

// ---- Sổ giỗ gia đình ----
let famView = "list", famCur = null, famData = [], famArmed = null;
function famGet(id) { return famData.find(g => g.id === id); }
function famLoad() { famData = JSON.parse(Android.families()); }
function famActs(btns) {
  $("famActs").innerHTML = btns.map((b, i) => `<button class="btn${b.ghost ? " ghost" : ""}${b.danger ? " danger" : ""}" data-i="${i}">${b.label}</button>`).join("");
  $("famActs").querySelectorAll("button").forEach(el => el.onclick = () => btns[+el.dataset.i].on(el));
}
/** Nút xóa cần bấm hai lần (WebView không có hộp thoại xác nhận). */
function famConfirm(el, key, run) {
  if (famArmed === key) { famArmed = null; run(); return; }
  famArmed = key; el.textContent = "Bấm lần nữa để xóa"; el.classList.add("armed");
  setTimeout(() => { if (famArmed === key) { famArmed = null; el.textContent = "Xóa"; el.classList.remove("armed"); } }, 3000);
}
function openFamily(id) {
  $("famBg").hidden = false; $("famSheet").hidden = false;
  famLoad();
  if (id && famGet(id)) famGroup(id); else famList();
}
function closeFamily() { $("famBg").hidden = true; $("famSheet").hidden = true; famView = "list"; }
function famList() {
  famView = "list"; famCur = null; famLoad();
  $("famTitle").textContent = "Sổ giỗ gia đình";
  const rows = famData.map(g => {
    const next = g.members.filter(m => m.daysLeft !== undefined).sort((a, b) => a.daysLeft - b.daysLeft)[0];
    const meta = `${g.members.length} ngày giỗ` + (next ? ` · gần nhất: ${esc(next.title)}, còn ${next.daysLeft} ngày` : "");
    return `<button class="ev fam-row" data-id="${esc(g.id)}"><div class="ic t-gold">${ic("book-heart")}</div><div class="b"><div class="t">${esc(g.name)}</div><div class="d">${meta}</div></div><span class="chev">${ic("chevron-right")}</span></button>`;
  }).join("");
  $("famBody").innerHTML = `<p class="fam-intro">Một người nhập ngày giỗ ông bà theo âm lịch rồi bấm <b>Gửi cho cả nhà</b> (Zalo, Messenger…). Người nhận mở app → Sổ giỗ gia đình → <b>Nhập mã</b>, dán tin nhắn là cùng được nhắc trước ngày giỗ và biết ai lo đồ cúng. Sửa xong thì gửi lại, máy nào nhập lại cũng được cập nhật.</p>`
    + (rows || `<div class="ev-empty">Chưa có sổ nào.</div>`);
  $("famBody").querySelectorAll(".fam-row").forEach(b => b.onclick = () => famGroup(b.dataset.id));
  famActs([
    { label: "Nhập mã", ghost: true, on: famImport },
    { label: "+ Tạo sổ", on: famCreate },
  ]);
}
function famCreate() {
  famView = "create";
  $("famTitle").textContent = "Tạo sổ giỗ";
  $("famBody").innerHTML = `<div class="field"><label for="famName">Tên sổ</label><input class="in" id="famName" maxlength="40" placeholder="Ví dụ: Nhà nội, Họ Nguyễn" autocomplete="off"></div>`;
  famActs([
    { label: "Hủy", ghost: true, on: famList },
    { label: "Tạo", on: () => { const n = $("famName").value.trim(); if (!n) { toast("Nhập tên sổ."); return; } famGroup(Android.familyCreate(n)); } },
  ]);
  $("famName").onkeydown = e => { if (e.key === "Enter") { e.preventDefault(); $("famActs").querySelector("button:last-child").click(); } };
  $("famName").focus();
}
function famGroup(id) {
  famLoad();
  const g = famGet(id); if (!g) return famList();
  famView = "group"; famCur = id;
  $("famTitle").textContent = g.name;
  const rows = g.members.slice().sort((a, b) => (a.daysLeft ?? 1e4) - (b.daysLeft ?? 1e4)).map(m => {
    const when = [`${m.d}/${m.m} âm`, m.next ? `lần tới ${m.next}` : ""].filter(Boolean).join(" · ");
    const who = m.who ? `<br><span class="fam-who">Lo đồ cúng: ${esc(m.who)}</span>` : "";
    const left = m.daysLeft !== undefined ? `<span class="left">${m.daysLeft === 0 ? "hôm nay" : "còn " + m.daysLeft + " ngày"}</span>` : "";
    return `<button class="ev fam-m" data-id="${esc(m.id)}"><div class="ic t-gold">${ic("flame")}</div><div class="b"><div class="t">${esc(m.title)}</div><div class="d">${esc(when)}${who}</div></div>${left}</button>`;
  }).join("");
  const opts = [...new Set([0, 1, 3, 7, g.remind])].sort((a, b) => a - b).map(v => `<option value="${v}"${v === g.remind ? " selected" : ""}>${v === 0 ? "Đúng ngày" : v + " ngày"}</option>`).join("");
  $("famBody").innerHTML = (rows || `<div class="ev-empty">Chưa có ai. Bấm <b>+ Thêm người</b> để ghi ngày giỗ.</div>`)
    + `<div class="row2" style="margin-top:12px"><div class="field" style="margin:0"><label for="famRemind">Nhắc trước</label><select class="in" id="famRemind">${opts}</select></div>
       <div class="field" style="margin:0"><label for="famRename">Tên sổ</label><input class="in" id="famRename" maxlength="40" value="${esc(g.name)}"></div></div>
       <div class="actions" style="margin-top:10px"><button class="btn ghost" id="famBack">‹ Các sổ</button><button class="btn ghost danger" id="famDel">Xóa</button></div>`;
  $("famBody").querySelectorAll(".fam-m").forEach(b => b.onclick = () => famEdit(g.members.find(m => m.id === b.dataset.id)));
  const saveMeta = () => {
    Android.familyUpdate(id, $("famRename").value, +$("famRemind").value); famLoad();
    const n = famGet(id).name; $("famTitle").textContent = n; $("famRename").value = n;
  };
  $("famRemind").onchange = saveMeta; $("famRename").onchange = saveMeta;
  $("famRename").onkeydown = e => { if (e.key === "Enter") { e.preventDefault(); e.target.blur(); } };
  $("famBack").onclick = famList;
  $("famDel").onclick = e => famConfirm(e.currentTarget, "g" + id, () => { Android.familyDelete(id); toast("Đã xóa sổ."); famList(); render(); });
  famActs([
    { label: "Gửi cho cả nhà", ghost: true, on: () => { if (!g.members.length) { toast("Thêm ít nhất một ngày giỗ trước khi gửi."); return; } Android.familyShare(id); } },
    { label: "+ Thêm người", on: () => famEdit(null) },
  ]);
}
function famEdit(m) {
  famView = "edit";
  const g = famGet(famCur); if (!g) return famList();
  $("famTitle").textContent = m ? "Sửa ngày giỗ" : "Thêm ngày giỗ";
  const dOpts = Array.from({ length: 30 }, (_, i) => `<option value="${i + 1}"${m && m.d === i + 1 ? " selected" : ""}>Ngày ${i + 1}</option>`).join("");
  const mOpts = Array.from({ length: 12 }, (_, i) => `<option value="${i + 1}"${m && m.m === i + 1 ? " selected" : ""}>Tháng ${LUNAR_MONTHS[i]}</option>`).join("");
  $("famBody").innerHTML = `
    <div class="row2"><div class="field" style="margin:0"><label for="fmRel">Quan hệ</label><input class="in" id="fmRel" list="famRel" maxlength="40" placeholder="Ông nội" value="${esc(m ? m.rel : "")}"></div>
      <div class="field" style="margin:0"><label for="fmName">Họ tên</label><input class="in" id="fmName" maxlength="60" placeholder="Nguyễn Văn An" value="${esc(m ? m.name : "")}"></div></div>
    <div class="field"><label>Ngày giỗ (ngày mất âm lịch)</label><div class="row2"><select class="in" id="fmD">${dOpts}</select><select class="in" id="fmM">${mOpts}</select></div>
      <div class="hint">Tháng thiếu không có ngày 30 thì nhắc vào ngày 29.</div></div>
    <div class="row2" style="margin-top:12px"><div class="field" style="margin:0"><label for="fmY">Năm mất</label><input class="in" id="fmY" type="number" inputmode="numeric" min="1800" max="${state.today.y}" placeholder="Không bắt buộc" value="${m && m.y ? m.y : ""}"></div>
      <div class="field" style="margin:0"><label for="fmWho">Người lo đồ cúng</label><input class="in" id="fmWho" maxlength="40" placeholder="Chú Ba" value="${esc(m ? m.who : "")}"></div></div>
    <div class="field"><label for="fmPlace">Nơi an táng</label><input class="in" id="fmPlace" maxlength="80" placeholder="Không bắt buộc" value="${esc(m ? m.place : "")}"></div>
    <div class="field"><label for="fmNote">Ghi chú</label><input class="in" id="fmNote" maxlength="120" placeholder="Mâm cúng, ai đón ai…" value="${esc(m ? m.note : "")}"></div>`;
  const btns = [{ label: "Hủy", ghost: true, on: () => famGroup(famCur) }];
  if (m) btns.push({ label: "Xóa", ghost: true, danger: true, on: el => famConfirm(el, "m" + m.id, () => { Android.familyDeleteMember(famCur, m.id); render(); famGroup(famCur); }) });
  btns.push({ label: "Lưu", on: () => {
    const yRaw = $("fmY").value.trim(), y = parseInt(yRaw, 10);
    if (yRaw && !(y >= 1800 && y <= state.today.y)) { toast(`Năm mất phải từ 1800 đến ${state.today.y}.`); return; }
    const data = { id: m ? m.id : "", rel: $("fmRel").value.trim(), name: $("fmName").value.trim(), d: +$("fmD").value, m: +$("fmM").value,
      y: y >= 1800 && y <= state.today.y ? y : null, place: $("fmPlace").value.trim(), who: $("fmWho").value.trim(), note: $("fmNote").value.trim() };
    if (!data.rel && !data.name) { toast("Nhập quan hệ hoặc họ tên."); return; }
    if (!Android.familySaveMember(famCur, JSON.stringify(data))) { toast("Không lưu được."); return; }
    toast("Đã lưu. Nhớ gửi lại cho cả nhà để cùng cập nhật.");
    render(); famGroup(famCur);
  } });
  famActs(btns);
  // Enter (nút "tiếp" của bàn phím) chuyển sang ô kế tiếp; ô cuối cùng mới lưu
  const fields = [...$("famBody").querySelectorAll("input, select")];
  fields.forEach((el, i) => {
    el.setAttribute("enterkeyhint", i === fields.length - 1 ? "done" : "next");
    el.onkeydown = e => {
      if (e.key !== "Enter") return;
      e.preventDefault();
      if (i < fields.length - 1) fields[i + 1].focus(); else $("famActs").querySelector("button:last-child").click();
    };
  });
}
function famImport() {
  famView = "import";
  $("famTitle").textContent = "Nhập mã sổ giỗ";
  $("famBody").innerHTML = `<p class="fam-intro">Dán cả tin nhắn sổ giỗ người nhà gửi (có dòng bắt đầu bằng <b>LICHGIAPHA1:</b>). Ngày giỗ sẽ thành sự kiện âm lịch và được nhắc như sự kiện khác.</p>
    <textarea class="in" id="famPaste" placeholder="Dán tin nhắn vào đây"></textarea>`;
  famActs([
    { label: "Hủy", ghost: true, on: famList },
    { label: "Dán", ghost: true, on: () => { const t = Android.clipboardText(); if (t) $("famPaste").value = t; else toast("Bộ nhớ tạm đang trống."); } },
    { label: "Nhập", on: () => {
      const r = JSON.parse(Android.familyImport($("famPaste").value));
      if (r.error) { toast(r.error); return; }
      const more = r.books > 1 ? ` (và ${r.books - 1} sổ khác)` : "";
      toast((r.isNew ? `Đã nhập sổ "${r.name}" với ${r.added} ngày giỗ.`
        : (r.added + r.updated + (r.removed || 0)) === 0 && !r.metaChanged ? `Sổ "${r.name}" đã có đủ, không có gì mới.`
        : (r.added + r.updated + (r.removed || 0)) === 0 ? `Đã cập nhật tên sổ / số ngày nhắc của "${r.name}".`
        : `Đã cập nhật sổ "${r.name}": thêm ${r.added}, sửa ${r.updated}, xóa ${r.removed || 0}.`) + more);
      render(); famGroup(r.id);
    } },
  ]);
}
$("famRow").onclick = () => openFamily();
$("famBg").onclick = closeFamily;

if (!window.Android) { const s = document.createElement("script"); s.src = "mock.js"; document.body.appendChild(s); }
