/* 내보내기: 필기를 합친 PDF 만들기 → 공유 또는 파일 저장
   - PDF 문서: 원본 페이지를 그대로 두고 그 위에 필기를 벡터로 얹음
   - 노트·문제지: 종이(템플릿·문항·내 선택)를 그림으로, 필기는 벡터로 얹음 */
(function (Y) {
'use strict';
const h = Y.h, $ = Y.$;
const A4W = 595.28;              // pt
const RASTER_W = 1400;           // 종이 그림 너비(px)

function loadScript(src, test) {
  if (test()) return Promise.resolve();
  return new Promise((res, rej) => {
    const s = document.createElement('script'); s.src = src;
    s.onload = () => res(); s.onerror = () => rej(new Error('load ' + src));
    document.head.append(s);
  });
}
const libs = () => Promise.all([
  loadScript('vendor/pdflib/pdf-lib.min.js', () => !!window.PDFLib),
  loadScript('vendor/html2canvas/html2canvas.min.js', () => !!window.html2canvas)
]);

function rgbOf(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
  const n = m ? parseInt(m[1], 16) : 0x1a1a20;
  return PDFLib.rgb(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255);
}
const f = v => Math.round(v * 100) / 100;

/* 획을 PDF 벡터로: 좌표는 페이지 너비 기준(0~1) → pt. drawSvgPath가 y축을 뒤집어 줌 */
function drawStrokes(page, strokes, W, H) {
  const { anc, segWidth } = Y.strokeGeom, L = PDFLib.LineCapStyle, B = PDFLib.BlendMode;
  const at = { x: 0, y: H };
  strokes.forEach(st => {
    const P = st.p, n = P.length, color = rgbOf(st.c);
    if (!n) return;
    if (st.t === 'hl') {
      const op = st.a == null ? 0.4 : st.a;
      if (n === 1) { page.drawCircle({ x: P[0][0] * W, y: H - P[0][1] * W, size: st.s * W / 2, color, opacity: op, blendMode: B.Multiply }); return; }
      let d = 'M' + f(P[0][0] * W) + ' ' + f(P[0][1] * W);
      for (let k = 0; k < n; k++) { const b = anc(P, k + 1), c = P[k]; d += ' Q' + f(c[0] * W) + ' ' + f(c[1] * W) + ' ' + f(b[0] * W) + ' ' + f(b[1] * W); }
      page.drawSvgPath(d, Object.assign({ borderColor: color, borderWidth: st.s * W, borderOpacity: op, borderLineCap: L.Butt, blendMode: B.Multiply }, at));
      return;
    }
    if (n === 1) { page.drawCircle({ x: P[0][0] * W, y: H - P[0][1] * W, size: Math.max(0.35, st.s * W / 2), color }); return; }
    // 굵기가 마디마다 달라서 마디별 곡선으로 (화면과 같은 계산)
    for (let k = 0; k < n; k++) {
      const a = anc(P, k), b = anc(P, k + 1), c = P[k];
      const d = 'M' + f(a[0] * W) + ' ' + f(a[1] * W) + ' Q' + f(c[0] * W) + ' ' + f(c[1] * W) + ' ' + f(b[0] * W) + ' ' + f(b[1] * W);
      page.drawSvgPath(d, Object.assign({ borderColor: color, borderWidth: segWidth(st, k, W), borderLineCap: L.Round }, at));
    }
  });
}

/* 줄·모눈·점 양식은 html2canvas가 못 그려서 벡터로 직접 (style.css .tpl-* 와 같은 간격) */
function paperLines() {
  if (document.documentElement.dataset.paper === 'dark') return ['#ced4da', '#e9ecef'];
  const cs = getComputedStyle(document.documentElement);
  return [cs.getPropertyValue('--paper-line').trim() || '#ced4da', cs.getPropertyValue('--paper-line2').trim() || '#e9ecef'];
}
function drawTemplate(page, tpl, W, H) {
  const dp = W / 560, [l1, l2] = paperLines(), t = 0.6;
  const hline = (y, c) => page.drawLine({ start: { x: 0, y: H - y }, end: { x: W, y: H - y }, thickness: t, color: rgbOf(c) });
  const vline = (x, c) => page.drawLine({ start: { x, y: 0 }, end: { x, y: H }, thickness: t, color: rgbOf(c) });
  const from = (o, step) => o - Math.floor(o / step) * step;
  if (tpl === 'lined') for (let y = from(44 * dp, 28 * dp); y <= H; y += 28 * dp) hline(y - t / 2, l1);
  else if (tpl === 'grid') {
    for (let y = from(4 * dp, 20 * dp); y <= H; y += 20 * dp) hline(y - t / 2, l2);
    for (let x = from(2 * dp, 20 * dp); x <= W; x += 20 * dp) vline(x - t / 2, l2);
  } else if (tpl === 'dot') {
    for (let y = 0; y <= H; y += 20 * dp) for (let x = 0; x <= W; x += 20 * dp) page.drawCircle({ x, y: H - y, size: 1.4 * dp, color: rgbOf(l1) });
  }
}

/* 화면의 종이를 그림으로 (필기 캔버스는 빼고, 어두운 종이는 흰 종이로) */
async function snap(el, w, transparent) {
  const c = await html2canvas(el, {
    scale: RASTER_W / w, backgroundColor: transparent ? null : '#ffffff', logging: false, useCORS: true,
    ignoreElements: n => n.tagName === 'CANVAS' && (n.classList.contains('ink') || n.classList.contains('pv')),
    onclone: d => {
      if (d.documentElement.dataset.paper === 'dark') d.documentElement.dataset.paper = 'white';
      d.querySelectorAll('.qcard, .toast').forEach(x => x.remove());
      // 문제지 정답 표시 애니메이션은 끝난 모습으로
      const s = d.createElement('style'); s.textContent = '*,*::before,*::after{animation:none!important;transition:none!important}'; d.head.append(s);
    }
  });
  return c;
}
const toBytes = (c, type, q) => new Promise(res => c.toBlob(b => b.arrayBuffer().then(res), type, q));

function progress() {
  const bar = h('i'), box = h('div', { class: 'xbusy', role: 'progressbar', 'aria-label': '내보내는 중' }, h('div', { class: 'xring' }, bar));
  $('#overlay').append(box); requestAnimationFrame(() => box.classList.add('in'));
  return {
    set: k => bar.style.setProperty('--k', Math.max(0.04, Math.min(1, k))),
    done: () => { box.classList.remove('in'); setTimeout(() => box.remove(), 260); }
  };
}

Y.exportPdf = async function (P) {
  P = P || Y.active; if (!P || !P.doc || Y._exporting) return;
  Y._exporting = true; Y.saveAll && Y.saveAll();
  const pr = progress(), d = P.doc;
  try {
    await libs();
    const { PDFDocument } = PDFLib;
    let out;
    if (d.type === 'pdf') {
      out = await PDFDocument.load(await d.pdf.getData(), { ignoreEncryption: true });
      for (let i = 0; i < d.pages.length; i++) {
        const pg = d.pages[i], page = out.getPage(i);
        const { width: W, height: H } = page.getSize();
        if (pg.texts && pg.texts.length) {
          const c = await snap(pg.tl, pg.tl.getBoundingClientRect().width || P.W, true);
          const img = await out.embedPng(await toBytes(c, 'image/png'));
          page.drawImage(img, { x: 0, y: 0, width: W, height: H });
        }
        drawStrokes(page, pg.strokes, W, H);
        pr.set((i + 1) / d.pages.length);
        await new Promise(r => setTimeout(r));
      }
    } else {
      out = await PDFDocument.create();
      for (let i = 0; i < d.pages.length; i++) {
        const pg = d.pages[i], W = A4W, H = f(A4W * pg.ratio);
        const page = out.addPage([W, H]);
        const c = await snap(pg.paper, pg.paper.getBoundingClientRect().width || P.W, false);
        const img = await out.embedJpg(await toBytes(c, 'image/jpeg', 0.9));
        page.drawImage(img, { x: 0, y: 0, width: W, height: H });
        if (d.type === 'note') drawTemplate(page, pg.tpl, W, H);
        drawStrokes(page, pg.strokes, W, H);
        pr.set((i + 1) / d.pages.length);
      }
    }
    // 여백 문제지·노트는 원본을 PDF 안에 첨부: 다른 앱에선 평범한 PDF, 여백에서 열면 다시 문제지/노트로
    if (d.type === 'quiz' || d.type === 'note') {
      const pack = { yeobaek: 1, kind: d.type, title: d.title, lv: d.lv, ybq: d.quiz, qs: d.qs,
        pages: d.pages.map(p => ({ ratio: p.ratio, tpl: p.tpl, qi: p.qi, head: p.head, strokes: p.strokes, texts: p.texts })) };
      await out.attach(new TextEncoder().encode(JSON.stringify(pack)), 'yeobaek.ybq', { mimeType: 'application/json', description: '여백 ' + (d.type === 'quiz' ? '문제지' : '노트'), creationDate: new Date(), modificationDate: new Date() });
    }
    out.setTitle(d.title || '여백'); out.setCreator('여백 노트'); out.setProducer('여백 노트');
    const bytes = await out.save();
    const name = (d.title || '여백').replace(/[\\/:*?"<>|]+/g, ' ').trim() + '.pdf';
    const file = new File([bytes], name, { type: 'application/pdf' });
    pr.done();
    await deliver(file);
  } catch (e) {
    pr.done();
    if (e && e.name === 'AbortError') return;
    console.error(e); Y.toast('PDF를 만들지 못했어요');
  } finally { Y._exporting = false; }
};

async function deliver(file) {
  const canShare = navigator.canShare && navigator.canShare({ files: [file] });
  if (Y.settings.exportDir === 'share' && canShare) {
    try { await navigator.share({ files: [file], title: file.name }); return; }
    catch (e) { if (e && e.name === 'AbortError') return; }
  }
  const url = URL.createObjectURL(file);
  const a = h('a', { href: url, download: file.name }); document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60000);
  Y.haptic(10);
  if (canShare) Y.toast('PDF로 저장했어요', { action: '공유', onAction: () => navigator.share({ files: [file], title: file.name }).catch(() => {}) });
  else Y.toast('PDF로 저장했어요');
}
})(window.Y);
