/* PDF: 열기, 페이지 렌더링, 검색, 목차, 썸네일 (pdf.js) */
(function (Y) {
'use strict';
const WORKER = new URL('vendor/pdfjs/pdf.worker.min.js', document.baseURI).href;

Y.openPdfFile = function (file) {
  return new Promise((resolve, reject) => {
    if (!window.pdfjsLib) { reject(new Error('engine')); return; }
    pdfjsLib.GlobalWorkerOptions.workerSrc = WORKER;
    const fr = new FileReader();
    fr.onerror = () => reject(new Error('read'));
    fr.onload = () => {
      pdfjsLib.getDocument({ data: new Uint8Array(fr.result) }).promise.then(async pdf => {
        const first = await pdf.getPage(1), v = first.getViewport({ scale: 1 }), ratio = v.height / v.width;
        const key = file.name + ':' + file.size;
        const saved = Y.store.get('yeobaek.v2.p.' + key, null);
        const pages = [];
        for (let i = 0; i < pdf.numPages; i++) {
          const sp = saved && saved.pages && saved.pages[i];
          pages.push({ ratio, tpl: 'blank', strokes: sp ? sp.strokes || [] : [], texts: sp ? sp.texts || [] : [] });
        }
        resolve({ id: 'p' + Y.uid(), type: 'pdf', title: file.name.replace(/\.pdf$/i, ''), key, pdf, pages });
      }).catch(err => reject(err));
    };
    fr.readAsArrayBuffer(file);
  });
};

Y.renderPdf = function (pg) {
  const P = pg.pane, doc = P.doc, idx = doc.pages.indexOf(pg), my = ++pg.token;
  if (pg.task) { try { pg.task.cancel(); } catch (e) {} pg.task = null; }
  doc.pdf.getPage(idx + 1).then(page => {
    if (my !== pg.token || !pg.active) return;
    const v0 = page.getViewport({ scale: 1 }), r = v0.height / v0.width;
    if (Math.abs(r - pg.ratio) > 0.001) { pg.ratio = r; Y.setSizes(pg); }
    const rs = pg.rs || 1, vp = page.getViewport({ scale: (P.W * rs) / v0.width });
    pg.bg.width = Math.round(vp.width); pg.bg.height = Math.round(vp.height);
    pg.task = page.render({ canvasContext: pg.bg.getContext('2d'), viewport: vp });
    Y.pdfText(doc, idx);
    return pg.task.promise.then(() => { if (my === pg.token) pg.el.classList.remove('loading'); });
  }).catch(e => { if (!e || e.name !== 'RenderingCancelledException') console.warn(e); });
};

Y.pdfThumb = function (doc, idx, canvas, cssW) {
  doc.pdf.getPage(idx + 1).then(page => {
    const v0 = page.getViewport({ scale: 1 }), sc = (cssW * Math.min(2, Y.DPR)) / v0.width, vp = page.getViewport({ scale: sc });
    canvas.width = Math.round(vp.width); canvas.height = Math.round(vp.height);
    return page.render({ canvasContext: canvas.getContext('2d'), viewport: vp }).promise;
  }).catch(() => {});
};

/* ---------- 글자 위치 (형광펜 맞추기용) ----------
   페이지를 그릴 때 미리 받아 두고, 형광펜을 뗄 때는 바로 씀. 좌표는 페이지 너비 기준 0~1 */
Y.pdfText = function (doc, idx) {
  const C = doc._txt || (doc._txt = {});
  if (C[idx]) return C[idx];
  C[idx] = { boxes: null };
  doc.pdf.getPage(idx + 1).then(async page => {
    const vp = page.getViewport({ scale: 1 }), tc = await page.getTextContent(), out = [];
    const mc = (Y._mc || (Y._mc = document.createElement('canvas').getContext('2d')));
    tc.items.forEach(it => {
      const s = it.str || '', t = it.transform;
      if (!s.trim() || !it.width || Math.abs(t[1]) > 0.01 || Math.abs(t[2]) > 0.01) return;   // 기울어진 글자는 제외
      const hh = it.height || Math.abs(t[3]), x = t[4], y = t[5] - hh * 0.22;               // 아래 꼬리(g, y)까지
      const r = vp.convertToViewportRectangle([x, y, x + it.width, y + hh * 1.22]);
      const bx = { x: Math.min(r[0], r[2]) / vp.width, y: Math.min(r[1], r[3]) / vp.width, w: Math.abs(r[2] - r[0]) / vp.width, h: Math.abs(r[3] - r[1]) / vp.width, n: s.length };
      // 글자마다 실제 폭 비율로 경계 계산 (비례 글꼴 대응)
      const st = tc.styles[it.fontName]; mc.font = '100px ' + ((st && st.fontFamily) || 'sans-serif');
      const ws = [0]; let acc = 0;
      for (const ch of s) { acc += mc.measureText(ch).width; ws.push(acc); }
      bx.o = acc > 0 ? ws.map(v => bx.x + bx.w * v / acc) : null; bx.c = Array.from(s);
      out.push(bx);
    });
    C[idx].boxes = out;
  }).catch(() => { C[idx].boxes = []; });
  return C[idx];
};

/* 자유롭게 그은 형광펜 → 지나간 글자 줄마다 반듯한 형광펜으로. 글자 위가 아니면 null */
Y.snapHL = function (pg, st) {
  const doc = pg.pane.doc, T = doc._txt && doc._txt[doc.pages.indexOf(pg)];
  const B = T && T.boxes; if (!B || !B.length || st.p.length < 2) return null;
  const hits = new Map(); let on = 0;
  st.p.forEach(p => {
    let hit = false;
    B.forEach(b => {
      const cw = b.w / b.n, py = b.h * 0.15;
      if (p[0] >= b.x - cw * 0.5 && p[0] <= b.x + b.w + cw * 0.5 && p[1] >= b.y - py && p[1] <= b.y + b.h + py) {
        hit = true; const r = hits.get(b) || [9, -9]; r[0] = Math.min(r[0], p[0]); r[1] = Math.max(r[1], p[0]); hits.set(b, r);
      }
    });
    if (hit) on++;
  });
  if (on / st.p.length < 0.6 || !hits.size) return null;
  // 같은 줄끼리 묶기
  const lines = [];
  [...hits.keys()].sort((a, b) => a.y - b.y).forEach(b => {
    const cy = b.y + b.h / 2, L = lines.find(l => Math.abs(l.cy - cy) < Math.min(l.h, b.h) * 0.5);
    if (L) L.items.push(b); else lines.push({ cy, h: b.h, items: [b] });
  });
  const out = [];
  lines.forEach(L => {
    let x0 = 9, x1 = -9, y0 = 9, y1 = -9;
    L.items.forEach(b => {
      const r = hits.get(b), cw = b.w / b.n;
      const a = Math.max(b.x, Math.min(r[0], b.x + b.w)), z = Math.max(b.x, Math.min(r[1], b.x + b.w));
      let s, e;
      if (b.o) {   // 글자 경계로: 시작은 글자 절반을 넘으면 그 글자부터, 끝도 절반을 넘으면 그 글자까지
        const O = b.o; let i = 0, j = O.length - 1;
        while (i < O.length - 2 && (O[i] + O[i + 1]) / 2 < a) i++;
        while (j > 1 && (O[j - 1] + O[j]) / 2 > z) j--;
        // 단어 단위로 넓힘 (글꼴 측정 오차로 단어가 반쯤 칠해지지 않게)
        const sp = k => /\s/.test(b.c[k] || ' ');
        if (i < j) { while (i > 0 && !sp(i - 1) && !sp(i)) i--; while (j < b.c.length && !sp(j) && !sp(j - 1)) j++; }
        while (i < j && sp(i)) i++; while (j > i && sp(j - 1)) j--;
        s = O[i]; e = O[j];
      } else { s = b.x + Math.floor((a - b.x) / cw + 0.15) * cw; e = b.x + Math.ceil((z - b.x) / cw - 0.15) * cw; }
      if (e - s < cw * 0.5) return;
      x0 = Math.min(x0, s); x1 = Math.max(x1, e); y0 = Math.min(y0, b.y); y1 = Math.max(y1, b.y + b.h);
    });
    if (x1 <= x0) return;
    const r3 = v => Math.round(v * 10000) / 10000, cy = r3((y0 + y1) / 2);
    out.push({ t: 'hl', c: st.c, s: r3(y1 - y0), a: st.a, p: [[r3(x0), cy, 0.5], [r3(x1), cy, 0.5]] });
  });
  return out.length ? out : null;
};

/* ---------- 목차 ---------- */
Y.pdfOutline = async function (pdf) {
  const ol = await pdf.getOutline(); if (!ol) return [];
  const out = [];
  async function walk(items, level) {
    for (const it of items) {
      let pi = null;
      try { let d = it.dest; if (typeof d === 'string') d = await pdf.getDestination(d); if (d && d[0]) pi = await pdf.getPageIndex(d[0]); } catch (e) {}
      out.push({ title: it.title, level, page: pi });
      if (it.items && it.items.length) await walk(it.items, level + 1);
    }
  }
  await walk(ol, 0);
  return out;
};

/* ---------- 본문 검색 ---------- */
Y.searchRects = function (pg) {
  const P = pg.pane, S = P.search; if (!S) return null;
  const i = P.doc.pages.indexOf(pg), rects = S.byPage[i]; if (!rects) return null;
  const c = S.list[S.cur];
  return { rects, cur: c && c[0] === i ? c[1] : -1 };
};
function redrawAll(P) { P.doc.pages.forEach(pg => Y.redraw(pg)); }
Y.searchGo = function (P, n) {
  const S = P.search; if (!S || !S.list.length) return;
  S.cur = (n + S.list.length) % S.list.length;
  const hit = S.list[S.cur], pg = P.doc.pages[hit[0]];
  redrawAll(P);
  const r = S.byPage[hit[0]][hit[1]], H = pg.el.offsetHeight, W = pg.el.offsetWidth;
  const top = pg.el.offsetTop + r[1] * W - P.scroll.clientHeight / 3;
  P.scroll.scrollTo({ top, behavior: Y.reduced.matches ? 'auto' : 'smooth' });
  Y.onSearchUpdate && Y.onSearchUpdate(P);
};
Y.pdfSearch = async function (P, q) {
  q = (q || '').trim();
  if (!q) { P.search = null; redrawAll(P); Y.onSearchUpdate && Y.onSearchUpdate(P); return; }
  const S = { q, byPage: {}, list: [], cur: -1, done: false };
  P.search = S;
  const lq = q.toLowerCase(), pdf = P.doc.pdf;
  for (let i = 0; i < pdf.numPages; i++) {
    if (P.search !== S) return;
    const page = await pdf.getPage(i + 1), tc = await page.getTextContent(), vp = page.getViewport({ scale: 1 });
    const rects = [];
    tc.items.forEach(it => {
      const s = it.str || '', ls = s.toLowerCase(); let from = 0, idx;
      while (s && (idx = ls.indexOf(lq, from)) >= 0) {
        from = idx + lq.length;
        const len = s.length, x = it.transform[4] + it.width * idx / len, w = it.width * lq.length / len, y = it.transform[5], hh = it.height || Math.abs(it.transform[3]);
        const r = vp.convertToViewportRectangle([x, y, x + w, y + hh]);
        rects.push([Math.min(r[0], r[2]) / vp.width, Math.min(r[1], r[3]) / vp.width, Math.abs(r[2] - r[0]) / vp.width, Math.abs(r[3] - r[1]) / vp.width]);
      }
    });
    if (rects.length) {
      S.byPage[i] = rects; rects.forEach((_, j) => S.list.push([i, j]));
      if (S.cur < 0) { S.cur = 0; Y.searchGo(P, 0); }
      else Y.redraw(P.doc.pages[i]);
    }
    Y.onSearchUpdate && Y.onSearchUpdate(P);
  }
  S.done = true; Y.onSearchUpdate && Y.onSearchUpdate(P);
};
})(window.Y);
