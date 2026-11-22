/* PlayChrono dashboard: the player's own data only.
 * Opened from ~/.playchrono/dashboard (file:// + data.js) or served live by the mod on http://127.0.0.1 (/api/data.json).
 */
(function () {
  'use strict';
  const C = window.PPCharts, I = window.PP_I18N, PI = window.PPIcons, esc = C.esc;
  const store = {
    get(k, d) { try { const v = localStorage.getItem('pp.' + k); return v == null ? d : v; } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem('pp.' + k, v); } catch (e) { /* private mode */ } },
  };
  const $ = (sel, root) => (root || document).querySelector(sel);
  const css = (n) => getComputedStyle(document.documentElement).getPropertyValue(n).trim();
  const CLS = ['p', 'u', 'k', 'm', 'l', 'b']; // stacking order = validated palette slot order (grass, lapis, gold...)
  const CLS_COLOR = { p: '--s1', u: '--s2', k: '--s3', m: '--s4', l: '--s5', b: '--s6' };
  const STATE_GROUP = { PLAY: 'p', BOOT: 'l', LOADING: 'l', IG_LOADING: 'l', RELOAD: 'l', LAG: 'l', TITLE: 'm', SP_MENU: 'm', MP_MENU: 'm', REALMS: 'm', OPTIONS: 'm', MODS: 'm', MENU: 'm', DISCONNECTED: 'm' };

  let lang = 'en', D = null, period = store.get('period', '30'), tab = (location.hash || '#overview').slice(1);

  // ============================================================================================ i18n & format
  function t(key, vars) {
    let s = (I[lang] && I[lang][key]) || I.en[key] || key;
    if (vars) for (const k in vars) s = s.split('{' + k + '}').join(vars[k]);
    return s;
  }
  function pickLang() {
    const saved = store.get('lang', '');
    if (saved && I[saved]) return saved;
    const game = D && D.env && D.env.lang ? String(D.env.lang).split('_')[0] : '';
    if (I[game]) return game;
    const nav = (navigator.language || 'en').slice(0, 2).toLowerCase();
    return I[nav] ? nav : 'en';
  }
  const nf = (o) => new Intl.NumberFormat(lang, o);
  const num = (n, d) => nf({ maximumFractionDigits: d == null ? 0 : d }).format(n || 0);
  const compact = (n) => nf({ notation: 'compact', maximumFractionDigits: 1 }).format(n || 0);
  function dur(ms) {
    ms = ms || 0;
    if (ms === 0) return '0 min';
    if (ms < 60000) return Math.round(ms / 1000) + ' s';
    const m = Math.round(ms / 60000), h = Math.floor(m / 60);
    if (h >= 100) return num(h) + ' h';
    return h ? h + ' h ' + String(m % 60).padStart(2, '0') : m + ' min';
  }
  const hours = (ms) => num((ms || 0) / 3.6e6, (ms || 0) < 3.6e7 ? 1 : 0) + ' h';
  const hoursAxis = (ms) => num(ms / 3.6e6, ms < 3.6e6 * 2 && ms > 0 ? 1 : 0) + ' h';
  const minAxis = (ms) => num(ms / 60000) + ' min';
  const pct = (a, b) => (b ? num((a / b) * 100, a / b < 0.1 ? 1 : 0) : '0') + ' %';
  function dist(blocks) {
    blocks = blocks || 0;
    return blocks < 1000 ? num(blocks) + ' ' + t('u.blocks') : num(blocks / 1000, blocks < 1e5 ? 1 : 0) + ' km';
  }
  const hm = (min) => String(Math.floor(min / 60) % 24).padStart(2, '0') + ':' + String(Math.round(min) % 60).padStart(2, '0');
  const dateFmt = (d, o) => new Intl.DateTimeFormat(lang, o || { day: 'numeric', month: 'short', year: 'numeric' }).format(typeof d === 'string' ? parseDay(d) : new Date(d));
  function parseDay(s) { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d, 12); }
  const iso = C.iso;
  const color = (cls) => css(CLS_COLOR[cls]);
  const slot = (i) => css('--s' + ((i % 6) + 1));
  const inkOn = (i) => (i === 2 ? '#1a1300' : '#fff'); // dark text on gold
  const ctxType = (k) => k.split(':')[0];
  function ctxLabel(k) {
    const meta = (D.ctx || {})[k] || {};
    const i = k.indexOf(':');
    const raw = i >= 0 ? k.slice(i + 1) : k;
    if (ctxType(k) === 'server' && meta.name && meta.name !== raw && meta.name !== '?') return meta.name + ' (' + raw + ')';
    return raw;
  }
  const verLabel = (v) => (!v || v === '?' ? t('unknown') : v);

  // ============================================================================================ data helpers
  function allDays() { return Object.keys(D.days || {}).sort(); }
  function periodDates() {
    const today = new Date();
    today.setHours(12, 0, 0, 0);
    const keys = allDays();
    let n = period === 'all' ? (keys.length ? Math.round((today - parseDay(keys[0])) / 864e5) + 1 : 1) : Number(period);
    n = Math.max(1, n);
    const out = [];
    for (let i = n - 1; i >= 0; i--) { const d = new Date(today); d.setDate(today.getDate() - i); out.push(iso(d)); }
    return out;
  }
  function inPeriod() {
    const dates = periodDates();
    return dates.map((d) => [d, D.days[d] || null]);
  }
  const played = (d) => d && (d.a >= 60000 || (d.imp && d.o >= 60000));
  function sum(list, f) { let s = 0; for (const [k, d] of list) if (d) s += f(d, k) || 0; return s; }
  function sumMap(list, key, sub) {
    const out = {};
    for (const [, d] of list) {
      if (!d) continue;
      const m = sub ? (d[key] || {})[sub] : d[key];
      if (!m) continue;
      for (const k in m) {
        const v = m[k];
        if (Array.isArray(v)) { out[k] = out[k] || v.map(() => 0); v.forEach((x, i) => (out[k][i] += x)); }
        else out[k] = (out[k] || 0) + v;
      }
    }
    return out;
  }
  function mergeN(list) {
    const out = {};
    const merge = (dst, src) => {
      for (const k in src) {
        const v = src[k];
        if (v && typeof v === 'object') merge(dst[k] = dst[k] || {}, v);
        else if (typeof v === 'number') {
          if (!(k in dst)) dst[k] = v;
          else if (k.endsWith('Max')) dst[k] = Math.max(dst[k], v);
          else if (k.endsWith('Min')) dst[k] = Math.min(dst[k], v);
          else dst[k] += v;
        }
      }
    };
    for (const [, d] of list) if (d && d.n) merge(out, d.n);
    return out;
  }
  const entries = (m) => Object.entries(m || {}).sort((a, b) => b[1] - a[1]);
  function streaks() {
    const keys = allDays();
    let best = 0, run = 0, prev = null;
    for (const k of keys) {
      if (!played(D.days[k])) continue;
      const day = parseDay(k);
      run = prev && Math.round((day - prev) / 864e5) === 1 ? run + 1 : 1;
      best = Math.max(best, run);
      prev = day;
    }
    let cur = 0;
    const c = new Date();
    c.setHours(12, 0, 0, 0);
    if (!played(D.days[iso(c)])) c.setDate(c.getDate() - 1);
    while (played(D.days[iso(c)])) { cur++; c.setDate(c.getDate() - 1); }
    return { cur, best };
  }
  function sessionsInPeriod() {
    const dates = periodDates();
    const from = parseDay(dates[0]).getTime() - 12 * 3600e3;
    return (D.sessions || []).filter((s) => s.s >= from);
  }

  // ============================================================================================ UI scaffolding
  function card(parent, o) {
    const c = document.createElement('section');
    c.className = 'card' + (o.wide ? ' wide' : '') + (o.full ? ' full' : '');
    c.style.setProperty('--i', String(cardIndex++));
    c.innerHTML = `<header><div><h3>${o.icon ? PI.svg(o.icon, 22) : ''}${esc(o.title)}</h3>${o.sub ? `<p>${esc(o.sub)}</p>` : ''}</div>`
      + (o.table ? `<button class="mc-btn tbl-btn" type="button">${esc(t('ui.table'))}</button>` : '') + '</header><div class="body"></div>';
    parent.appendChild(c);
    const body = $('.body', c);
    const draw = () => { try { o.render(body); } catch (e) { console.error(e); C.empty(body, '⚠ ' + e.message); } };
    let asTable = false;
    if (o.table) {
      $('.tbl-btn', c).addEventListener('click', (e) => {
        asTable = !asTable;
        e.target.textContent = asTable ? t('ui.chart') : t('ui.table');
        if (asTable) renderTable(body, o.table()); else draw();
      });
    }
    draw();
    renderers.push(() => { if (!asTable) draw(); });
    return c;
  }
  function renderTable(host, tb) {
    host.innerHTML = '';
    const wrap = document.createElement('div');
    wrap.className = 'tbl-wrap';
    wrap.innerHTML = `<table><thead><tr>${tb.head.map((h) => `<th>${esc(h)}</th>`).join('')}</tr></thead><tbody>`
      + tb.rows.map((r) => `<tr>${r.map((v) => `<td>${esc(v)}</td>`).join('')}</tr>`).join('') + '</tbody></table>';
    host.appendChild(wrap);
  }
  function tiles(parent, list) {
    const w = document.createElement('div');
    w.className = 'tiles';
    const items = list.filter(Boolean);
    w.innerHTML = items.map((x, i) => `<div class="tile${x.hero ? ' hero-tile' : ''}" style="--i:${i}">`
      + `<span class="slot">${PI.svg(x.icon || 'grass', x.hero ? 36 : 28)}</span><span class="txt"><span class="lbl">${esc(x.label)}</span>`
      + `<span class="val">${esc(x.value)}</span>${x.sub ? `<span class="sub">${esc(x.sub)}</span>` : ''}`
      + (x.xp ? `<span class="xp" role="img" aria-label="${esc(x.xp.label)}"><i style="width:${Math.max(1, Math.min(100, x.xp.pct))}%"></i></span><span class="xp-level">${esc(x.xp.label)}</span>` : '')
      + '</span></div>').join('');
    parent.appendChild(w);
    items.forEach((x, i) => { if (x.raw != null && x.fmt) countUp(w.children[i].querySelector('.val'), x.raw, x.fmt); });
  }
  const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)');
  function countUp(el, to, fmt) {
    if (reduceMotion.matches || document.documentElement.classList.contains('no-anim') || !(to > 0)) { el.textContent = fmt(to); return; }
    const t0 = performance.now(), d = 700;
    const step = (now) => {
      const k = Math.min(1, (now - t0) / d), e = 1 - Math.pow(1 - k, 3);
      el.textContent = fmt(to * e);
      if (k < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
  function grid(parent) { const g = document.createElement('div'); g.className = 'grid'; parent.appendChild(g); return g; }
  let renderers = [], cardIndex = 0, renderSeq = 0;

  // ============================================================================================ tabs
  const TABS = ['overview', 'schedule', 'activity', 'places', 'versions', 'world', 'inputs', 'perf', 'leaderboard'];
  const TAB_ICON = { overview: 'grass', schedule: 'clock', activity: 'chest', places: 'globe', versions: 'book', world: 'compass', inputs: 'key', perf: 'torch', leaderboard: 'trophy' };

  /**
   * Draws the current tab. quiet = live refresh: no animation at all (the page used to replay its entrance every
   * minute). Otherwise boxes below the fold appear as they scroll into view.
   */
  function renderTab(quiet) {
    renderers = [];
    cardIndex = 0;
    renderSeq++;
    const main = $('#main');
    main.classList.toggle('quiet', !!quiet);
    main.innerHTML = '';
    if (!quiet) requestAnimationFrame(() => revealOnScroll(main));
    if (tab === 'leaderboard') { leaderboard(main); return; }
    if (!D || !Object.keys(D.days || {}).length) { C.empty(main, t('ui.empty')); return; }
    const list = inPeriod();
    const imported = sessionsInPeriod().filter((s) => s.imp).length;
    if (imported) {
      const n = document.createElement('div');
      n.className = 'note';
      n.textContent = t('ui.imported', { n: num(imported) });
      main.appendChild(n);
    }
    ({ overview, schedule, activity, places, versions, world, inputs, perf }[tab] || overview)(main, list);
  }

  // ---------------------------------------------------------------------------------------------- motion
  const motionOff = () => reduceMotion.matches || document.documentElement.classList.contains('no-anim');
  let revealIO = null;
  /** Boxes that start below the screen fade and rise in when they arrive; their charts grow at that moment. */
  function revealOnScroll(main) {
    if (revealIO) revealIO.disconnect();
    if (motionOff() || !('IntersectionObserver' in window)) return;
    const items = [...main.querySelectorAll('.card, .tiles, .note')].filter((el) => el.getBoundingClientRect().top > window.innerHeight - 20);
    if (!items.length) return;
    items.forEach((el) => el.classList.add('reveal'));
    revealIO = new IntersectionObserver((entries) => {
      const shown = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top || a.boundingClientRect.left - b.boundingClientRect.left);
      shown.forEach((e, i) => {
        e.target.style.setProperty('--rd', `${Math.min(i, 5) * 70}ms`);
        e.target.classList.add('in');
        revealIO.unobserve(e.target);
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
    items.forEach((el) => revealIO.observe(el));
  }
  /** Tab change: the old page slides out, the new one slides in from the side of the hotbar move. */
  let switching = 0;
  function switchTab(next) {
    const main = $('#main');
    const from = TABS.indexOf(tab), to = TABS.indexOf(next);
    tab = next;
    const dir = to >= from ? 1 : -1;
    const seq = ++switching;
    const draw = () => {
      if (seq !== switching) return;
      main.classList.remove('page-out');
      shell();
      main.style.setProperty('--dir', dir);
      main.classList.remove('page-in');
      void main.offsetWidth; // restart the animation
      main.classList.add('page-in');
      const heroBottom = $('.hero').offsetHeight - 40;
      if (window.scrollY > heroBottom) window.scrollTo({ top: Math.max(0, heroBottom - 20), behavior: motionOff() ? 'auto' : 'smooth' });
    };
    if (motionOff() || !main.children.length) { draw(); return; }
    main.style.setProperty('--dir', dir);
    main.classList.remove('page-in');
    main.classList.add('page-out');
    setTimeout(draw, 150);
  }
  /** Header depth on scroll + the XP-bar style scroll indicator. One rAF per frame at most. */
  function scrollEffects() {
    let pending = false;
    const land = $('#land'), clouds = $('#clouds'), fx = $('#fx'), bar = $('#scrollxp');
    const update = () => {
      pending = false;
      const y = window.scrollY, max = document.documentElement.scrollHeight - window.innerHeight;
      bar.classList.toggle('on', max > 200 && y > 4);
      bar.firstChild.style.width = (max > 0 ? Math.min(100, (y / max) * 100) : 0) + '%';
      if (motionOff()) { land.style.translate = clouds.style.translate = fx.style.translate = ''; return; }
      const py = Math.min(y, 400); // the header is out of sight beyond that
      land.style.translate = `0 ${(py * 0.35).toFixed(1)}px`;
      clouds.style.translate = `0 ${(py * 0.18).toFixed(1)}px`;
      fx.style.translate = `0 ${(py * 0.25).toFixed(1)}px`;
    };
    window.addEventListener('scroll', () => { if (!pending) { pending = true; requestAnimationFrame(update); } }, { passive: true });
    window.addEventListener('resize', update);
    update();
  }

  // ---------------------------------------------------------------------------------------------- overview
  function overview(main, list) {
    const a = sum(list, (d) => d.a), o = sum(list, (d) => d.o), k = sum(list, (d) => d.k);
    const daysPlayed = list.filter(([, d]) => played(d)).length;
    const sess = sessionsInPeriod();
    const longest = sess.reduce((m, s) => Math.max(m, s.e - s.s), 0);
    const st = streaks();
    const cls = sumMap(list, 'cls');
    const weeks = Math.max(1, list.length / 7);
    const allA = Object.values(D.days).reduce((x, d) => x + (d.a || 0), 0);
    const level = Math.floor(allA / 3.6e6), into = allA - level * 3.6e6;
    const since = D.installedAt || (D.me && D.me.since) || 0;
    backupReminder(main, allA);
    tiles(main, [
      { label: t('kpi.active'), value: hours(a), raw: a, fmt: hours, sub: since ? t('ui.since', { d: dateFmt(since) }) : t('kpi.active_sub'), hero: true, icon: 'grass',
        xp: { pct: (into / 3.6e6) * 100, label: t('hero.level', { n: num(level) }) + ' · ' + t('hero.next', { t: dur(3.6e6 - into), n: num(level + 1) }) } },
      { label: t('kpi.open'), value: hours(o), icon: 'clock' },
      { label: t('kpi.afk'), value: hours(k), sub: pct(k, o), icon: 'bed' },
      { label: t('kpi.perDay'), value: dur(daysPlayed ? a / daysPlayed : 0), sub: t('kpi.days') + ' : ' + daysPlayed + ' / ' + list.length, icon: 'star' },
      { label: t('kpi.perWeek'), value: dur(a / weeks), icon: 'clock' },
      { label: t('kpi.sessions'), value: num(sess.length), sub: t('kpi.longest') + ' : ' + dur(longest), icon: 'chest' },
      { label: t('kpi.streak'), value: t('u.days', { n: st.cur }), sub: t('kpi.bestStreak') + ' : ' + t('u.days', { n: st.best }), icon: 'flame' },
      { label: t('kpi.menus'), value: hours(cls.m || 0), sub: t('kpi.loading') + ' : ' + hours(cls.l || 0), icon: 'book' },
    ]);
    const g = grid(main);
    card(g, {
      icon: 'clock', title: t('ch.daily'), sub: t('ch.daily_sub'), full: true,
      render: (host) => dailyColumns(host, list),
      table: () => ({ head: [t('col.date'), ...CLS.map((c) => t('cls.' + c))], rows: list.map(([dt, d]) => [dt, ...CLS.map((c) => dur(d && d.cls ? d.cls[c] : 0))]) }),
    });
    card(g, {
      title: t('ch.split'), sub: t('ch.split_sub'), wide: true,
      render: (host) => C.segbar(host, { parts: CLS.map((c) => ({ name: t('cls.' + c), value: cls[c] || 0, color: color(c), ink: c === 'k' ? '#1a1300' : '#fff' })), fmt: dur, emptyText: t('ui.empty') }),
      table: () => ({ head: [t('col.what'), t('col.time'), '%'], rows: CLS.map((c) => [t('cls.' + c), dur(cls[c] || 0), pct(cls[c] || 0, o)]) }),
    });
    const today = D.days[iso(new Date())];
    const avg = daysPlayed ? a / daysPlayed : 0;
    card(g, {
      title: t('ui.today'),
      render: (host) => {
        const ta = today ? today.a : 0;
        host.innerHTML = `<div class="big">${esc(dur(ta))}</div><p class="muted">${esc(t('ui.vsAvg'))} : ${esc(dur(avg))}</p>`
          + `<div class="meter"><i style="width:${Math.min(100, avg ? (ta / avg) * 100 : 0)}%"></i></div>`
          + (today ? `<p class="muted">${esc(t('kpi.open'))} : ${esc(dur(today.o))} · ${esc(t('kpi.afk'))} : ${esc(dur(today.k))}</p>` : '');
      },
    });
    weeklyCard(g, 'full');
    funCards(g, list);
    backupCard(g);
  }

  // ---------------------------------------------------------------------------------------------- fun facts
  /** Death message key ("attack.lava", "fell.accident.ladder"...) -> a family with a translated name. */
  const DEATH_CAUSES = [
    ['lava', /lava/], ['fire', /infire|onfire|hotfloor|campfire/], ['fall', /^fell|^attack\.fall|flykillintowall|stalagmite/],
    ['drown', /drown/], ['explosion', /explosion|fireworks|badrespawnpoint/], ['player', /^attack\.player/],
    ['void', /outofworld|outsideborder/], ['starve', /starve/], ['cactus', /cactus|sweetberry/], ['suffocate', /inwall|cramming/],
    ['freeze', /freeze/], ['lightning', /lightning/], ['magic', /magic|wither|dragonbreath/], ['anvil', /anvil|fallingblock|fallingstalactite/],
    ['mob', /^attack\.(mob|arrow|trident|thrown|fireball|sting|sonic|mace|witherskull|spit|thorns)/],
  ];
  function deathCause(k) {
    for (const [c, re] of DEATH_CAUSES) if (re.test(k)) return c;
    return 'other';
  }
  function causes(map) {
    const out = {};
    for (const k in map || {}) { const c = deathCause(k); out[c] = (out[c] || 0) + map[k]; }
    return entries(out);
  }
  function funCards(g, list) {
    const n = mergeN(list), ev = sumMap(list, 'ev'), st = sumMap(list, 'st'), cls = sumMap(list, 'cls');
    const act = sum(list, (d) => d.a), afk = sum(list, (d) => d.k);
    const deaths = ev.death || 0;
    const seen = n.dm || 0, mine = n.dmMe || 0;
    const facts = [];
    if (seen) {
      const p = mine / seen;
      facts.push({ icon: 'skull', label: t('fun.dmTitle'), value: pct(mine, seen), sub: t('fun.dm', { mine: num(mine), seen: num(seen) })
        + (seen >= 5 ? ' ' + t(mine === 0 ? 'fun.dm0' : p >= 0.5 ? 'fun.dmHigh' : p <= 0.1 ? 'fun.dmLow' : 'fun.dmMid') : '') });
    }
    const myCauses = causes(n.dmkMe), allCauses = causes(n.dmk);
    if (myCauses.length) facts.push({ icon: 'flame', label: t('fun.cause'), value: t('dc.' + myCauses[0][0]), sub: t('u.times', { n: num(myCauses[0][1]) }) });
    if (allCauses.length && seen > mine) facts.push({ icon: 'globe', label: t('fun.killer'), value: t('dc.' + allCauses[0][0]), sub: t('fun.killerSub', { n: num(allCauses[0][1]) }) });
    if (deaths && act) facts.push({ icon: 'clock', label: t('fun.deathEvery'), value: dur(act / deaths), sub: t('fun.deathEverySub') });
    const jumpM = (n.jump || 0) * 1.25;
    if (jumpM >= 10) facts.push({ icon: 'boot', label: t('fun.jumps'), value: num(jumpM) + ' m', sub: t('fun.jumpsSub', { n: num(jumpM / 330, jumpM < 3300 ? 1 : 0) }) });
    if (afk >= 600000) facts.push({ icon: 'bed', label: t('fun.afk'), value: dur(afk), sub: t('fun.afkSub', { n: num(afk / 7.2e6, afk < 7.2e7 ? 1 : 0) }) });
    const inv = (st.INVENTORY || 0) + (st.CONTAINER || 0);
    if (inv >= 60000) facts.push({ icon: 'chest', label: t('fun.inv'), value: dur(inv), sub: t('fun.invSub', { p: pct(inv, act) }) });
    if ((st.PAUSE || 0) >= 60000) facts.push({ icon: 'book', label: t('fun.pause'), value: dur(st.PAUSE), sub: t('fun.pauseSub') });
    if ((cls.l || 0) >= 60000) facts.push({ icon: 'torch', label: t('fun.loading'), value: dur(cls.l), sub: t('fun.loadingSub', { n: num(cls.l / 3e5, 0) }) });
    const meters = (n.mousePx || 0) * 0.0254 / 96;
    if (meters >= 105) facts.push({ icon: 'compass', label: t('fun.mouse'), value: meters >= 1000 ? num(meters / 1000, 2) + ' km' : num(meters) + ' m', sub: t('fun.mouseSub', { n: num(meters / 105, meters < 1050 ? 1 : 0) }) });
    if ((n.keys || 0) >= 2000) facts.push({ icon: 'key', label: t('fun.keys'), value: compact(n.keys), sub: t('fun.keysSub', { n: num(n.keys / 1800) }) });
    if ((n.chatSeen || 0) >= 20 && (n.chat || 0) > 0) {
      const r = n.chatSeen / n.chat;
      facts.push({ icon: 'book', label: t('fun.reader'), value: num(r, r < 10 ? 1 : 0) + ' : 1', sub: t(r >= 5 ? 'fun.readerQuiet' : r <= 1.5 ? 'fun.readerChatty' : 'fun.readerMid') });
    }
    if ((n.hungryMs || 0) >= 60000) facts.push({ icon: 'star', label: t('fun.hungry'), value: dur(n.hungryMs), sub: t('fun.hungrySub') });
    if ((n.fireMs || 0) >= 10000) facts.push({ icon: 'flame', label: t('fun.fire'), value: dur(n.fireMs), sub: t('fun.fireSub') });
    card(g, {
      icon: 'star', title: t('fun.title'), sub: t('fun.sub'), full: true,
      render: (host) => {
        if (!facts.length) { C.empty(host, t('ui.noLive')); return; }
        host.innerHTML = '';
        tiles(host, facts);
        host.querySelector('.tiles').classList.add('fun-tiles');
      },
    });
    if (seen || myCauses.length) {
      const items = (map) => map.map(([k, v], i) => ({ label: t('dc.' + k), value: v, color: slot(i) }));
      card(g, {
        icon: 'skull', title: t('fun.causes'), sub: t('fun.causesSub'), wide: true,
        render: (host) => C.hbars(host, { items: items(myCauses), fmt: (v) => num(v), limit: 10, emptyText: t('fun.noDeath') }),
        table: () => ({ head: [t('fun.causeCol'), t('fun.you'), t('fun.everyone')], rows: allCauses.map(([k, v]) => [t('dc.' + k), num((myCauses.find((x) => x[0] === k) || [0, 0])[1]), num(v)]) }),
      });
      card(g, {
        icon: 'globe', title: t('fun.killers'), sub: t('fun.killersSub'),
        render: (host) => C.hbars(host, { items: items(allCauses), fmt: (v) => num(v), limit: 8, emptyText: t('ui.noLive') }),
      });
    }
  }

  // ---------------------------------------------------------------------------------------------- backup
  const isLive = () => location.protocol.startsWith('http');
  const MONTH = 30 * 864e5;
  /** True when nothing protects the stats: no working backup folder and no download in the last 30 days. */
  function unprotected() {
    const B = D.backup;
    if (!B) return false; // older mod or CLI export: nothing to say
    if (B.dir && !B.error) return false;
    return !(B.download > Date.now() - MONTH);
  }
  /** Top-of-page reminder once there is something worth losing (1 h of play), hidden for 30 days by ✕. */
  function backupReminder(main, totalActive) {
    if (!unprotected() || totalActive < 3.6e6 || Number(store.get('backupHide', 0)) > Date.now() - MONTH) return;
    const n = document.createElement('div');
    n.className = 'note bk-note';
    n.innerHTML = `${PI.svg('chest', 24)}<span>${esc(t('bk.remind'))}</span>`
      + (isLive() ? `<a class="mc-btn sm green" href="api/backup.zip" download>${esc(t('bk.download'))}</a>` : '')
      + `<a class="mc-btn sm" href="#backup-card">${esc(t('bk.how'))}</a>`
      + `<button type="button" class="mc-btn sm" aria-label="${esc(t('season.hide'))}">✕</button>`;
    $('button', n).addEventListener('click', () => { store.set('backupHide', String(Date.now())); n.remove(); });
    $('a[href="#backup-card"]', n).addEventListener('click', (e) => {
      e.preventDefault();
      const c = $('#backup-card');
      if (c) c.scrollIntoView({ behavior: motionOff() ? 'auto' : 'smooth', block: 'center' });
    });
    const dl = $('a[download]', n);
    if (dl) dl.addEventListener('click', afterDownload);
    main.appendChild(n);
  }
  /** The download is recorded by the mod: reload the status a moment later. */
  function afterDownload() {
    setTimeout(async () => { try { D = await api('api/data.json'); renderTab(true); } catch (e) { /* game closed */ } }, 2500);
  }
  function backupCard(g) {
    const B = D.backup;
    if (!B) return;
    const c = card(g, {
      title: t('bk.title'), icon: 'chest', sub: t('bk.sub'), full: true,
      render: (host) => {
        const when = (ms) => dateFmt(ms, { dateStyle: 'medium', timeStyle: 'short' });
        let status;
        if (B.dir && !B.error) {
          status = `<p class="bk-ok"><b>✔ ${esc(t('bk.on'))}</b></p><p class="muted">${esc(t('bk.folder'))} : <code class="bk-path">${esc(B.dir)}</code></p>`
            + `<p class="muted">${esc(B.last ? t('bk.last', { d: when(B.last) }) : t('bk.notYet'))}</p>`;
        } else if (B.error) {
          status = `<p class="bk-warn"><b>⚠ ${esc(t('bk.err.' + B.error))}</b></p>`
            + (B.dir ? `<p class="muted">${esc(t('bk.folder'))} : <code class="bk-path">${esc(B.dir)}</code></p>` : '')
            + `<p class="muted">${esc(t('bk.setup'))}</p>`;
        } else {
          status = `<p class="bk-warn"><b>⚠ ${esc(t('bk.off'))}</b></p><p class="muted">${esc(t('bk.setup'))}</p>`
            + '<p class="muted"><code class="bk-path">backup.dir=C:\\Users\\…\\OneDrive\\PlayChrono</code></p>';
        }
        const dl = isLive()
          ? `<a class="mc-btn green" href="api/backup.zip" download>${esc(t('bk.download'))}</a>`
          : `<span class="muted">${esc(t('bk.fileMode'))}</span>`;
        host.innerHTML = `<div class="lb-bar"><div class="grow">${status}</div>${dl}</div>`
          + (B.download ? `<p class="muted">${esc(t('bk.downloaded', { d: when(B.download) }))}</p>` : '')
          + `<h4 class="bk-h">${esc(t('bk.restoreTitle'))}</h4><p class="muted">${esc(t('bk.restore'))}</p>`
          + `<p class="muted"><code class="bk-path">${esc(B.restoreDir || '')}</code></p>`
          + (B.restored ? `<p class="muted">${esc(t('bk.restored', { d: when(B.restored.at), n: num((B.restored.added || 0) + (B.restored.replaced || 0)) }))}</p>` : '');
        const a = $('a[download]', host);
        if (a) a.addEventListener('click', afterDownload);
      },
    });
    c.id = 'backup-card';
  }

  function dailyColumns(host, list) {
    let buckets = list.map(([dt, d]) => ({ label: dt, title: dateFmt(dt, { weekday: 'short', day: 'numeric', month: 'short' }), days: [d] }));
    if (list.length > 120) {
      const by = list.length > 730 ? (dt) => dt.slice(0, 7) : (dt) => weekKey(dt);
      const m = new Map();
      for (const [dt, d] of list) { const kk = by(dt); if (!m.has(kk)) m.set(kk, { label: kk, title: kk, days: [] }); m.get(kk).days.push(d); }
      buckets = [...m.values()];
      buckets.forEach((b) => { b.title = list.length > 730 ? dateFmt(b.label + '-01', { month: 'long', year: 'numeric' }) : t('ui.weekOf', { d: dateFmt(b.label) }); });
    }
    const short = (b) => (list.length > 730 ? dateFmt(b.label + '-01', { month: 'short', year: '2-digit' }) : dateFmt(b.label.length === 10 ? b.label : b.label, { day: 'numeric', month: 'short' }));
    C.columns(host, {
      labels: buckets.map(short),
      tipTitle: (i) => buckets[i].title,
      series: CLS.map((c) => ({ name: t('cls.' + c), color: color(c), values: buckets.map((b) => b.days.reduce((s, d) => s + (d && d.cls ? d.cls[c] || 0 : 0), 0)) })),
      fmt: dur, fmtAxis: hoursAxis, unit: 3.6e6, height: 240, totalLabel: t('col.total'),
    });
  }
  function weekKey(dt) {
    const d = parseDay(dt);
    const wd = (d.getDay() + 6) % 7;
    d.setDate(d.getDate() - wd);
    return iso(d);
  }
  function weeklyCard(g, size) {
    const m = new Map();
    for (const k of allDays()) { const w = weekKey(k); m.set(w, (m.get(w) || 0) + (D.days[k].a || 0)); }
    const keys = [...m.keys()].sort();
    if (keys.length) { // fill gaps with zero weeks
      const all = [];
      for (let d = parseDay(keys[0]); iso(d) <= keys[keys.length - 1]; d.setDate(d.getDate() + 7)) all.push(iso(d));
      card(g, {
        title: t('ch.weekly'), sub: t('ch.weekly_sub'), [size]: true,
        render: (host) => C.line(host, {
          labels: all.map((w) => dateFmt(w, { month: 'short', year: '2-digit' })), tipTitle: (i) => t('ui.weekOf', { d: dateFmt(all[i]) }),
          series: [{ name: t('kpi.active'), color: css('--s1'), values: all.map((w) => m.get(w) || 0) }],
          fmt: dur, fmtAxis: hoursAxis, unit: 3.6e6, area: true,
        }),
        table: () => ({ head: [t('col.week'), t('kpi.active')], rows: all.map((w) => [w, dur(m.get(w) || 0)]) }),
      });
    }
  }

  // ---------------------------------------------------------------------------------------------- schedule
  function schedule(main, list) {
    const playedDays = list.filter(([, d]) => played(d));
    const firsts = playedDays.map(([, d]) => d.first).filter((x) => x >= 0).sort((a, b) => a - b);
    const lasts = playedDays.map(([, d]) => d.last).filter((x) => x >= 0).sort((a, b) => a - b);
    const med = (arr) => (arr.length ? arr[Math.floor(arr.length / 2)] : null);
    const h = sumMap(list.map(([k, d]) => [k, d ? { h: { v: d.h } } : null]), 'h').v || new Array(24).fill(0);
    const a = h.reduce((s, x) => s + x, 0);
    const band = (x, y) => h.slice(x, y).reduce((s, v) => s + v, 0);
    const buckets = { owl: band(0, 5), early: band(5, 12), afternoon: band(12, 18), evening: band(18, 24) };
    const chrono = Object.entries(buckets).sort((x, y) => y[1] - x[1])[0][0];
    const weekend = sum(list, (d, k) => ([0, 6].includes(parseDay(k).getDay()) ? d.a : 0));
    tiles(main, [
      { icon: 'clock', label: t('kpi.chrono'), value: a ? t('chrono.' + chrono) : '—', hero: true },
      { label: t('kpi.avgStart'), value: med(firsts) == null ? '—' : hm(med(firsts)) },
      { label: t('kpi.avgEnd'), value: med(lasts) == null ? '—' : hm(med(lasts) + 1) },
      { label: t('kpi.night'), value: pct(buckets.owl, a) },
      { label: t('kpi.weekend'), value: pct(weekend, a) },
    ]);
    const g = grid(main);
    const recent = list.filter(([, d]) => played(d)).slice(-14).reverse();
    const classes = {};
    CLS.forEach((c) => (classes[c] = { name: t('cls.' + c), color: color(c) }));
    card(g, {
      title: t('ch.timelines'), full: true,
      render: (host) => C.timeline(host, { rows: recent.map(([dt, d]) => ({ label: dateFmt(dt, { weekday: 'short', day: 'numeric', month: 'short' }), segs: d ? d.tl || [] : [] })), classes, fmt: dur }),
      table: () => ({ head: [t('col.date'), t('col.from'), t('col.to'), t('col.what')], rows: recent.flatMap(([dt, d]) => (d ? d.tl || [] : []).map((s) => [dt, hm(s[0]), hm(s[1]), t('cls.' + s[2])])) }),
    });
    const wdNames = [...Array(7)].map((_, i) => new Intl.DateTimeFormat(lang, { weekday: 'short' }).format(new Date(2024, 0, 1 + i)));
    const matrix = [...Array(7)].map(() => new Array(24).fill(0)), count = new Array(7).fill(0);
    for (const [k, d] of list) {
      const wd = (parseDay(k).getDay() + 6) % 7;
      count[wd]++;
      if (d) d.h.forEach((v, i) => (matrix[wd][i] += v));
    }
    matrix.forEach((r, i) => r.forEach((v, j) => (r[j] = count[i] ? v / count[i] : 0)));
    card(g, {
      icon: 'clock', title: t('ch.heat'), sub: t('ch.heat_sub'), full: true,
      render: (host) => C.heatmap(host, { rows: wdNames, cols: [...Array(24)].map((_, i) => String(i).padStart(2, '0')), colTitle: (j) => hm(j * 60) + '–' + hm(j * 60 + 60), values: matrix, fmt: dur }),
      table: () => ({ head: ['', ...[...Array(24)].map((_, i) => i + 'h')], rows: matrix.map((r, i) => [wdNames[i], ...r.map((v) => Math.round(v / 60000))]) }),
    });
    const nd = Math.max(1, list.length);
    card(g, {
      title: t('ch.hours'), sub: t('ch.avgPerDay'), wide: true,
      render: (host) => C.columns(host, { labels: h.map((_, i) => String(i)), tipTitle: (i) => hm(i * 60) + '–' + hm(i * 60 + 60), series: [{ name: t('kpi.active'), color: css('--s1'), values: h.map((v) => v / nd) }], fmt: dur, fmtAxis: minAxis, unit: 60000 }),
      table: () => ({ head: [t('col.hour'), t('kpi.active')], rows: h.map((v, i) => [i + 'h', dur(v / nd)]) }),
    });
    const wdTot = new Array(7).fill(0);
    for (const [k, d] of list) if (d) wdTot[(parseDay(k).getDay() + 6) % 7] += d.a;
    card(g, {
      title: t('ch.weekdays'), sub: t('ch.avgPerDay'),
      render: (host) => C.columns(host, { labels: wdNames, series: [{ name: t('kpi.active'), color: css('--s1'), values: wdTot.map((v, i) => (count[i] ? v / count[i] : 0)) }], fmt: dur, fmtAxis: hoursAxis, unit: 3.6e6, highlight: (new Date().getDay() + 6) % 7 }),
      table: () => ({ head: [t('col.day'), t('kpi.active')], rows: wdNames.map((n, i) => [n, dur(count[i] ? wdTot[i] / count[i] : 0)]) }),
    });
    const vals = {};
    for (const k of allDays()) vals[k] = D.days[k].a || (D.days[k].imp ? D.days[k].o : 0);
    card(g, {
      title: t('ch.calendar'), full: true,
      render: (host) => C.calendar(host, { values: vals, end: new Date(), weeks: 53, locale: lang, fmt: dur, weekStart: 1 }),
    });
  }

  // ---------------------------------------------------------------------------------------------- activity
  function activity(main, list) {
    const st = sumMap(list, 'st'), sk = sumMap(list, 'sk'), sc = sumMap(list, 'sc');
    const ev = sumMap(list, 'ev');
    const bg = sum(list, (d) => d.bg);
    tiles(main, [
      { icon: 'bed', label: t('kpi.afkCount'), value: num(ev.afk || 0), sub: t('kpi.afkMax') + ' : ' + dur(list.reduce((m, [, d]) => Math.max(m, d && d.ev ? d.ev.afkMax || 0 : 0), 0)) },
      { label: t('kpi.bg'), value: hours(bg) },
      { label: t('kpi.boot'), value: ev.boot ? dur(ev.bootMs / ev.boot) : '—', sub: t('u.times', { n: num(ev.boot || 0) }) },
      { label: t('kpi.load'), value: ev.load ? dur(ev.loadMs / ev.load) : '—', sub: t('u.times', { n: num(ev.load || 0) }) },
      { label: t('kpi.dimChanges'), value: num(ev.dim || 0) },
    ]);
    const g = grid(main);
    const total = Object.values(st).reduce((s, v) => s + v, 0);
    const items = entries(st).map(([k, v]) => ({ label: t('st.' + k), value: v, color: color(STATE_GROUP[k] || 'u') }));
    card(g, {
      icon: 'chest', title: t('ch.states'), sub: t('ch.states_sub'), wide: true,
      render: (host) => C.hbars(host, { items, fmt: dur, limit: 28, share: (v) => pct(v, total), emptyText: t('ui.empty') }),
      table: () => ({ head: [t('col.what'), t('col.time'), '%'], rows: items.map((x) => [x.label, dur(x.value), pct(x.value, total)]) }),
    });
    const afkItems = entries(sk).map(([k, v]) => ({ label: t('st.' + k), value: v, color: css('--s2') }));
    card(g, {
      title: t('ch.afkBy'),
      render: (host) => C.hbars(host, { items: afkItems, fmt: dur, emptyText: t('ui.empty') }),
      table: () => ({ head: [t('col.what'), t('kpi.afk')], rows: afkItems.map((x) => [x.label, dur(x.value)]) }),
    });
    const scItems = entries(sc).map(([k, v]) => ({ label: k, value: v }));
    card(g, {
      title: t('ch.screens'), sub: t('ch.screens_sub'),
      render: (host) => C.hbars(host, { items: scItems, fmt: dur, limit: 15, emptyText: t('ui.noLive') }),
      table: () => ({ head: [t('col.screen'), t('col.time')], rows: scItems.map((x) => [x.label, dur(x.value)]) }),
    });
  }

  // ---------------------------------------------------------------------------------------------- places
  function places(main, list) {
    const ctx = sumMap(list, 'ctx');
    const keys = Object.keys(ctx).sort((a, b) => ctx[b][2] - ctx[a][2]);
    const types = { server: 0, world: 0, realm: 0, lan: 0 };
    keys.forEach((k) => (types[ctxType(k)] = (types[ctxType(k)] || 0) + ctx[k][2]));
    const fav = keys[0];
    tiles(main, [
      { icon: 'globe', label: t('kpi.places'), value: num(keys.length), sub: t('kpi.servers') + ' : ' + keys.filter((k) => ctxType(k) === 'server').length + ' · ' + t('kpi.worlds') + ' : ' + keys.filter((k) => ctxType(k) === 'world').length, hero: true },
      fav && { label: t('kpi.fav'), value: ctxLabel(fav), sub: dur(ctx[fav][2]) },
      { label: t('kpi.disc'), value: num(sum(list, (d) => (d.ev || {}).disc)) },
      { label: t('kpi.joins'), value: num(sum(list, (d) => (d.ev || {}).join)) },
    ]);
    const g = grid(main);
    const icon = (k) => `<span class="tag tag-${ctxType(k)}">${esc(t('type.' + ctxType(k)))}</span>`;
    const items = keys.map((k) => ({ label: ctxLabel(k), icon: icon(k), value: ctx[k][2], sub: (D.ctx[k] || {}).brand ? (D.ctx[k].brand + (D.ctx[k].ver ? ' · ' + D.ctx[k].ver : '')) : '' }));
    card(g, {
      icon: 'globe', title: t('ch.places'), sub: t('ch.places_sub'), wide: true,
      render: (host) => C.hbars(host, { items, fmt: dur, limit: 15, emptyText: t('ui.empty') }),
      table: () => ({
        head: [t('col.place'), t('col.type'), t('kpi.active'), t('kpi.afk'), t('kpi.open'), t('kpi.sessions'), t('col.joins'), t('col.deaths'), t('col.ping'), t('sv.first'), t('col.last')],
        rows: keys.map((k) => {
          const m = D.ctx[k] || {}, n = m.n || {};
          return [ctxLabel(k), t('type.' + ctxType(k)), dur(ctx[k][0]), dur(ctx[k][1]), dur(ctx[k][2]), num(m.v || 0), num(m.j || 0), num(m.d || 0),
            n.pingMs ? num(n.pingSum / n.pingMs) + ' ms' : '—', m.first ? dateFmt(m.first) : '—', m.last ? dateFmt(m.last) : '—'];
        }),
      }),
    });
    card(g, {
      title: t('ch.mpsp'),
      render: (host) => C.segbar(host, { parts: ['server', 'world', 'realm', 'lan'].map((x, i) => ({ name: t('type.' + x), value: types[x] || 0, color: slot(i) })), fmt: dur, emptyText: t('ui.empty') }),
    });
    const acc = D.accounts || {};
    const accItems = entries(acc).map(([k, v]) => ({ label: k, value: v }));
    card(g, {
      title: t('ch.accounts'), sub: t('ch.allTime'),
      render: (host) => C.hbars(host, { items: accItems, fmt: dur, emptyText: t('ui.empty') }),
      table: () => ({ head: [t('col.account'), t('kpi.open')], rows: accItems.map((x) => [x.label, dur(x.value)]) }),
    });
    const from = parseDay(periodDates()[0]).getTime() - 12 * 3600e3;
    const disc = (D.disc || []).filter((x) => x.ts >= from);
    const reasons = {};
    disc.forEach((x) => { const r = (x.r || '?').replace(/\s+/g, ' ').slice(0, 60); reasons[r] = (reasons[r] || 0) + 1; });
    card(g, {
      title: t('ch.discReasons'),
      render: (host) => C.hbars(host, { items: entries(reasons).map(([k, v]) => ({ label: k, value: v, color: css('--s8') })), fmt: (v) => num(v), emptyText: t('ui.empty') }),
      table: () => ({ head: [t('col.date'), t('col.place'), t('col.reason')], rows: disc.slice(0, 100).map((x) => [dateFmt(x.ts, { dateStyle: 'short', timeStyle: 'short' }), x.c ? ctxLabel(x.c) : '—', x.r || '?']) }),
    });
    // one server or world in detail, all time (since the first connection)
    const allCtx = Object.keys(D.ctx || {}).filter((k) => (D.ctx[k].o || 0) > 0).sort((a, b) => (D.ctx[b].o || 0) - (D.ctx[a].o || 0));
    if (allCtx.length) {
      let sel = store.get('srv', '');
      if (!allCtx.includes(sel)) sel = keys.find((k) => allCtx.includes(k)) || allCtx[0];
      card(g, {
        icon: 'globe', title: t('sv.title'), sub: t('sv.sub'), full: true,
        render: (host) => serverDetail(host, allCtx, sel, (k) => { sel = k; store.set('srv', k); }),
      });
    }
  }

  function relTime(ts) {
    const diff = (ts - Date.now()) / 1000, a = Math.abs(diff);
    const rtf = new Intl.RelativeTimeFormat(lang, { numeric: 'auto' });
    if (a < 3600) return rtf.format(Math.round(diff / 60), 'minute');
    if (a < 86400) return rtf.format(Math.round(diff / 3600), 'hour');
    if (a < 86400 * 60) return rtf.format(Math.round(diff / 86400), 'day');
    if (a < 86400 * 730) return rtf.format(Math.round(diff / (86400 * 30.4)), 'month');
    return rtf.format(Math.round(diff / (86400 * 365)), 'year');
  }
  /** Everything recorded on one server / world. Re-renders itself when another one is picked. */
  function serverDetail(host, keys, cur, pick) {
    const m = D.ctx[cur] || {}, n = m.n || {};
    const mv = n.mv || {}, distance = Object.values(mv).reduce((a, b) => a + b, 0);
    const allActive = Object.values(D.ctx).reduce((a, x) => a + (x.a || 0), 0);
    const v = m.v || 0;
    host.innerHTML = '';
    const head = document.createElement('div');
    head.className = 'sv-head';
    head.innerHTML = `<label class="controls sv-pick"><span>${esc(t('sv.pick'))}</span><select>`
      + keys.map((k) => `<option value="${esc(k)}"${k === cur ? ' selected' : ''}>${esc(ctxLabel(k))}</option>`).join('') + '</select></label>'
      + `<div class="sv-id"><span class="tag tag-${ctxType(cur)}">${esc(t('type.' + ctxType(cur)))}</span>`
      + (m.brand ? ` <span>${esc(m.brand + (m.ver ? ' · ' + m.ver : ''))}</span>` : '') + (m.imp ? ` <span class="tag">${esc(t('ui.imported'))}</span>` : '') + '</div>';
    $('select', head).addEventListener('change', (e) => { pick(e.target.value); serverDetail(host, keys, e.target.value, pick); });
    host.appendChild(head);

    const full = { dateStyle: 'long', timeStyle: 'short' };
    const facts = [
      ['clock', t('sv.first'), m.first ? dateFmt(m.first, full) : '—', m.first ? relTime(m.first) : ''],
      ['clock', t('sv.last'), m.last ? dateFmt(m.last, full) : '—', m.last ? relTime(m.last) : ''],
      ['chest', t('kpi.sessions'), num(v), m.j ? t('col.joins') + ' : ' + num(m.j) : ''],
      ['clock', t('sv.avg'), v ? dur((m.o || 0) / v) : '—', ''],
      ['bed', t('kpi.longest'), m.vMax ? dur(m.vMax) : '—', ''],
      ['star', t('kpi.days'), num(m.days || 0), ''],
      ['grass', t('kpi.active'), dur(m.a || 0), t('sv.share', { p: pct(m.a || 0, allActive) })],
      ['bed', t('kpi.afk'), dur(m.k || 0), pct(m.k || 0, m.o || 0)],
      ['clock', t('kpi.open'), dur(m.o || 0), ''],
      ['torch', t('kpi.load'), m.ld ? num(m.ldMs / m.ld / 1000, 1) + ' s' : '—', ''],
      ['globe', t('kpi.ping'), n.pingMs ? num(n.pingSum / n.pingMs) + ' ms' : '—', n.pingMax ? 'max ' + num(n.pingMax) + ' ms' : ''],
      ['globe', t('sv.players'), n.playersMax ? num(n.playersMax) : '—', ''],
      ['torch', t('kpi.fps'), n.fpsMs ? num(n.fpsSum / n.fpsMs) : '—', ''],
      ['boot', t('kpi.distance'), dist(distance), ''],
      ['compass', t('kpi.biomes'), num(Object.keys(n.bio || {}).length), ''],
      ['skull', t('kpi.deaths'), num(m.d || 0), ''],
      ['skull', t('kpi.disc'), num(m.dc || 0), m.dcR ? String(m.dcR).slice(0, 60) : ''],
      ['boot', t('kpi.jumps'), num(n.jump || 0), ''],
      ['book', t('kpi.chat'), num(n.chat || 0), t('kpi.cmds') + ' : ' + num(n.cmds || 0)],
      ['star', t('rec.xp'), num(n.xpLevels || 0), ''],
      ['star', t('rec.yMax'), n.yMax != null ? 'Y ' + num(n.yMax) : '—', n.yMin != null ? t('rec.yMin') + ' : Y ' + num(n.yMin) : ''],
      ['compass', t('rec.far'), n.farMax != null ? dist(n.farMax) : '—', ''],
      ['elytra', t('rec.speed'), n.speedMax != null ? num(n.speedMax, 1) + ' ' + t('u.bps') : '—', ''],
      ['bed', t('rec.sleeps'), num(n.sleeps || 0), ''],
      ['skull', t('rec.dmg'), num((n.dmg || 0) / 2) + ' ♥', t('rec.heal') + ' : ' + num((n.heal || 0) / 2) + ' ♥'],
    ];
    const box = document.createElement('div');
    box.className = 'sv-facts';
    box.innerHTML = facts.map(([ic, l, val, sub]) => `<div class="sv-fact">${PI.svg(ic, 20)}<span><span class="lbl">${esc(l)}</span>`
      + `<b>${esc(val)}</b>${sub ? `<small>${esc(sub)}</small>` : ''}</span></div>`).join('');
    host.appendChild(box);

    const charts = document.createElement('div');
    charts.className = 'sv-charts';
    host.appendChild(charts);
    const block = (title, wide, draw) => {
      const b = document.createElement('div');
      b.className = 'sv-block' + (wide ? ' wide' : '');
      b.innerHTML = `<h4>${esc(title)}</h4><div></div>`;
      charts.appendChild(b);
      try { draw(b.lastChild); } catch (e) { console.error(e); C.empty(b.lastChild, '⚠ ' + e.message); }
    };
    const wd = [...Array(7)].map((_, i) => new Intl.DateTimeFormat(lang, { weekday: 'short' }).format(new Date(2024, 0, 1 + i)));
    const h = m.h || new Array(24).fill(0);
    block(t('ch.hours'), true, (x) => C.columns(x, { labels: h.map((_, i) => String(i)), tipTitle: (i) => hm(i * 60) + '–' + hm(i * 60 + 60),
      series: [{ name: t('kpi.active'), color: css('--s1'), values: h }], fmt: dur, fmtAxis: hoursAxis, unit: 3.6e6, height: 160 }));
    block(t('ch.weekdays'), false, (x) => C.columns(x, { labels: wd, series: [{ name: t('kpi.active'), color: css('--s2'), values: m.wd || new Array(7).fill(0) }],
      fmt: dur, fmtAxis: hoursAxis, unit: 3.6e6, height: 160 }));
    const dimName = (k) => (t('dim.' + k.replace('minecraft:', '')).startsWith('dim.') ? pretty(k) : t('dim.' + k.replace('minecraft:', '')));
    block(t('ch.dims'), false, (x) => C.segbar(x, { parts: entries(m.dim).slice(0, 4).map(([k, val], i) => ({ name: dimName(k), value: val, color: slot(i), ink: inkOn(i) })), fmt: dur, emptyText: t('ui.noLive') }));
    block(t('ch.modes'), false, (x) => C.segbar(x, { parts: ['survival', 'creative', 'adventure', 'spectator'].map((k, i) => ({ name: t('gm.' + k), value: (n.gm || {})[k] || 0, color: slot(i), ink: inkOn(i) })), fmt: dur, emptyText: t('ui.noLive') }));
    block(t('ch.versions'), false, (x) => C.hbars(x, { items: entries(m.mcT).map(([k, val]) => ({ label: verLabel(k), value: val })), fmt: dur, limit: 6, emptyText: t('ui.empty') }));
    if (Object.keys(m.acT || {}).length > 1) block(t('ch.accounts'), false, (x) => C.hbars(x, { items: entries(m.acT).map(([k, val]) => ({ label: k, value: val })), fmt: dur }));
    const bio = entries(n.bio || {});
    block(t('ch.biomes') + ' (' + num(bio.length) + ')', false, (x) => C.hbars(x, { items: bio.map(([k, val]) => ({ label: pretty(k), value: val, color: css('--s3') })), fmt: dur, limit: 8, emptyText: t('ui.noLive') }));
    const vl = m.vl || [];
    block(t('sv.recent'), true, (x) => {
      if (!vl.length) { C.empty(x, t('ui.empty')); return; }
      renderTable(x, { head: [t('col.date'), t('col.from'), t('kpi.open'), t('kpi.active')],
        rows: vl.map(([st, len, act]) => [dateFmt(st, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }), hm(new Date(st).getHours() * 60 + new Date(st).getMinutes()), dur(len), dur(act)]) });
    });
  }

  // ---------------------------------------------------------------------------------------------- versions
  function versions(main, list) {
    const ver = sumMap(list, 'ver'), ldr = sumMap(list, 'ldr');
    const vk = entries(ver);
    const total = vk.reduce((s, [, v]) => s + v, 0);
    const snap = vk.filter(([k]) => /w\d\d[a-z]|snapshot|-pre|-rc|craftmine/i.test(k)).reduce((s, [, v]) => s + v, 0);
    const known = vk.filter(([k]) => k !== '?');
    const latest = Object.entries(D.versions || {}).filter(([k]) => k !== '?').sort((a, b) => (b[1].last || 0) - (a[1].last || 0))[0];
    tiles(main, [
      { icon: 'book', label: t('kpi.versions'), value: num(known.length), hero: true },
      { label: t('kpi.latest'), value: latest ? latest[0] : '—', sub: latest ? dateFmt(latest[1].last) : '' },
      { label: t('kpi.snapshots'), value: pct(snap, total) },
      { label: t('kpi.topVersion'), value: known[0] ? known[0][0] : '—', sub: known[0] ? dur(known[0][1]) : '' },
    ]);
    const g = grid(main);
    const items = vk.map(([k, v]) => ({ label: verLabel(k), value: v, color: k === '?' ? css('--muted') : undefined, sub: k === '?' ? t('ui.unknownVer') : '' }));
    card(g, {
      icon: 'book', title: t('ch.versions'), wide: true,
      render: (host) => C.hbars(host, { items, fmt: dur, limit: 20, share: (v) => pct(v, total), emptyText: t('ui.empty') }),
      table: () => ({ head: [t('col.version'), t('kpi.open'), '%'], rows: items.map((x) => [x.label, dur(x.value), pct(x.value, total)]) }),
    });
    card(g, {
      title: t('ch.loaders'),
      render: (host) => C.segbar(host, { parts: entries(ldr).slice(0, 6).map(([k, v], i) => ({ name: k, value: v, color: slot(i), ink: inkOn(i) })), fmt: dur, emptyText: t('ui.empty') }),
      table: () => ({ head: [t('col.loader'), t('kpi.open')], rows: entries(ldr).map(([k, v]) => [k, dur(v)]) }),
    });
    // versions over time (months, top 7 + other)
    const months = new Map();
    for (const [dt, d] of list) { if (!d) continue; const mk = dt.slice(0, 7); const m = months.get(mk) || {}; for (const v in d.ver) m[v] = (m[v] || 0) + d.ver[v]; months.set(mk, m); }
    const mk = [...months.keys()].sort();
    const top = vk.slice(0, 5).map(([k]) => k);
    const series = top.map((v, i) => ({ name: verLabel(v), color: v === '?' ? css('--muted') : slot(i), values: mk.map((m) => months.get(m)[v] || 0) }));
    const otherVals = mk.map((m) => Object.entries(months.get(m)).filter(([v]) => !top.includes(v)).reduce((s, [, x]) => s + x, 0));
    if (otherVals.some((x) => x > 0)) series.push({ name: t('ui.other'), color: css('--axis'), values: otherVals });
    card(g, {
      title: t('ch.versionsMonth'), full: true,
      render: (host) => C.columns(host, { labels: mk.map((m) => dateFmt(m + '-01', { month: 'short', year: '2-digit' })), tipTitle: (i) => dateFmt(mk[i] + '-01', { month: 'long', year: 'numeric' }), series, fmt: dur, fmtAxis: hoursAxis, unit: 3.6e6, totalLabel: t('col.total') }),
      table: () => ({ head: [t('col.month'), ...series.map((s) => s.name)], rows: mk.map((m, i) => [m, ...series.map((s) => dur(s.values[i]))]) }),
    });
    const mods = Array.isArray(D.mods) ? D.mods : [];
    card(g, {
      title: t('ch.mods', { n: mods.length }), wide: true,
      render: (host) => {
        if (!mods.length) { C.empty(host, t('ui.noLive')); return; }
        host.innerHTML = `<div class="chips">${mods.map((m) => `<span class="chip" title="${esc(m[1])}">${esc(m[0])}</span>`).join('')}</div>`;
      },
      table: () => ({ head: ['id', t('col.version')], rows: mods.map((m) => [m[0], m[1]]) }),
    });
  }

  // ---------------------------------------------------------------------------------------------- world
  function world(main, list) {
    const n = mergeN(list), ev = sumMap(list, 'ev');
    const mv = n.mv || {};
    const distTotal = Object.values(mv).reduce((s, v) => s + v, 0);
    const act = sum(list, (d) => d.a);
    const deaths = ev.death || 0;
    tiles(main, [
      { label: t('kpi.distance'), value: dist(distTotal), hero: true, icon: 'boot', raw: distTotal, fmt: dist, sub: t('eq.walk', { n: num(distTotal / 324, 1) }) },
      { label: t('kpi.deaths'), value: num(deaths), icon: 'skull', sub: t('kpi.deathRate') + ' : ' + num(act ? deaths / (act / 3.6e6) : 0, 2) },
      { label: t('kpi.biomes'), value: num(Object.keys(n.bio || {}).length), icon: 'globe' },
      { icon: 'star', label: t('rec.yMax'), value: n.yMax != null ? 'Y ' + num(n.yMax) : '—', sub: t('rec.yMin') + ' : ' + (n.yMin != null ? 'Y ' + num(n.yMin) : '—') },
      { icon: 'compass', label: t('rec.far'), value: n.farMax != null ? dist(n.farMax) : '—' },
      { icon: 'elytra', label: t('rec.speed'), value: n.speedMax != null ? num(n.speedMax, 1) + ' ' + t('u.bps') : '—' },
    ]);
    const g = grid(main);
    const mvItems = entries(mv).map(([k, v], i) => ({ label: t('mv.' + k), value: v, color: slot(i) }));
    card(g, {
      icon: 'boot', title: t('ch.moves'), wide: true,
      render: (host) => C.hbars(host, { items: mvItems, fmt: dist, share: (v) => pct(v, distTotal), emptyText: t('ui.noLive') }),
      table: () => ({ head: [t('col.what'), t('col.distance')], rows: mvItems.map((x) => [x.label, dist(x.value)]) }),
    });
    const dims = sumMap(list, 'dim');
    const dimName = (k) => t('dim.' + k.replace('minecraft:', ''), {}).startsWith('dim.') ? k : t('dim.' + k.replace('minecraft:', ''));
    card(g, {
      title: t('ch.dims'),
      render: (host) => C.segbar(host, { parts: entries(dims).slice(0, 4).map(([k, v], i) => ({ name: dimName(k), value: v, color: slot(i), ink: inkOn(i) })), fmt: dur, emptyText: t('ui.noLive') }),
      table: () => ({ head: [t('col.dim'), t('kpi.active')], rows: entries(dims).map(([k, v]) => [dimName(k), dur(v)]) }),
    });
    const bio = entries(n.bio || {});
    card(g, {
      title: t('ch.biomes'), sub: t('ui.count', { n: bio.length }),
      render: (host) => C.hbars(host, { items: bio.map(([k, v]) => ({ label: pretty(k), value: v, color: css('--s3') })), fmt: dur, limit: 15, emptyText: t('ui.noLive') }),
      table: () => ({ head: [t('col.biome'), t('col.time')], rows: bio.map(([k, v]) => [pretty(k), dur(v)]) }),
    });
    const four = (title, map, keys) => card(g, {
      title,
      render: (host) => C.segbar(host, { parts: keys.map((k, i) => ({ name: t(k[1]), value: (map || {})[k[0]] || 0, color: slot(i), ink: inkOn(i) })), fmt: dur, emptyText: t('ui.noLive') }),
    });
    four(t('ch.weather'), n.wx, [['clear', 'wx.clear'], ['rain', 'wx.rain'], ['thunder', 'wx.thunder']]);
    four(t('ch.tod'), n.tod, [['day', 'tod.day'], ['dusk', 'tod.dusk'], ['night', 'tod.night'], ['dawn', 'tod.dawn']]);
    four(t('ch.modes'), n.gm, [['survival', 'gm.survival'], ['creative', 'gm.creative'], ['adventure', 'gm.adventure'], ['spectator', 'gm.spectator']]);
    tiles(g, [
      { label: t('rec.dmg'), value: num((n.dmg || 0) / 2, 0) + ' ♥' },
      { label: t('rec.heal'), value: num((n.heal || 0) / 2, 0) + ' ♥' },
      { label: t('rec.xp'), value: num(n.xpLevels || 0) },
      { label: t('rec.sleeps'), value: num(n.sleeps || 0) },
      { label: t('rec.tp'), value: num(n.tp || 0) },
      { label: t('rec.underwater'), value: dur(n.underwaterMs || 0) },
    ]);
    const rides = entries(n.ridev || {});
    if (rides.length) card(g, { title: t('ch.vehicles'), render: (host) => C.hbars(host, { items: rides.map(([k, v]) => ({ label: pretty(k), value: v, color: css('--s7') })), fmt: dist }) });
    const from = parseDay(periodDates()[0]).getTime() - 12 * 3600e3;
    const ds = (D.deaths || []).filter((x) => x.ts >= from);
    card(g, {
      title: t('ch.deaths'), wide: true,
      render: (host) => {
        if (!ds.length) { C.empty(host, t('ui.noDeaths')); return; }
        renderTable(host, { head: [t('col.date'), t('col.place'), t('col.dim'), 'X / Y / Z'], rows: ds.slice(0, 12).map((x) => [dateFmt(x.ts, { dateStyle: 'short', timeStyle: 'short' }), x.c ? ctxLabel(x.c) : '—', x.dm ? dimName(x.dm) : '—', x.x != null ? `${x.x} / ${x.y} / ${x.z}` : '—']) });
      },
    });
  }
  const pretty = (k) => String(k).replace(/^minecraft:/, '').replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());

  // ---------------------------------------------------------------------------------------------- inputs
  function inputs(main, list) {
    const n = mergeN(list);
    const act = sum(list, (d) => d.a);
    const clicks = (n.clickL || 0) + (n.clickR || 0) + (n.clickM || 0);
    const meters = (n.mousePx || 0) * 0.0254 / 96;
    tiles(main, [
      { icon: 'key', label: t('kpi.apm'), value: num(act ? ((n.keys || 0) + clicks) / (act / 60000) : 0, 1), hero: true },
      { label: t('kpi.keys'), value: compact(n.keys || 0) },
      { label: t('kpi.clicks'), value: compact(clicks), sub: `${t('clicks.l')} ${compact(n.clickL || 0)} · ${t('clicks.r')} ${compact(n.clickR || 0)}` },
      { label: t('kpi.mouse'), value: meters >= 1000 ? num(meters / 1000, 2) + ' km' : num(meters, 0) + ' m' },
      { label: t('kpi.jumps'), value: compact(n.jump || 0) },
      { label: t('kpi.hotbar'), value: compact(n.hotbar || 0) },
      { label: t('kpi.chat'), value: num(n.chat || 0), sub: n.chatSeen ? t('kpi.chatSeen') + ' : ' + compact(n.chatSeen) : '' },
      { label: t('kpi.cmds'), value: num(n.cmds || 0) },
      { label: t('kpi.shots'), value: num(n.shot || 0) },
    ]);
    const g = grid(main);
    const kc = n.kc || {};
    card(g, {
      icon: 'key', title: t('ch.keyboard'), sub: t('ch.keyboard_sub'), full: true,
      render: (host) => {
        if (!Object.keys(kc).length) { C.empty(host, t('ui.noLive')); return; }
        const lay = kbLayout();
        C.keyboard(host, { counts: kc, fmt: (v) => num(v), labels: lay === 'azerty' ? AZERTY : null });
        const sw = document.createElement('div');
        sw.className = 'kb-switch';
        sw.setAttribute('role', 'group');
        sw.setAttribute('aria-label', t('kb.layout'));
        sw.innerHTML = ['qwerty', 'azerty'].map((x) => `<button type="button" class="mc-btn sm${x === lay ? ' green' : ''}" data-l="${x}" aria-pressed="${x === lay}">${x.toUpperCase()}</button>`).join('');
        sw.querySelectorAll('button').forEach((b) => b.addEventListener('click', () => { savePref('keyboard', b.dataset.l); renderers.forEach((r) => r()); }));
        host.insertBefore(sw, host.firstChild);
      },
      table: () => ({ head: [t('col.key'), t('col.count')], rows: entries(kc).map(([k, v]) => [keyName(k), num(v)]) }),
    });
    const cmd = entries(n.cmd || {});
    card(g, {
      title: t('ch.commands'), wide: true,
      render: (host) => C.hbars(host, { items: cmd.map(([k, v]) => ({ label: '/' + k, value: v, color: css('--s7') })), fmt: (v) => num(v), limit: 15, emptyText: t('ui.noLive') }),
      table: () => ({ head: [t('col.command'), t('col.count')], rows: cmd.map(([k, v]) => ['/' + k, num(v)]) }),
    });
    card(g, {
      title: t('ch.clicks'),
      render: (host) => C.segbar(host, { parts: [['clickL', 'clicks.l'], ['clickR', 'clicks.r'], ['clickM', 'clicks.m']].map(([k, l], i) => ({ name: t(l), value: n[k] || 0, color: slot(i) })), fmt: (v) => num(v), emptyText: t('ui.noLive') }),
    });
  }
  const KEYN = { 32: 'Space', 256: 'Esc', 257: 'Enter', 258: 'Tab', 259: 'Backspace', 262: '→', 263: '←', 264: '↓', 265: '↑', 340: 'L-Shift', 341: 'L-Ctrl', 342: 'L-Alt', 344: 'R-Shift', 345: 'R-Ctrl', 346: 'AltGr', 280: 'Caps Lock' };
  function keyName(code) {
    code = Number(code);
    if (kbLayout() === 'azerty' && AZERTY[code]) return AZERTY[code].slice(0, 1);
    if (KEYN[code]) return KEYN[code];
    if (code >= 290 && code <= 314) return 'F' + (code - 289);
    if (code >= 320 && code <= 329) return 'Num ' + (code - 320);
    if (code >= 32 && code < 127) return String.fromCharCode(code);
    return '#' + code;
  }

  // ---------------------------------------------------------------------------------------------- perf
  function perf(main, list) {
    const n = mergeN(list);
    const fpsAvg = n.fpsMs ? n.fpsSum / n.fpsMs : 0;
    tiles(main, [
      { icon: 'torch', label: t('kpi.fps'), value: fpsAvg ? num(fpsAvg) : '—', hero: true, sub: n.fpsMin != null ? t('kpi.fpsMin') + ' : ' + num(n.fpsMin) : '' },
      { icon: 'heart', label: t('kpi.hitches'), value: num(n.hitch || 0), sub: t('kpi.frozen') + ' : ' + dur(n.hitchMs || 0) },
      { label: t('kpi.ram'), value: n.ramMax ? num(n.ramMax) + ' MB' : '—' },
      { label: t('kpi.ping'), value: n.pingMs ? num(n.pingSum / n.pingMs) + ' ms' : '—', sub: n.pingMax ? 'max ' + num(n.pingMax) + ' ms' : '' },
    ]);
    const g = grid(main);
    const fb = n.fpsB || {};
    const fk = [['lt30', '< 30'], ['30_60', '30–60'], ['60_120', '60–120'], ['120_240', '120–240'], ['240p', '240+']];
    card(g, {
      title: t('ch.fps'), wide: true,
      render: (host) => C.columns(host, { labels: fk.map((x) => x[1] + ' FPS'), series: [{ name: t('col.time'), color: css('--s1'), values: fk.map((x) => fb[x[0]] || 0) }], fmt: dur, fmtAxis: hoursAxis, unit: 3.6e6 }),
      table: () => ({ head: ['FPS', t('col.time')], rows: fk.map((x) => [x[1], dur(fb[x[0]] || 0)]) }),
    });
    const sess = sessionsInPeriod().filter((s) => s.boot > 0).slice(0, 60).reverse();
    card(g, {
      title: t('ch.boot'), sub: t('ch.boot_sub'),
      render: (host) => (sess.length ? C.line(host, { labels: sess.map((s) => dateFmt(s.s, { day: 'numeric', month: 'short' })), tipTitle: (i) => dateFmt(sess[i].s, { dateStyle: 'medium', timeStyle: 'short' }) + ' · ' + verLabel(sess[i].ver), series: [{ name: t('kpi.boot'), color: css('--s5'), values: sess.map((s) => s.boot) }], fmt: dur, fmtAxis: (v) => num(v / 1000) + ' s', unit: 1000 }) : C.empty(host, t('ui.noLive'))),
      table: () => ({ head: [t('col.date'), t('col.version'), t('kpi.boot')], rows: sess.map((s) => [dateFmt(s.s, { dateStyle: 'short', timeStyle: 'short' }), verLabel(s.ver), dur(s.boot)]) }),
    });
    const e = D.env || {};
    const rows = [
      [t('sys.mc'), e.mc ? e.mc + (e.mcType === 'snapshot' ? ' (snapshot)' : '') : '—'], [t('sys.loader'), e.ldr ? e.ldr + ' ' + (e.ldrv || '') : '—'],
      [t('sys.os'), e.os || '—'], [t('sys.cpu'), e.cpu ? e.cpu + (e.cpus ? ' · ' + e.cpus + ' threads' : '') : '—'],
      [t('sys.ram'), e.ramMb ? num(e.ramMb / 1024, 1) + ' GB · ' + t('sys.heap') + ' ' + num(e.heapMb) + ' MB' : '—'], [t('sys.gpu'), e.gpu || '—'],
      [t('sys.java'), e.java ? e.java + ' (' + (e.javaVendor || '') + ')' : '—'], [t('sys.res'), e.win ? e.win + (e.fs ? ' · fullscreen' : '') : '—'],
      [t('sys.rd'), e.rd ? e.rd + ' chunks' : '—'], [t('sys.fov'), e.fov || '—'], [t('sys.launcher'), e.launcher || e.launched || '—'],
      [t('sys.lang'), e.lang || '—'],
    ];
    card(g, { title: t('ch.system'), wide: true, render: (host) => renderTable(host, { head: ['', ''], rows }) });
  }

  // ---------------------------------------------------------------------------------------------- leaderboard
  const METRICS = [['active', 'dur', 'clock'], ['last7', 'dur', 'clock'], ['last30', 'dur', 'clock'], ['streak', 'days', 'flame'],
    ['bestStreak', 'days', 'flame'], ['longest', 'dur', 'bed'], ['days', 'days', 'grass'], ['distance', 'dist', 'boot'], ['elytra', 'dist', 'elytra'],
    ['biomes', 'num', 'globe'], ['servers', 'num', 'globe'], ['versions', 'num', 'book'], ['jumps', 'num', 'boot'], ['apm', 'dec', 'key'],
    ['deaths', 'num', 'skull'], ['maxY', 'y', 'star'], ['farthest', 'dist', 'compass'], ['speed', 'speed', 'elytra'], ['levels', 'num', 'star'], ['chat', 'num', 'book']];
  function mfmt(kind, v) {
    v = Number(v) || 0;
    switch (kind) {
      case 'dur': return dur(v);
      case 'days': return t('u.days', { n: num(v) });
      case 'dist': return dist(v);
      case 'dec': return num(v, 1);
      case 'y': return 'Y ' + num(v);
      case 'speed': return num(v, 1) + ' ' + t('u.bps');
      default: return num(v);
    }
  }
  async function fetchJson(url, ms) {
    const ctl = new AbortController();
    const timer = setTimeout(() => ctl.abort(), ms);
    try {
      const r = await fetch(url, { signal: ctl.signal, cache: 'no-store', credentials: 'omit', referrerPolicy: 'no-referrer' });
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return await r.json();
    } finally { clearTimeout(timer); }
  }
  async function leaderboard(main) {
    const seq = renderSeq;
    const L = (D && D.leaderboard) || {}, me = (D && D.me) || {};
    const uuid = (D && D.env && D.env.uuid) || '';
    const g = grid(main);
    const live = location.protocol.startsWith('http');
    card(g, {
      title: t('lb.title'), icon: 'trophy', sub: t('lb.sub'), full: true,
      render: (host) => {
        const st = L.status || 'off';
        host.innerHTML = `<div class="lb-bar"><div class="grow"><div class="lb-status">${PI.svg(L.enabled && st === 'ok' ? 'medal1' : 'trophy', 20)} <b>${esc(t('lb.st.' + st))}</b>`
          + (L.last ? ` · ${esc(t('lb.updated', { d: dateFmt(L.last, { dateStyle: 'medium', timeStyle: 'short' }) }))}` : '')
          + ' <span id="lbPlayers"></span></div>'
          + `<p class="muted">${esc(L.url ? t('lb.privacy') : t('lb.noServer'))}</p></div>`
          + (L.url ? (live || L.snapshot ? `<button class="mc-btn ${L.enabled ? '' : 'green'}" type="button" id="lbToggle">${esc(L.enabled ? t('lb.leave') : t('lb.join'))}</button>`
            : `<span class="muted">${esc(t('lb.fileMode'))}</span>`) : '') + '</div>';
        const b = $('#lbToggle', host);
        if (b) b.addEventListener('click', async () => {
          b.disabled = true;
          try {
            await fetch('api/leaderboard', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-PlayChrono': '1' }, body: JSON.stringify({ enabled: !L.enabled }) });
            setTimeout(async () => { try { D = await api('api/data.json'); renderTab(); } catch (e) { /* game closed */ } }, 1500);
          } catch (e) { b.disabled = false; }
        });
      },
    });
    let board = L.snapshot || null, failed = false;
    if (!board && L.url) {
      try { board = await fetchJson(L.url + '/api/v1/leaderboard' + (uuid ? '?uuid=' + encodeURIComponent(uuid) : ''), 6000); } catch (e) { failed = true; }
      if (seq !== renderSeq) return; // user switched tab meanwhile
    }
    const info = $('#lbPlayers');
    if (info) info.textContent = failed ? '· ' + t('lb.offline') : board && board.players ? '· ' + t('lb.players', { n: num(board.players) }) : '';
    const myId = String(uuid).replace(/-/g, '');
    const srv = board && Array.isArray(board.servers) ? board.servers.slice(0, 10) : [];
    const mine = Array.isArray(me.topServers) ? me.topServers.slice(0, 10) : [];
    card(g, {
      title: t('lb.srv'), icon: 'globe', sub: srv.length ? t('lb.srv_sub') : t('lb.srvMine'), full: true,
      render: (host) => {
        const rows = srv.length
          ? srv.map((x, i) => `<li style="--i:${i}"><span class="rank">${num(x.rank)}</span>${PI.svg('globe', 20)}<span class="name">${esc(x.host)}`
            + ` <small class="muted">${esc(t('lb.srvPlayers', { n: num(x.players) }))}</small></span><span class="lb-value">${esc(dur(x.ms))}</span></li>`).join('')
          : mine.map((x, i) => `<li style="--i:${i}"><span class="rank">${i + 1}</span>${PI.svg('globe', 20)}<span class="name">${esc(x[0])}</span><span class="lb-value">${esc(dur(x[1]))}</span></li>`).join('');
        host.innerHTML = rows ? `<ol class="lb-list">${rows}</ol>` : `<div class="pp-empty">${esc(t('ui.noLive'))}</div>`;
      },
    });
    for (const [key, kind, icon] of METRICS) {
      const m = board && board.metrics && board.metrics[key];
      const top = m && Array.isArray(m.top) ? m.top.slice(0, 10) : [];
      card(g, {
        title: t('m.' + key), icon,
        render: (host) => {
          const rows = top.map((p, i) => `<li class="${String(p.uuid) === myId ? 'me' : ''}" style="--i:${i}"><span class="rank">${num(p.rank)}</span>`
            + `${PI.avatar(p.uuid, 22)}<span class="name">${esc(p.name)}</span><span class="lb-value">${esc(mfmt(kind, p.value))}</span></li>`).join('');
          const mine = m && m.me ? t('lb.rank', { n: num(m.me.rank) }) : t('lb.notRanked');
          host.innerHTML = (rows ? `<ol class="lb-list">${rows}</ol>` : '')
            + `<div class="lb-me"><span>${PI.avatar(myId || 'me', 18)} ${esc(t('lb.you'))} · ${esc(board ? mine : t('lb.mine'))}</span><b>${esc(mfmt(kind, me[key]))}</b></div>`;
        },
      });
    }
  }


  // ============================================================================================ preferences
  /** Saved in the browser AND in the mod's config (survives a new port / file vs live page). */
  function savePref(key, value) {
    store.set({ theme: 'theme', keyboard: 'kb', seasonal: 'seasonal' }[key] || key, value);
    if (!location.protocol.startsWith('http')) return;
    fetch('api/prefs', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-PlayChrono': '1' }, body: JSON.stringify({ [key]: value }) }).catch(() => {});
  }
  function kbLayout() {
    const saved = store.get('kb', '');
    if (saved === 'qwerty' || saved === 'azerty') return saved;
    const p = D && D.prefs && D.prefs.keyboard;
    if (p === 'qwerty' || p === 'azerty') return p;
    return lang === 'fr' || /^fr\b/i.test(navigator.language || '') ? 'azerty' : 'qwerty';
  }
  // physical key (GLFW code, US names) -> label printed on an AZERTY keyboard
  const AZERTY = { 81: 'A', 87: 'Z', 65: 'Q', 90: 'W', 59: 'M', 77: ',', 44: ';', 46: ':', 47: '!', 91: '^', 93: '$', 39: 'ù', 92: '*', 96: '²',
    45: ')', 49: '&1', 50: 'é2', 51: '"3', 52: "'4", 53: '(5', 54: '-6', 55: 'è7', 56: '_8', 57: 'ç9', 48: 'à0' };

  // ============================================================================================ loading & shell
  async function api(path) {
    const r = await fetch(path, { cache: 'no-store' });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.json();
  }
  async function load() {
    if (location.protocol.startsWith('http')) {
      try { D = await api('api/data.json'); return; } catch (e) { /* game closed: fall back to data.js */ }
    }
    D = window.PLAYCHRONO_DATA || null;
  }

  function shell() {
    document.documentElement.lang = lang;
    document.title = 'PlayChrono';
    $('#subtitle').textContent = t('ui.subtitle');
    const gen = D && D.generated ? t('ui.generated', { d: dateFmt(D.generated, { dateStyle: 'medium', timeStyle: 'short' }) }) : '';
    $('#generated').textContent = gen;
    appliedSeason = currentSeason();
    seasonNote(appliedSeason);
    seasonButton();
    const meName = D && D.env && D.env.acct, meId = D && D.env && D.env.uuid;
    const me = $('#me');
    if (meName) {
      const face = D.face && /^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(D.face)
        ? `<img src="${D.face}" width="32" height="32" alt="">` : PI.avatar(meId || meName, 32);
      me.innerHTML = `<span class="me-face">${face}</span><span class="me-name">${esc(meName)}</span>`;
      me.title = meName;
      me.hidden = false;
    } else me.hidden = true;
    $('#hotbar').innerHTML = TABS.map((x, i) => `<a href="#${x}" class="${x === tab ? 'on' : ''}" ${x === tab ? 'aria-current="page"' : ''} title="${esc(t('tab.' + x))}" aria-label="${esc(t('tab.' + x))}">`
      + `${PI.svg(TAB_ICON[x], 34)}<span class="n">${i + 1}</span></a>`).join('');
    $('#hotbar').querySelectorAll('a').forEach((a, i) => {
      const html = () => `<div class="pp-tip-title">${esc(t('tab.' + TABS[i]))}</div><div>${esc(t('tabd.' + TABS[i]))}</div>`
        + `<div class="pp-tip-key">${esc(t('ui.key', { n: i + 1 }))}</div>`;
      a.addEventListener('mouseenter', (e) => { showLabel(t('tab.' + TABS[i])); C.tip(html(), e); });
      a.addEventListener('mousemove', (e) => C.tip(html(), e));
      a.addEventListener('mouseleave', () => C.tip(null));
      a.addEventListener('focus', () => { const r = a.getBoundingClientRect(); C.tip(html(), { clientX: r.left, clientY: r.top - 90 }); });
      a.addEventListener('blur', () => C.tip(null));
      a.addEventListener('click', () => C.tip(null));
    });
    showLabel(t('tab.' + tab));
    drawLandscape();
    const sel = $('#period');
    sel.innerHTML = ['7', '30', '90', '365', 'all'].map((p) => `<option value="${p}"${p === period ? ' selected' : ''}>${esc(t('period.' + p))}</option>`).join('');
    const ls = $('#lang');
    ls.innerHTML = Object.keys(I).map((l) => `<option value="${l}"${l === lang ? ' selected' : ''}>${esc(I[l]._name)}</option>`).join('');
    $('#theme').title = t('ui.theme');
    renderTab();
  }

  let labelTimer;
  function showLabel(text) {
    const l = $('#hblabel');
    l.textContent = text;
    l.classList.add('show');
    clearTimeout(labelTimer);
    labelTimer = setTimeout(() => l.classList.remove('show'), 1600);
  }
  let landKey = '';
  // ============================================================================================ seasons
  const SEASONS = ['xmas', 'newyear', 'easter', 'halloween'];
  /** Christmas, New Year, Easter, Halloween: from the local date, every year (?season=xmas|...|none to preview). */
  /** The event of today's local date (or the ?season= preview), whether or not the player turned themes off. */
  function dateSeason() {
    const q = new URLSearchParams(location.search).get('season');
    if (q) return SEASONS.includes(q) ? q : null;
    return PI.season(new Date());
  }
  /** On unless the player switched it off (button next to light / dark, saved in the browser and in the mod). */
  function seasonOn() {
    const s = store.get('seasonal', '');
    if (s === 'true' || s === 'false') return s === 'true';
    return !(D && D.prefs && D.prefs.seasonal === 'false');
  }
  function currentSeason() { return seasonOn() ? dateSeason() : null; }
  function seasonButton() {
    const s = dateSeason(), b = $('#seasonBtn');
    if (!s) { b.hidden = true; return; } // nothing to switch outside the events
    const on = seasonOn();
    b.hidden = false;
    b.innerHTML = PI.svg(PI.seasonIcon(s), 22);
    b.classList.toggle('off', !on);
    b.setAttribute('aria-pressed', String(on));
    b.title = t(on ? 'season.btnOn' : 'season.btnOff');
    b.setAttribute('aria-label', b.title);
  }
  let appliedSeason;
  /** Applies the theme of the day; called at start, on the switch, and every minute (midnight on 29 Dec -> New Year). */
  function applySeason(force) {
    seasonButton();
    const s = currentSeason();
    if (!force && s === appliedSeason) return;
    appliedSeason = s;
    landKey = '';
    drawLandscape();
    seasonNote(s);
  }
  function seasonNote(s) {
    const box = $('#season');
    const now = new Date();
    const year = now.getMonth() === 0 ? now.getFullYear() : now.getFullYear() + (s === 'newyear' ? 1 : 0);
    const id = s + '-' + (now.getMonth() === 0 ? now.getFullYear() - 1 : now.getFullYear()); // one event = one id, Jan 1-2 included
    if (!s || store.get('seasonHide', '') === id) { box.hidden = true; return; }
    box.innerHTML = `${PI.svg(PI.seasonIcon(s), 28)}<span>${esc(t('season.' + s, { y: year }))}</span>`
      + `<button type="button" class="mc-btn sm" aria-label="${esc(t('season.hide'))}">✕</button>`;
    $('button', box).addEventListener('click', () => { store.set('seasonHide', id); box.hidden = true; });
    box.hidden = false;
  }

  function drawLandscape() {
    const night = document.documentElement.dataset.theme ? document.documentElement.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
    const cols = Math.max(80, Math.min(240, Math.round(window.innerWidth / 10)));
    const season = currentSeason();
    const key = cols + (night ? 'n' : 'd') + (season || '');
    if (key === landKey) return;
    landKey = key;
    if (season) document.documentElement.dataset.season = season; else delete document.documentElement.dataset.season;
    $('#land').innerHTML = PI.landscape(cols, night, season);
    $('#fx').innerHTML = PI.fx(cols, season);
    const cn = night || season === 'newyear'; // the New Year sky is always a night sky
    $('#clouds').innerHTML = PI.clouds(cols, cn) + PI.clouds(cols, cn);
    $('#theme').textContent = night ? '☾' : '☀';
  }
  function go(i) {
    const x = TABS[(i + TABS.length) % TABS.length];
    if (x !== tab) location.hash = x;
  }

  async function boot() {
    try { await load(); } catch (e) { return; }
    const q = new URLSearchParams(location.search);
    if (q.get('lang') && I[q.get('lang')]) store.set('lang', q.get('lang'));
    lang = pickLang();
    const pt = D && D.prefs && D.prefs.theme; // theme saved by the mod, if this browser has none
    if (!store.get('theme', '') && !q.get('theme') && (pt === 'light' || pt === 'dark')) document.documentElement.dataset.theme = pt;
    if (q.get('period')) period = q.get('period');
    else if (!store.get('period', '') && D && D.days) { // nothing in the last 30 days yet: show everything
      const recent = periodDates().some((d) => played(D.days[d]));
      if (!recent) period = 'all';
    }
    shell();
  }

  // ============================================================================================ events
  document.addEventListener('DOMContentLoaded', () => {
    const th = store.get('theme', '');
    if (th) document.documentElement.dataset.theme = th;
    $('#period').addEventListener('change', (e) => { period = e.target.value; store.set('period', period); renderTab(); });
    $('#lang').addEventListener('change', (e) => { lang = e.target.value; store.set('lang', lang); shell(); });
    $('#theme').addEventListener('click', () => {
      const dark = document.documentElement.dataset.theme ? document.documentElement.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
      document.documentElement.dataset.theme = dark ? 'light' : 'dark';
      savePref('theme', document.documentElement.dataset.theme);
      drawLandscape();
      renderers.forEach((r) => r());
    });
    $('#seasonBtn').addEventListener('click', () => {
      savePref('seasonal', String(!seasonOn()));
      applySeason(true);
      renderers.forEach((r) => r());
    });
    setInterval(() => applySeason(false), 60000);
    // hotbar: keys 1-9 and the mouse wheel, like in game
    document.addEventListener('keydown', (e) => {
      if (e.ctrlKey || e.altKey || e.metaKey || /input|select|textarea/i.test(e.target.tagName)) return;
      const n = Number(e.key);
      if (n >= 1 && n <= TABS.length) go(n - 1);
    });
    $('#hotbar').addEventListener('wheel', (e) => { e.preventDefault(); go(TABS.indexOf(tab) + (e.deltaY > 0 ? 1 : -1)); }, { passive: false });
    window.addEventListener('hashchange', () => {
      const next = location.hash.slice(1) || 'overview';
      if (next !== tab) switchTab(TABS.includes(next) ? next : 'overview');
    });
    scrollEffects();
    let rt;
    window.addEventListener('resize', () => { clearTimeout(rt); rt = setTimeout(() => { drawLandscape(); renderers.forEach((r) => r()); }, 150); });
    matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => renderers.forEach((r) => r()));
    boot();
    if (location.protocol.startsWith('http')) setInterval(async () => { // live refresh
      if (document.hidden) return;
      if (tab === 'leaderboard') return; // keep the board stable while reading it
      try { D = await api('api/data.json'); renderTab(true); $('#generated').textContent = t('ui.generated', { d: dateFmt(D.generated, { dateStyle: 'medium', timeStyle: 'short' }) }); } catch (e) { /* game closed */ }
    }, 60000);
  });
})();
