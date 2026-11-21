/* 에디터: 크롬(탭·도구줄·펜 트레이), 팝오버, 페이지 사이드바, 분할 보기, 설정 */
(function (Y) {
'use strict';
const h = Y.h, $ = Y.$;
const E = Y.E = { tab: 'draw', sidebar: false, grid: false, collapsed: false, ratio: 0.5, vertical: false, primary: null };
const TABS = [['home', 'Home', '홈'], ['insert', 'Insert', '삽입'], ['draw', 'Draw', '필기'], ['view', 'View', '보기']];
const later = () => Y.toast('준비 중이에요');
const MODE_NAME = { pen: '펜', eraser: '지우개' };

/* =============== 들어가기 / 나가기 =============== */
Y.enterEditor = function (o) {
  const ed = $('#editor');
  Y.panes.slice().forEach(Y.destroyPane);
  $('#panes').innerHTML = '';
  ed.hidden = false; ed.classList.remove('shown', 'collapsed'); ed.classList.remove('split');
  E.tab = 'draw'; E.sidebar = false; E.grid = false; E.collapsed = false; E.ratio = 0.5; E.primary = o.doc;
  ed.classList.remove('gridview', 'sidebar-open');
  Y.mode = 'pen';
  const P = Y.createPane($('#panes'));
  Y.active = P; P.root.classList.add('active');
  Y.openDoc(P, o.doc);
  measureChrome(); renderAll();
  const reveal = () => { requestAnimationFrame(() => ed.classList.add('shown')); setTimeout(() => { $('#lib').hidden = true; }, 160); };
  reveal();   // 표지에서 커지는 전환 없이 짧은 페이드만
  Y.haptic(8);
};
Y.closeEditor = function () {
  Y.closePopover(); Y.saveAll();
  const ed = $('#editor');
  $('#lib').hidden = false; Y.libRefresh && Y.libRefresh();
  ed.classList.remove('shown');
  setTimeout(() => { ed.hidden = true; Y.panes.slice().forEach(Y.destroyPane); $('#panes').innerHTML = ''; }, 220);
};

function measureChrome() {
  const c = $('#chrome');
  $('#editor').style.setProperty('--chrome-h', (E.collapsed ? 0 : c.offsetHeight) + 'px');
}
new ResizeObserver(() => { if (!$('#editor').hidden) { measureChrome(); placeIndicator(); } }).observe($('#chrome'));
window.addEventListener('resize', () => {
  if ($('#editor').hidden) return;
  E.vertical = innerWidth < 820 && innerHeight > innerWidth;
  $('#panes').classList.toggle('vsplit', E.vertical);
  clearTimeout(E.rt); E.rt = setTimeout(() => { Y.panes.forEach(P => Y.layout(P)); Y.closePopover(); }, 150);
});

/* =============== 탭 =============== */
function tabLabel(t) { return Y.settings.tabLang === 'ko' ? t[2] : t[1]; }
const TAB_IC = { home: 'home', insert: 'insert', draw: 'draw', view: 'eye' };
function renderTabs() {
  const tabs = $('#tabs');
  Y.$$('.tab', tabs).forEach(b => {
    const t = TABS.find(x => x[0] === b.dataset.tab);
    b.innerHTML = Y.ic(TAB_IC[t[0]]); b.setAttribute('aria-label', tabLabel(t)); b.setAttribute('aria-selected', b.dataset.tab === E.tab ? 'true' : 'false');
  });
  placeIndicator();
}
function placeIndicator() {
  const tabs = $('#tabs'), ind = $('#ind'); if (!tabs.clientWidth) return;
  const step = (tabs.clientWidth - 8) / 4, i = TABS.findIndex(t => t[0] === E.tab);
  ind.style.width = step + 'px'; ind.style.transform = 'translateX(' + (i * step) + 'px)';
}
Y.$$('#tabs .tab').forEach(b => b.addEventListener('click', () => {
  if (E.tab === b.dataset.tab) return;
  E.tab = b.dataset.tab; Y.closePopover(); Y.haptic(8);
  if (E.tab !== 'draw' && Y.mode === 'lasso') { Y.clearSel(); }
  renderTabs(); renderTools(true); renderTray();
}));

/* =============== 도구줄 =============== */
function sep() { return h('span', { class: 'vsep' }); }
Y.refreshTools = function () {
  if (Y.E && !$('#editor').hidden) {
    const a = document.activeElement;
    if (a && a.closest && a.closest('#tools') && a.tagName === 'INPUT') return;
    renderTools(false);
  }
};
function renderTools(anim) { Y.iconOnly = true; try { renderTools0(anim); } finally { Y.iconOnly = false; } }
function renderTools0(anim) {
  const box = $('#tools'); box.innerHTML = '';
  const P = Y.active; if (!P || !P.doc) return;
  const d = P.doc, row = h('div', { class: 'tools-in' + (anim ? ' swap' : '') });
  const B = Y.btn;
  if (E.tab === 'draw') {
    row.append(B('undo', '', () => Y.undo(P), { disabled: !P.hist.length, aria: '실행취소' }),
      B('redo', '', () => Y.redo(P), { disabled: !P.redo.length, aria: '다시실행' }), sep(),
      B('text', 'T 텍스트 모드'.slice(2), () => setMode(Y.mode === 'text' ? 'pen' : 'text'), { pressed: Y.mode === 'text' }),
      B('lasso', '올가미 선택', () => setMode(Y.mode === 'lasso' ? 'pen' : 'lasso'), { pressed: Y.mode === 'lasso' }),
      B('space', '공간 삽입', () => setMode(Y.mode === 'space' ? 'pen' : 'space'), { pressed: Y.mode === 'space' }), sep(),
      B(Y.settings.finger ? 'finger' : 'hand', Y.settings.finger ? '손가락: 필기' : '손가락: 이동', () => {
        Y.settings.finger = !Y.settings.finger; Y.saveSettings(); Y.applyPaper();
        renderTools(false);
      }, { pressed: Y.settings.finger }));
    if (P.sel) {
      const rc = B('palette', '색 변경', () => recolorPop(rc, P));
      row.append(sep(), B('copy', '복사', () => Y.copySel(P)), rc, B('trash', '삭제', () => Y.deleteSel(P), { cls: 'warn' }));
    }
  } else if (E.tab === 'home') {
    row.append(B('library', '라이브러리', Y.closeEditor));
    if (d.type === 'quiz') {
      row.append(B('sidebar', '페이지 사이드바', toggleSidebar, { pressed: E.sidebar }), sep(),
        B('check', '전체 채점', () => Y.gradeAll(d)));
      const ab = B('answer', '정답표', () => Y.answerSheet(ab, d)); row.append(ab);
      row.append(B('focus', '틀린 문제만 보기', () => { Y.quizFocus(d); renderTools(false); }, { pressed: !!d.qs.focus }));
      row.append(B('retry', '틀린 문제로 새 문제지', () => { const p = Y.retryQuiz(d); if (p) p.then(meta => { Y.closeEditor(); setTimeout(() => Y.openNote(meta), 250); }); }));
      row.append(B('reset', '처음 상태로', () => Y.resetQuiz(d)));
    } else if (d.type === 'note') {
      row.append(B('pageplus', '새 페이지', () => Y.addPage(P)));
      row.append(B('sidebar', '페이지 사이드바', toggleSidebar, { pressed: E.sidebar }));
      const tb = B('template', '템플릿 변경', () => tplPop(tb, P)); row.append(tb);
    } else {
      row.append(B('sidebar', '페이지 사이드바', toggleSidebar, { pressed: E.sidebar }));
      const tb = B('toc', '목차', () => tocPop(tb, P)); row.append(tb);
      const has = P.bookmarks.has(P.pageIdx);
      row.append(B('bookmark', '북마크', () => { if (P.bookmarks.has(P.pageIdx)) P.bookmarks.delete(P.pageIdx); else P.bookmarks.add(P.pageIdx); renderTools(false); }, { pressed: has }));
      row.append(sep(), searchBox(P));
    }
  } else if (E.tab === 'insert') {
    row.append(B('image', '이미지', later), B('text', '텍스트 상자', () => { E.tab = 'draw'; renderTabs(); setMode('text'); renderTray(); }),
      B('shape', '도형', later), B('link', '링크', later), B('pageplus', 'PDF 페이지 추가', later));
  } else {
    const z = P.zoom;
    row.append(B('fit', '맞춤', () => Y.setZoom(P, 'fit'), { pressed: z === 'fit' }),
      B('width', '너비', () => Y.setZoom(P, 'width'), { pressed: z === 'width' }),
      sep(),
      B('minus', '', () => Y.setZoom(P, Math.max(0.4, P.W / 794 - 0.25)), { aria: '축소' }),
      B('plus', '', () => Y.setZoom(P, Math.min(3, P.W / 794 + 0.25)), { aria: '확대' }), sep(),
      B('grid', '격자 보기', () => { E.grid = !E.grid; $('#editor').classList.toggle('gridview', E.grid); renderTools(false); }, { pressed: E.grid }),
      B('split', '분할 보기', toggleSplit, { pressed: Y.panes.length === 2 }));
    const pb = B('paper', '종이 색', () => paperPop(pb)); row.append(pb);
    row.append(B('moon', '다크 모드', () => {
      Y.settings.dark = Y.isDark() ? 'off' : 'on'; Y.saveSettings(); Y.applyPaper(); redrawAllPages(); renderTools(false); Y.haptic(8);
    }, { pressed: Y.isDark() }));
  }
  box.append(row);
}
function setMode(m) {
  if (Y.mode === 'lasso' && m !== 'lasso') Y.clearSel();
  Y.mode = m; Y.closePopover(); renderTools(false); renderTray();
  document.body.classList.toggle('m-text', m === 'text');
}

/* ---------- PDF 검색 ---------- */
function searchBox(P) {
  const inp = h('input', { class: 'sinput', type: 'search', placeholder: '본문 검색', 'aria-label': '본문 검색', value: P.search ? P.search.q : '' });
  inp.addEventListener('keydown', e => { if (e.key === 'Enter') { Y.pdfSearch(P, inp.value); inp.blur(); } });
  const cnt = h('span', { class: 'mono scount', id: 'scount' });
  const wrap = h('div', { class: 'sbox' }, h('span', { html: Y.ic('search') }), inp,
    h('button', { type: 'button', class: 'mini', 'aria-label': '이전 결과', html: Y.ic('up'), onclick: () => P.search && Y.searchGo(P, P.search.cur - 1) }),
    h('button', { type: 'button', class: 'mini', 'aria-label': '다음 결과', html: Y.ic('down'), onclick: () => P.search && Y.searchGo(P, P.search.cur + 1) }), cnt);
  updateCount(P);
  return wrap;
}
function updateCount(P) {
  const c = $('#scount'); if (!c) return;
  const S = P.search;
  c.textContent = !S ? '' : S.list.length ? (S.cur + 1) + ' / ' + S.list.length + (S.done ? '' : '…') : (S.done ? '0' : '…');
}
Y.onSearchUpdate = updateCount;

/* ---------- 팝오버들 ---------- */
function tplPop(anchor, P) {
  const pg = P.doc.pages[P.pageIdx];
  const list = h('div', { class: 'plist' });
  Y.TEMPLATES.forEach(t => {
    list.append(h('button', { type: 'button', class: 'pitem' + (pg.tpl === t[0] ? ' on' : ''), text: t[1], onclick: () => { Y.setTemplate(pg, t[0]); Y.closePopover(); buildSidebar(); } }));
  });
  Y.popover(anchor, h('div', {}, h('div', { class: 'ptitle', text: '템플릿' }), list), { width: 220, align: 'left' });
}
function tocPop(anchor, P) {
  const box = h('div', { class: 'plist toc' }, h('div', { class: 'pempty', text: '' }));
  Y.popover(anchor, h('div', {}, h('div', { class: 'ptitle', text: '목차' }), box), { width: 300, align: 'left' });
  Y.pdfOutline(P.doc.pdf).then(items => {
    box.innerHTML = '';
    const bms = Array.from(P.bookmarks).sort((a, b) => a - b);
    if (bms.length) {
      box.append(h('div', { class: 'psub', text: '북마크' }));
      bms.forEach(i => box.append(h('button', { type: 'button', class: 'pitem', text: (i + 1) + '쪽', onclick: () => { P.doc.pages[i].el.scrollIntoView({ block: 'start' }); Y.closePopover(); } })));
      box.append(h('div', { class: 'psub', text: '목차' }));
    }
    if (!items.length) box.append(h('div', { class: 'pempty', text: '목차 없음' }));
    items.forEach(it => box.append(h('button', { type: 'button', class: 'pitem', style: 'padding-left:' + (12 + it.level * 14) + 'px', text: it.title, disabled: it.page == null,
      onclick: () => { P.doc.pages[it.page].el.scrollIntoView({ block: 'start' }); Y.closePopover(); } })));
  }).catch(() => { box.innerHTML = ''; box.append(h('div', { class: 'pempty', text: '목차 없음' })); });
}
function paperPop(anchor) {
  const list = h('div', { class: 'plist' });
  [['white', '흰색'], ['gray', '연회색']].forEach(p => {
    list.append(h('button', { type: 'button', class: 'pitem' + (Y.settings.paper === p[0] ? ' on' : ''), text: p[1], onclick: () => {
      Y.settings.paper = p[0]; Y.saveSettings(); Y.applyPaper(); redrawAllPages(); Y.closePopover();
    } }));
  });
  Y.popover(anchor, h('div', {}, h('div', { class: 'ptitle', text: '종이 색' }), list), { width: 180, align: 'left' });
}
Y.onThemeChange = () => { redrawAllPages(); if (!$('#editor').hidden) renderTools(false); };
function redrawAllPages() { Y.panes.forEach(P => P.doc.pages.forEach(pg => { Y.redraw(pg); Y.setSizes(pg); })); }
function recolorPop(anchor, P) {
  const row = h('div', { class: 'swatches' });
  Y.PEN_COLORS.forEach(c => row.append(h('button', { type: 'button', class: 'dot', style: '--c:' + c, 'aria-label': '색 ' + c, onclick: () => { Y.recolorSel(P, c); Y.closePopover(); } })));
  Y.popover(anchor, h('div', {}, h('div', { class: 'ptitle', text: '색' }), row), { width: 250 });
}

/* =============== 펜 트레이 =============== */
/* 펜 그림: 위에서 내려다본 촉(레퍼런스 스타일) — 색 페룰 + 회색 원뿔 + 색 촉 */
(function () {
  const d = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  d.setAttribute('width', 0); d.setAttribute('height', 0); d.style.position = 'absolute';
  d.innerHTML = '<defs><pattern id="ybSpeck" width="6" height="6" patternUnits="userSpaceOnUse"><rect width="6" height="6" fill="#b9bfc6"/><circle cx="1" cy="1.5" r=".45" fill="#868e96"/><circle cx="4" cy="4" r=".4" fill="#dee2e6"/><circle cx="4.6" cy="1" r=".3" fill="#868e96"/><circle cx="1.8" cy="4.6" r=".35" fill="#ced4da"/></pattern>' +
    '<linearGradient id="ybShade" x1="0" x2="1"><stop offset="0" stop-color="#fff" stop-opacity=".35"/><stop offset=".45" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#212529" stop-opacity=".18"/></linearGradient>' +
    '<linearGradient id="ybSilver" x1="0" x2="1"><stop offset="0" stop-color="#f1f3f5"/><stop offset=".5" stop-color="#ced4da"/><stop offset="1" stop-color="#adb5bd"/></linearGradient></defs>';
  document.body.append(d);
})();
function penSvg(kind, color, type) {
  const c = color || '#212529';
  const ferrule = (fill, scallop) => scallop
    ? '<path d="M11 2h18v7c-1.5 0-1.5 2-3 2s-1.5-2-3-2-1.5 2-3 2-1.5-2-3-2-1.5 2-3 2-1.5-2-3-2z" fill="' + fill + '"/>'
    : '<rect x="11" y="2" width="18" height="8" rx="1.2" fill="' + fill + '"/><rect x="11" y="8.2" width="18" height="1.8" fill="#000" opacity=".2"/>';
  let g;
  if (kind === 'hl') g = ferrule(c) +
    '<path d="M11 10h18v11l-4 8H15l-4-8z" fill="url(#ybSpeck)"/><path d="M11 10h18v11l-4 8H15l-4-8z" fill="url(#ybShade)"/>' +
    '<path d="M15 29h10v3.5L15 37z" fill="' + c + '"/>';
  else if (kind === 'eraser') g =
    '<rect x="11" y="2" width="18" height="8" rx="1.2" fill="#495057"/>' +
    '<path d="M11 10h18v16a9 9 0 0 1-18 0z" fill="#f1f3f5" stroke="#ced4da" stroke-width=".8"/><path d="M14 12v13a6 6 0 0 0 3 5" stroke="#fff" stroke-width="2" fill="none" stroke-linecap="round"/>';
  else if (type === 'fountain') g = ferrule('#212529') +
    '<path d="M11.5 10h17v5c0 8-5 14-8.5 23-3.5-9-8.5-15-8.5-23z" fill="url(#ybSilver)"/>' +
    '<path d="M20 25v13" stroke="#495057" stroke-width=".9"/><circle cx="20" cy="24" r="1.8" fill="#495057"/>' +
    '<path d="M11.5 15c0 4 2 7 4 10" stroke="#fff" stroke-width="1" fill="none" opacity=".7"/>' +
    '<rect x="11" y="6.5" width="18" height="2" fill="' + c + '"/>';
  else if (type === 'fine') g = ferrule(c) +
    '<path d="M11 10h18l-5 15h-8z" fill="url(#ybSpeck)"/><path d="M11 10h18l-5 15h-8z" fill="url(#ybShade)"/>' +
    '<rect x="18.4" y="25" width="3.2" height="7" fill="url(#ybSilver)"/><rect x="19.3" y="32" width="1.4" height="5" rx=".5" fill="' + c + '"/>';
  else if (type === 'brush') g = ferrule(c, true) +
    '<path d="M11 10h18l-7.6 23.5c-.4 1.3-2.4 1.3-2.8 0z" fill="url(#ybSpeck)"/><path d="M11 10h18l-7.6 23.5c-.4 1.3-2.4 1.3-2.8 0z" fill="url(#ybShade)"/>' +
    '<path d="M18.6 30.5h2.8L20 38z" fill="' + c + '"/>';
  else g = ferrule('#212529') +
    '<path d="M11 10h18l-6.8 22.5c-.6 1.6-3.8 1.6-4.4 0z" fill="url(#ybSpeck)"/><path d="M11 10h18l-6.8 22.5c-.6 1.6-3.8 1.6-4.4 0z" fill="url(#ybShade)"/>' +
    '<circle cx="20" cy="34" r="1.6" fill="' + c + '"/><rect x="11" y="6.5" width="18" height="2" fill="' + c + '"/>';
  return '<svg viewBox="0 0 40 40" aria-hidden="true">' + g + '</svg>';
}
function renderTray() {
  const tr = $('#tray');
  tr.hidden = E.tab !== 'draw';
  if (E.tab !== 'draw') return;
  const n = Math.max(3, Math.min(8, Y.settings.trayCount));
  const pens = Y.pens.slice(0, n);
  if (Y.penIdx >= pens.length) Y.penIdx = 0;
  const sig = n + JSON.stringify(pens.map(p => [p.kind, p.color, p.type]));
  if (tr._sig === sig && tr.children.length) {
    Array.from(tr.querySelectorAll('.pen')).forEach((b, i) => {
      const sel = b.classList.contains('eraser') ? Y.mode === 'eraser' : (Y.mode === 'pen' && Y.penIdx === i);
      b.classList.toggle('sel', sel); b.setAttribute('aria-pressed', sel ? 'true' : 'false');
    });
    return;
  }
  tr._sig = sig; tr.innerHTML = '';
  pens.forEach((p, i) => {
    const sel = Y.mode === 'pen' && Y.penIdx === i;
    const b = h('button', { type: 'button', class: 'pen' + (sel ? ' sel' : ''), 'aria-label': (p.kind === 'hl' ? '형광펜 ' : '펜 ') + (i + 1), 'aria-pressed': sel ? 'true' : 'false', html: penSvg(p.kind, p.color, p.type) });
    b.addEventListener('click', () => {
      const same = Y.mode === 'pen' && Y.penIdx === i;
      Y.penIdx = i; Y.haptic(8);
      if (Y.mode !== 'pen') setMode('pen');
      renderTray();
      if (same && !(Y._pop && Y._pop.owner === 'pen' + i)) openPenPop(b, i);
      else if (same) Y.closePopover();
      else Y.closePopover();
    });
    tr.append(b);
  });
  const eb = h('button', { type: 'button', class: 'pen eraser' + (Y.mode === 'eraser' ? ' sel' : ''), 'aria-label': '지우개', 'aria-pressed': Y.mode === 'eraser' ? 'true' : 'false', html: penSvg('eraser') });
  eb.addEventListener('click', () => {
    const same = Y.mode === 'eraser'; Y.haptic(8);
    if (!same) { setMode('eraser'); return; }
    if (Y._pop && Y._pop.owner === 'eraser') Y.closePopover(); else openEraserPop(eb);
  });
  tr.append(eb);
  tr.append(h('button', { type: 'button', class: 'penplus', 'aria-label': '펜 추가', html: Y.ic('plus'), onclick: () => {
    if (Y.pens.length >= n) { Y.toast('펜 칸이 가득 찼어요'); return; }
    Y.pens.push({ kind: 'pen', type: 'ball', color: Y.PEN_COLORS[Y.pens.length % 6], mm: 0.8, alpha: 1 }); Y.penIdx = Y.pens.length - 1; Y.mode = 'pen'; Y.savePens(); renderTray(); renderTools(false);
  } }));
}
Y.renderTray = renderTray;

function openPenPop(anchor, i) {
  const pen = Y.pens[i];
  const root = h('div', { class: 'penpop' });
  const val = h('span', { class: 'mono val' });
  const range = h('input', { type: 'range', min: 0.3, max: 12, step: 0.1, 'aria-label': '굵기' });
  const cv = h('canvas', { class: 'prev', width: 520, height: 112 });
  const draw = () => {
    const c = cv.getContext('2d'), w = cv.width, hh = cv.height;
    c.clearRect(0, 0, w, hh);
    const pts = [];
    for (let k = 0; k <= 90; k++) {
      const t = k / 90, x = 0.06 + t * 0.88;
      const y = (hh / 2 + Math.sin(t * Math.PI * 2.2) * 26 + Math.cos(t * 9) * 4) / w;
      pts.push([x, y, 0.25 + 0.6 * Math.sin(t * Math.PI)]);
    }
    Y.drawStroke(c, { t: pen.kind, k: pen.kind === 'pen' ? pen.type : undefined, c: pen.color, s: pen.mm / 90, a: pen.kind === 'hl' ? pen.alpha : 1, p: pts });
    val.textContent = pen.mm.toFixed(1) + 'mm'; range.value = pen.mm;
  };
  const types = h('div', { class: 'ptypes' });
  const paintTypes = () => {
    types.innerHTML = ''; types.hidden = pen.kind !== 'pen';
    Y.PEN_TYPES.forEach(([id, name]) => {
      const on = pen.type === id;
      types.append(h('button', { type: 'button', class: 'ptype' + (on ? ' on' : ''), 'aria-label': name, 'aria-pressed': on ? 'true' : 'false',
        html: '<span class="ptile">' + penSvg('pen', pen.color, id) + '</span><span class="pname">' + name + '</span>',
        onclick: () => { pen.type = id; Y.savePens(); paintTypes(); draw(); renderTray(); } }));
    });
  };
  paintTypes(); root.append(types);
  range.addEventListener('input', () => { pen.mm = +range.value; draw(); Y.savePens(); });
  root.append(h('div', { class: 'prevbox' }, cv), h('div', { class: 'prow' }, range, val));
  const sw = h('div', { class: 'swatches' });
  const paint = () => {
    sw.innerHTML = '';
    Y.PEN_COLORS.forEach(c => sw.append(dotBtn(c, pen.kind === 'pen' && pen.color === c, () => { if (pen.kind !== 'pen') { pen.kind = 'pen'; pen.mm = 1; pen.alpha = 1; } pen.color = c; commit(); })));
    sw.append(h('span', { class: 'brk' }));
    Y.HL_COLORS.forEach(c => sw.append(dotBtn(c, pen.kind === 'hl' && pen.color === c, () => { if (pen.kind !== 'hl') { pen.kind = 'hl'; pen.mm = 6; pen.alpha = 0.4; } pen.color = c; commit(); })));
    const cu = h('label', { class: 'custom', title: '사용자 지정 색' },
      h('input', { type: 'color', value: /^#[0-9a-f]{6}$/i.test(pen.color) ? pen.color : '#1A1A20', 'aria-label': '사용자 지정 색', oninput: e => { pen.color = e.target.value.toUpperCase(); Y.savePens(); draw(); paintTypes(); renderTray(); } }));
    sw.append(cu);
  };
  const op = h('div', { class: 'prow', id: 'oprow' }, h('span', { class: 'plabel', text: '투명도' }),
    h('input', { type: 'range', min: 0.1, max: 1, step: 0.05, value: pen.alpha, 'aria-label': '투명도', oninput: e => { pen.alpha = +e.target.value; ov.textContent = Math.round(pen.alpha * 100) + '%'; draw(); Y.savePens(); } }));
  const ov = h('span', { class: 'mono val', text: Math.round(pen.alpha * 100) + '%' }); op.append(ov);
  const commit = () => { if (pen.kind === 'pen' && !pen.type) pen.type = 'ball'; Y.savePens(); paint(); paintTypes(); op.hidden = pen.kind !== 'hl'; ov.textContent = Math.round(pen.alpha * 100) + '%'; op.querySelector('input').value = pen.alpha; draw(); renderTray(); };
  paint(); op.hidden = pen.kind !== 'hl';
  root.append(sw, op);
  Y.popover(anchor, root, { width: 320, align: 'right', owner: 'pen' + i, keep: t => t.closest && t.closest('#tray') });
  draw();
}
function dotBtn(c, on, fn) { return h('button', { type: 'button', class: 'dot', style: '--c:' + c, 'aria-label': '색 ' + c, 'aria-pressed': on ? 'true' : 'false', onclick: fn }); }
function openEraserPop(anchor) {
  const e = Y.eraser;
  const val = h('span', { class: 'mono val', text: e.mm.toFixed(1) + 'mm' });
  const seg = h('div', { class: 'seg' });
  const paint = () => { seg.innerHTML = ''; [[false, '획 지우기'], [true, '부분 지우기']].forEach(m => seg.append(h('button', { type: 'button', class: e.partial === m[0] ? 'on' : '', text: m[1], onclick: () => { e.partial = m[0]; Y.savePens(); paint(); } }))); };
  paint();
  const root = h('div', { class: 'penpop' },
    h('div', { class: 'prow' }, h('span', { class: 'plabel', text: '크기' }), h('input', { type: 'range', min: 1, max: 20, step: 0.5, value: e.mm, 'aria-label': '지우개 크기', oninput: ev => { e.mm = +ev.target.value; val.textContent = e.mm.toFixed(1) + 'mm'; Y.savePens(); } }), val), seg);
  Y.popover(anchor, root, { width: 280, align: 'right', owner: 'eraser', keep: t => t.closest && t.closest('#tray') });
}

/* =============== 페이지 사이드바 =============== */
function toggleSidebar() {
  E.sidebar = !E.sidebar;
  $('#editor').classList.toggle('sidebar-open', E.sidebar);
  if (E.sidebar) buildSidebar();
  setTimeout(() => Y.panes.forEach(P => Y.layout(P)), 280);
  renderTools(false);
}
function buildSidebar() {
  const bar = $('#pagebar'); bar.innerHTML = '';
  const P = Y.active; if (!P || !P.doc || !E.sidebar) return;
  P.doc.pages.forEach((pg, i) => {
    const w = 116, hgt = Math.round(w * pg.ratio);
    const tp = h('div', { class: 'tpaper paper tpl-' + (P.doc.type === 'note' ? pg.tpl : 'blank'), style: 'width:' + w + 'px;height:' + hgt + 'px;--dp:' + (w / 560) + 'px;--qs:' + (w / 560) + ';--w:' + w + 'px' });
    if (P.doc.type === 'note') { const html = Y.tplHTML(pg.tpl); if (html) tp.insertAdjacentHTML('beforeend', html); }
    let pc = null;
    if (P.doc.type === 'pdf') { pc = h('canvas', { class: 'pdfbg' }); tp.append(pc); Y.pdfThumb(P.doc, i, pc, w); }
    if (P.doc.type === 'quiz') { const ql = Y.quizLayer(P.doc, pg); ql.removeAttribute('data-doc'); ql.classList.add('thumb'); tp.append(ql); }
    const sc = h('canvas', { class: 'ink', width: Math.round(w * 2), height: Math.round(hgt * 2) });
    const ctx = sc.getContext('2d'); pg.strokes.forEach(st => Y.drawStroke(ctx, st));
    tp.append(sc);
    const b = h('button', { type: 'button', class: 'pthumb' + (P.pageIdx === i ? ' cur' : ''), 'aria-label': (i + 1) + '쪽으로 이동', 'data-i': i }, tp, h('span', { class: 'mono pn', text: i + 1 }));
    b.addEventListener('click', () => { if (b._lp) { b._lp = false; return; } pg.el.scrollIntoView({ block: 'start', behavior: Y.reduced.matches ? 'auto' : 'smooth' }); });
    if (P.doc.type === 'note') {
      let t;
      b.addEventListener('pointerdown', () => { t = setTimeout(() => { b._lp = true; Y.haptic(12); pageMenu(b, P, i); }, 450); });
      ['pointerup', 'pointerleave', 'pointercancel'].forEach(ev => b.addEventListener(ev, () => clearTimeout(t)));
      b.addEventListener('contextmenu', ev => { ev.preventDefault(); pageMenu(b, P, i); });
    }
    bar.append(b);
  });
}
Y.buildSidebar = buildSidebar;
function pageMenu(anchor, P, i) {
  const pages = P.doc.pages, list = h('div', { class: 'plist' });
  const item = (icon, label, fn, dis) => h('button', { type: 'button', class: 'pitem ic-l', html: Y.ic(icon) + '<span>' + label + '</span>', disabled: dis, onclick: () => { Y.closePopover(); fn(); } });
  const after = () => { P.hist = []; P.redo = []; Y.rebuildPane(P); Y.queueSave(P); buildSidebar(); Y.refreshTools(); };
  list.append(
    item('dup', '복제', () => {
      const pg = pages[i];
      pages.splice(i + 1, 0, { ratio: pg.ratio, tpl: pg.tpl, strokes: JSON.parse(JSON.stringify(pg.strokes)), texts: JSON.parse(JSON.stringify(pg.texts)) }); after();
    }),
    item('template', '템플릿 변경', () => tplPopAt(anchor, P, i)),
    item('up', '위로 이동', () => { const t = pages[i]; pages[i] = pages[i - 1]; pages[i - 1] = t; after(); }, i === 0),
    item('down', '아래로 이동', () => { const t = pages[i]; pages[i] = pages[i + 1]; pages[i + 1] = t; after(); }, i === pages.length - 1),
    item('trash', '삭제', () => { if (pages.length < 2) { Y.haptic(20); return; } pages.splice(i, 1); after(); }));
  Y.popover(anchor, list, { width: 200, align: 'left', top: anchor.getBoundingClientRect().top + 20 });
}
function tplPopAt(anchor, P, i) {
  setTimeout(() => {
    const list = h('div', { class: 'plist' });
    Y.TEMPLATES.forEach(t => list.append(h('button', { type: 'button', class: 'pitem' + (P.doc.pages[i].tpl === t[0] ? ' on' : ''), text: t[1], onclick: () => { Y.setTemplate(P.doc.pages[i], t[0]); Y.closePopover(); buildSidebar(); } })));
    Y.popover(anchor, h('div', {}, h('div', { class: 'ptitle', text: '템플릿' }), list), { width: 200, align: 'left', top: anchor.getBoundingClientRect().top + 20 });
  }, 160);
}
/* 왼쪽 가장자리에서 밀면 사이드바 열기 */
(function () {
  const z = $('#edge'); let x0 = null;
  z.addEventListener('pointerdown', e => { x0 = e.clientX; z.setPointerCapture(e.pointerId); });
  z.addEventListener('pointermove', e => { if (x0 != null && e.clientX - x0 > 40) { x0 = null; if (!E.sidebar) toggleSidebar(); } });
  z.addEventListener('pointerup', () => { x0 = null; });
})();

/* =============== 페이지 칩 / 저장됨 칩 =============== */
Y.onPageChange = function (P, n, changed) {
  if (P !== Y.active || !P.doc) return;
  if (E.sidebar) Y.$$('#pagebar .pthumb').forEach(b => b.classList.toggle('cur', +b.dataset.i === n));
  if (!changed) return;
  const c = $('#chip'); c.textContent = (n + 1) + ' / ' + P.doc.pages.length; c.classList.add('in');
  clearTimeout(Y._ct); Y._ct = setTimeout(() => c.classList.remove('in'), 1600);
  if (E.tab === 'home' && P.doc.type === 'pdf') { const a = document.activeElement; if (!(a && a.tagName === 'INPUT')) renderTools(false); }
};
Y.flashSaved = function () {
  // 저장은 조용히 (표시 없음)
};
Y.onActive = function (P) {
  $('#title').value = P.doc ? P.doc.title : '';
  renderTools(false); if (E.sidebar) buildSidebar();
};

/* =============== 상단 아이콘, 핸들, 제목 =============== */
$('#ic-share').innerHTML = Y.ic('share'); $('#ic-more').innerHTML = Y.ic('more'); $('#ic-more2').innerHTML = Y.ic('more');
$('#ic-back').innerHTML = Y.ic('back'); $('#ic-search').innerHTML = Y.ic('search');
$('#btn-back').addEventListener('click', Y.closeEditor);
$('#btn-share').addEventListener('click', () => Y.exportPdf());
// ⋯ 메뉴: 설정, 다크 모드, 도구 막대 숨기기
$('#btn-more').addEventListener('click', e => {
  const btn = e.currentTarget, list = h('div', { class: 'plist' });
  const item = (icon, label, fn, on) => list.append(h('button', { type: 'button', class: 'pitem' + (on ? ' on' : ''), html: Y.ic(icon) + '<span>' + label + '</span>', onclick: () => { Y.closePopover(); fn(); } }));
  item('collapse', '도구 막대 숨기기', toggleChrome);
  item('moon', '다크 모드', () => { Y.settings.dark = Y.isDark() ? 'off' : 'on'; Y.saveSettings(); Y.applyPaper(); redrawAllPages(); renderTools(false); }, Y.isDark());
  item('settings', '설정', () => Y.openSettings());
  Y.popover(btn, list, { width: 220, align: 'right' });
});
function toggleChrome() {
  E.collapsed = !E.collapsed; Y.closePopover();
  $('#editor').classList.toggle('collapsed', E.collapsed); measureChrome();
  setTimeout(() => Y.panes.forEach(P => P.zoom === 'fit' && Y.layout(P)), 270);
}
$('#handle').addEventListener('click', toggleChrome);
$('#title').addEventListener('input', e => { const P = Y.active; if (P && P.doc) { P.doc.title = e.target.value; Y.queueSave(P); } });
$('#title').addEventListener('keydown', e => { if (e.key === 'Enter') e.target.blur(); });

function renderAll() { renderTabs(); renderTools(false); renderTray(); $('#title').value = Y.active && Y.active.doc ? Y.active.doc.title : ''; E.vertical = innerWidth < 820 && innerHeight > innerWidth; $('#panes').classList.toggle('vsplit', E.vertical); }
Y.renderTabs = renderTabs;

/* =============== 분할 보기 =============== */
function applySplit() {
  const ps = Y.panes;
  if (ps.length < 2) return;
  const r = E.ratio, root = $('#panes');
  ps[0].root.style.display = r === 0 ? 'none' : ''; ps[1].root.style.display = r === 1 ? 'none' : '';
  ps[0].root.style.flex = r + ' 1 0'; ps[1].root.style.flex = (1 - r) + ' 1 0';
  root.classList.toggle('collapsed-left', r === 0); root.classList.toggle('collapsed-right', r === 1);
  ps.forEach(P => P.root.offsetParent && Y.layout(P));
}
function toggleSplit() {
  if (Y.panes.length === 2) { exitSplit(); return; }
  const P0 = Y.panes[0], doc = P0.doc;
  if (doc.type === 'note') {
    const inp = h('input', { type: 'file', accept: 'application/pdf' });
    inp.addEventListener('change', () => {
      const f = inp.files[0]; if (!f) return;
      Y.openPdfFile(f).then(pdoc => startSplit(pdoc, P0, true)).catch(() => Y.toast('열 수 없는 파일이에요'));
    });
    inp.click();
  } else {
    const meta = { id: Y.uid(), title: doc.title + ' 노트', cover: 'slate', folder: null, fav: false, trashed: false, created: Date.now(), updated: Date.now(), pageCount: 1 };
    Y.lib.notes.push(meta); Y.saveLib();
    const ndoc = { id: meta.id, type: 'note', title: meta.title, pages: [Y.makePage('lined')] };
    Y.store.set('yeobaek.v2.n.' + meta.id, { pages: ndoc.pages.map(p => ({ ratio: p.ratio, tpl: p.tpl, strokes: [], texts: [] })) });
    startSplit(ndoc, P0, false);
  }
}
function startSplit(newDoc, P0, newOnLeft) {
  const root = $('#panes'), P1 = Y.createPane(root);
  const hd = h('div', { class: 'shandle', role: 'separator', 'aria-label': '분할 크기 조절' }, h('i'));
  if (newOnLeft) { root.insertBefore(P1.root, P0.root); root.insertBefore(hd, P0.root); }
  else { root.insertBefore(hd, P1.root); }
  Y.panes.sort((a, b) => (a.root.compareDocumentPosition(b.root) & 4 ? -1 : 1));
  E.ratio = 0.5; Y.openDoc(P1, newDoc); $('#editor').classList.add('split');
  Y.setActive(P1);
  hd.addEventListener('pointerdown', e => dragHandle(e, hd));
  applySplit(); renderTools(false); Y.haptic(10);
}
function dragHandle(e, hd) {
  e.preventDefault(); hd.setPointerCapture(e.pointerId);
  const root = $('#panes'), rect = root.getBoundingClientRect(), vert = E.vertical;
  hd.classList.add('drag');
  const mv = ev => {
    let r = vert ? (ev.clientY - rect.top) / rect.height : (ev.clientX - rect.left) / rect.width;
    r = Math.max(0, Math.min(1, r)); Y.panes[0].root.style.display = ''; Y.panes[1].root.style.display = '';
    Y.panes[0].root.style.flex = r + ' 1 0'; Y.panes[1].root.style.flex = (1 - r) + ' 1 0';
    E.liveR = r;
  };
  const up = () => {
    hd.removeEventListener('pointermove', mv); hd.removeEventListener('pointerup', up); hd.classList.remove('drag');
    const r = E.liveR == null ? E.ratio : E.liveR, snaps = [0, 0.4, 0.5, 0.6, 1];
    const nr = snaps.reduce((a, b) => Math.abs(b - r) < Math.abs(a - r) ? b : a);
    if (nr !== E.ratio) Y.haptic(12);
    E.ratio = nr; E.liveR = null; applySplit();
  };
  hd.addEventListener('pointermove', mv); hd.addEventListener('pointerup', up);
}
function exitSplit() {
  const keep = Y.panes.find(P => P.doc === E.primary) || Y.panes[0];
  Y.saveAll();
  Y.panes.filter(P => P !== keep).forEach(Y.destroyPane);
  Y.$$('.shandle').forEach(n => n.remove());
  keep.root.style.display = ''; keep.root.style.flex = '';
  $('#editor').classList.remove('split'); $('#panes').classList.remove('collapsed-left', 'collapsed-right');
  Y.setActive(keep); keep.root.classList.remove('active');
  Y.layout(keep); renderTools(false); Y.haptic(10);
}

/* =============== 설정 =============== */
Y.openSettings = function () {
  const S = Y.settings;
  const save = () => { Y.saveSettings(); };
  const row = (label, ctrl) => h('div', { class: 'srow' }, h('div', { class: 'sl' }, h('span', { text: label })), ctrl);
  const toggle = (key, after) => {
    const b = h('button', { type: 'button', class: 'switch', role: 'switch', 'aria-checked': S[key] ? 'true' : 'false', 'aria-label': '켜기/끄기' }, h('i'));
    b.addEventListener('click', () => { S[key] = !S[key]; b.setAttribute('aria-checked', S[key] ? 'true' : 'false'); save(); after && after(); });
    return b;
  };
  const segc = (key, opts, after) => {
    const s = h('div', { class: 'seg' });
    const paint = () => { s.innerHTML = ''; opts.forEach(o => s.append(h('button', { type: 'button', class: S[key] === o[0] ? 'on' : '', text: o[1], onclick: () => { S[key] = o[0]; save(); paint(); after && after(); } }))); };
    paint(); return s;
  };
  const stepper = () => {
    const v = h('span', { class: 'mono', text: S.trayCount });
    const set = d => { S.trayCount = Math.max(3, Math.min(8, S.trayCount + d)); v.textContent = S.trayCount; save(); renderTray(); };
    return h('div', { class: 'stepper' }, h('button', { type: 'button', 'aria-label': '줄이기', html: Y.ic('minus'), onclick: () => set(-1) }), v, h('button', { type: 'button', 'aria-label': '늘리기', html: Y.ic('plus'), onclick: () => set(1) }));
  };
  const redraw = () => { Y.applyPaper(); redrawAllPages(); };
  const body = h('div', { class: 'settings' },
    h('div', { class: 'sh' }, h('button', { type: 'button', class: 'round', 'aria-label': '닫기', html: Y.ic('back'), onclick: () => m.close() }), h('h2', { text: '설정' })),
    h('h4', { text: '필기' }),
    row('손가락으로 쓰기', toggle('finger', () => { Y.applyPaper(); renderTools(false); })),
    row('S Pen 버튼', segc('penButton', [['eraser', '지우개'], ['lasso', '올가미']])),
    row('모양 보정', toggle('snap')),
    row('형광펜 글자 맞춤', toggle('hlSnap')),
    row('손바닥 무시', segc('palm', [['weak', '약'], ['mid', '중'], ['strong', '강']])),
    h('h4', { text: '화면' }),
    row('다크 모드', segc('dark', [['off', '끔'], ['on', '켬'], ['auto', '기기']], redraw)),
    row('종이 색', segc('paper', [['white', '흰색'], ['gray', '회색']], redraw)),
    row('펜 칸 개수', stepper()),
    row('진동', segc('haptic', [['off', '끔'], ['mid', '약'], ['strong', '강']])),
    h('h4', { text: '저장' }),
    row('자동 저장', segc('autosave', [[0, '즉시'], [1000, '1초'], [5000, '5초']])),
    row('내보내기', segc('exportDir', [['files', '파일'], ['share', '공유']])),
    row('휴지통 보관', segc('trashDays', [[7, '7일'], [30, '30일'], [90, '90일']])));
  const m = Y.modal(body, { cls: 'page' });
};
})(window.Y);
