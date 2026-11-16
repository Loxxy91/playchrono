/* PlayChrono pixel art: 16x16 icons and the block landscape of the header. All drawn for PlayChrono (no game assets). */
(function () {
  'use strict';
  const PAL = {
    G: '#62b13c', g: '#4a8f2c', d: '#8d5b34', D: '#6b4224', O: '#3b2510', w: '#b47c40', W: '#966330', Y: '#f4c84a', y: '#ffe07a',
    o: '#a77b12', K: '#2b2014', B: '#2f6fc9', b: '#5aa0f0', P: '#e8e2d0', p: '#cfc6ab', R: '#e0402c', r: '#a02a1c', S: '#9aa0a6',
    s: '#6d737a', L: '#7a4fbe', l: '#a57de0', H: '#f2f2f2', h: '#c8c8c8', E: '#1e1e22', F: '#ff8a2a', f: '#ffd24a', T: '#27a596', t: '#7be0d2',
    k: '#f08cc0',
  };
  const I = {
    grass: ['GGgGGGGgGGGGgGGG', 'GGGGgGGGGGgGGGGG', 'gGGGGGGgGGGGGGgG', 'GGgGGGGGGGgGGGGG', 'GdGGgdGGGdGgGGdG', 'dddGddddGdddGddd',
      'dDddddDdddddDddd', 'ddddDdddddDddddD', 'dDdddddDdddddddd', 'ddddDddddDdddDdd', 'dddddddDdddddddd', 'DddDdddddddDdddd',
      'dddddDdddDdddDdd', 'ddDddddddddddddd', 'dddddddDddDddddD', 'DddddDdddddddddd'],
    clock: ['.....oooooo.....', '...ooYYYYYYoo...', '..oYYyyyyyyYYo..', '.oYyyyyyKyyyyYo.', '.oYyyyyyKyyyyYo.', 'oYyyyyyyKyyyyyYo',
      'oYyyyyyyKyyyyyYo', 'oYyyyyyyKKKKyyYo', 'oYyyyyyyyyyyyyYo', 'oYyyyyyyyyyyyyYo', '.oYyyyyyyyyyyYo.', '.oYyyyyyyyyyyYo.',
      '..oYYyyyyyyYYo..', '...ooYYYYYYoo...', '.....oooooo.....', '................'],
    chest: ['................', '.OOOOOOOOOOOOOO.', '.OwwwwwwwwwwwwO.', '.OwWWWWWWWWWWwO.', '.OwwwwwwwwwwwwO.', '.OOOOOOYYOOOOOO.',
      '.OwwwwwYYwwwwwO.', '.OwWWWWooWWWWwO.', '.OwwwwwwwwwwwwO.', '.OwWWWWWWWWWWwO.', '.OwwwwwwwwwwwwO.', '.OwWWWWWWWWWWwO.',
      '.OwwwwwwwwwwwwO.', '.OOOOOOOOOOOOOO.', '................', '................'],
    globe: ['.....BBBBBB.....', '...BBbbGGbbBB...', '..BbbGGGGbbbbB..', '.BbbGGGGGbbbbbB.', '.BbGGGGGbbbGGbB.', 'BbbbGGGbbbGGGGbB',
      'BbbbbGGbbbGGGGbB', 'BbbbbbbbbGGGGbbB', 'BbGGbbbbbbGGbbbB', 'BGGGGbbbbbbbbbbB', '.BGGGGbbbbbbbbB.', '.BbGGbbbbbGGbbB.',
      '..BbbbbbbGGGbB..', '...BBbbbbbbBB...', '.....BBBBBB.....', '................'],
    book: ['................', '..OOOOOOOOOOO...', '..OLLLLLLLLLLO..', '..OLlllllllLLO..', '..OLlYYYYYlLLO..', '..OLlllllllLLO..',
      '..OLLLLLLLLLLO..', '..OLLlYyYlLLLO..', '..OLLLLLLLLLLO..', '..OLLLLLLLLLLO..', '..OLLLLLLLLLLO..', '..OLLLLLLLLLLO..',
      '..OPPPPPPPPPPPO.', '..OOOOOOOOOOOOO.', '................', '................'],
    compass: ['.....ssssss.....', '...ssSSSSSSss...', '..sSShhhhhhSSs..', '.sShhhhRhhhhSSs.', '.sShhhRRRhhhhSs.', 'sShhhhRRRhhhhhSs',
      'sShhhhhRhhhhhhSs', 'sShhhhhEhhhhhhSs', 'sShhhhhhKhhhhhSs', 'sShhhhhKKKhhhhSs', '.sShhhhKKKhhhSs.', '.sShhhhhKhhhhSs.',
      '..sSShhhhhhSSs..', '...ssSSSSSSss...', '.....ssssss.....', '................'],
    key: ['................', '................', '..ssssssssssss..', '.sHHHHHHHHHHHHs.', '.sHhhhhhhhhhhHs.', '.sHhhEEhhEEhhHs.',
      '.sHhhEEhhEEhhHs.', '.sHhhhhhhhhhhHs.', '.sHhhhhhhhhhhHs.', '.sHhhEEEEEEhhHs.', '.sHhhhhhhhhhhHs.', '.sHHHHHHHHHHHHs.',
      '..ssssssssssss..', '................', '................', '................'],
    torch: ['................', '......ffff......', '.....fFFFFf.....', '.....fFRRFf.....', '......FRRF......', '......rRRr......',
      '.......ww.......', '.......Ww.......', '.......ww.......', '.......Ww.......', '.......ww.......', '.......Ww.......',
      '.......ww.......', '.......WW.......', '................', '................'],
    trophy: ['................', '..oYYYYYYYYYYo..', 'oooYyyyyyyyyYooo', 'oY.YyyyyyyyyY.Yo', 'oY.YyyyyyyyyY.Yo', '.oYYyyyyyyyyYYo.',
      '..ooYyyyyyyyYoo.', '....oYyyyyyYo...', '.....oYyyyYo....', '......oYYo......', '.......YY.......', '......oYYo......',
      '....oYYYYYYo....', '....oooooooo....', '................', '................'],
    bed: ['................', '................', '................', '................', 'HH..............', 'HHhhhhhRRRRRRRR.',
      'HHhhhhhRrrrrrrR.', 'HHRRRRRRRRRRRRRR', 'HHrrrrrrrrrrrrrr', 'wwwwwwwwwwwwwwww', 'WWwwwwwwwwwwwwWW', 'WW............WW',
      'WW............WW', '................', '................', '................'],
    flame: ['.......F........', '......FF........', '......FFF.......', '.....FFfF...F...', '....FFffFF..FF..', '....FffffF.FFF..',
      '...FFffyfFFFfF..', '...FfffyyfFffF..', '..FFffyyyyffffF.', '..FffyyyyyyfffF.', '..FfyyyyyyyyffF.', '..FfyyyyyyyyyfF.',
      '...FfyyyyyyyfF..', '....FFfffffFF...', '......FFFFF.....', '................'],
    skull: ['................', '....HHHHHHHH....', '...HHHHHHHHHH...', '..HHHHHHHHHHHH..', '..HHHHHHHHHHHH..', '..HEEEHHHHEEEH..',
      '..HEEEHHHHEEEH..', '..HEEEHHHHEEEH..', '..HHHHHEEHHHHH..', '..HHHHHEEHHHHH..', '...HHHHHHHHHH...', '....HEHEHEHE....',
      '....HHHHHHHH....', '................', '................', '................'],
    boot: ['................', '................', '....OOOOO.......', '....OwwwO.......', '....OwWwO.......', '....OwwwO.......',
      '....OwWwO.......', '....OwwwO.......', '....OwwwOOOOO...', '....OwwwwwwwwO..', '....OwwwwwwwwwO.', '....OwWWWWWWwwO.',
      '....OOOOOOOOOOO.', '....ssssssssss..', '................', '................'],
    star: ['................', '.......Y........', '.......Y........', '......YyY.......', '......YyY.......', 'YYYYYYYyYYYYYY..',
      '.YyyyyyyyyyyY...', '..YyyyyyyyyY....', '...YyyyyyyY.....', '...YyyyyyyY.....', '..YyyyYyyyyY....', '..YyyY.YyyyY....',
      '.YyY.....YyY....', '.YY.......YY....', '................', '................'],
    heart: ['................', '................', '..RRRR...RRRR...', '.RRrrRR.RRrrRR..', 'RRrHHrRRRrrrrRR.', 'RRrHrrrrrrrrrRR.',
      'RRrrrrrrrrrrrRR.', '.RRrrrrrrrrrRR..', '..RRrrrrrrrRR...', '...RRrrrrrRR....', '....RRrrrRR.....', '.....RRrRR......',
      '......RRR.......', '.......R........', '................', '................'],
    elytra: ['................', '.LL..........LL.', 'LllL........LllL', 'LlllL......LlllL', 'LllllL....LllllL', 'LlLlllL..LlllLlL',
      'LlLllllLLllllLlL', '.LlLlllllllllLl.', '.LllLllllllLllL.', '..LllLllllLllL..', '..LlllL..LlllL..', '...LllL..LllL...',
      '...LlL....LlL...', '....L......L....', '................', '................'],
    medal1: ['................', '....RRR..RRR....', '.....RrRRrR.....', '......RRRR......', '.....oYYYYo.....', '....oYyyyyYo....',
      '...oYyyYYyyYo...', '...oYyYyyYyYo...', '...oYyyYYyyYo...', '...oYyyYyyyYo...', '....oYyyyyYo....', '.....oYYYYo.....',
      '......oooo......', '................', '................', '................'],
  };
  I.present = ['................', '....yy....yy....', '...yooy..yooy...', '....yyoyyoyy....', '......oYYo......', '.RRRRRRYYRRRRRR.', '.RrrrrrYYrrrrrR.', '.RRRRRRYYRRRRRR.', '..RRRRRYYRRRRR..', '..RrrrrYYrrrrR..', '..YYYYYYYYYYYY..', '..RrrrrYYrrrrR..', '..RRRRRYYRRRRR..', '..RrrrrYYrrrrR..', '..RRRRRYYRRRRR..', '................'];
  I.firework = ['.......Y........', '..k....Y....k...', '...Y...y...Y....', '....Y..y..Y.....', '.....y.y.y......', '......yyy.......', 'kYyyyyyHyyyyyYk.', '......yyy.......', '.....y.y.y......', '....Y..y..Y.....', '...Y...y...Y....', '..k....Y....k...', '.......Y........', '.......o........', '.......o........', '.......o........'];
  I.egg = ['................', '......kkkk......', '.....kkkkkk.....', '....kkHkkHkk....', '....kkkkkkkk....', '...kkkkkkkkkk...', '...HbHbHbHbHb...', '...bHbHbHbHbH...', '...kkkkkkkkkk...', '...kkkkkkkkkk...', '...kkHkkHkkHk...', '....kkkkkkkk....', '....kkkkkkkk....', '.....kkkkkk.....', '......kkkk......', '................'];
  I.pumpkin = ['.......Gg.......', '.......g........', '...FFFFFFFFFF...', '..FFrFFFFFFrFF..', '.FFrFFFFFFFFrFF.', '.FFrffFFFFffrFF.', '.FFrffFFFFffrFF.', '.FFrFFFFFFFFrFF.', '.FFrFFFffFFFrFF.', '.FFrFFFFFFFFrFF.', '.FFfFfFfFfFfrFF.', '.FFrfFfFfFfFrFF.', '..FFrFFFFFFrFF..', '...FFFFFFFFFF...', '................', '................'];
  const cache = {};
  /** <svg> string of a pixel icon; one path per colour (cheap DOM, crisp at any size). */
  function svg(name, size, extraClass) {
    const key = name + '|' + (size || 16) + '|' + (extraClass || '');
    if (cache[key]) return cache[key];
    const g = I[name];
    if (!g) return '';
    const paths = {};
    for (let y = 0; y < 16; y++) {
      const row = g[y] || '';
      for (let x = 0; x < 16; x++) {
        const c = row[x];
        if (!c || c === '.') continue;
        (paths[c] = paths[c] || []).push(`M${x} ${y}h1v1h-1z`);
      }
    }
    const s = size || 16;
    const out = `<svg class="px ${extraClass || ''}" width="${s}" height="${s}" viewBox="0 0 16 16" shape-rendering="crispEdges" aria-hidden="true">`
      + Object.entries(paths).map(([c, p]) => `<path fill="${PAL[c]}" d="${p.join('')}"/>`).join('') + '</svg>';
    cache[key] = out;
    return out;
  }

  // -------------------------------------------------------------------------------------------- seasons
  /** Easter Sunday (Gregorian calendar, anonymous algorithm): [month 1-12, day]. */
  function easter(y) {
    const a = y % 19, b = Math.floor(y / 100), c = y % 100, d = Math.floor(b / 4), e = b % 4, f = Math.floor((b + 8) / 25);
    const g = Math.floor((b - f + 1) / 3), h = (19 * a + b - d - g + 15) % 30, i = Math.floor(c / 4), k = c % 4;
    const l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451);
    return [Math.floor((h + l - 7 * m + 114) / 31), ((h + l - 7 * m + 114) % 31) + 1];
  }
  /**
   * Seasonal theme for a local date, every year: New Year 30 Dec - 2 Jan (wins over Christmas on the 30th),
   * Christmas 10 - 29 Dec, Halloween 10 Oct - 2 Nov, Easter on Easter Sunday. Null otherwise.
   */
  function season(date) {
    const m = date.getMonth() + 1, d = date.getDate();
    if ((m === 12 && d >= 30) || (m === 1 && d <= 2)) return 'newyear';
    if (m === 12 && d >= 10) return 'xmas';
    if ((m === 10 && d >= 10) || (m === 11 && d <= 2)) return 'halloween';
    const [em, ed] = easter(date.getFullYear());
    return m === em && d === ed ? 'easter' : null;
  }
  const SEASON_ICON = { xmas: 'present', newyear: 'firework', easter: 'egg', halloween: 'pumpkin' };

  // -------------------------------------------------------------------------------------------- landscape
  function rng(seed) { return () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296); }
  /** Seeded block terrain: hills, dirt, stone, trees, clouds, sun or moon + stars. Returns an SVG string. */
  function landscape(cols, night, season) {
    const rows = 26, r = rng(20261101), h = [];
    if (season === 'newyear') night = true; // fireworks need a night sky
    let y = 17;
    for (let x = 0; x < cols; x++) {
      if (r() < 0.32) y += r() < 0.5 ? -1 : 1;
      y = Math.max(13, Math.min(21, y));
      h.push(y);
    }
    let sky = night ? ['#0b1430', '#1b2a5a'] : ['#6fb4ff', '#bfe2ff'];
    let top = night ? '#2f6a2a' : '#5fae3b', topDark = night ? '#24521f' : '#4b922d';
    if (season === 'xmas') { top = night ? '#c9d6e8' : '#f4f8ff'; topDark = night ? '#aebfd6' : '#dbe6f2'; } // snow
    if (season === 'halloween') { sky = night ? ['#120a1f', '#3a1640'] : ['#4a2a6b', '#e07a3a']; top = night ? '#3d5a1e' : '#6b8f2a'; topDark = night ? '#2e4416' : '#557320'; }
    if (season === 'easter') sky = night ? ['#1a1f4a', '#4a3a7a'] : ['#8fd0ff', '#ffe3f1'];
    if (season === 'newyear') sky = ['#090b26', '#2a1f5c'];
    const dirt = night ? '#4d3420' : '#8d5b34', dirtDark = night ? '#3b2717' : '#744828';
    const stone = night ? '#3a3d44' : '#8a8f96', stoneDark = night ? '#2c2f35' : '#757a81';
    const byColor = {};
    const rect = (x, y2, w, hh, c) => { (byColor[c] = byColor[c] || []).push(`M${x} ${y2}h${w}v${hh}h-${w}z`); };
    if (night) {
      for (let i = 0; i < cols * 0.6; i++) rect(Math.floor(r() * cols), Math.floor(r() * 11), 1, 1, r() < 0.3 ? '#ffffff' : '#9fb4ff');
      if (season === 'halloween') { rect(cols - 19, 2, 6, 6, '#ffb347'); rect(cols - 18, 3, 1, 1, '#e0902a'); rect(cols - 15, 5, 2, 1, '#e0902a'); }
      else { rect(cols - 18, 3, 4, 4, '#f4f1de'); rect(cols - 18, 3, 1, 1, '#d7d3bd'); rect(cols - 16, 5, 1, 1, '#d7d3bd'); }
    } else if (season === 'halloween') {
      rect(cols - 20, 3, 5, 5, '#ffcf70'); rect(cols - 19, 4, 3, 3, '#ffb347');
    } else {
      rect(cols - 20, 2, 5, 5, '#fff3a6'); rect(cols - 19, 3, 3, 3, '#ffe25a');
    }
    let lastTree = -99, lastDeco = -99;
    const pastel = [['#f08cc0', '#ffffff'], ['#8fd6ff', '#fff3a6'], ['#b9f28f', '#f08cc0'], ['#ffd27a', '#8fd6ff']];
    for (let x = 0; x < cols; x++) {
      const t = h[x];
      rect(x, t, 1, 1, (x * 7 + t) % 5 === 0 ? topDark : top);
      for (let yy = t + 1; yy < rows; yy++) {
        const deep = yy > t + 3;
        const n = r();
        rect(x, yy, 1, 1, deep ? (n < 0.25 ? stoneDark : stone) : (n < 0.25 ? dirtDark : dirt));
      }
      if (x > 3 && x < cols - 4 && x - lastTree > 5 && r() < 0.07) { // tree
        lastTree = x;
        let trunk = night ? '#4a3320' : '#6e4a2a', leaf = night ? '#1f4d1d' : '#3f8a2a', leaf2 = night ? '#2a5f26' : '#4f9f34';
        if (season === 'halloween') { leaf = night ? '#7a3a10' : '#c8641e'; leaf2 = night ? '#5c2a0a' : '#a14a12'; }
        for (let k = 1; k <= 3; k++) rect(x, t - k, 1, 1, trunk);
        for (let ly = t - 6; ly <= t - 3; ly++) for (let lx = x - 2; lx <= x + 2; lx++) {
          if ((ly === t - 6 || ly === t - 3) && Math.abs(lx - x) === 2) continue;
          let c = r() < 0.3 ? leaf2 : leaf;
          if (season === 'xmas') {
            const topRow = ly === t - 6 || (ly === t - 5 && Math.abs(lx - x) === 2);
            if (topRow) c = night ? '#c9d6e8' : '#f4f8ff'; // snow on the leaves
            else if (r() < 0.16) c = ['#e0402c', '#f4c84a', '#5aa0f0'][Math.floor(r() * 3)]; // baubles
          }
          rect(lx, ly, 1, 1, c);
        }
        continue;
      }
      // seasonal decorations on the ground, away from trees
      if (season && x > 2 && x < cols - 3 && x - lastTree > 3 && x - lastDeco > 4 && r() < 0.12) {
        lastDeco = x;
        if (season === 'xmas') { // present
          rect(x, t - 2, 2, 2, '#e0402c'); rect(x, t - 2, 2, 1, '#c62f20'); rect(x + 1, t - 2, 1, 2, '#f4c84a'); rect(x + 1, t - 3, 1, 1, '#f4c84a');
        } else if (season === 'halloween') { // pumpkin (lit at night)
          rect(x, t - 2, 3, 2, '#e07b1a'); rect(x + 1, t - 3, 1, 1, '#4b7a22');
          rect(x, t - 2, 1, 1, night ? '#ffd23f' : '#3a1d06'); rect(x + 2, t - 2, 1, 1, night ? '#ffd23f' : '#3a1d06');
        } else if (season === 'easter') { // painted egg, or a flower
          if (r() < 0.6) { const [a, b] = pastel[Math.floor(r() * pastel.length)]; rect(x, t - 2, 2, 2, a); rect(x, t - 2, 1, 1, b); }
          else { rect(x, t - 2, 1, 1, ['#ff7ab6', '#ffe25a', '#ffffff'][Math.floor(r() * 3)]); rect(x, t - 1, 1, 1, '#3f8a2a'); }
        }
      }
    }
    return `<svg viewBox="0 0 ${cols} ${rows}" preserveAspectRatio="xMidYMax slice" shape-rendering="crispEdges" aria-hidden="true">`
      + `<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${sky[0]}"/><stop offset="1" stop-color="${sky[1]}"/></linearGradient></defs>`
      + `<rect width="${cols}" height="${rows}" fill="url(#sky)"/>`
      + Object.entries(byColor).map(([c, d]) => `<path fill="${c}" d="${d.join('')}"/>`).join('') + '</svg>';
  }
  /** Animated seasonal layer (CSS-animated, frozen when the system asks for less motion). */
  function fx(cols, season) {
    const r = rng(1225), parts = [];
    if (season === 'xmas') {
      for (let i = 0; i < cols * 0.7; i++) parts.push(`<rect x="${Math.floor(r() * cols)}" y="${Math.floor(r() * 52)}" width="1" height="1" fill="#ffffff" opacity="${(0.55 + r() * 0.45).toFixed(2)}"/>`);
      return `<svg class="fx-snow" viewBox="0 0 ${cols} 52" preserveAspectRatio="xMidYMin slice" shape-rendering="crispEdges" aria-hidden="true">${parts.join('')}</svg>`;
    }
    if (season === 'newyear') {
      const cols3 = ['#ffd24a', '#f08cc0', '#7be0d2', '#ff8a2a', '#a57de0'];
      for (let i = 0; i < 6; i++) {
        const cx = 6 + Math.floor(r() * (cols - 30)), cy = 3 + Math.floor(r() * 6), c = cols3[i % cols3.length], ray = [];
        for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]])
          for (let k = 1; k <= 3; k++) ray.push(`M${cx + dx * k} ${cy + dy * k}h1v1h-1z`);
        parts.push(`<g class="fw" style="animation-delay:${(i * 0.9).toFixed(1)}s"><path fill="${c}" d="M${cx} ${cy}h1v1h-1z${ray.join('')}"/></g>`);
      }
      return `<svg class="fx-fw" viewBox="0 0 ${cols} 26" preserveAspectRatio="xMidYMax slice" shape-rendering="crispEdges" aria-hidden="true">${parts.join('')}</svg>`;
    }
    if (season === 'halloween') {
      for (let i = 0; i < 4; i++) {
        const x = Math.floor(r() * (cols - 10)), y = 2 + Math.floor(r() * 6);
        parts.push(`<g class="bat" style="animation-delay:${(-i * 7).toFixed(0)}s"><path fill="#1b1020" d="M${x} ${y}h1v1h-1zM${x + 1} ${y + 1}h1v1h-1zM${x + 2} ${y + 1}h1v1h-1zM${x + 3} ${y}h1v1h-1zM${x + 2} ${y}h0v0z"/></g>`);
      }
      return `<svg class="fx-bats" viewBox="0 0 ${cols} 26" preserveAspectRatio="xMidYMax slice" shape-rendering="crispEdges" aria-hidden="true">${parts.join('')}</svg>`;
    }
    return '';
  }
  function clouds(cols, night) {
    const r = rng(77), parts = [], c = night ? 'rgba(255,255,255,.10)' : '#ffffff', c2 = night ? 'rgba(255,255,255,.06)' : '#eef6ff';
    for (let i = 0; i < 7; i++) {
      const x = Math.floor(r() * cols), y = 1 + Math.floor(r() * 7), w = 6 + Math.floor(r() * 10);
      parts.push(`<rect x="${x}" y="${y}" width="${w}" height="2" fill="${c}"/><rect x="${x + 2}" y="${y - 1}" width="${w - 4}" height="1" fill="${c}"/><rect x="${x + 1}" y="${y + 2}" width="${w - 2}" height="1" fill="${c2}"/>`);
    }
    return `<svg viewBox="0 0 ${cols} 26" preserveAspectRatio="xMidYMin slice" shape-rendering="crispEdges" aria-hidden="true">${parts.join('')}</svg>`;
  }
  /** Deterministic 8x8 pixel avatar from a player's UUID (no third-party head service, no tracking). */
  function avatar(uuid, size) {
    let n = 0;
    for (const ch of String(uuid || '?')) n = (n * 31 + ch.charCodeAt(0)) >>> 0;
    const r = rng(n || 1);
    const hues = [[95, 55], [210, 60], [35, 70], [270, 45], [8, 60], [170, 50]];
    const [hu, sat] = hues[Math.floor(r() * hues.length)];
    const skin = `hsl(${25 + r() * 10},${45 + r() * 15}%,${55 + r() * 15}%)`;
    const hair = `hsl(${hu},${sat}%,${25 + r() * 15}%)`;
    let d = '';
    for (let y = 0; y < 8; y++) for (let x = 0; x < 4; x++) {
      const isHair = y < 2 || (y < 3 && r() < 0.5) || ((x === 0) && y < 5 && r() < 0.5);
      const fill = y === 4 && (x === 1) ? '#ffffff' : y === 4 && x === 2 ? '#2b2b6b' : isHair ? hair : skin;
      const cell = (xx) => `<rect x="${xx}" y="${y}" width="1" height="1" fill="${fill}"/>`;
      d += cell(x) + cell(7 - x);
    }
    return `<svg class="px avatar" width="${size || 24}" height="${size || 24}" viewBox="0 0 8 8" shape-rendering="crispEdges" aria-hidden="true">${d}<rect x="3" y="6" width="2" height="1" fill="rgba(0,0,0,.35)"/></svg>`;
  }
  window.PPIcons = { svg, landscape, clouds, fx, avatar, season, easter, seasonIcon: (s) => SEASON_ICON[s], names: Object.keys(I) };
})();
