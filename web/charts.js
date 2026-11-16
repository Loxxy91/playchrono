/* PlayChrono charts — dependency-free SVG charts (works offline, from file://, inside the mod jar).
 * Spec: thin marks (bars <= 24px, 4px rounded data-end, square at baseline), 2px surface gaps between stacked
 * segments, 2px lines, recessive hairline grid, hover tooltip on every mark, colors read from CSS tokens so the
 * dark theme uses its own validated steps. */
(function () {
  'use strict';
  const NS = 'http://www.w3.org/2000/svg';
  const css = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

  function svg(tag, attrs, parent) {
    const e = document.createElementNS(NS, tag);
    for (const k in attrs || {}) if (attrs[k] !== undefined && attrs[k] !== null) e.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(e);
    return e;
  }

  // ---------------------------------------------------------------------------------------------- tooltip
  let tipEl;
  function tip(html, ev) {
    if (!tipEl) {
      tipEl = document.createElement('div');
      tipEl.className = 'pp-tip';
      tipEl.setAttribute('role', 'tooltip');
      document.body.appendChild(tipEl);
    }
    if (html == null) { tipEl.style.opacity = 0; return; }
    tipEl.innerHTML = html;
    tipEl.style.opacity = 1;
    const pad = 14, r = tipEl.getBoundingClientRect();
    let x = ev.clientX + pad, y = ev.clientY + pad;
    if (x + r.width > window.innerWidth - 8) x = ev.clientX - r.width - pad;
    if (y + r.height > window.innerHeight - 8) y = ev.clientY - r.height - pad;
    tipEl.style.left = Math.max(8, x) + 'px';
    tipEl.style.top = Math.max(8, y) + 'px';
  }
  function bindTip(node, htmlFn) {
    node.addEventListener('mousemove', (e) => tip(htmlFn(), e));
    node.addEventListener('mouseleave', () => tip(null));
    node.setAttribute('tabindex', '0');
    node.addEventListener('focus', () => {
      const r = node.getBoundingClientRect();
      tip(htmlFn(), { clientX: r.left + r.width / 2, clientY: r.top });
    });
    node.addEventListener('blur', () => tip(null));
  }
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const swatch = (c) => `<i class="pp-sw" style="background:${c}"></i>`;

  // ---------------------------------------------------------------------------------------------- helpers
  function niceStep(max, count) {
    const raw = max / Math.max(1, count);
    const mag = Math.pow(10, Math.floor(Math.log10(raw || 1)));
    const n = raw / mag;
    return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * mag;
  }
  /** Round ticks computed in display units (unit = 3.6e6 -> whole hours, 60000 -> minutes...). */
  function ticks(max, count, unit, integer) {
    unit = unit || 1;
    const m = max / unit;
    if (m <= 0) return [0];
    const step = integer ? Math.max(1, Math.round(niceStep(m, count))) : niceStep(m, count);
    const out = [];
    for (let v = 0; v <= m + step * 0.001; v += step) out.push(v);
    if (out[out.length - 1] < m) out.push(out[out.length - 1] + step);
    return out.map((v) => Math.round(v * 1e6) / 1e6 * unit);
  }
  /** Bar/column path: 4px rounded data-end, square at the baseline. */
  function barPath(x, y, w, h, horizontal) {
    const r = Math.min(2, (horizontal ? h : w) / 2, horizontal ? w : h);
    if (horizontal) return `M${x},${y}h${w - r}a${r},${r} 0 0 1 ${r},${r}v${h - 2 * r}a${r},${r} 0 0 1 -${r},${r}h-${w - r}z`;
    return `M${x},${y + h}v-${h - r}a${r},${r} 0 0 1 ${r},-${r}h${w - 2 * r}a${r},${r} 0 0 1 ${r},${r}v${h - r}z`;
  }
  function clear(host) { while (host.firstChild) host.removeChild(host.firstChild); }
  function width(host) { return Math.max(260, host.clientWidth || host.parentNode.clientWidth || 600); }

  // ---------------------------------------------------------------------------------------------- columns
  /** Vertical columns, single or stacked. opts: labels[], series[{name,color,values[]}], fmt(v), tipTitle(i),
   *  height, unitStep (axis step multiple), labelEvery, highlight (index). */
  function columns(host, o) {
    clear(host);
    const W = width(host), H = o.height || 220, m = { l: 48, r: 8, t: 10, b: 26 };
    const n = o.labels.length;
    const totals = o.labels.map((_, i) => o.series.reduce((s, se) => s + (se.values[i] || 0), 0));
    const max = Math.max(...totals, 0);
    const tk = ticks(max, 4, o.unit, o.integer);
    const top = tk[tk.length - 1] || 1;
    const s = svg('svg', { viewBox: `0 0 ${W} ${H}`, width: '100%', height: H, role: 'img', 'aria-label': o.aria || '' }, host);
    const ih = H - m.t - m.b, iw = W - m.l - m.r;
    const y = (v) => m.t + ih - (v / top) * ih;
    for (const t of tk) {
      svg('line', { x1: m.l, x2: W - m.r, y1: y(t), y2: y(t), stroke: t === 0 ? css('--axis') : css('--grid'), 'stroke-width': 1 }, s);
      const tx = svg('text', { x: m.l - 6, y: y(t) + 4, 'text-anchor': 'end', class: 'pp-axis' }, s);
      tx.textContent = o.fmtAxis ? o.fmtAxis(t) : o.fmt(t);
    }
    const band = iw / Math.max(1, n);
    const bw = Math.max(2, Math.min(24, band * 0.7));
    const every = o.labelEvery || Math.ceil(n / Math.max(1, Math.floor(iw / 48)));
    for (let i = 0; i < n; i++) {
      const cx = m.l + band * i + band / 2;
      let acc = 0;
      const segs = o.series.map((se) => ({ se, v: se.values[i] || 0 })).filter((x) => x.v > 0);
      segs.forEach((x, k) => {
        const y0 = y(acc), y1 = y(acc + x.v);
        const last = k === segs.length - 1;
        const gap = k > 0 ? 2 : 0; // 2px surface gap between stacked segments
        const h = Math.max(0, y0 - y1 - gap);
        if (h > 0) {
          const st = 'animation-delay:' + Math.min(400, i * 10) + 'ms';
          if (last) svg('path', { d: barPath(cx - bw / 2, y1, bw, h), fill: x.se.color, class: 'pp-grow', style: st }, s);
          else svg('rect', { x: cx - bw / 2, y: y1, width: bw, height: h, fill: x.se.color, class: 'pp-grow', style: st }, s);
        }
        acc += x.v;
      });
      if (i % every === 0 || i === n - 1 && n < 40) {
        const lx = svg('text', { x: cx, y: H - 8, 'text-anchor': 'middle', class: 'pp-axis' }, s);
        lx.textContent = o.labels[i];
      }
      const hit = svg('rect', { x: m.l + band * i, y: m.t, width: band, height: ih, fill: 'transparent', class: 'pp-hit' }, s);
      bindTip(hit, () => {
        const rows = o.series.filter((se) => (se.values[i] || 0) > 0)
          .map((se) => `<div>${swatch(se.color)}${esc(se.name)} <b>${o.fmt(se.values[i])}</b></div>`).join('');
        const title = o.tipTitle ? o.tipTitle(i) : o.labels[i];
        const total = o.series.length > 1 ? `<div class="pp-tip-total">${esc(o.totalLabel || 'Total')} <b>${o.fmt(totals[i])}</b></div>` : '';
        return `<div class="pp-tip-title">${esc(title)}</div>${rows || '<div>—</div>'}${total}`;
      });
    }
    if (o.highlight != null && o.highlight >= 0) {
      const cx = m.l + band * o.highlight + band / 2;
      svg('circle', { cx, cy: H - m.b + 6, r: 2.5, fill: css('--ink2') }, s);
    }
    legend(host, o.series.length > 1 ? o.series : null);
  }

  function legend(host, series) {
    if (!series) return;
    const l = document.createElement('div');
    l.className = 'pp-legend';
    l.innerHTML = series.map((se) => `<span>${swatch(se.color)}${esc(se.name)}</span>`).join('');
    host.appendChild(l);
  }

  // ---------------------------------------------------------------------------------------------- horizontal bars
  /** Ranked horizontal bars. items[{label, value, sub?, color?}], fmt(v), limit. */
  function hbars(host, o) {
    clear(host);
    const items = o.items.filter((x) => x.value > 0).slice(0, o.limit || 12);
    if (!items.length) { empty(host, o.emptyText); return; }
    const max = Math.max(...items.map((x) => x.value));
    const list = document.createElement('div');
    list.className = 'pp-hbars';
    for (const it of items) {
      const row = document.createElement('div');
      row.className = 'pp-hrow' + (o.onClick ? ' pp-click' : '');
      const pct = Math.max(0.5, (it.value / max) * 100);
      row.innerHTML = `<div class="pp-hlabel" title="${esc(it.label)}">${it.icon || ''}${esc(it.label)}${it.sub ? `<small>${esc(it.sub)}</small>` : ''}</div>`
        + `<div class="pp-htrack"><div class="pp-hbar" style="width:${pct}%;background:${it.color || css('--s1')}"></div></div>`
        + `<div class="pp-hval">${o.fmt(it.value)}</div>`;
      bindTip(row, () => `<div class="pp-tip-title">${esc(it.label)}</div><div><b>${o.fmt(it.value)}</b>${o.share ? ' · ' + o.share(it.value) : ''}</div>${it.tip || ''}`);
      if (o.onClick) row.addEventListener('click', () => o.onClick(it));
      list.appendChild(row);
    }
    host.appendChild(list);
  }

  function empty(host, text) {
    const d = document.createElement('div');
    d.className = 'pp-empty';
    d.textContent = text || '—';
    host.appendChild(d);
  }

  // ---------------------------------------------------------------------------------------------- sequential color
  function seq(frac) {
    const ramp = css('--seq').split(',').map((x) => x.trim());
    if (frac <= 0) return css('--cell-empty');
    const idx = Math.min(ramp.length - 1, Math.floor(Math.sqrt(frac) * ramp.length));
    return ramp[idx];
  }

  // ---------------------------------------------------------------------------------------------- heatmap
  /** rows x cols matrix (e.g. weekday x hour). */
  function heatmap(host, o) {
    clear(host);
    const W = width(host), lw = 44, cw = (W - lw - 4) / o.cols.length, ch = Math.min(26, Math.max(16, cw * 0.9));
    const H = ch * o.rows.length + 22;
    const s = svg('svg', { viewBox: `0 0 ${W} ${H}`, width: '100%', height: H, role: 'img' }, host);
    const max = Math.max(1e-9, ...o.values.flat());
    o.rows.forEach((r, i) => {
      const t = svg('text', { x: lw - 6, y: i * ch + ch / 2 + 4, 'text-anchor': 'end', class: 'pp-axis' }, s);
      t.textContent = r;
      o.cols.forEach((c, j) => {
        const v = o.values[i][j] || 0;
        const cell = svg('rect', { x: lw + j * cw + 1, y: i * ch + 1, width: Math.max(1, cw - 2), height: ch - 2, rx: 1, fill: seq(v / max), class: 'pp-fade' }, s);
        bindTip(cell, () => `<div class="pp-tip-title">${esc(r)} · ${esc(o.colTitle ? o.colTitle(j) : c)}</div><div><b>${o.fmt(v)}</b></div>`);
      });
    });
    const every = Math.ceil(o.cols.length / Math.max(1, Math.floor((W - lw) / 30)));
    o.cols.forEach((c, j) => {
      if (j % every) return;
      const t = svg('text', { x: lw + j * cw + cw / 2, y: H - 6, 'text-anchor': 'middle', class: 'pp-axis' }, s);
      t.textContent = c;
    });
    scaleLegend(host, o.fmt(0), o.fmt(max));
  }

  function scaleLegend(host, lo, hi) {
    const d = document.createElement('div');
    d.className = 'pp-scale';
    const ramp = css('--seq').split(',').map((x) => x.trim());
    d.innerHTML = `<span>${esc(lo)}</span>${ramp.map((c) => `<i style="background:${c}"></i>`).join('')}<span>${esc(hi)}</span>`;
    host.appendChild(d);
  }

  // ---------------------------------------------------------------------------------------------- calendar
  /** GitHub-like year calendar. o.values: {YYYY-MM-DD: number}, o.end (Date), o.weeks, o.locale, o.weekStart. */
  function calendar(host, o) {
    clear(host);
    const W = width(host), lw = 30, weeks = o.weeks || 53;
    const cs = Math.min(15, Math.max(6, (W - lw) / weeks));
    const H = cs * 7 + 20;
    const s = svg('svg', { viewBox: `0 0 ${W} ${H}`, width: '100%', height: H, role: 'img' }, host);
    const end = new Date(o.end);
    end.setHours(12, 0, 0, 0);
    const dow = (d) => (d.getDay() - (o.weekStart || 1) + 7) % 7;
    const start = new Date(end);
    start.setDate(end.getDate() - dow(end) - (weeks - 1) * 7);
    const max = Math.max(1e-9, ...Object.values(o.values));
    const mfmt = new Intl.DateTimeFormat(o.locale, { month: 'short' });
    const dfmt = new Intl.DateTimeFormat(o.locale, { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' });
    let lastMonth = -1;
    for (let d = new Date(start), i = 0; d <= end; d.setDate(d.getDate() + 1), i++) {
      const w = Math.floor(i / 7), r = dow(d);
      const key = iso(d);
      const v = o.values[key] || 0;
      const x = lw + w * cs, y = 14 + r * cs;
      const cell = svg('rect', { x: x + 1, y: y + 1, width: cs - 2, height: cs - 2, rx: 0, fill: seq(v / max) }, s);
      const label = dfmt.format(d);
      bindTip(cell, () => `<div class="pp-tip-title">${esc(label)}</div><div><b>${o.fmt(v)}</b></div>`);
      if (r === 0 && d.getMonth() !== lastMonth) {
        lastMonth = d.getMonth();
        const t = svg('text', { x, y: 10, class: 'pp-axis' }, s);
        t.textContent = mfmt.format(d);
      }
    }
    const wd = new Intl.DateTimeFormat(o.locale, { weekday: 'narrow' });
    [0, 2, 4].forEach((r) => {
      const d = new Date(start);
      d.setDate(start.getDate() + r);
      const t = svg('text', { x: lw - 6, y: 14 + r * cs + cs / 2 + 4, 'text-anchor': 'end', class: 'pp-axis' }, s);
      t.textContent = wd.format(d);
    });
    scaleLegend(host, o.fmt(0), o.fmt(max));
  }

  function iso(d) {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  // ---------------------------------------------------------------------------------------------- day timelines
  /** rows[{label, segs:[[startMin,endMin,cls]]}], classes{cls:{name,color}} — WakaTime-style schedule strips. */
  function timeline(host, o) {
    clear(host);
    const W = width(host), lw = 86, rh = 18, gap = 6;
    const H = o.rows.length * (rh + gap) + 22;
    const s = svg('svg', { viewBox: `0 0 ${W} ${H}`, width: '100%', height: H, role: 'img' }, host);
    const iw = W - lw - 8;
    const x = (min) => lw + (min / 1440) * iw;
    for (let hr = 0; hr <= 24; hr += 3) {
      svg('line', { x1: x(hr * 60), x2: x(hr * 60), y1: 0, y2: H - 18, stroke: css('--grid'), 'stroke-width': 1 }, s);
      const t = svg('text', { x: x(hr * 60), y: H - 5, 'text-anchor': 'middle', class: 'pp-axis' }, s);
      t.textContent = String(hr).padStart(2, '0') + 'h';
    }
    o.rows.forEach((row, i) => {
      const y = i * (rh + gap);
      const t = svg('text', { x: lw - 8, y: y + rh / 2 + 4, 'text-anchor': 'end', class: 'pp-axis' }, s);
      t.textContent = row.label;
      svg('rect', { x: lw, y, width: iw, height: rh, rx: 0, fill: css('--cell-empty') }, s);
      for (const seg of row.segs) {
        const c = o.classes[seg[2]];
        if (!c) continue;
        const x0 = x(seg[0]), x1 = x(seg[1]);
        const r = svg('rect', { x: x0, y, width: Math.max(1.5, x1 - x0 - 1), height: rh, rx: 0, fill: c.color, class: 'pp-fade' }, s);
        bindTip(r, () => `<div class="pp-tip-title">${esc(row.label)}</div><div>${swatch(c.color)}${esc(c.name)}</div><div><b>${hm(seg[0])} – ${hm(seg[1])}</b> (${o.fmt((seg[1] - seg[0]) * 60000)})</div>`);
      }
    });
    legend(host, Object.values(o.classes));
  }
  const hm = (min) => String(Math.floor(min / 60) % 24).padStart(2, '0') + ':' + String(min % 60).padStart(2, '0');

  // ---------------------------------------------------------------------------------------------- line
  /** labels[], series[{name,color,values[]}] with crosshair tooltip. */
  function line(host, o) {
    clear(host);
    const W = width(host), H = o.height || 200, m = { l: 48, r: 12, t: 10, b: 26 };
    const n = o.labels.length;
    const max = Math.max(0, ...o.series.flatMap((se) => se.values.filter((v) => v != null)));
    const tk = ticks(max, 4, o.unit, o.integer);
    const top = tk[tk.length - 1] || 1;
    const s = svg('svg', { viewBox: `0 0 ${W} ${H}`, width: '100%', height: H, role: 'img' }, host);
    const ih = H - m.t - m.b, iw = W - m.l - m.r;
    const X = (i) => m.l + (n <= 1 ? iw / 2 : (i / (n - 1)) * iw);
    const Y = (v) => m.t + ih - (v / top) * ih;
    for (const t of tk) {
      svg('line', { x1: m.l, x2: W - m.r, y1: Y(t), y2: Y(t), stroke: t === 0 ? css('--axis') : css('--grid') }, s);
      const tx = svg('text', { x: m.l - 6, y: Y(t) + 4, 'text-anchor': 'end', class: 'pp-axis' }, s);
      tx.textContent = o.fmtAxis ? o.fmtAxis(t) : o.fmt(t);
    }
    const every = Math.ceil(n / Math.max(1, Math.floor(iw / 64)));
    o.labels.forEach((l, i) => {
      if (i % every) return;
      const t = svg('text', { x: X(i), y: H - 8, 'text-anchor': 'middle', class: 'pp-axis' }, s);
      t.textContent = l;
    });
    for (const se of o.series) {
      let d = '';
      se.values.forEach((v, i) => { if (v != null) d += (d ? 'L' : 'M') + X(i) + ',' + Y(v); });
      if (o.area && se === o.series[0]) {
        svg('path', { d: d + `L${X(n - 1)},${Y(0)}L${X(0)},${Y(0)}z`, fill: se.color, opacity: 0.1 }, s);
      }
      svg('path', { d, fill: 'none', stroke: se.color, 'stroke-width': 2.5, 'stroke-linejoin': 'round', 'stroke-linecap': 'round', class: 'pp-fade' }, s);
      const li = se.values.length - 1;
      if (li >= 0 && se.values[li] != null) svg('circle', { cx: X(li), cy: Y(se.values[li]), r: 4, fill: se.color, stroke: css('--surface'), 'stroke-width': 2 }, s);
    }
    const cross = svg('line', { y1: m.t, y2: m.t + ih, stroke: css('--axis'), 'stroke-width': 1, opacity: 0 }, s);
    const hit = svg('rect', { x: m.l, y: m.t, width: iw, height: ih, fill: 'transparent' }, s);
    hit.addEventListener('mousemove', (e) => {
      const r = s.getBoundingClientRect();
      const px = ((e.clientX - r.left) / r.width) * W;
      const i = Math.max(0, Math.min(n - 1, Math.round(((px - m.l) / iw) * (n - 1))));
      cross.setAttribute('x1', X(i));
      cross.setAttribute('x2', X(i));
      cross.setAttribute('opacity', 1);
      tip(`<div class="pp-tip-title">${esc(o.tipTitle ? o.tipTitle(i) : o.labels[i])}</div>` + o.series.map((se) =>
        `<div>${swatch(se.color)}${esc(se.name)} <b>${se.values[i] == null ? '—' : o.fmt(se.values[i])}</b></div>`).join(''), e);
    });
    hit.addEventListener('mouseleave', () => { cross.setAttribute('opacity', 0); tip(null); });
    legend(host, o.series.length > 1 ? o.series : null);
  }

  // ---------------------------------------------------------------------------------------------- segmented bar
  function segbar(host, o) {
    clear(host);
    const parts = o.parts.filter((p) => p.value > 0);
    const total = parts.reduce((s, p) => s + p.value, 0);
    if (!total) { empty(host, o.emptyText); return; }
    const bar = document.createElement('div');
    bar.className = 'pp-seg';
    bar.setAttribute('role', 'img');
    bar.setAttribute('aria-label', parts.map((p) => `${p.name} ${Math.round((p.value / total) * 100)}%`).join(', '));
    for (const p of parts) {
      const share = p.value / total;
      const b = document.createElement('div');
      b.style.flex = String(share);
      b.style.background = p.color;
      b.style.color = p.ink || '#fff';
      const pct = Math.round(share * 100);
      if (share > 0.07) b.textContent = pct + '%';
      bindTip(b, () => `<div class="pp-tip-title">${swatch(p.color)}${esc(p.name)}</div><div><b>${o.fmt(p.value)}</b> · ${pct}%</div>`);
      bar.appendChild(b);
    }
    host.appendChild(bar);
    legend(host, parts);
  }

  // ---------------------------------------------------------------------------------------------- keyboard
  const KB = [
    [[256, 'Esc', 1.2], [290, 'F1'], [291, 'F2'], [292, 'F3'], [293, 'F4'], [294, 'F5'], [295, 'F6'], [296, 'F7'], [297, 'F8'], [298, 'F9'], [299, 'F10'], [300, 'F11'], [301, 'F12']],
    [[96, '`'], [49, '1'], [50, '2'], [51, '3'], [52, '4'], [53, '5'], [54, '6'], [55, '7'], [56, '8'], [57, '9'], [48, '0'], [45, '-'], [61, '='], [259, '⌫', 1.8]],
    [[258, 'Tab', 1.4], [81, 'Q'], [87, 'W'], [69, 'E'], [82, 'R'], [84, 'T'], [89, 'Y'], [85, 'U'], [73, 'I'], [79, 'O'], [80, 'P'], [91, '['], [93, ']'], [92, '\\', 1.4]],
    [[280, 'Caps', 1.7], [65, 'A'], [83, 'S'], [68, 'D'], [70, 'F'], [71, 'G'], [72, 'H'], [74, 'J'], [75, 'K'], [76, 'L'], [59, ';'], [39, "'"], [257, '↵', 2.1]],
    [[340, 'Shift', 2.2], [90, 'Z'], [88, 'X'], [67, 'C'], [86, 'V'], [66, 'B'], [78, 'N'], [77, 'M'], [44, ','], [46, '.'], [47, '/'], [344, 'Shift', 2.6]],
    [[341, 'Ctrl', 1.5], [343, '⊞', 1.2], [342, 'Alt', 1.3], [32, 'Space', 6.4], [346, 'AltGr', 1.3], [345, 'Ctrl', 1.5], [263, '←'], [264, '↓'], [265, '↑'], [262, '→']],
  ];
  function keyboard(host, o) {
    clear(host);
    const max = Math.max(1, ...Object.values(o.counts));
    const wrap = document.createElement('div');
    wrap.className = 'pp-kb';
    for (const row of KB) {
      const r = document.createElement('div');
      r.className = 'pp-kb-row';
      for (const [code, label, w] of row) {
        const v = o.counts[code] || 0;
        const k = document.createElement('div');
        k.className = 'pp-key';
        k.style.flex = String(w || 1);
        const frac = v / max;
        k.style.background = seq(frac);
        if (frac > 0.45) k.classList.add('pp-key-dark');
        const shown = (o.labels && o.labels[code]) || label;
        k.textContent = shown;
        bindTip(k, () => `<div class="pp-tip-title">${esc(shown)}</div><div><b>${o.fmt(v)}</b></div>`);
        r.appendChild(k);
      }
      wrap.appendChild(r);
    }
    host.appendChild(wrap);
    scaleLegend(host, o.fmt(0), o.fmt(max));
  }

  window.PPCharts = { columns, hbars, heatmap, calendar, timeline, line, segbar, keyboard, tip, esc, iso, empty };
})();
