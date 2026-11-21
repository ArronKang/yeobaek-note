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
