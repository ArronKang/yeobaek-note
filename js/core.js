/* 코어: 네임스페이스, 토큰 상수, 저장소, 공용 UI(토스트·모달·팝오버) */
window.Y = window.Y || {};
(function (Y) {
'use strict';

Y.$ = (s, r) => (r || document).querySelector(s);
Y.$$ = (s, r) => Array.from((r || document).querySelectorAll(s));
Y.rnd = v => Math.round(v * 10000) / 10000;
Y.uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
Y.DPR = Math.min(window.devicePixelRatio || 1, 2);
Y.reduced = window.matchMedia('(prefers-reduced-motion: reduce)');

Y.h = function (tag, props, ...kids) {
  const e = document.createElement(tag);
  if (props) for (const k in props) {
    const v = props[k];
    if (v == null || v === false) continue;
    if (k === 'class') e.className = v;
    else if (k === 'html') e.innerHTML = v;
    else if (k === 'text') e.textContent = v;
    else if (k === 'style') e.style.cssText = v;
    else if (k.slice(0, 2) === 'on') e.addEventListener(k.slice(2), v);
    else e.setAttribute(k, v === true ? '' : v);
  }
  kids.flat().forEach(c => { if (c != null && c !== false) e.append(c.nodeType ? c : document.createTextNode(c)); });
  return e;
};
const h = Y.h;

/* ---------- 아이콘 (24 격자, 1.75 스트로크, 라인 스타일 하나) ---------- */
const ICON = {
  back: '<path d="M15 18l-6-6 6-6"/>',
  chevron: '<path d="M9 6l6 6-6 6"/>',
  down: '<path d="M6 9l6 6 6-6"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.2-4.2"/>',
  share: '<path d="M12 15V4"/><path d="M8 8l4-4 4 4"/><path d="M5 12v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M18.4 5.6l-1.8 1.8M7.4 16.6l-1.8 1.8"/>',
  collapse: '<path d="M14 10l6-6M20 9V4h-5"/><path d="M10 14l-6 6M4 15v5h5"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  home: '<path d="M4 10.5L12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z"/>',
  insert: '<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M12 8.5v7M8.5 12h7"/>',
  draw: '<path d="M4 20l4-1L19 8a2.1 2.1 0 0 0-3-3L5 16z"/><path d="M14 7l3 3"/>',
  eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  retry: '<path d="M4.5 12a7.5 7.5 0 1 0 2.2-5.3"/><path d="M4.5 4.5v3.2h3.2"/><path d="M10 10l4 4M14 10l-4 4"/>',
  compose: '<path d="M11 4.5H6.5a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V13"/><path d="M17.6 3.6a1.9 1.9 0 0 1 2.7 2.7L12.5 14l-3.6.9.9-3.6z"/>',
  importf: '<path d="M12 4v11"/><path d="M7.5 10.5 12 15l4.5-4.5"/><path d="M5 19.5h14"/>',
  focus: '<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3.5"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/>',
  quiz: '<rect x="5" y="3.5" width="14" height="17" rx="2"/><path d="M9 8.5h6M9 12h6M9 15.5h3"/><path d="M14.5 15.5l1.3 1.3 2.4-2.6"/>',
  reset: '<path d="M4 12a8 8 0 1 0 2.5-5.8"/><path d="M4 4v4h4"/>',
  answer: '<path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z"/><path d="M8.5 12.2l2.4 2.4 4.6-5"/>',
  hand: '<path d="M8 13V5.5a1.5 1.5 0 0 1 3 0V11"/><path d="M11 10.5V4a1.5 1.5 0 0 1 3 0v6.5"/><path d="M14 10.5V5.5a1.5 1.5 0 0 1 3 0V12"/><path d="M17 9.5a1.5 1.5 0 0 1 3 0V14a7 7 0 0 1-7 7h-1.2a6 6 0 0 1-4.6-2.2L4.3 15.2a1.6 1.6 0 0 1 2.4-2.1L8 14.5"/>',
  finger: '<path d="M9 11V5a1.5 1.5 0 0 1 3 0v5.5"/><path d="M12 10V8.5a1.5 1.5 0 0 1 3 0V11"/><path d="M15 10.5a1.5 1.5 0 0 1 3 0V15a6 6 0 0 1-6 6h-1a6 6 0 0 1-4.7-2.3l-1.9-2.5a1.6 1.6 0 0 1 2.4-2.1L9 15.5"/><path d="M3 4.5l2 1M3 9l2-.5"/>',
  minus: '<path d="M5 12h14"/>',
  undo: '<path d="M3 7v6h6"/><path d="M21 17a9 9 0 0 0-15-6.7L3 13"/>',
  redo: '<path d="M21 7v6h-6"/><path d="M3 17a9 9 0 0 1 15-6.7L21 13"/>',
  text: '<path d="M5 6V4h14v2"/><path d="M12 4v16"/><path d="M9 20h6"/>',
  lasso: '<path d="M7 18c-3-1-4-3-4-5 0-4 4-7 9-7s9 3 9 6-4 5-9 5c-1 0-2 0-3-.3"/><path d="M7 18c0 2 1 3 2 3s2-1 1-3"/>',
  space: '<path d="M12 3v7"/><path d="M9 7l3 3 3-3"/><path d="M12 21v-7"/><path d="M9 17l3-3 3 3"/><path d="M4 12h3M17 12h3"/>',
  library: '<rect x="4" y="4" width="6.5" height="16" rx="1.5"/><rect x="13.5" y="4" width="6.5" height="7" rx="1.5"/><rect x="13.5" y="14" width="6.5" height="6" rx="1.5"/>',
  page: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/>',
  pageplus: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M12 12v5M9.5 14.5h5"/>',
  moon: '<path d="M19.5 14.6A7.8 7.8 0 0 1 9.4 4.5 7.8 7.8 0 1 0 19.5 14.6z"/>',
  sidebar: '<rect x="3.5" y="4.5" width="17" height="15" rx="2.5"/><path d="M9.5 4.5v15"/>',
  split: '<rect x="3.5" y="4.5" width="17" height="15" rx="2.5"/><path d="M12 4.5v15"/>',
  template: '<rect x="4" y="4" width="16" height="16" rx="2.5"/><path d="M4 10h16M10 10v10"/>',
  image: '<rect x="4" y="5" width="16" height="14" rx="2.5"/><circle cx="9" cy="10" r="1.6"/><path d="M5 17l5-4.5 3.5 3 2.5-2L20 17"/>',
  shape: '<rect x="4" y="11" width="9" height="9" rx="1.5"/><circle cx="15.5" cy="8.5" r="4.5"/>',
  link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
  fit: '<path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/>',
  width: '<path d="M4 12h16M7 9l-3 3 3 3M17 9l3 3-3 3"/>',
  grid: '<rect x="4" y="4" width="16" height="16" rx="2"/><path d="M4 9.3h16M4 14.7h16M9.3 4v16M14.7 4v16"/>',
  paper: '<rect x="5" y="4" width="14" height="16" rx="2"/><path d="M8.5 9h7M8.5 13h4"/>',
  trash: '<path d="M4 7h16"/><path d="M9 7V4h6v3"/><path d="M6 7l1 13h10l1-13"/>',
  star: '<path d="M12 4l2.4 5 5.4.7-4 3.8 1 5.4L12 16.3 7.2 18.9l1-5.4-4-3.8L9.6 9z"/>',
  folder: '<path d="M4 7a2 2 0 0 1 2-2h4l2 2h6a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"/>',
  clock: '<circle cx="12" cy="12" r="8"/><path d="M12 8v4l3 2"/>',
  notes: '<rect x="5" y="4" width="14" height="16" rx="2"/><path d="M9 4v16M12 9h4M12 13h4"/>',
  list: '<path d="M8 7h12M8 12h12M8 17h12M4 7h.01M4 12h.01M4 17h.01"/>',
  cover: '<rect x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/>',
  sort: '<path d="M7 5v14M4 16l3 3 3-3M17 19V5M14 8l3-3 3 3"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  copy: '<rect x="8" y="8" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>',
  palette: '<circle cx="12" cy="12" r="8"/><path d="M8.5 11h.01M12 8h.01M15.5 11h.01"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  bookmark: '<path d="M7 4h10v16l-5-3.5L7 20z"/>',
  more: '<path d="M6 12h.01M12 12h.01M18 12h.01"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  move: '<path d="M4 7a2 2 0 0 1 2-2h4l2 2h6a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"/><path d="M12 11v4M10 13l2 2 2-2"/>',
  up: '<path d="M12 19V5M6 11l6-6 6 6"/>',
  dup: '<rect x="8" y="8" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>',
  restore: '<path d="M4 12a8 8 0 1 0 2.5-5.8"/><path d="M4 4v4h4"/>',
  pdf: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M8.5 16.5h7"/>'
};
Y.ic = (n, sz) => '<svg class="ic" viewBox="0 0 24 24"' + (sz ? ' width="' + sz + '" height="' + sz + '"' : '') + ' aria-hidden="true">' + (ICON[n] || '') + '</svg>';

/* 화면에 보이는 글자에서 가운뎃점(·) 빼기 */
Y.clean = s => String(s == null ? '' : s).replace(/\s*[·•]\s*/g, ' ').replace(/\s{2,}/g, ' ').trim();

/* ---------- 토큰 상수 ---------- */
Y.COVERS = [
  // 너무 회색 일색이지 않게: 채도를 낮춘 차분한 색 (흰 글자가 잘 보이는 밝기)
  { id: 'char', name: '먹', c: '#2b2d31' }, { id: 'slate', name: '남색', c: '#3b4a63' },
  { id: 'sage', name: '세이지', c: '#5f7a6b' }, { id: 'rose', name: '로즈', c: '#a2616c' },
  { id: 'mustard', name: '머스터드', c: '#b08636' }, { id: 'terra', name: '테라코타', c: '#a35a43' },
  { id: 'moss', name: '이끼', c: '#6c7a4a' }, { id: 'plum', name: '자두', c: '#6b5884' }
];
Y.coverLight = id => !!(Y.COVERS.find(c => c.id === id) || {}).light;
Y.PEN_COLORS = ['#1A1A20', '#2B4FD8', '#1F9D6B', '#E5484D', '#F2A93B', '#7A5AF8'];
Y.HL_COLORS = ['#FFE45C', '#8FF0C4', '#FF9EC7', '#C9BEFF'];
Y.TEMPLATES = [['lined', '줄 노트'], ['grid', '모눈'], ['dot', '점 노트'], ['planner', '플래너'], ['recipe', '레시피 저널'], ['blank', '빈 페이지']];
Y.coverColor = id => (Y.COVERS.find(c => c.id === id) || Y.COVERS[0]).c;

/* ---------- 저장소 ---------- */
Y.store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
  set(k, v) {
    try { localStorage.setItem(k, JSON.stringify(v)); return true; }
    catch (e) { Y.toast('저장 공간이 부족해요'); return false; }
  },
  del(k) { try { localStorage.removeItem(k); } catch (e) {} }
};

Y.settings = Object.assign({
  finger: false, theme: 'light', penButton: 'eraser', doubleTap: 'undo', snap: true, palm: 'mid', linkPenOnly: false,
  tabLang: 'en', paper: 'white', trayCount: 5, haptic: 'mid',
  autosave: 500, exportDir: 'files', trashDays: 30, hlSnap: true
}, Y.store.get('yeobaek.v2.settings', {}));
if (!Y.settings.v3) { Y.settings.v3 = 1; Y.settings.finger = false; Y.settings.theme = Y.settings.theme || 'light'; }
Y.saveSettings = () => Y.store.set('yeobaek.v2.settings', Y.settings);
// 다크 모드: 끔 / 켬 / 기기 설정 따라. 켜지면 화면과 종이가 함께 어두워짐
if (!Y.settings.dark) Y.settings.dark = Y.settings.theme === 'dark' || Y.settings.paper === 'dark' ? 'on' : 'off';
if (Y.settings.paper === 'dark') Y.settings.paper = 'white';
const sysDark = window.matchMedia ? matchMedia('(prefers-color-scheme: dark)') : { matches: false };
Y.isDark = () => Y.settings.dark === 'on' || (Y.settings.dark === 'auto' && sysDark.matches);
Y.applyPaper = () => {
  const d = document.documentElement, dark = Y.isDark();
  d.dataset.theme = dark ? 'dark' : 'light'; d.dataset.paper = dark ? 'dark' : (Y.settings.paper === 'dark' ? 'white' : Y.settings.paper);
  const tc = document.querySelector('meta[name="theme-color"]'); if (tc) tc.content = dark ? '#212529' : '#f8f9fa';
  const cs = document.querySelector('meta[name="color-scheme"]'); if (cs) cs.content = dark ? 'dark' : 'light';
  document.body && document.body.classList.toggle('finger-draw', !!Y.settings.finger);
};
Y.applyPaper();
// 기기 설정이 바뀌면 따라감 (필기 색 다시 그리기는 편집 화면이 맡음)
if (sysDark.addEventListener) sysDark.addEventListener('change', () => { if (Y.settings.dark === 'auto') { Y.applyPaper(); Y.onThemeChange && Y.onThemeChange(); } });
Y.haptic = ms => {
  if (Y.settings.haptic === 'off' || !navigator.vibrate) return;
  try { navigator.vibrate(Y.settings.haptic === 'strong' ? ms * 2 : ms); } catch (e) {}
};

/* 펜 트레이 상태 */
Y.PEN_TYPES = [['ball', '볼펜'], ['fountain', '만년필'], ['fine', '세필'], ['brush', '붓펜']];
Y.pens = Y.store.get('yeobaek.v2.pens', null) || [
  { kind: 'pen', type: 'fountain', color: '#1A1A20', mm: 0.9, alpha: 1 },
  { kind: 'pen', type: 'ball', color: '#2B4FD8', mm: 0.8, alpha: 1 },
  { kind: 'pen', type: 'brush', color: '#E5484D', mm: 1.4, alpha: 1 },
  { kind: 'hl', color: '#FFE45C', mm: 6, alpha: 0.4 },
  { kind: 'hl', color: '#8FF0C4', mm: 6, alpha: 0.4 }
];
Y.pens.forEach(p => { if (p.kind === 'pen' && !p.type) p.type = 'ball'; });
Y.penIdx = 0;
Y.eraser = Y.store.get('yeobaek.v2.eraser', null) || { mm: 5, partial: false };
Y.mode = 'pen'; // pen | eraser | lasso | space | text
Y.savePens = () => { Y.store.set('yeobaek.v2.pens', Y.pens); Y.store.set('yeobaek.v2.eraser', Y.eraser); };

/* ---------- 라이브러리 데이터 ---------- */
(function initLib() {
  let lib = Y.store.get('yeobaek.v2.lib', null);
  if (!lib) {
    lib = { folders: [], notes: [] };
    const old = Y.store.get('yeobaek.notes.v1', []);
    old.forEach((n, i) => {
      const id = n.id || Y.uid();
      const pages = (n.pages || []).map(p => ({
        ratio: p.ratio || 1.4142, tpl: ['lined', 'grid', 'blank'].includes(n.bg) ? n.bg : 'lined', texts: [],
        strokes: (p.strokes || []).map(s => Object.assign({ a: s.t === 'hl' ? 0.4 : 1 }, s))
      }));
      Y.store.set('yeobaek.v2.n.' + id, { pages });
      lib.notes.push({ id, title: n.title || '제목 없음', cover: Y.COVERS[i % 8].id, folder: null, fav: false, trashed: false, created: n.updated || Date.now(), updated: n.updated || Date.now(), pageCount: pages.length || 1 });
    });
  }
  Y.lib = lib;
  const lim = Y.settings.trashDays * 864e5;
  Y.lib.notes = Y.lib.notes.filter(n => {
    if (n.trashed && Date.now() - n.trashedAt > lim) { Y.store.del('yeobaek.v2.n.' + n.id); return false; }
    return true;
  });
})();
Y.saveLib = () => Y.store.set('yeobaek.v2.lib', Y.lib);
Y.fmtDate = ts => new Date(ts).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' });

/* ---------- 토스트 ---------- */
Y.toast = function (msg, opt) {
  opt = opt || {};
  const root = Y.$('#overlay');
  const old = Y.$('.toast', root); if (old) old.remove();
  const t = h('div', { class: 'toast', role: 'status' }, h('span', { text: msg }));
  if (opt.action) t.append(h('button', { class: 'toast-act', text: opt.action, onclick: () => { t.remove(); opt.onAction && opt.onAction(); } }));
  root.append(t);
  requestAnimationFrame(() => t.classList.add('in'));
  clearTimeout(Y._tt);
  Y._tt = setTimeout(() => { t.classList.remove('in'); setTimeout(() => t.remove(), 200); }, opt.ms || 2600);
};

/* ---------- 모달 / 확인 ---------- */
Y._modals = [];
Y.modal = function (content, opt) {
  opt = opt || {};
  Y.closePopover();
  const back = h('div', { class: 'backdrop' });
  const box = h('div', { class: 'modal' + (opt.cls ? ' ' + opt.cls : ''), role: 'dialog', 'aria-modal': 'true' });
  box.append(content); back.append(box);
  Y.$('#overlay').append(back);
  requestAnimationFrame(() => back.classList.add('in'));
  const close = () => {
    const i = Y._modals.indexOf(close); if (i < 0) return; Y._modals.splice(i, 1);
    document.removeEventListener('keydown', key);
    back.classList.remove('in'); setTimeout(() => back.remove(), 180);
    opt.onClose && opt.onClose();
  };
  const key = e => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', key);
  back.addEventListener('pointerdown', e => { if (e.target === back) close(); });
  Y._modals.push(close);
  return { close, box };
};
Y.confirm = function (o) {
  return new Promise(res => {
    let m, done = false;
    const fin = v => { if (done) return; done = true; m.close(); res(v); };
    const body = h('div', { class: 'dlg' },
      h('h3', { text: o.title }),
      o.text ? h('p', { text: o.text }) : null,
      h('div', { class: 'dlg-act' },
        h('button', { class: 'btn ghost', text: o.cancel || '취소', onclick: () => fin(false) }),
        h('button', { class: 'btn ' + (o.danger ? 'danger' : 'primary'), text: o.ok, onclick: () => fin(true) })));
    m = Y.modal(body, { cls: 'small', onClose: () => { if (!done) { done = true; res(false); } } });
  });
};

/* ---------- 팝오버 ---------- */
Y.popover = function (anchor, node, opt) {
  opt = opt || {};
  // 켜고 끄기: 같은 버튼을 다시 누르면 닫기만 함
  if (Y._pop && Y._pop.anchor === anchor) { Y.closePopover(); return null; }
  Y.closePopover();
  const pop = h('div', { class: 'popover', role: 'dialog' }, node);
  if (opt.width) pop.style.width = opt.width + 'px';
  Y.$('#overlay').append(pop);
  const r = anchor.getBoundingClientRect(), pw = pop.offsetWidth, ph = pop.offsetHeight;
  let left = opt.align === 'left' ? r.left : opt.align === 'right' ? r.right - pw : r.left + r.width / 2 - pw / 2;
  left = Math.max(8, Math.min(left, innerWidth - pw - 8));
  const top = Math.max(8, Math.min(opt.top != null ? opt.top : r.bottom + 8, innerHeight - ph - 8));
  pop.style.left = left + 'px'; pop.style.top = top + 'px';
  pop.style.transformOrigin = Math.round(r.left + r.width / 2 - left) + 'px 0px';
  requestAnimationFrame(() => pop.classList.add('in'));
  const off = e => { if (pop.contains(e.target) || anchor.contains(e.target)) return; if (opt.keep && opt.keep(e.target)) return; Y.closePopover(); };
  setTimeout(() => document.addEventListener('pointerdown', off, true), 0);
  Y._pop = { pop, off, anchor, owner: opt.owner, onClose: opt.onClose };
  return pop;
};
Y.closePopover = function () {
  const p = Y._pop; if (!p) return;
  Y._pop = null;
  document.removeEventListener('pointerdown', p.off, true);
  p.pop.classList.remove('in');
  setTimeout(() => p.pop.remove(), 140);
  p.onClose && p.onClose();
};

/* 버튼 생성 도우미 */
Y.btn = function (icon, label, fn, o) {
  o = o || {};
  const b = h('button', { type: 'button', class: 'tool' + (o.cls ? ' ' + o.cls : '') });
  const iconOnly = icon && !o.text && Y.iconOnly;
  b.innerHTML = (icon ? Y.ic(icon) : '') + (label && !iconOnly ? '<span>' + label + '</span>' : '');
  if (o.aria || label) b.setAttribute('aria-label', o.aria || label);
  if (o.pressed !== undefined) b.setAttribute('aria-pressed', o.pressed ? 'true' : 'false');
  if (o.disabled) b.disabled = true;
  b.addEventListener('click', fn);
  return b;
};
})(window.Y);
