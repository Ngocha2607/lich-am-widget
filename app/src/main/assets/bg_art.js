// Tranh nền lịch tự vẽ (SVG, khung dọc 400×800): cảm hứng phong cảnh Việt Nam và tranh dân gian.
// Không sao chép tranh có sẵn. Dùng: BG_ART[ten] trả về chuỗi SVG.
window.BG_ART = (() => {
  const svg = (defs, body) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 800" preserveAspectRatio="xMidYMid slice"><defs>${defs}</defs>${body}</svg>`;
  const petal = (cx, cy, rot, len, w, fill) => `<path transform="translate(${cx} ${cy}) rotate(${rot})" d="M0,0 C${-w},${-len * .3} ${-w * .8},${-len * .78} 0,${-len} C${w * .8},${-len * .78} ${w},${-len * .3} 0,0 Z" fill="${fill}"/>`;

  // Hoa sen: đầm sen buổi sớm, lá sen xanh, hoa và nụ hồng
  function lotusFlower(x, y, s) {
    let out = "";
    [-62, -36, 36, 62].forEach(r => { out += petal(x, y, r, 70 * s, 22 * s, "url(#senB)"); });
    [-20, 20, -44, 44].forEach(r => { out += petal(x, y, r, 78 * s, 20 * s, "url(#senA)"); });
    out += petal(x, y, 0, 82 * s, 21 * s, "url(#senA)");
    out += `<ellipse cx="${x}" cy="${y - 6 * s}" rx="${16 * s}" ry="${6 * s}" fill="#F2C94C"/>`;
    return out;
  }
  function leaf(x, y, rx, ry, rot) {
    let veins = "";
    for (let a = 0; a < 360; a += 30) veins += `<line x1="0" y1="0" x2="${(rx * .92 * Math.cos(a * Math.PI / 180)).toFixed(1)}" y2="${(ry * .92 * Math.sin(a * Math.PI / 180)).toFixed(1)}"/>`;
    return `<g transform="translate(${x} ${y}) rotate(${rot})"><ellipse rx="${rx}" ry="${ry}" fill="url(#leaf)"/><g stroke="#2F7558" stroke-opacity=".35" stroke-width="1.4">${veins}</g><circle r="3" fill="#2F7558" fill-opacity=".5"/></g>`;
  }
  const sen = svg(`
    <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFF7EC"/><stop offset=".45" stop-color="#EEF6EE"/><stop offset="1" stop-color="#BFDDD3"/></linearGradient>
    <radialGradient id="leaf" cx=".4" cy=".35" r=".8"><stop offset="0" stop-color="#7CC59A"/><stop offset=".7" stop-color="#3E9268"/><stop offset="1" stop-color="#2C6E4F"/></radialGradient>
    <linearGradient id="senA" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#E5658E"/><stop offset=".75" stop-color="#F7B4C8"/><stop offset="1" stop-color="#FFF1F5"/></linearGradient>
    <linearGradient id="senB" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#D9557F"/><stop offset="1" stop-color="#F3A0B9"/></linearGradient>`,
    `<rect width="400" height="800" fill="url(#sky)"/>
    <g stroke="#FFFFFF" stroke-opacity=".5" stroke-width="2" fill="none"><path d="M40,560 q30,-6 60,0 M220,610 q40,-8 80,0 M90,690 q50,-8 100,0"/></g>
    <g stroke="#3E8E6A" stroke-width="4" fill="none"><path d="M300,640 C296,560 292,520 290,470"/><path d="M110,700 C118,640 122,600 126,560"/><path d="M210,760 C206,700 214,660 222,620"/></g>
    ${leaf(90, 620, 70, 26, -8)}${leaf(320, 700, 85, 30, 6)}${leaf(200, 560, 52, 18, 4)}${leaf(60, 760, 90, 30, 2)}${leaf(250, 790, 70, 24, -4)}
    ${lotusFlower(290, 470, 1)}${lotusFlower(126, 560, .72)}
    ${petal(222, 620, 0, 58, 16, "url(#senA)")}${petal(222, 620, -14, 50, 12, "url(#senB)")}`);

  // Vịnh Hạ Long: bình minh, núi đá vôi nhiều lớp sương, thuyền buồm nâu đỏ
  const peaks = (base, pts, fill, op) => `<path d="M0,${base} ${pts} L400,${base} L400,800 L0,800 Z" fill="${fill}" fill-opacity="${op}"/>`;
  const halong = svg(`
    <linearGradient id="hs" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F9DDBD"/><stop offset=".35" stop-color="#F4BFA6"/><stop offset=".62" stop-color="#C7CEDD"/><stop offset="1" stop-color="#7D93B2"/></linearGradient>
    <linearGradient id="hw" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#B9C6D8"/><stop offset="1" stop-color="#56708F"/></linearGradient>`,
    `<rect width="400" height="800" fill="url(#hs)"/><circle cx="292" cy="250" r="46" fill="#FFF3DC" fill-opacity=".85"/>
    ${peaks(470, "L20,430 C30,380 50,360 62,400 L80,440 C95,390 110,330 130,350 C140,365 146,420 160,445 L200,470 C215,420 236,360 262,372 C280,382 286,430 300,455 L330,470 C345,440 352,400 372,410 L400,440", "#A6B4CC", .75)}
    ${peaks(560, "L30,520 C38,450 60,420 78,452 C90,475 96,520 110,540 L150,560 C160,500 178,440 205,452 C224,462 230,520 246,548 L280,560 C292,520 300,470 326,480 C346,488 352,530 364,552 L400,520", "#7389AA", .85)}
    <rect y="560" width="400" height="240" fill="url(#hw)"/>
    <g stroke="#FFFFFF" stroke-opacity=".35" stroke-width="2"><path d="M40,600 h70 M200,620 h90 M90,660 h60 M260,700 h80 M30,740 h90"/></g>
    ${peaks(640, "L0,600 C10,560 30,540 46,572 C56,592 60,620 70,630 L90,640", "#3F5677", 1)}
    <g transform="translate(250 600)"><path d="M-46,30 L46,30 L34,44 L-36,44 Z" fill="#4A2E24"/><line x1="-6" y1="30" x2="-6" y2="-48" stroke="#4A2E24" stroke-width="2"/><line x1="18" y1="30" x2="18" y2="-30" stroke="#4A2E24" stroke-width="2"/>
      <path d="M-6,-48 C-34,-30 -38,6 -30,26 L-6,26 Z" fill="#C2553B"/><path d="M18,-30 C-2,-18 -4,8 0,26 L18,26 Z" fill="#D46A44"/>
      <g stroke="#8E3A27" stroke-width="1"><path d="M-6,-30 L-34,-22 M-6,-12 L-36,-6 M-6,8 L-34,12 M18,-14 L1,-8 M18,4 L-2,8"/></g></g>`);

  // Hoa đào: cành đào Tết, hoa năm cánh và cánh rơi
  const blossom = (x, y, s, rot) => {
    let p = "";
    for (let i = 0; i < 5; i++) p += `<ellipse cx="0" cy="${-9 * s}" rx="${6.5 * s}" ry="${9 * s}" transform="rotate(${i * 72 + rot})" fill="url(#dao)"/>`;
    return `<g transform="translate(${x} ${y})">${p}<circle r="${3.2 * s}" fill="#B8325E"/><g fill="#F6D365">${[0, 72, 144, 216, 288].map(a => `<circle cx="${(5 * s * Math.cos(a * Math.PI / 180)).toFixed(1)}" cy="${(5 * s * Math.sin(a * Math.PI / 180)).toFixed(1)}" r="${s * .9}"/>`).join("")}</g></g>`;
  };
  const dao = svg(`
    <linearGradient id="dbg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFF7F1"/><stop offset="1" stop-color="#FADADF"/></linearGradient>
    <radialGradient id="dao" cx=".5" cy=".8" r=".9"><stop offset="0" stop-color="#E35D86"/><stop offset=".55" stop-color="#F49AB4"/><stop offset="1" stop-color="#FFE3EC"/></radialGradient>`,
    `<rect width="400" height="800" fill="url(#dbg)"/><circle cx="300" cy="170" r="80" fill="#F7C9A8" fill-opacity=".45"/>
    <g stroke="#4B3328" stroke-linecap="round" fill="none"><path d="M-10,80 C80,110 150,180 210,270 C250,330 300,360 380,380" stroke-width="12"/>
      <path d="M120,150 C150,120 190,110 230,118" stroke-width="6"/><path d="M200,250 C230,230 270,232 300,250" stroke-width="5"/><path d="M260,340 C270,300 300,280 330,276" stroke-width="5"/><path d="M60,100 C70,140 64,180 50,210" stroke-width="5"/></g>
    ${blossom(140, 148, 1.6, 10)}${blossom(228, 116, 1.3, 30)}${blossom(205, 262, 1.7, 0)}${blossom(298, 250, 1.2, 20)}${blossom(330, 276, 1.4, 5)}${blossom(260, 338, 1.5, 15)}${blossom(52, 206, 1.3, 40)}${blossom(370, 382, 1.2, 0)}${blossom(96, 116, 1.1, 25)}
    <g fill="#F49AB4" fill-opacity=".8">${[[80, 420, 20], [180, 520, -30], [300, 470, 50], [130, 640, 10], [260, 700, -20], [340, 600, 35], [60, 560, -10]].map(([x, y, r]) => `<ellipse cx="${x}" cy="${y}" rx="5" ry="8" transform="rotate(${r} ${x} ${y})"/>`).join("")}</g>`);

  // Ruộng bậc thang: các bậc lúa xanh và vàng uốn theo sườn đồi
  let terraces = "";
  const cols = ["#B5D16A", "#8DB255", "#E9C25A", "#A8C960", "#76A04B", "#F0CF6E", "#9CC05C", "#6E9846"];
  for (let i = 0; i < 16; i++) {
    const y = 330 + i * 30, c = cols[i % cols.length], amp = 26 + (i % 3) * 8;
    terraces += `<path d="M-20,${y} C80,${y - amp} 160,${y + amp * .6} 240,${y - amp * .4} C300,${y - amp} 360,${y + amp * .3} 420,${y - amp * .2} L420,820 L-20,820 Z" fill="${c}"/>`
      + `<path d="M-20,${y} C80,${y - amp} 160,${y + amp * .6} 240,${y - amp * .4} C300,${y - amp} 360,${y + amp * .3} 420,${y - amp * .2}" stroke="#FFFBE6" stroke-opacity=".55" stroke-width="2" fill="none"/>`;
  }
  const ruong = svg(`<linearGradient id="rs" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#DDEFFC"/><stop offset="1" stop-color="#FFF3D9"/></linearGradient>`,
    `<rect width="400" height="800" fill="url(#rs)"/>
    <path d="M0,300 C60,240 120,230 180,262 C240,210 310,200 400,250 L400,400 L0,400 Z" fill="#9DB6CF" fill-opacity=".6"/>
    <path d="M0,340 C80,290 150,300 220,320 C280,290 340,290 400,310 L400,420 L0,420 Z" fill="#7FA38A" fill-opacity=".7"/>${terraces}`);

  // Cá chép trông trăng: cảm hứng tranh dân gian (giấy điệp, nét đen, đỏ son, vàng hòe, xanh chàm)
  let speck = "";
  let sd = 9; const rnd = () => (sd = (sd * 9301 + 49297) % 233280) / 233280;
  for (let i = 0; i < 160; i++) speck += `<circle cx="${(rnd() * 400).toFixed(1)}" cy="${(rnd() * 800).toFixed(1)}" r="${(.6 + rnd() * 1.2).toFixed(1)}"/>`;
  let waves = "";
  for (let r = 0; r < 7; r++) for (let c = -1; c < 9; c++) {
    const x = c * 50 + (r % 2) * 25, y = 560 + r * 34;
    waves += `<path d="M${x},${y} a25,25 0 0,1 50,0" /><path d="M${x + 10},${y} a15,15 0 0,1 30,0"/>`;
  }
  let scales = "";
  for (let r = 0; r < 3; r++) for (let c = 0; c < 6; c++) scales += `<path d="M${-58 + c * 20 + (r % 2) * 10},${-14 + r * 13} a10,10 0 0,0 20,0"/>`;
  const cachep = svg(`<linearGradient id="dp" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F6E7C8"/><stop offset="1" stop-color="#EBD3A6"/></linearGradient>`,
    `<rect width="400" height="800" fill="url(#dp)"/><g fill="#FFFFFF" fill-opacity=".55">${speck}</g>
    <rect x="14" y="14" width="372" height="772" fill="none" stroke="#B23A2A" stroke-width="5"/><rect x="24" y="24" width="352" height="752" fill="none" stroke="#2B2118" stroke-width="1.5"/>
    <circle cx="290" cy="170" r="58" fill="#F3C845" stroke="#2B2118" stroke-width="3"/><circle cx="290" cy="170" r="44" fill="none" stroke="#2B2118" stroke-width="1.2" stroke-dasharray="4 5"/>
    <g fill="none" stroke="#2A6A8A" stroke-width="2.4">${waves}</g>
    <g transform="translate(195 430) rotate(-28) scale(1.55)" stroke="#2B2118" stroke-width="3" stroke-linejoin="round">
      <path d="M58,0 L96,-34 C102,-14 100,14 96,34 Z" fill="#F2B33D"/>
      <path d="M-70,0 C-60,-32 -10,-44 40,-26 C60,-18 64,18 40,26 C-10,44 -60,32 -70,0 Z" fill="#D9472B"/>
      <path d="M-18,-40 C-6,-62 18,-62 26,-32 Z" fill="#F2B33D"/><path d="M-10,38 C0,56 16,52 20,30 Z" fill="#F2B33D"/>
      <g fill="none" stroke="#F6D27A" stroke-width="2">${scales}</g>
      <circle cx="-52" cy="-8" r="5" fill="#FFFFFF"/><circle cx="-52" cy="-8" r="2.4" fill="#2B2118" stroke="none"/>
      <path d="M-70,6 C-84,12 -92,22 -100,34" fill="none" stroke-width="2"/></g>`);

  return { sen, halong, dao, ruong, cachep };
})();
