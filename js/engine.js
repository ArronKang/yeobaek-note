/* 필기 엔진: 패널·페이지, 획 그리기, 지우개, 올가미, 공간 삽입, 텍스트, 모양 보정, 제스처 */
(function (Y) {
'use strict';
const h = Y.h, $ = Y.$, rnd = Y.rnd;
const MM = 210, RATIO = 1.4142, LILAC = '#B9A9FF', ACT_W = 794;
Y.MM = MM; Y.RATIO = RATIO;
Y.panes = []; Y.active = null; Y.penDown = false; Y.lastPen = -1e9;
Y.makePage = tpl => ({ ratio: RATIO, tpl: tpl || 'lined', strokes: [], texts: [] });
const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
const paperDark = () => document.documentElement.dataset.paper === 'dark';

/* =============== 패널 =============== */
Y.createPane = function (parent) {
  const root = h('div', { class: 'pane' });
  const scroll = h('div', { class: 'scroll' });
  const pages = h('div', { class: 'pages' });
  scroll.append(pages); root.append(scroll); parent.append(root);
  const P = { root, scroll, pages, doc: null, W: 0, zoom: 'width', hist: [], redo: [], sel: null, cur: null, scrolling: null, pinch: null, io: null, search: null, bookmarks: new Set(), pageIdx: 0 };
  root.addEventListener('pointerdown', () => Y.setActive(P), true);
  scroll.addEventListener('scroll', () => { cancelAnimationFrame(P.raf); P.raf = requestAnimationFrame(() => updatePageIdx(P)); }, { passive: true });
  attachGestures(P);
  Y.panes.push(P);
  return P;
};
Y.destroyPane = function (P) {
  if (P.io) P.io.disconnect();
  P.root.remove();
  Y.panes = Y.panes.filter(p => p !== P);
  if (Y.active === P) Y.active = Y.panes[0] || null;
};
Y.setActive = function (P) {
  if (Y.active === P) return;
  Y.active = P;
  Y.panes.forEach(p => p.root.classList.toggle('active', p === P && Y.panes.length > 1));
  Y.onActive && Y.onActive(P);
};

function computeW(P) {
  const sc = P.scroll, cs = getComputedStyle(sc);
  const cw = sc.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight);
  const ch = sc.clientHeight - parseFloat(cs.paddingTop) - 24;
  const first = P.doc && P.doc.pages[0] ? P.doc.pages[0].ratio : RATIO;
  let W;
  if (P.zoom === 'fit') W = Math.min(cw - 8, ch / first);
  else if (P.zoom === 'width') W = Math.min(cw - 8, 1100);
  else if (P.zoom === 'actual') W = ACT_W;
  else W = ACT_W * P.zoom;
  return Math.round(Math.max(280, Math.min(2400, W)));
}
Y.zoomPercent = P => Math.round(P.W / ACT_W * 100);

Y.openDoc = function (P, doc) {
  P.doc = doc; P.hist = []; P.redo = []; P.sel = null; P.search = null; P.pageIdx = 0;
  buildPane(P);
};
function buildPane(P) {
  if (P.io) P.io.disconnect();
  P.pages.innerHTML = '';
  P.W = computeW(P);
  P.io = new IntersectionObserver(es => es.forEach(en => {
    const pg = en.target._pg; if (!pg) return;
    if (en.isIntersecting) { if (!pg.active) { pg.active = true; allocate(pg); } }
    else if (pg.active) { pg.active = false; free(pg); }
  }), { root: P.scroll, rootMargin: '1400px 0px' });
  P.doc.pages.forEach(pg => createPage(P, pg));
  if (P.doc.type === 'note') {
    const b = h('button', { type: 'button', class: 'addpage', html: Y.ic('plus') + '<span>페이지 추가</span>', onclick: () => Y.addPage(P) });
    P.pages.append(b); P.addBtn = b;
  }
  P.doc.pages.forEach(pg => { setSizes(pg); P.io.observe(pg.el); });
  P.scroll.scrollTop = 0; P.scroll.scrollLeft = 0;
  updatePageIdx(P);
}
Y.rebuildPane = buildPane;

function createPage(P, pg, before) {
  pg.pane = P; pg.active = false; pg.token = 0; pg.task = null; pg.rs = 1; pg.dash = 0;
  const wrap = h('div', { class: 'page' });
  const paper = h('div', { class: 'paper tpl-' + pg.tpl });
  const tpl = Y.tplHTML(pg.tpl); if (tpl) paper.insertAdjacentHTML('beforeend', tpl);
  if (P.doc.type === 'pdf') { pg.bg = h('canvas', { class: 'pdfbg' }); paper.append(pg.bg); wrap.classList.add('loading'); }
  else if (pg.bg) pg.bg = null;
  if (P.doc.type === 'note') paper.append(h('div', { class: 'holes' }));
  pg.ink = h('canvas', { class: 'ink' });
  // 저지연 캔버스: 브라우저 합성 단계를 건너뛰어 펜 끝을 더 빨리 그림 (첫 getContext에서만 지정 가능)
  try { pg.ink.getContext('2d', { desynchronized: true }); } catch (e) {}
  // 예측 획: 펜이 갈 곳을 미리 그려 끝이 따라붙는 느낌을 줄임. 다음 움직임에서 지움
  pg.pv = h('canvas', { class: 'pv', 'aria-hidden': 'true' });
  pg.tl = h('div', { class: 'tlayer' });
  paper.append(pg.ink, pg.pv, pg.tl);
  if (P.doc.type === 'quiz' && Y.quizLayer) paper.append(Y.quizLayer(P.doc, pg));
  wrap.append(paper);
  if (P.doc.type === 'note') wrap.append(h('div', { class: 'rings' }));
  pg.el = wrap; pg.paper = paper; wrap._pg = pg;
  pg.ink.addEventListener('pointerdown', e => onDown(e, pg));
  pg.ink.addEventListener('pointermove', e => onMove(e));
  pg.ink.addEventListener('pointerup', e => onUp(e));
  pg.ink.addEventListener('pointercancel', e => onUp(e));
  pg.ink.addEventListener('contextmenu', e => e.preventDefault());
  if (before) P.pages.insertBefore(wrap, before); else P.pages.append(wrap);
  buildTexts(pg);
}
Y.addPage = function (P, tpl) {
  const last = P.doc.pages[P.doc.pages.length - 1];
  const pg = Y.makePage(tpl || (last && last.tpl) || 'lined');
  P.doc.pages.push(pg);
  createPage(P, pg, P.addBtn);
  setSizes(pg); P.io.observe(pg.el);
  queueSave(P); updatePageIdx(P);
  pg.el.scrollIntoView({ behavior: Y.reduced.matches ? 'auto' : 'smooth', block: 'start' });
};
Y.setTemplate = function (pg, tpl) {
  pg.tpl = tpl;
  pg.paper.className = 'paper tpl-' + tpl;
  pg.paper.querySelectorAll('.tpl').forEach(n => n.remove());
  const html = Y.tplHTML(tpl); if (html) pg.paper.insertAdjacentHTML('afterbegin', html);
  queueSave(pg.pane);
};

function setSizes(pg) {
  const P = pg.pane, W = P.W, H = Math.round(W * pg.ratio);
  pg.el.style.setProperty('--w', W + 'px');
  pg.el.style.setProperty('--dp', (W / 560) + 'px');
  pg.el.style.setProperty('--qs', W / 560);
  pg.el.style.width = W + 'px'; pg.el.style.height = H + 'px';
  if (pg.active) {
    const rs = Math.min(Y.DPR, 3000 / W, 4096 / H);
    pg.rs = rs;
    pg.ink.width = Math.round(W * rs); pg.ink.height = Math.round(H * rs);
    pg.pv.width = pg.ink.width; pg.pv.height = pg.ink.height;
    redraw(pg); layoutTexts(pg);
  }
}
function allocate(pg) { setSizes(pg); if (pg.pane.doc.type === 'pdf') Y.renderPdf(pg); }
function free(pg) {
  if (pg.task) { try { pg.task.cancel(); } catch (e) {} pg.task = null; }
  pg.token++; pg.ink.width = 0; pg.ink.height = 0; pg.pv.width = 0; pg.pv.height = 0;
  if (pg.bg) { pg.bg.width = 0; pg.bg.height = 0; pg.el.classList.add('loading'); }
}
Y.setSizes = setSizes;

Y.layout = function (P, anchor) {
  if (!P.doc) return;
  const sc = P.scroll, r = sc.getBoundingClientRect();
  const cx = anchor ? anchor.x - r.left : sc.clientWidth / 2, cy = anchor ? anchor.y - r.top : sc.clientHeight / 2;
  const ax = (sc.scrollLeft + cx) / Math.max(1, sc.scrollWidth), ay = (sc.scrollTop + cy) / Math.max(1, sc.scrollHeight);
  P.W = computeW(P);
  P.doc.pages.forEach(pg => { setSizes(pg); if (pg.active && P.doc.type === 'pdf') Y.renderPdf(pg); });
  sc.scrollLeft = ax * sc.scrollWidth - cx; sc.scrollTop = ay * sc.scrollHeight - cy;
  updatePageIdx(P);
  Y.refreshTools && Y.refreshTools();
};
Y.setZoom = function (P, z, anchor) {
  if (typeof z === 'number') z = Math.max(0.4, Math.min(3, z));
  P.zoom = z; Y.layout(P, anchor);
};

function updatePageIdx(P) {
  if (!P.doc) return;
  const r = P.scroll.getBoundingClientRect(), mid = r.top + (r.height + parseFloat(getComputedStyle(P.scroll).paddingTop)) / 2;
  let n = 0;
  for (let i = 0; i < P.doc.pages.length; i++) { const pr = P.doc.pages[i].el.getBoundingClientRect(); n = i; if (pr.bottom > mid) break; }
  if (n !== P.pageIdx) { P.pageIdx = n; Y.onPageChange && Y.onPageChange(P, n, true); }
  else Y.onPageChange && Y.onPageChange(P, n, false);
}

/* =============== 저장 =============== */
function queueSave(P) {
  clearTimeout(P.saveT);
  P.saveT = setTimeout(() => Y.saveDoc(P.doc), Y.settings.autosave);
}
Y.queueSave = queueSave;
Y.saveDoc = function (doc) {
  if (!doc) return;
  const pages = doc.pages.map(p => { const o = { ratio: p.ratio, tpl: p.tpl, strokes: p.strokes, texts: p.texts }; if (p.qi) { o.qi = p.qi; o.head = p.head; } return o; });
  if (doc.type === 'note' || doc.type === 'quiz') {
    if (!Y.store.set('yeobaek.v2.n.' + doc.id, doc.lv ? { pages, lv: doc.lv } : { pages })) return;
    const m = Y.lib.notes.find(n => n.id === doc.id);
    if (m) { m.title = doc.title; m.updated = Date.now(); m.pageCount = pages.length; Y.saveLib(); }
  } else if (doc.key) {
    const any = pages.some(p => p.strokes.length || p.texts.length);
    if (any) Y.store.set('yeobaek.v2.p.' + doc.key, { pages: pages.map(p => ({ strokes: p.strokes, texts: p.texts })) });
    else Y.store.del('yeobaek.v2.p.' + doc.key);
  }
  Y.flashSaved && Y.flashSaved();
};
Y.saveAll = () => Y.panes.forEach(P => P.doc && Y.saveDoc(P.doc));
window.addEventListener('pagehide', Y.saveAll);
document.addEventListener('visibilitychange', () => { if (document.hidden) Y.saveAll(); });

/* =============== 기록(실행취소) =============== */
function pushHist(P, pg, undo, redo) {
  P.hist.push({ pg, undo, redo }); P.redo = [];
  queueSave(P); Y.refreshTools && Y.refreshTools();
}
Y.undo = function (P) {
  P = P || Y.active; if (!P) return;
  const e = P.hist.pop(); if (!e) return;
  clearSel(P, true); e.undo(); P.redo.push(e);
  redraw(e.pg); buildTexts(e.pg); queueSave(P); Y.refreshTools && Y.refreshTools();
};
Y.redo = function (P) {
  P = P || Y.active; if (!P) return;
  const e = P.redo.pop(); if (!e) return;
  clearSel(P, true); e.redo(); P.hist.push(e);
  redraw(e.pg); buildTexts(e.pg); queueSave(P); Y.refreshTools && Y.refreshTools();
};

/* =============== 그리기 =============== */
function anc(P, i) {
  const n = P.length;
  if (i <= 0) return P[0]; if (i >= n) return P[n - 1];
  return [(P[i - 1][0] + P[i][0]) / 2, (P[i - 1][1] + P[i][1]) / 2];
}
function strokeColor(st) { return paperDark() && st.c.toUpperCase() === '#1A1A20' ? '#F2F2F6' : st.c; }
function segWidth(st, k, K) {
  const P = st.p, n = st._live ? 1e9 : P.length, c = P[k], pr = c[2] || 0.5, base = st.s * K;
  const a = P[Math.max(0, k - 1)], b = P[Math.min(P.length - 1, k + 1)];
  const dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
  switch (st.k) {
    case 'fine': return Math.max(0.6, base * 0.8);
    case 'fountain': {                       // 45° 납작 촉: 방향에 따라 굵기
      const ang = len ? Math.atan2(dy, dx) : 0;
      const t = Math.min(1, (k + 2) / 4, (n - k + 1) / 4);
      return Math.max(0.5, base * (0.3 + 1.15 * Math.abs(Math.sin(ang - Math.PI / 4))) * (0.75 + pr * 0.5) * (0.5 + 0.5 * t));
    }
    case 'brush': {                          // 압력·속도에 크게 반응, 끝이 길게 빠짐
      const sp = len * K / Math.max(1, base * 3);
      const t = Math.min(1, (k + 1) / 7, (n - k) / 9);
      return Math.max(0.4, base * (0.25 + pr * 1.6) * Math.max(0.35, Math.min(1.25, 1.3 - sp * 0.25)) * (0.2 + 0.8 * t));
    }
    default: {
      const taper = Math.min(1, (k + 2) / 5, (n - k + 1) / 5);
      return Math.max(0.6, base * (0.55 + pr * 0.9) * (0.45 + 0.55 * taper));
    }
  }
}
function drawSeg(ctx, st, k) {
  const K = ctx.canvas.width, P = st.p;
  const a = anc(P, k), b = anc(P, k + 1), c = P[k];
  ctx.lineWidth = segWidth(st, k, K);
  ctx.beginPath(); ctx.moveTo(a[0] * K, a[1] * K); ctx.quadraticCurveTo(c[0] * K, c[1] * K, b[0] * K, b[1] * K); ctx.stroke();
}
function dotAt(ctx, st) {
  const K = ctx.canvas.width, p = st.p[0];
  ctx.beginPath(); ctx.arc(p[0] * K, p[1] * K, Math.max(0.7, st.s * K / 2), 0, Math.PI * 2); ctx.fill();
}
function drawStroke(ctx, st) {
  const K = ctx.canvas.width, P = st.p, n = P.length;
  ctx.save();
  ctx.lineJoin = 'round'; ctx.strokeStyle = ctx.fillStyle = strokeColor(st);
  if (st.t === 'hl') {
    ctx.globalAlpha = st.a == null ? 0.4 : st.a;
    ctx.globalCompositeOperation = paperDark() ? 'source-over' : 'multiply';
    ctx.lineCap = 'butt';
    if (n === 1) dotAt(ctx, st);
    else {
      ctx.lineWidth = st.s * K; ctx.beginPath(); ctx.moveTo(P[0][0] * K, P[0][1] * K);
      for (let k = 0; k < n; k++) { const b = anc(P, k + 1), c = P[k]; ctx.quadraticCurveTo(c[0] * K, c[1] * K, b[0] * K, b[1] * K); }
      ctx.stroke();
    }
  } else {
    ctx.lineCap = 'round';
    if (n === 1) dotAt(ctx, st); else for (let j = 0; j < n; j++) drawSeg(ctx, st, j);
  }
  ctx.restore();
}
Y.drawStroke = drawStroke;
Y.strokeGeom = { anc, segWidth };

function bboxOf(list) {
  let x0 = 9, y0 = 9, x1 = -9, y1 = -9;
  list.forEach(st => {
    const pad = st.s / 2 + 0.006;
    st.p.forEach(p => { x0 = Math.min(x0, p[0] - pad); y0 = Math.min(y0, p[1] - pad); x1 = Math.max(x1, p[0] + pad); y1 = Math.max(y1, p[1] + pad); });
  });
  return [x0, y0, x1, y1];
}

function redraw(pg) {
  if (!pg.active || !pg.ink.width) return;
  const ctx = pg.ink.getContext('2d'), K = pg.ink.width, P = pg.pane;
  ctx.clearRect(0, 0, K, pg.ink.height); clearPred(pg);
  const sr = Y.searchRects && Y.searchRects(pg);
  if (sr) {
    sr.rects.forEach((r, i) => {
      ctx.save();
      ctx.fillStyle = 'rgba(185,169,255,.38)'; ctx.fillRect(r[0] * K, r[1] * K, r[2] * K, r[3] * K);
      if (i === sr.cur) { ctx.strokeStyle = '#7A5AF8'; ctx.lineWidth = 2 * pg.rs; ctx.strokeRect(r[0] * K, r[1] * K, r[2] * K, r[3] * K); }
      ctx.restore();
    });
  }
  pg.strokes.forEach(st => drawStroke(ctx, st));
  const c = P.cur;
  if (c && c.pg === pg && c.mode === 'space' && c.space) {
    const y = c.space.y0 * K;
    ctx.save(); ctx.strokeStyle = LILAC; ctx.lineWidth = 1.6 * pg.rs; ctx.setLineDash([10 * pg.rs, 6 * pg.rs]);
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(K, y); ctx.stroke(); ctx.restore();
  }
  if (P.sel && P.sel.pg === pg) drawSel(ctx, pg, K);
}
Y.redraw = redraw;
function drawSel(ctx, pg, K) {
  const b = pg.pane.sel.box, rs = pg.rs, x = b[0] * K, y = b[1] * K, w = (b[2] - b[0]) * K, hh = (b[3] - b[1]) * K;
  ctx.save();
  ctx.fillStyle = 'rgba(185,169,255,.10)'; ctx.fillRect(x, y, w, hh);
  ctx.setLineDash([6 * rs, 4 * rs]); ctx.lineDashOffset = -pg.dash; ctx.lineWidth = 1.6 * rs; ctx.strokeStyle = LILAC; ctx.strokeRect(x, y, w, hh);
  ctx.setLineDash([]);
  [[x, y], [x + w, y], [x, y + hh], [x + w, y + hh]].forEach(p => {
    ctx.beginPath(); ctx.arc(p[0], p[1], 5 * rs, 0, Math.PI * 2); ctx.fillStyle = '#fff'; ctx.fill(); ctx.lineWidth = 1.8 * rs; ctx.strokeStyle = LILAC; ctx.stroke();
  });
  ctx.restore();
}
function flowDash(pg) {
  if (Y.reduced.matches) return;
  const t0 = performance.now();
  (function f(t) { const k = Math.min(1, (t - t0) / 400); pg.dash = k * 20; redraw(pg); if (k < 1) requestAnimationFrame(f); else pg.dash = 0; })(t0);
}
function drawLasso(pg, pts) {
  const ctx = pg.ink.getContext('2d'), K = pg.ink.width, rs = pg.rs;
  ctx.save(); ctx.setLineDash([6 * rs, 4 * rs]); ctx.lineWidth = 1.6 * rs; ctx.strokeStyle = LILAC; ctx.lineJoin = 'round';
  ctx.beginPath(); pts.forEach((p, i) => { if (i) ctx.lineTo(p[0] * K, p[1] * K); else ctx.moveTo(p[0] * K, p[1] * K); }); ctx.stroke(); ctx.restore();
}
function snapStart(pg, st) {
  const i = pg.strokes.indexOf(st); if (i >= 0) pg.strokes.splice(i, 1);
  redraw(pg);
  const sn = pg._snap || (pg._snap = document.createElement('canvas'));
  sn.width = pg.ink.width; sn.height = pg.ink.height;
  sn.getContext('2d').drawImage(pg.ink, 0, 0);
  pg.strokes.push(st);
}
function liveHL(pg, st) {
  const ctx = pg.ink.getContext('2d');
  if (!pg._snap || pg._snap.width !== pg.ink.width) { redraw(pg); return; }
  ctx.clearRect(0, 0, pg.ink.width, pg.ink.height); ctx.drawImage(pg._snap, 0, 0); drawStroke(ctx, st);
}
function livePen(pg, st) {
  const ctx = pg.ink.getContext('2d'), n = st.p.length;
  ctx.save(); ctx.lineCap = 'round'; ctx.lineJoin = 'round'; ctx.strokeStyle = strokeColor(st);
  if (n >= 2) drawSeg(ctx, st, n - 2);
  ctx.restore();
}
function clearPred(pg) {
  if (pg && pg._pred) { pg._pred = false; pg.pv.getContext('2d').clearRect(0, 0, pg.pv.width, pg.pv.height); }
}
function drawPred(pg, st, e, rect) {
  const ps = e.getPredictedEvents ? e.getPredictedEvents() : [];
  clearPred(pg);
  if (!ps.length || st.p.length < 2) return;
  const last = st.p[st.p.length - 1], pts = st.p.slice(-2);
  // 너무 멀리 앞서 그리면 오히려 어색해서 짧게만
  for (let i = 0; i < Math.min(2, ps.length); i++) {
    const q = norm(ps[i], pg, rect); q[2] = last[2];
    if (Math.hypot(q[0] - last[0], q[1] - last[1]) > 0.03) break;
    pts.push(q);
  }
  if (pts.length < 3) return;
  drawStroke(pg.pv.getContext('2d'), Object.assign({}, st, { p: pts, _live: true }));
  pg._pred = true;
}
function norm(e, pg, r) {
  r = r || pg.ink.getBoundingClientRect();
  // S Pen 압력: 약하게 눌러도 선이 끊기지 않도록 완만한 곡선
  const pr = e.pointerType === 'pen' && e.pressure > 0 ? Math.min(1, 0.12 + 0.88 * Math.pow(e.pressure, 0.75)) : 0.5;
  return [rnd((e.clientX - r.left) / r.width), rnd((e.clientY - r.top) / r.width), Math.round(pr * 100) / 100];
}
const pageW = pg => pg.ink.getBoundingClientRect().width;

/* =============== 지우개 =============== */
function dSeg(p, a, b) {
  const dx = b[0] - a[0], dy = b[1] - a[1], l = dx * dx + dy * dy;
  let t = l ? ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / l : 0;
  t = Math.max(0, Math.min(1, t));
  return Math.hypot(a[0] + t * dx - p[0], a[1] + t * dy - p[1]);
}
function hits(st, pt, r) {
  const lim = r + st.s / 2, P = st.p;
  if (P.length === 1) return dist(P[0], pt) <= lim;
  for (let i = 1; i < P.length; i++) if (dSeg(pt, P[i - 1], P[i]) <= lim) return true;
  return false;
}
function densify(P, step) {
  const out = [P[0]];
  for (let i = 1; i < P.length; i++) {
    const a = P[i - 1], b = P[i], d = dist(a, b), n = Math.max(1, Math.ceil(d / step));
    for (let k = 1; k <= n; k++) { const t = k / n; out.push([rnd(a[0] + (b[0] - a[0]) * t), rnd(a[1] + (b[1] - a[1]) * t), b[2]]); }
  }
  return out;
}
function eraseAt(P, pt) {
  const c = P.cur, pg = c.pg;
  const r = Y.eraser.mm / MM / 2 * 1.0, step = Math.max(0.004, r / 2);
  let any = false;
  for (let i = pg.strokes.length - 1; i >= 0; i--) {
    const st = pg.strokes[i];
    if (!hits(st, pt, r)) continue;
    any = true;
    const ai = c.added.indexOf(st);
    if (ai >= 0) c.added.splice(ai, 1); else c.removed.push({ st, idx: i });
    pg.strokes.splice(i, 1);
    if (Y.eraser.partial) {
      const D = densify(st.p, step), pieces = []; let run = [];
      D.forEach(p => { if (dist(p, pt) <= r + st.s / 2) { if (run.length > 1) pieces.push(run); run = []; } else run.push(p); });
      if (run.length > 1) pieces.push(run);
      pieces.reverse().forEach(run2 => {
        const ns = Object.assign({}, st, { p: run2 });
        pg.strokes.splice(i, 0, ns); c.added.push(ns);
      });
    }
  }
  if (any) { if (P.sel && P.sel.pg === pg) clearSel(P, true); redraw(pg); }
}

/* =============== 올가미 =============== */
function inPoly(p, poly) {
  let c = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const a = poly[i], b = poly[j];
    if ((a[1] > p[1]) !== (b[1] > p[1]) && p[0] < (b[0] - a[0]) * (p[1] - a[1]) / (b[1] - a[1]) + a[0]) c = !c;
  }
  return c;
}
function moveStrokes(list, dx, dy) { list.forEach(st => st.p.forEach(p => { p[0] = rnd(p[0] + dx); p[1] = rnd(p[1] + dy); })); }
function scaleStrokes(list, k, ox, oy) {
  list.forEach(st => { st.s = st.s * k; st.p.forEach(p => { p[0] = rnd(ox + (p[0] - ox) * k); p[1] = rnd(oy + (p[1] - oy) * k); }); });
}
function clearSel(P, silent) {
  const old = P.sel; P.sel = null;
  if (old) redraw(old.pg);
  if (!silent) Y.refreshTools && Y.refreshTools();
}
Y.clearSel = P => clearSel(P || Y.active);
Y.deleteSel = function (P) {
  P = P || Y.active; if (!P || !P.sel) return;
  const pg = P.sel.pg, items = [];
  P.sel.strokes.forEach(st => { const i = pg.strokes.indexOf(st); if (i >= 0) items.push({ st, idx: i }); });
  items.sort((a, b) => b.idx - a.idx).forEach(it => pg.strokes.splice(it.idx, 1));
  P.sel = null;
  const asc = items.slice().reverse();
  pushHist(P, pg, () => asc.forEach(it => pg.strokes.splice(Math.min(it.idx, pg.strokes.length), 0, it.st)), () => asc.forEach(it => { const i = pg.strokes.indexOf(it.st); if (i >= 0) pg.strokes.splice(i, 1); }));
  redraw(pg); Y.refreshTools();
};
Y.copySel = function (P) {
  P = P || Y.active; if (!P || !P.sel) return;
  const pg = P.sel.pg;
  const clones = P.sel.strokes.map(st => Object.assign({}, st, { p: st.p.map(p => p.slice()) }));
  moveStrokes(clones, 0.03, 0.03);
  clones.forEach(c => pg.strokes.push(c));
  P.sel = { pg, strokes: clones, box: bboxOf(clones) };
  pushHist(P, pg, () => clones.forEach(c => { const i = pg.strokes.indexOf(c); if (i >= 0) pg.strokes.splice(i, 1); }), () => clones.forEach(c => pg.strokes.push(c)));
  redraw(pg); Y.refreshTools();
};
Y.recolorSel = function (P, color) {
  P = P || Y.active; if (!P || !P.sel) return;
  const list = P.sel.strokes, old = list.map(s => s.c), pg = P.sel.pg;
  list.forEach(s => { s.c = color; });
  pushHist(P, pg, () => list.forEach((s, i) => { s.c = old[i]; }), () => list.forEach(s => { s.c = color; }));
  redraw(pg);
};

/* =============== 모양 보정 =============== */
function detectShape(P) {
  if (P.length < 8) return null;
  let x0 = 9, y0 = 9, x1 = -9, y1 = -9, len = 0;
  P.forEach((p, i) => { x0 = Math.min(x0, p[0]); y0 = Math.min(y0, p[1]); x1 = Math.max(x1, p[0]); y1 = Math.max(y1, p[1]); if (i) len += dist(P[i - 1], p); });
  const bw = x1 - x0, bh = y1 - y0, diag = Math.hypot(bw, bh);
  if (diag < 0.05) return null;
  const a = P[0], b = P[P.length - 1], chord = dist(a, b);
  if (chord / len > 0.92) {
    let dev = 0; P.forEach(p => { dev = Math.max(dev, dSeg(p, a, b)); });
    if (dev < diag * 0.07) return [[a[0], a[1], 0.5], [b[0], b[1], 0.5]];
  }
  if (chord < diag * 0.25 && len > diag * 1.8 && bw > 0.03 && bh > 0.03) {
    const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, rx = bw / 2, ry = bh / 2;
    let ee = 0, re = 0;
    P.forEach(p => {
      ee += Math.abs(Math.hypot((p[0] - cx) / rx, (p[1] - cy) / ry) - 1);
      re += Math.min(Math.min(Math.abs(p[0] - x0), Math.abs(p[0] - x1)), Math.min(Math.abs(p[1] - y0), Math.abs(p[1] - y1)));
    });
    ee /= P.length; re /= P.length * diag;
    if (ee < 0.13) {
      const out = []; for (let i = 0; i <= 48; i++) { const t = i / 48 * Math.PI * 2; out.push([rnd(cx + rx * Math.cos(t)), rnd(cy + ry * Math.sin(t)), 0.5]); }
      return out;
    }
    if (re < 0.05) return [[x0, y0, 0.5], [x1, y0, 0.5], [x1, y1, 0.5], [x0, y1, 0.5], [x0, y0, 0.5]].map(p => [rnd(p[0]), rnd(p[1]), 0.5]);
  }
  return null;
}

/* =============== 텍스트 상자 =============== */
function buildTexts(pg) {
  pg.tl.innerHTML = '';
  pg.texts.forEach(t => pg.tl.append(makeBox(pg, t)));
  layoutTexts(pg);
}
function layoutTexts(pg) {
  const W = pg.pane.W;
  Array.from(pg.tl.children).forEach(d => {
    const t = d._t; if (!t) return;
    d.style.left = t.x * W + 'px'; d.style.top = t.y * W + 'px'; d.style.width = t.w * W + 'px';
    d.style.fontSize = t.fs * W + 'px'; d.style.color = paperDark() && t.c.toUpperCase() === '#1A1A20' ? '#F2F2F6' : t.c;
  });
}
function makeBox(pg, t) {
  const d = h('div', { class: 'tbox', contenteditable: 'true', spellcheck: 'false', role: 'textbox', 'aria-label': '텍스트 상자' });
  d.textContent = t.t; d._t = t;
  d.addEventListener('input', () => { t.t = d.innerText; queueSave(pg.pane); });
  d.addEventListener('blur', () => {
    t.t = d.innerText;
    if (!t.t.trim()) { const i = pg.texts.indexOf(t); if (i >= 0) pg.texts.splice(i, 1); d.remove(); return; }
    if (!t._h) {
      t._h = true;
      pushHist(pg.pane, pg, () => { const i = pg.texts.indexOf(t); if (i >= 0) pg.texts.splice(i, 1); }, () => { if (pg.texts.indexOf(t) < 0) pg.texts.push(t); });
    }
    queueSave(pg.pane);
  });
  d.addEventListener('keydown', e => { if (e.key === 'Escape') d.blur(); });
  return d;
}
function newText(pg, pt) {
  const cur = Y.pens[Math.min(Y.penIdx, Y.pens.length - 1)];
  const t = { id: Y.uid(), x: Math.min(pt[0], 0.8), y: pt[1], w: 0.4, fs: 0.028, c: cur && cur.kind === 'pen' ? cur.color : '#1A1A20', t: '' };
  pg.texts.push(t);
  const d = makeBox(pg, t); pg.tl.append(d); layoutTexts(pg);
  setTimeout(() => d.focus(), 0);
}

/* =============== 링크 =============== */
function hitLink(e, pg) {
  if (!pg.paper) return false;
  const links = pg.paper.querySelectorAll('.tlink');
  for (const a of links) {
    const r = a.getBoundingClientRect();
    if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom) {
      if (Y.settings.linkPenOnly && e.pointerType === 'touch') continue;
      e.preventDefault(); Y.linkAction(a.dataset.link); return true;
    }
  }
  return false;
}

/* =============== 손가락 관성 스크롤 =============== */
function stopGlide(P) { if (P.glide) { cancelAnimationFrame(P.glide); P.glide = 0; } }
Y.stopGlide = stopGlide;
function glide(P, vx, vy) {
  stopGlide(P);
  if (Math.hypot(vx, vy) < 0.12) return;
  let last = performance.now();
  const step = now => {
    const dt = Math.min(32, now - last); last = now;
    const k = Math.pow(0.9965, dt);           // 마찰: 부드럽게 감속
    vx *= k; vy *= k;
    P.scroll.scrollLeft += vx * dt; P.scroll.scrollTop += vy * dt;
    if (Math.hypot(vx, vy) < 0.02) { P.glide = 0; return; }
    P.glide = requestAnimationFrame(step);
  };
  P.glide = requestAnimationFrame(step);
}

/* 손바닥: 접촉 면적이 크면 무시 */
const isPalm = e => e.pointerType === 'touch' && Math.max(e.width || 0, e.height || 0) > 46;
const palmWin = () => ({ weak: 250, mid: 900, strong: 2500 }[Y.settings.palm] || 900);
const penRecent = () => Y.penDown || performance.now() - Y.lastPen < palmWin();
function haltFingers() {
  Y.panes.forEach(Q => {
    stopGlide(Q); Q.scrolling = null;
    if (Q.pinch) { if (Q.pinch.raf) cancelAnimationFrame(Q.pinch.raf); Q.pinch = null; Q.pages.style.transform = ''; }
    if (Q.touches) Q.touches.clear();
  });
}

/* =============== 꾹 누르기 (문제지: 정답 보기) =============== */
function startLP(P, e, pg) {
  if (P.doc.type !== 'quiz' || !Y.quizLongPress) return;
  clearTimeout(P.lpT);
  const id = e.pointerId, x = e.clientX, y = e.clientY;
  P.lp = { id, x, y };
  P.lpT = setTimeout(() => {
    if (!P.lp || P.lp.id !== id) return;
    P.lp = null;
    if (!Y.quizLongPress(pg, x, y)) return;
    Y._lpAt = performance.now(); Y.haptic(15);
    // 누르는 동안 찍힌 점·스크롤은 없던 일로
    if (P.cur && P.cur.id === id) {
      const c = P.cur; P.cur = null;
      if (c.st) { const k = c.pg.strokes.indexOf(c.st); if (k >= 0) c.pg.strokes.splice(k, 1); c.pg._snap = null; }
      redraw(c.pg);
    }
    if (P.scrolling && P.scrolling.id === id) P.scrolling = null;
  }, 450);
}
function moveLP(e) {
  Y.panes.forEach(Q => { if (Q.lp && Q.lp.id === e.pointerId && Math.hypot(e.clientX - Q.lp.x, e.clientY - Q.lp.y) > 9) { clearTimeout(Q.lpT); Q.lp = null; } });
}
function endLP(e) { Y.panes.forEach(Q => { if (Q.lp && Q.lp.id === e.pointerId) { clearTimeout(Q.lpT); Q.lp = null; } }); }

/* =============== 포인터 =============== */
function curTool() { return Y.pens[Math.min(Y.penIdx, Y.pens.length - 1)]; }
function onDown(e, pg) {
  const P = pg.pane;
  if (e.pointerType === 'mouse' && e.button !== 0) return;
  if (e.pointerType === 'pen') {
    Y.penDown = true; Y.lastPen = performance.now(); haltFingers();
    // 손가락 필기 중이던 획이 있으면 마무리
    // 펜보다 먼저 닿은 손바닥이 그린 획은 지움
    if (P.cur && P.cur.touch) {
      const c0 = P.cur; P.cur = null;
      if (c0.st) { const k = c0.pg.strokes.indexOf(c0.st); if (k >= 0) c0.pg.strokes.splice(k, 1); c0.pg._snap = null; }
      redraw(c0.pg);
    }
  }
  if (hitLink(e, pg)) return;
  if (e.pointerType === 'touch') {
    if (penRecent() || isPalm(e)) return;
    if (!Y.settings.finger) {
      stopGlide(P);
      if (P.pinch) return;
      P.scrolling = { id: e.pointerId, x0: e.clientX, y0: e.clientY, l0: P.scroll.scrollLeft, t0: P.scroll.scrollTop, hist: [[e.clientX, e.clientY, performance.now()]] };
      startLP(P, e, pg);
      try { pg.ink.setPointerCapture(e.pointerId); } catch (_) {}; return;
    }
  }
  if (P.cur) return;
  e.preventDefault();
  Y.closePopover();
  let mode = Y.mode;
  if (e.pointerType === 'pen' && (e.buttons & 2 || e.buttons & 32)) mode = Y.settings.penButton === 'lasso' ? 'lasso' : 'eraser';
  const pt = norm(e, pg);
  if (mode === 'text') { newText(pg, pt); return; }
  try { pg.ink.setPointerCapture(e.pointerId); } catch (_) {};
  const c = P.cur = { pg, id: e.pointerId, mode, removed: [], added: [], temp: mode !== Y.mode, touch: e.pointerType === 'touch' };
  if (mode === 'eraser') { eraseAt(P, pt); return; }
  if (mode === 'lasso') {
    const sel = P.sel;
    if (sel && sel.pg === pg) {
      const r = pg.ink.getBoundingClientRect(), tol = 16 / r.width, b = sel.box;
      const corners = [[b[0], b[1]], [b[2], b[1]], [b[0], b[3]], [b[2], b[3]]], opp = [[b[2], b[3]], [b[0], b[3]], [b[2], b[1]], [b[0], b[1]]];
      for (let i = 0; i < 4; i++) if (dist(pt, corners[i]) < tol) { c.scale = { o: opp[i], d0: dist(corners[i], opp[i]), total: 1, base: corners[i] }; return; }
      if (pt[0] >= b[0] && pt[0] <= b[2] && pt[1] >= b[1] && pt[1] <= b[3]) { c.move = { last: pt, dx: 0, dy: 0 }; return; }
    }
    const old = P.sel; P.sel = null; if (old && old.pg !== pg) redraw(old.pg);
    c.lasso = [pt]; redraw(pg); Y.refreshTools(); return;
  }
  if (mode === 'space') {
    const y0 = pt[1];
    c.space = { y0, dy: 0, strokes: pg.strokes.filter(s => s.p.every(p => p[1] >= y0 - 0.002)), texts: pg.texts.filter(t => t.y >= y0 - 0.002) };
    redraw(pg); return;
  }
  const tool = curTool();
  const st = { t: tool.kind, c: tool.color, s: tool.mm / MM, a: tool.kind === 'hl' ? tool.alpha : 1, p: [pt] };
  if (tool.kind === 'pen' && tool.type && tool.type !== 'ball') st.k = tool.type;
  if (tool.kind === 'pen') st._live = true;
  c.st = st; pg.strokes.push(st);
  if (tool.kind === 'pen') startLP(P, e, pg);
  if (st.t === 'hl') { snapStart(pg, st); liveHL(pg, st); }
}
function onMove(e) {
  if (e.pointerType === 'pen') Y.lastPen = performance.now();
  moveLP(e);
  const P = Y.panes.find(p => (p.scrolling && p.scrolling.id === e.pointerId) || (p.cur && p.cur.id === e.pointerId));
  if (!P) return;
  if (P.scrolling && P.scrolling.id === e.pointerId) {
    const S = P.scrolling;
    S.hist.push([e.clientX, e.clientY, performance.now()]); if (S.hist.length > 6) S.hist.shift();
    if (!S.raf) S.raf = requestAnimationFrame(() => {
      S.raf = 0; const l = S.hist[S.hist.length - 1];
      P.scroll.scrollTop = S.t0 - (l[1] - S.y0);
      P.scroll.scrollLeft = S.l0 - (l[0] - S.x0);
    });
    return;
  }
  const c = P.cur, pg = c.pg;
  if (c.move) {
    const q = norm(e, pg), dx = q[0] - c.move.last[0], dy = q[1] - c.move.last[1];
    moveStrokes(P.sel.strokes, dx, dy);
    P.sel.box = [P.sel.box[0] + dx, P.sel.box[1] + dy, P.sel.box[2] + dx, P.sel.box[3] + dy];
    c.move.last = q; c.move.dx += dx; c.move.dy += dy; redraw(pg); return;
  }
  if (c.scale) {
    const q = norm(e, pg), d = dist(q, c.scale.o), tot = Math.max(0.1, Math.min(8, d / c.scale.d0)), k = tot / c.scale.total;
    scaleStrokes(P.sel.strokes, k, c.scale.o[0], c.scale.o[1]); c.scale.total = tot;
    P.sel.box = bboxOf(P.sel.strokes); redraw(pg); return;
  }
  if (c.lasso) { c.lasso.push(norm(e, pg)); redraw(pg); drawLasso(pg, c.lasso); return; }
  if (c.space) {
    const q = norm(e, pg), want = Math.max(-0.2, q[1] - c.space.y0), dy = want - c.space.dy;
    moveStrokes(c.space.strokes, 0, dy); c.space.texts.forEach(t => { t.y = rnd(t.y + dy); });
    c.space.dy = want; layoutTexts(pg); redraw(pg); return;
  }
  if (c.mode === 'eraser') { eraseAt(P, norm(e, pg)); return; }
  const st = c.st; if (!st || c.snapped) return;
  let evs = (e.getCoalescedEvents && e.getCoalescedEvents()) || []; if (!evs.length) evs = [e];
  const rect = pg.ink.getBoundingClientRect();
  evs.forEach(ev => {
    const pt = norm(ev, pg, rect), last = st.p[st.p.length - 1];
    // 압력 흔들림 완화 (지수 평활)
    pt[2] = Math.round((last[2] * 0.6 + pt[2] * 0.4) * 100) / 100;
    const d = Math.hypot(pt[0] - last[0], pt[1] - last[1]);
    if (d < 0.0005) return;
    // 갑자기 멀리 튄 점(센서 노이즈)은 버림
    if (d > 0.12 && st.p.length > 1) return;
    st.p.push(pt); if (st.t === 'pen') livePen(pg, st);
  });
  if (st.t === 'hl') liveHL(pg, st);
  else if (st.t === 'pen' && e.pointerType === 'pen') drawPred(pg, st, e, rect);
  clearTimeout(c.snapT);
  if (Y.settings.snap && st.t === 'pen' && st.p.length > 8) {
    c.snapT = setTimeout(() => {
      const np = detectShape(st.p); if (!np || P.cur !== c) return;
      c.snapOld = st.p; st.p = np; c.snapped = true; redraw(pg); Y.haptic(15);
    }, 600);
  }
}
function onUp(e) {
  if (e.pointerType === 'pen') { Y.penDown = false; Y.lastPen = performance.now(); }
  endLP(e);
  const P = Y.panes.find(p => (p.scrolling && p.scrolling.id === e.pointerId) || (p.cur && p.cur.id === e.pointerId));
  if (!P) return;
  if (P.scrolling && P.scrolling.id === e.pointerId) {
    const S = P.scrolling; P.scrolling = null;
    const now = performance.now(), hs = S.hist.filter(q => now - q[2] < 100);
    if (hs.length >= 2 && e.type !== 'pointercancel') {
      const a = hs[0], b = hs[hs.length - 1], dt = Math.max(8, b[2] - a[2]);
      glide(P, -(b[0] - a[0]) / dt, -(b[1] - a[1]) / dt);
    }
    return;
  }
  const c = P.cur, pg = c.pg, st = c.st; clearTimeout(c.snapT);
  P.cur = null; clearPred(pg);
  if (c.move) {
    const dx = c.move.dx, dy = c.move.dy, list = P.sel.strokes;
    if (Math.abs(dx) + Math.abs(dy) > 0.0005) {
      pushHist(P, pg, () => moveStrokes(list, -dx, -dy), () => moveStrokes(list, dx, dy));
    }
    redraw(pg);
  } else if (c.scale) {
    const k = c.scale.total, o = c.scale.o, list = P.sel.strokes;
    if (Math.abs(k - 1) > 0.002) pushHist(P, pg, () => scaleStrokes(list, 1 / k, o[0], o[1]), () => scaleStrokes(list, k, o[0], o[1]));
    redraw(pg);
  } else if (c.lasso) {
    const poly = c.lasso; let chosen = [];
    if (poly.length >= 4) chosen = pg.strokes.filter(s => { let n = 0; s.p.forEach(p => { if (inPoly(p, poly)) n++; }); return n / s.p.length >= 0.5; });
    P.sel = chosen.length ? { pg, strokes: chosen, box: bboxOf(chosen) } : null;
    redraw(pg); if (P.sel) flowDash(pg); Y.refreshTools();
  } else if (c.space) {
    const dy = c.space.dy, sl = c.space.strokes, tl = c.space.texts;
    if (Math.abs(dy) > 0.0005) pushHist(P, pg,
      () => { moveStrokes(sl, 0, -dy); tl.forEach(t => { t.y = rnd(t.y - dy); }); layoutTexts(pg); },
      () => { moveStrokes(sl, 0, dy); tl.forEach(t => { t.y = rnd(t.y + dy); }); layoutTexts(pg); });
    redraw(pg);
  } else if (c.mode === 'eraser') {
    const rem = c.removed.slice(), add = c.added.slice();
    if (rem.length || add.length) {
      const asc = rem.slice().sort((a, b) => a.idx - b.idx);
      pushHist(P, pg,
        () => { add.forEach(s => { const i = pg.strokes.indexOf(s); if (i >= 0) pg.strokes.splice(i, 1); }); asc.forEach(it => pg.strokes.splice(Math.min(it.idx, pg.strokes.length), 0, it.st)); },
        () => { rem.forEach(it => { const i = pg.strokes.indexOf(it.st); if (i >= 0) pg.strokes.splice(i, 1); }); add.forEach(s => pg.strokes.push(s)); });
    }
  } else if (st && st.t === 'hl' && P.doc.type === 'pdf' && Y.settings.hlSnap !== false && Y.snapHL && (c.rep = Y.snapHL(pg, st))) {
    // 글자에 맞춘 형광펜으로 바꿈 (되돌리기 한 번에 전부)
    const rep = c.rep, i = pg.strokes.indexOf(st);
    pg.strokes.splice(i < 0 ? pg.strokes.length : i, i < 0 ? 0 : 1, ...rep); pg._snap = null;
    redraw(pg); Y.haptic(6);
    pushHist(P, pg, () => rep.forEach(s => { const k = pg.strokes.indexOf(s); if (k >= 0) pg.strokes.splice(k, 1); }), () => rep.forEach(s => pg.strokes.push(s)));
  } else if (st) {
    delete st._live; pg._snap = null;
    redraw(pg);
    pushHist(P, pg, () => { const i = pg.strokes.indexOf(st); if (i >= 0) pg.strokes.splice(i, 1); }, () => pg.strokes.push(st));
    if (c.snapped) {
      const oldP = c.snapOld, newP = st.p;
      P.hist.push({ pg, undo: () => { st.p = oldP; }, redo: () => { st.p = newP; } });
    }
  }
  Y.refreshTools && Y.refreshTools();
}

/* =============== 두 손가락 제스처 (펜 전용 모드) =============== */
function attachGestures(P) {
  const touches = P.touches = new Map();
  const dd = () => { const a = Array.from(touches.values()); return a.length < 2 ? 0 : Math.hypot(a[0].x - a[1].x, a[0].y - a[1].y); };
  const mid = () => { const a = Array.from(touches.values()); return { x: (a[0].x + a[1].x) / 2, y: (a[0].y + a[1].y) / 2 }; };
  P.root.addEventListener('pointerdown', e => {
    if (e.pointerType !== 'touch') return;
    if (penRecent() || isPalm(e)) return;
    touches.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (Y.settings.finger) return;
    if (touches.size >= 2 && !P.pinch) {
      P.scrolling = null; stopGlide(P);
      const m = mid();
      // 손가락 사이에 있는 페이지와 그 안의 상대 위치를 기억 → 확대 뒤에도 같은 자리에
      let el = document.elementFromPoint(m.x, m.y); el = el && el.closest ? el.closest('.page') : null;
      if (!el) el = (P.doc.pages[P.pageIdx] || P.doc.pages[0]).el;
      const r = el.getBoundingClientRect();
      P.pinch = { d0: Math.max(1, dd()), t0: performance.now(), maxN: touches.size, moved: false, s: 1, m, tx: 0, ty: 0, el, fx: (m.x - r.left) / r.width, fy: (m.y - r.top) / r.height };
      const pr = P.pages.getBoundingClientRect();
      P.pages.style.transformOrigin = (m.x - pr.left) + 'px ' + (m.y - pr.top) + 'px';
    } else if (P.pinch) P.pinch.maxN = Math.max(P.pinch.maxN, touches.size);
  }, true);
  P.root.addEventListener('pointermove', e => {
    if (e.pointerType !== 'touch' || !touches.has(e.pointerId)) return;
    touches.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const pc = P.pinch;
    if (!pc || touches.size < 2 || pc.maxN > 2) return;
    const m = mid();
    const minS = 0.4 * ACT_W / P.W, maxS = 3 * ACT_W / P.W;
    const s = Math.max(minS, Math.min(maxS, dd() / pc.d0));
    pc.tx = m.x - pc.m.x; pc.ty = m.y - pc.m.y;
    if (Math.abs(s - 1) > 0.03 || Math.hypot(pc.tx, pc.ty) > 6) pc.moved = true;
    pc.s = s;
    if (pc.moved && !pc.raf) pc.raf = requestAnimationFrame(() => {
      pc.raf = 0;
      P.pages.style.transform = 'translate(' + pc.tx + 'px,' + pc.ty + 'px) scale(' + pc.s + ')';
    });
  }, true);
  const end = e => {
    if (e.pointerType !== 'touch') return;
    touches.delete(e.pointerId);
    const pc = P.pinch; if (!pc || touches.size >= 2) return;
    if (pc.raf) cancelAnimationFrame(pc.raf);
    P.pinch = null;
    if (pc.moved && pc.maxN === 2) {
      P.pages.style.transform = '';
      if (Math.abs(pc.s - 1) > 0.03) Y.setZoom(P, P.W * pc.s / ACT_W);
      const r2 = pc.el.getBoundingClientRect();
      P.scroll.scrollLeft += (r2.left + pc.fx * r2.width) - (pc.m.x + pc.tx);
      P.scroll.scrollTop += (r2.top + pc.fy * r2.height) - (pc.m.y + pc.ty);
      touches.clear();
    } else {
      P.pages.style.transform = '';
      if (!pc.moved && performance.now() - pc.t0 < 300 && !penRecent()) {
        if (pc.maxN === 2) Y.undo(P); else if (pc.maxN >= 3) Y.redo(P);
      }
    }
  };
  P.root.addEventListener('pointerup', end, true);
  P.root.addEventListener('pointercancel', end, true);
}
})(window.Y);
