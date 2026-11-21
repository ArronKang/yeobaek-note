/* 라이브러리: 사이드바(폴더 트리) + 노트 커버/목록, 새 노트, 휴지통 */
(function (Y) {
'use strict';
const h = Y.h, $ = Y.$;
const L = Y.L = { view: 'all', mode: Y.store.get('yeobaek.v2.libmode', 'cover'), sort: 'updated', q: '', sel: new Set(), selecting: false, collapsed: new Set() };
const SORTS = [['updated', '수정일'], ['name', '이름'], ['created', '만든 날']];

/* ---------- 데이터 도우미 ---------- */
const folderOf = id => Y.lib.folders.find(f => f.id === id);
function descendants(id) { const out = [id]; Y.lib.folders.filter(f => f.parent === id).forEach(c => out.push(...descendants(c.id))); return out; }
function viewTitle() {
  if (L.q) return '검색 결과';
  if (L.view === 'all') return '노트'; if (L.view === 'quiz') return '문제지'; if (L.view === 'recent') return '최근';
  if (L.view === 'fav') return '즐겨찾기'; if (L.view === 'trash') return '휴지통';
  const f = folderOf(L.view.slice(2)); return f ? f.name : '모든 노트';
}
function visible() {
  let a = Y.lib.notes.filter(n => !!n.trashed === (L.view === 'trash' && !L.q));
  if (L.q) a = a.filter(n => !n.trashed && n.title.toLowerCase().includes(L.q.toLowerCase()));
  else if (L.view === 'fav') a = a.filter(n => n.fav);
  else if (L.view === 'all') a = a.filter(n => n.kind !== 'quiz');
  else if (L.view === 'quiz') a = a.filter(n => n.kind === 'quiz');
  else if (L.view.startsWith('f:')) { const ids = descendants(L.view.slice(2)); a = a.filter(n => ids.includes(n.folder)); }
  const by = L.view === 'recent' && !L.q ? 'updated' : L.sort;
  a = a.slice().sort((x, y) => by === 'name' ? x.title.localeCompare(y.title, 'ko') : by === 'created' ? y.created - x.created : y.updated - x.updated);
  if (L.view === 'recent' && !L.q) a = a.slice(0, 12);
  return a;
}
const count = (fn) => Y.lib.notes.filter(n => !n.trashed && fn(n)).length;

/* ---------- 사이드바 ---------- */
function renderSide() {
  const side = $('#side'); side.innerHTML = '';
  const search = h('input', { class: 'sq', type: 'search', placeholder: '노트 검색', 'aria-label': '노트 검색', value: L.q });
  search.addEventListener('input', () => { L.q = search.value; L.selecting = false; L.sel.clear(); renderBody(); });
  side.append(h('div', { class: 'brand' }, h('span', { class: 'bname', text: '여백' })),
    h('label', { class: 'sbar' }, h('span', { html: Y.ic('search') }), search));
  const nav = h('nav', { class: 'snav', 'aria-label': '라이브러리' });
  const item = (id, icon, label, n, depth, extra) => {
    const on = L.view === id && !L.q;
    const b = h('button', { type: 'button', class: 'sitem' + (on ? ' on' : ''), style: depth ? 'padding-left:' + (12 + depth * 12) + 'px' : '' });
    b.innerHTML = (extra || '') + Y.ic(icon) + '<span class="sl">' + label + '</span>' + (n != null ? '<span class="mono sn">' + n + '</span>' : '');
    b.addEventListener('click', e => {
      if (e.target.closest('.chev')) return;
      L.view = id; L.q = ''; L.selecting = false; L.sel.clear(); Y.drawer(false); renderSide(); renderBody();
    });
    return b;
  };
  nav.append(item('all', 'notes', '노트', count(n => n.kind !== 'quiz')), item('quiz', 'quiz', '문제지', count(n => n.kind === 'quiz')), item('recent', 'clock', '최근'),
    item('fav', 'star', '즐겨찾기', count(n => n.fav)), item('trash', 'trash', '휴지통', Y.lib.notes.filter(n => n.trashed).length));
  side.append(nav);
  const fh = h('div', { class: 'fhead' }, h('span', { text: '폴더' }),
    h('button', { type: 'button', class: 'round sm', 'aria-label': '폴더 추가', html: Y.ic('plus'), onclick: () => addFolder(L.view.startsWith('f:') ? L.view.slice(2) : null) }));
  side.append(fh);
  const tree = h('div', { class: 'ftree' });
  const walk = (parent, depth) => {
    Y.lib.folders.filter(f => (f.parent || null) === parent).forEach(f => {
      const kids = Y.lib.folders.some(k => k.parent === f.id), closed = L.collapsed.has(f.id);
      const chev = kids ? '<span class="chev' + (closed ? '' : ' open') + '" role="button" aria-label="접기/펼치기">' + Y.ic('chevron') + '</span>' : '<span class="chev none"></span>';
      const b = item('f:' + f.id, 'folder', f.name, count(n => n.folder === f.id), depth, chev);
      const c = b.querySelector('.chev'); if (c && kids) c.addEventListener('click', e => { e.stopPropagation(); closed ? L.collapsed.delete(f.id) : L.collapsed.add(f.id); renderSide(); });
      if (L.view === 'f:' + f.id) b.append(h('span', { class: 'more', role: 'button', 'aria-label': '폴더 메뉴', html: Y.ic('more'), onclick: e => { e.stopPropagation(); folderMenu(e.currentTarget, f); } }));
      b.addEventListener('contextmenu', e => { e.preventDefault(); folderMenu(b, f); });
      tree.append(b);
      if (kids && !closed) walk(f.id, depth + 1);
    });
  };
  walk(null, 0);
  if (!Y.lib.folders.length) tree.append(h('div', { class: 'fempty', text: '폴더가 없어요' }));
  side.append(tree);
  side.append(h('button', { type: 'button', class: 'sitem setb', html: Y.ic('settings') + '<span class="sl">설정</span>', onclick: () => Y.openSettings() }));
}
function prompt(o) {
  return new Promise(res => {
    let m, done = false;
    const inp = h('input', { type: 'text', class: 'tinput', value: o.value || '', maxlength: 40, 'aria-label': o.title, placeholder: o.placeholder || '' });
    const fin = v => { if (done) return; done = true; m.close(); res(v); };
    inp.addEventListener('keydown', e => { if (e.key === 'Enter' && inp.value.trim()) fin(inp.value.trim()); });
    m = Y.modal(h('div', { class: 'dlg' }, h('h3', { text: o.title }), inp,
      h('div', { class: 'dlg-act' }, h('button', { class: 'btn ghost', text: '취소', onclick: () => fin(null) }),
        h('button', { class: 'btn primary', text: o.ok, onclick: () => inp.value.trim() ? fin(inp.value.trim()) : inp.focus() }))), { cls: 'small', onClose: () => { if (!done) { done = true; res(null); } } });
    setTimeout(() => { inp.focus(); inp.select(); }, 60);
  });
}
async function addFolder(parent) {
  const name = await prompt({ title: '새 폴더', ok: '만들기', placeholder: '폴더 이름' });
  if (!name) return;
  const f = { id: Y.uid(), name, parent: parent || null };
  Y.lib.folders.push(f); Y.saveLib(); L.view = 'f:' + f.id; L.q = ''; if (parent) L.collapsed.delete(parent); renderSide(); renderBody();
}
function folderMenu(anchor, f) {
  const list = h('div', { class: 'plist' });
  const it = (icon, label, fn, cls) => h('button', { type: 'button', class: 'pitem ic-l ' + (cls || ''), html: Y.ic(icon) + '<span>' + label + '</span>', onclick: () => { Y.closePopover(); fn(); } });
  list.append(
    it('page', '이름 바꾸기', async () => { const n = await prompt({ title: '폴더 이름', value: f.name, ok: '바꾸기' }); if (n) { f.name = n; Y.saveLib(); renderSide(); renderBody(); } }),
    it('plus', '하위 폴더 추가', () => addFolder(f.id)),
    it('trash', '폴더 삭제', async () => {
      const ok = await Y.confirm({ title: '‘' + f.name + '’ 폴더를 삭제할까요?', text: '안에 있는 노트는 폴더 없이 남아요.', ok: '폴더 삭제', danger: true });
      if (!ok) return;
      const ids = descendants(f.id);
      Y.lib.notes.forEach(n => { if (ids.includes(n.folder)) n.folder = null; });
      Y.lib.folders = Y.lib.folders.filter(x => !ids.includes(x.id)); Y.saveLib();
      L.view = 'all'; renderSide(); renderBody();
    }, 'warn'));
  Y.popover(anchor, list, { width: 210, align: 'left' });
}

/* ---------- 본문 ---------- */
function coverEl(n, small) {
  const c = h('div', { class: 'cover' + (small ? ' sm' : '') + (Y.coverLight(n.cover) ? ' light' : ''), style: '--cv:' + Y.coverColor(n.cover) });
  c.append(h('div', { class: 'choles' }));
  if (!small) c.append(h('div', { class: 'ctitle', text: n.title || '제목 없음' }));
  if (n.kind === 'quiz' && !small) {
    c.append(h('div', { class: 'cbadge', html: Y.ic('quiz') }));
    if (n.score) c.append(h('div', { class: 'cscore', text: n.score }));
  }
  return c;
}
Y.coverEl = coverEl;

function renderBody() {
  const main = $('#libmain'); main.innerHTML = '';
  const notes = visible();
  const trash = L.view === 'trash' && !L.q;
  /* 헤더 */
  const title = h('div', { class: 'ltitle' },
    h('button', { type: 'button', class: 'round menu', 'aria-label': '메뉴 열기', html: Y.ic('menu'), onclick: () => Y.drawer(true) }),
    h('div', {}, h('h1', { class: 'disp', text: viewTitle() }),
      h('div', { class: 'cap', text: trash ? '노트 ' + notes.length + '개 · ' + Y.settings.trashDays + '일 뒤 영구 삭제' : L.q ? '“' + L.q + '” · 노트 ' + notes.length + '개' : '노트 ' + notes.length + '개' })));
  const act = h('div', { class: 'lact' });
  if (L.selecting) {
    act.append(h('span', { class: 'cap sc', text: L.sel.size + '개 선택됨' }));
    if (trash) {
      act.append(Y.btn('restore', '복원', () => { L.sel.forEach(id => { const n = Y.lib.notes.find(x => x.id === id); if (n) { n.trashed = false; delete n.trashedAt; } }); Y.saveLib(); exitSel(); }, { cls: 'sbtn' }),
        Y.btn('trash', '영구 삭제', async () => { if (await Y.confirm({ title: L.sel.size + '개 노트를 영구 삭제할까요?', text: '이 작업은 되돌릴 수 없어요.', ok: '영구 삭제', danger: true })) { L.sel.forEach(id => ['n', 'q', 'qs'].forEach(k => Y.store.del('yeobaek.v2.' + k + '.' + id))); Y.lib.notes = Y.lib.notes.filter(n => !L.sel.has(n.id)); Y.saveLib(); exitSel(); } }, { cls: 'sbtn warn' }));
    } else {
      const mv = Y.btn('move', '이동', () => movePop(mv), { cls: 'sbtn' });
      act.append(Y.btn('star', '즐겨찾기', () => { const all = Array.from(L.sel).every(id => (Y.lib.notes.find(n => n.id === id) || {}).fav); L.sel.forEach(id => { const n = Y.lib.notes.find(x => x.id === id); if (n) n.fav = !all; }); Y.saveLib(); exitSel(); }, { cls: 'sbtn' }), mv,
        Y.btn('trash', '휴지통으로 이동', trashSel, { cls: 'sbtn warn' }));
    }
    act.append(Y.btn('close', '취소', exitSel, { cls: 'sbtn' }));
  } else {
    const seg = h('div', { class: 'seg icons', role: 'group', 'aria-label': '보기 전환' },
      h('button', { type: 'button', class: L.mode === 'cover' ? 'on' : '', 'aria-label': '커버로 보기', html: Y.ic('cover'), onclick: () => { L.mode = 'cover'; Y.store.set('yeobaek.v2.libmode', L.mode); renderBody(); } }),
      h('button', { type: 'button', class: L.mode === 'list' ? 'on' : '', 'aria-label': '목록으로 보기', html: Y.ic('list'), onclick: () => { L.mode = 'list'; Y.store.set('yeobaek.v2.libmode', L.mode); renderBody(); } }));
    act.append(seg);
    if (L.view !== 'recent' && !trash) {
      const chip = h('button', { type: 'button', class: 'chip', html: Y.ic('sort') + '<span>' + SORTS.find(s => s[0] === L.sort)[1] + '</span>' });
      chip.addEventListener('click', () => {
        const list = h('div', { class: 'plist' });
        SORTS.forEach(s => list.append(h('button', { type: 'button', class: 'pitem' + (L.sort === s[0] ? ' on' : ''), text: s[1], onclick: () => { L.sort = s[0]; Y.closePopover(); renderBody(); } })));
        Y.popover(chip, h('div', {}, h('div', { class: 'ptitle', text: '정렬' }), list), { width: 170, align: 'right' });
      });
      act.append(chip);
    }
    if (trash) { if (notes.length) act.append(Y.btn('trash', '휴지통 비우기', emptyTrash, { cls: 'sbtn warn' })); }
    else {
      act.append(Y.btn('pdf', '가져오기', () => $('#libfile').click(), { cls: 'sbtn' }));
      act.append(h('button', { type: 'button', class: 'btn primary', text: '새 노트', onclick: newNoteModal }));
    }
  }
  main.append(h('header', { class: 'lhead' }, title, act));
  /* 내용 */
  const body = h('div', { class: 'lbody' });
  if (!notes.length) body.append(emptyState(trash));
  else if (L.mode === 'cover' || trash === false && false) {
    const grid = h('div', { class: 'cgrid' });
    notes.forEach(n => grid.append(coverItem(n, trash)));
    body.append(grid);
  } else {
    const list = h('div', { class: 'rlist' });
    notes.forEach(n => list.append(rowItem(n, trash)));
    body.append(list);
  }
  main.append(body);
}
function emptyState(trash) {
  const fav = L.view === 'fav' && !L.q;
  if (L.q) return h('div', { class: 'empty' }, h('div', { class: 'eic', html: Y.ic('search', 40) }), h('h2', { text: '검색 결과가 없어요' }), h('p', { text: '‘' + L.q + '’와 일치하는 노트가 없어요. 다른 단어로 검색해 보세요' }));
  if (trash) return h('div', { class: 'empty' }, h('div', { class: 'eic', html: Y.ic('trash', 40) }), h('h2', { text: '휴지통이 비어 있어요' }), h('p', { text: '삭제한 노트는 ' + Y.settings.trashDays + '일 동안 여기에 보관돼요' }));
  if (fav) return h('div', { class: 'empty' }, h('div', { class: 'eic', html: Y.ic('star', 40) }), h('h2', { text: '즐겨찾기한 노트가 없어요' }), h('p', { text: '노트를 길게 눌러 선택한 뒤 즐겨찾기를 누르세요' }));
  return h('div', { class: 'empty' }, h('div', { class: 'eic', html: Y.ic('notes', 40) }), h('h2', { text: '아직 노트가 없어요' }), h('p', { text: '새 노트를 만들거나 PDF를 가져와 시작하세요' }),
    h('div', { class: 'eact' }, h('button', { type: 'button', class: 'btn primary', text: '새 노트', onclick: newNoteModal }), h('button', { type: 'button', class: 'btn ghost', text: '가져오기', onclick: () => $('#libfile').click() })));
}
function bindItem(el, n, trash) {
  let t, moved = false, sx = 0, sy = 0;
  el.addEventListener('pointerdown', e => { moved = false; sx = e.clientX; sy = e.clientY; el._lp = false; t = setTimeout(() => { if (!moved) { el._lp = true; Y.haptic(12); L.selecting = true; L.sel.add(n.id); renderBody(); } }, 500); });
  el.addEventListener('pointermove', e => { if (Math.abs(e.clientX - sx) + Math.abs(e.clientY - sy) > 8) { moved = true; clearTimeout(t); } });
  ['pointerup', 'pointerleave', 'pointercancel'].forEach(ev => el.addEventListener(ev, () => clearTimeout(t)));
  el.addEventListener('contextmenu', e => { e.preventDefault(); });
  el.addEventListener('click', () => {
    if (el._lp) { el._lp = false; return; }
    if (L.selecting) { L.sel.has(n.id) ? L.sel.delete(n.id) : L.sel.add(n.id); if (!L.sel.size) L.selecting = false; renderBody(); return; }
    if (trash) { Y.toast('휴지통의 노트는 복원한 뒤 열 수 있어요'); return; }
    Y.openNote(n, el.querySelector('.cover'));
  });
}
function coverItem(n, trash) {
  const sel = L.sel.has(n.id);
  const it = h('div', { class: 'citem' + (sel ? ' sel' : ''), 'data-id': n.id, role: 'button', tabindex: 0, 'aria-label': n.title });
  const cv = coverEl(n);
  if (n.fav) cv.append(h('span', { class: 'cfav', html: Y.ic('star', 14) }));
  if (L.selecting) cv.append(h('span', { class: 'ccheck' + (sel ? ' on' : ''), html: sel ? Y.ic('check', 14) : '' }));
  it.append(cv, h('div', { class: 'cname', text: n.title || '제목 없음' }), h('div', { class: 'cap', text: Y.fmtDate(n.updated) }));
  it.addEventListener('keydown', e => { if (e.key === 'Enter') it.click(); });
  bindItem(it, n, trash); return it;
}
function rowItem(n, trash) {
  const sel = L.sel.has(n.id);
  const it = h('div', { class: 'ritem' + (sel ? ' sel' : ''), 'data-id': n.id, role: 'button', tabindex: 0, 'aria-label': n.title });
  it.append(coverEl(n, true), h('div', { class: 'rmain' }, h('div', { class: 'rt', text: n.title || '제목 없음' }), h('div', { class: 'cap', text: Y.fmtDate(n.updated) })),
    h('div', { class: 'rpg' }, h('span', { class: 'mono', text: n.pageCount || 1 }), ' 페이지'),
    L.selecting ? h('span', { class: 'ccheck inl' + (sel ? ' on' : ''), html: sel ? Y.ic('check', 14) : '' }) : (n.fav ? h('span', { class: 'rfav', html: Y.ic('star', 16) }) : null));
  it.addEventListener('keydown', e => { if (e.key === 'Enter') it.click(); });
  bindItem(it, n, trash); return it;
}
function exitSel() { L.selecting = false; L.sel.clear(); renderSide(); renderBody(); }
async function trashSel() {
  const ids = Array.from(L.sel), one = ids.length === 1 ? Y.lib.notes.find(n => n.id === ids[0]) : null;
  const ok = await Y.confirm({ title: one ? '‘' + one.title + '’를 휴지통으로 옮길까요?' : ids.length + '개 노트를 휴지통으로 옮길까요?', ok: '휴지통으로 이동', cancel: '취소' });
  if (!ok) return;
  ids.forEach(id => { const n = Y.lib.notes.find(x => x.id === id); if (n) { n.trashed = true; n.trashedAt = Date.now(); } });
  Y.saveLib(); exitSel();
  Y.toast('휴지통으로 옮겼어요', { action: '되돌리기', onAction: () => { ids.forEach(id => { const n = Y.lib.notes.find(x => x.id === id); if (n) { n.trashed = false; delete n.trashedAt; } }); Y.saveLib(); renderSide(); renderBody(); }, ms: 4500 });
}
async function emptyTrash() {
  const ok = await Y.confirm({ title: '휴지통을 비울까요?', text: '비운 노트는 되돌릴 수 없어요.', ok: '휴지통 비우기', danger: true });
  if (!ok) return;
  Y.lib.notes.filter(n => n.trashed).forEach(n => ['n', 'q', 'qs'].forEach(k => Y.store.del('yeobaek.v2.' + k + '.' + n.id)));
  Y.lib.notes = Y.lib.notes.filter(n => !n.trashed); Y.saveLib(); renderSide(); renderBody();
}
function movePop(anchor) {
  const list = h('div', { class: 'plist' });
  const put = fid => { L.sel.forEach(id => { const n = Y.lib.notes.find(x => x.id === id); if (n) n.folder = fid; }); Y.saveLib(); Y.closePopover(); exitSel(); };
  list.append(h('button', { type: 'button', class: 'pitem', text: '폴더 없음', onclick: () => put(null) }));
  const walk = (p, d) => Y.lib.folders.filter(f => (f.parent || null) === p).forEach(f => { list.append(h('button', { type: 'button', class: 'pitem', style: 'padding-left:' + (12 + d * 14) + 'px', text: f.name, onclick: () => put(f.id) })); walk(f.id, d + 1); });
  walk(null, 0);
  Y.popover(anchor, h('div', {}, h('div', { class: 'ptitle', text: '이동할 폴더' }), list), { width: 220, align: 'right' });
}

/* ---------- 새 노트 ---------- */
function newNoteModal() {
  const st = { title: '새 노트', cover: Y.COVERS[0].id, tpl: 'lined' };
  const inp = h('input', { type: 'text', class: 'tinput', value: st.title, maxlength: 40, 'aria-label': '노트 제목' });
  const covers = h('div', { class: 'swrow', role: 'radiogroup', 'aria-label': '커버 색' });
  const tpls = h('div', { class: 'tplrow', role: 'radiogroup', 'aria-label': '페이지 양식' });
  const paint = () => {
    covers.innerHTML = ''; tpls.innerHTML = '';
    Y.COVERS.forEach(c => covers.append(h('button', { type: 'button', class: 'cdot' + (st.cover === c.id ? ' on' : ''), style: '--c:' + c.c, role: 'radio', 'aria-checked': st.cover === c.id ? 'true' : 'false', 'aria-label': c.name, onclick: () => { st.cover = c.id; paint(); prev(); } })));
    Y.TEMPLATES.forEach(t => tpls.append(h('button', { type: 'button', class: 'tpick' + (st.tpl === t[0] ? ' on' : ''), role: 'radio', 'aria-checked': st.tpl === t[0] ? 'true' : 'false', onclick: () => { st.tpl = t[0]; paint(); } },
      h('div', { class: 'paper tpl-' + t[0] + ' tmini', style: '--dp:.1px' }, t[0] === 'planner' || t[0] === 'recipe' ? h('span', { class: 'tmark', text: t[0] === 'planner' ? 'WEEK' : 'RECIPE' }) : null), h('span', { text: t[1] }))));
  };
  const pv = h('div', { class: 'npprev' });
  const prev = () => { pv.innerHTML = ''; pv.append(coverEl({ title: inp.value || '새 노트', cover: st.cover })); };
  inp.addEventListener('input', prev);
  paint(); prev();
  let m;
  const go = () => {
    const now = Date.now(), id = Y.uid(), title = inp.value.trim() || '새 노트';
    const meta = { id, title, cover: st.cover, folder: L.view.startsWith('f:') ? L.view.slice(2) : null, fav: false, trashed: false, created: now, updated: now, pageCount: 1 };
    Y.lib.notes.push(meta); Y.saveLib();
    Y.store.set('yeobaek.v2.n.' + id, { pages: [{ ratio: Y.RATIO, tpl: st.tpl, strokes: [], texts: [] }] });
    if (L.view === 'trash' || L.view === 'fav') L.view = 'all'; L.q = '';
    m.close(); renderSide(); renderBody();
    const el = Y.$('.citem[data-id="' + id + '"] .cover') || Y.$('.ritem[data-id="' + id + '"] .cover');
    setTimeout(() => Y.openNote(meta, el), 120);
  };
  inp.addEventListener('keydown', e => { if (e.key === 'Enter') go(); });
  m = Y.modal(h('div', { class: 'newnote' },
    h('h3', { text: '새 노트' }),
    h('div', { class: 'nnrow' }, pv, h('div', { class: 'nnf' }, h('label', { class: 'flabel', text: '제목' }), inp, h('label', { class: 'flabel', text: '커버 색' }), covers)),
    h('label', { class: 'flabel', text: '페이지 양식' }), tpls,
    h('div', { class: 'dlg-act' }, h('button', { class: 'btn ghost', text: '취소', onclick: () => m.close() }), h('button', { class: 'btn primary', text: '만들기', onclick: go }))), { cls: 'wide' });
  setTimeout(() => { inp.focus(); inp.select(); }, 80);
}

/* ---------- 열기 ---------- */
Y.noteDoc = function (n) {
  const data = Y.store.get('yeobaek.v2.n.' + n.id, null);
  const pages = data && data.pages && data.pages.length ? data.pages.map(p => ({ ratio: p.ratio || Y.RATIO, tpl: p.tpl || 'lined', strokes: p.strokes || [], texts: p.texts || [] })) : [Y.makePage('lined')];
  return { id: n.id, type: 'note', title: n.title, pages };
};
const prepped = new WeakSet();
Y.openNote = function (n, coverElm) {
  const rect = coverElm ? coverElm.getBoundingClientRect() : null;
  if (n.kind === 'quiz' && !prepped.has(n)) { prepped.add(n); Y.prepQuiz(n).then(() => Y.openNote(n, coverElm)); return; }
  prepped.delete(n);
  const doc = n.kind === 'quiz' ? Y.quizDoc(n) : Y.noteDoc(n);
  if (!doc) { Y.toast('문제지 데이터를 찾을 수 없어요'); return; }
  Y.enterEditor({ doc, rect, color: Y.coverColor(n.cover) });
  if (n.kind === 'quiz' && !Y.store.get('yeobaek.v5.hint', false)) {
    Y.store.set('yeobaek.v5.hint', true);
    setTimeout(() => Y.toast('문제나 답칸을 꾹 누르면 정답과 풀이가 떠올라요', { ms: 4000 }), 900);
  }
};
$('#libfile').addEventListener('change', e => {
  const f = e.target.files[0]; e.target.value = ''; if (!f) return;
  const isQuiz = /\.ybq$/i.test(f.name) || /json/.test(f.type);
  const asQuiz = () => f.text().then(t => {
    const spec = Y.parseQuiz(t);
    Y.toast('문제지를 준비하는 중…', { ms: 4000 });
    return Y.importQuiz(spec, { folder: (L.view.startsWith && L.view.startsWith('f:')) ? L.view.slice(2) : null }).then(meta => {
      if (!L.view.startsWith('f:')) L.view = 'quiz';
      renderSide(); renderBody(); Y.toast('문제지를 가져왔어요'); setTimeout(() => Y.openNote(meta), 150);
    });
  });
  if (isQuiz) { asQuiz().catch(() => Y.toast('문제지 파일을 읽을 수 없어요. .ybq 형식인지 확인하세요', { ms: 4500 })); return; }
  const head = f.slice(0, 5).text ? f.slice(0, 5).text() : Promise.resolve('');
  head.then(sig => {
    if (sig.trim().startsWith('{')) return asQuiz().catch(() => Y.toast('문제지 파일을 읽을 수 없어요', { ms: 4500 }));
    Y.toast('PDF를 여는 중…', { ms: 8000 });
    Y.openPdfFile(f).then(doc => { Y.$('.toast') && Y.$('.toast').remove(); Y.enterEditor({ doc }); })
      .catch(err => Y.toast(err && err.message === 'engine' ? 'PDF 엔진을 불러오지 못했어요. 인터넷 연결을 확인하세요' : '이 파일은 열 수 없어요. PDF나 .ybq 문제지를 선택하세요', { ms: 5000 }));
  });
});

/* 처음 실행하면 예제 문제지를 하나 넣어 둬요 */
if (!Y.store.get('yeobaek.v3.seeded', false) && Y.QUIZ_SAMPLE) {
  try { Y.addQuiz(Y.parseQuiz(JSON.stringify(Y.QUIZ_SAMPLE)), { cover: 'char' }); Y.store.set('yeobaek.v3.seeded', true); } catch (e) {}
}
if (!Y.store.get('yeobaek.v4.seeded', false) && Y.QUIZ_SAMPLES) {
  try { Y.QUIZ_SAMPLES.slice().reverse().forEach(q => Y.addQuiz(Y.parseQuiz(JSON.stringify(q)), { cover: q.cover })); Y.store.set('yeobaek.v4.seeded', true); } catch (e) {}
}

/* ---------- 좁은 화면: 사이드바 드로어 ---------- */
Y.drawer = function (open) { $('#lib').classList.toggle('drawer', !!open); };
$('#scrim').addEventListener('click', () => Y.drawer(false));

Y.libRefresh = function () { exitSelQuiet(); renderSide(); renderBody(); };
function exitSelQuiet() { L.selecting = false; L.sel.clear(); }
renderSide(); renderBody();
})(window.Y);
