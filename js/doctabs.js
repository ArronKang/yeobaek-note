/* 문서 탭: 여러 노트·문제지·PDF를 열어 두고 위에서 오가기
   - 두 개 이상 열었을 때만 뒤로가기 옆에 나타남 (하나일 땐 조용히)
   - 탭마다 보던 위치를 기억, 꾹 누르고 있으면 오그라들며 닫힘 */
(function (Y) {
'use strict';
const h = Y.h, $ = Y.$;
const T = Y.docTabs = [];
let cur = null, switching = false;

const keyOf = d => d.type === 'pdf' ? 'pdf:' + (d.key || d.id) : d.id;
function remember() {
  const P = Y.panes && Y.panes[0];
  const t = T.find(x => x.key === cur);
  if (t && P && P.scroll) t.scroll = P.scroll.scrollTop;
}

const enter = Y.enterEditor;
Y.enterEditor = function (o) {
  if (!switching) remember();
  enter(o);
  const d = o.doc, key = keyOf(d);
  let t = T.find(x => x.key === key);
  if (!t) { t = { key, type: d.type, id: d.id, doc: d.type === 'pdf' ? d : null, scroll: 0 }; T.push(t); }
  else if (d.type === 'pdf') t.doc = d;
  t.title = d.title || '제목 없음';
  cur = key; render();
};

function open(t) {
  if (t.key === cur) return;
  remember(); Y.saveAll && Y.saveAll();
  switching = true;
  if (t.type === 'pdf') Y.enterEditor({ doc: t.doc });
  else {
    const n = Y.lib.notes.find(x => x.id === t.id);
    if (!n || n.trashed) { close(t); switching = false; return; }
    Y.openNote(n);
  }
  switching = false;
  // 보던 위치로 (페이지가 그려진 뒤)
  const go = (k) => { const P = Y.panes[0]; if (P && P.doc && keyOf(P.doc) === t.key) P.scroll.scrollTop = t.scroll || 0; else if (k < 20) setTimeout(() => go(k + 1), 60); };
  setTimeout(() => go(0), 60);
  Y.haptic(6);
}
function close(t) {
  const i = T.indexOf(t); if (i < 0) return;
  T.splice(i, 1);
  if (t.key === cur) {
    const next = T[i] || T[i - 1];
    cur = null;
    if (next) open(next); else Y.closeEditor();
  }
  render();
}

function render() {
  let bar = $('#doctabs');
  if (!bar) { bar = h('div', { id: 'doctabs', role: 'tablist', 'aria-label': '열린 문서' }); $('#chrome .left').append(bar); }
  bar.innerHTML = '';
  // 노트가 지워졌으면 탭도 정리
  for (let i = T.length - 1; i >= 0; i--) { const t = T[i]; if (t.type !== 'pdf' && !Y.lib.notes.some(n => n.id === t.id && !n.trashed)) T.splice(i, 1); }
  bar.hidden = T.length < 2;
  T.forEach(t => {
    const on = t.key === cur;
    const b = h('button', { type: 'button', class: 'dtab' + (on ? ' on' : ''), role: 'tab', 'aria-selected': on ? 'true' : 'false', title: t.title }, h('span', { text: Y.clean(t.title) }));
    // 톡: 그 문서로. 꾹 누르고 있으면 점점 오그라들다가 닫힘
    let lp = null, done = false;
    b.addEventListener('pointerdown', () => { done = false; b.classList.add('pressing'); lp = setTimeout(() => { done = true; Y.haptic(12); close(t); }, 550); });
    ['pointerup', 'pointerleave', 'pointercancel'].forEach(ev => b.addEventListener(ev, () => { clearTimeout(lp); b.classList.remove('pressing'); }));
    b.addEventListener('click', () => { if (!done) open(t); });
    bar.append(b);
  });
  if (T.length >= 2) {
    bar.append(h('button', { type: 'button', class: 'dtab add', 'aria-label': '다른 문서 열기', html: Y.ic('plus'), onclick: () => Y.closeEditor() }));
    const on = bar.querySelector('.on'); if (on) on.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }
}
Y.renderDocTabs = render;
})(window.Y);
