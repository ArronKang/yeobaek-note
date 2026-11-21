/* 여백 문제지 (.ybq) — 이 앱에서만 열리는 인터랙티브 문제지
   종이처럼 필기·이동은 그대로, 문항마다 선택·정답·풀이·채점이 살아 있어요. */
(function (Y) {
'use strict';
const h = Y.h, $ = Y.$;
const PW = 560, PH = Math.round(560 * 1.4142), PAD_T = 44, PAD_B = 40, PAD_X = 44;
const CIRC = ['①', '②', '③', '④', '⑤', '⑥', '⑦', '⑧'];

/* ---------- 텍스트 + 수식(KaTeX) ---------- */
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
function tex(src) {
  return String(src == null ? '' : src).split(/(\$\$[\s\S]+?\$\$|\$[^$\n]+?\$)/).map(part => {
    const disp = part.startsWith('$$') && part.endsWith('$$') && part.length > 4;
    const inl = !disp && part.length > 2 && part[0] === '$' && part[part.length - 1] === '$';
    if ((disp || inl) && window.katex) {
      try { return katex.renderToString(part.slice(disp ? 2 : 1, disp ? -2 : -1), { displayMode: disp, throwOnError: false, output: 'html' }); }
      catch (e) { return esc(part); }
    }
    return esc(part).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/__([^_\s][^_]*?)__/g, '<u>$1</u>').replace(/\n/g, '<br>');
  }).join('');
}
Y.tex = tex;

/* ---------- 파일 읽기/검사 ---------- */
Y.parseQuiz = function (text) {
  let j; try { j = JSON.parse(text); } catch (e) { throw new Error('형식'); }
  if (!j || !Array.isArray(j.problems) || !j.problems.length) throw new Error('형식');
  let no = 0;
  j.problems = j.problems.map((p, i) => p.type === 'passage' ? ({ type: 'passage', q: p.q || '', text: p.text || '', id: String(p.id || 'p' + i) }) : ({
    no: ++no,
    id: String(p.id || i + 1), type: p.type === 'short' ? 'short' : 'choice',
    q: p.q || '', choices: Array.isArray(p.choices) ? p.choices.slice(0, 8) : [],
    answer: p.answer, solution: p.solution || '', points: p.points || 0,
    space: Math.max(30, Math.min(500, +p.space || (p.type === 'short' ? 130 : 70)))
  }));
  j.title = j.title || '문제지'; j.subject = j.subject || '';
  return j;
};

/* ---------- 문항 마크업 (단위: --dp) ---------- */
function choiceCols(p) {
  const L = Math.max.apply(null, p.choices.map(c => String(c).replace(/\$[^$]*\$/g, 'xxx').length));
  return L <= 6 ? 5 : L <= 16 ? 3 : 1;
}
function blockHTML(spec, idx) {
  const p = spec.problems[idx];
  if (p.type === 'passage') return '<div class="qb qpass" data-qi="' + idx + '">' + (p.q ? '<div class="qp-h">' + tex(p.q) + '</div>' : '') + '<div class="qp-t">' + tex(p.text) + '</div></div>';
  let s = '<div class="qb" data-qi="' + idx + '">' +
    '<div class="qhead"><button type="button" class="qno" aria-label="' + p.no + '번 다시 볼 문제 표시">' + p.no + '<svg class="qmark" viewBox="0 0 40 40" aria-hidden="true"></svg><span class="qstar">' + Y.ic('star') + '</span></button>' +
    '<div class="qtext">' + tex(p.q) + (p.points ? ' <span class="qpts">[' + p.points + '점]</span>' : '') + '</div></div>';
  if (p.type === 'choice' && p.choices.length) {
    s += '<div class="qchoices c' + choiceCols(p) + '">' + p.choices.map((c, i) =>
      '<button type="button" class="qch" data-i="' + (i + 1) + '" aria-label="' + (i + 1) + '번 보기"><span class="qcn">' + CIRC[i] + '</span><span class="qct">' + tex(c) + '</span></button>').join('') + '</div>';
  }
  s += '<div class="qwork" style="height:calc(' + p.space + ' * var(--dp))">' + (p.type === 'short' ? '<div class="qansline"><span>답</span><b></b></div>' : '') + '</div>';
  return s + '</div>';
}
const qcount = spec => spec.problems.filter(p => p.type !== 'passage').length;
function headHTML(spec) {
  return '<div class="qhdr">' + (spec.subject ? '<div class="qsub">' + esc(spec.subject) + '</div>' : '') +
    '<h2 class="qtitle">' + esc(spec.title) + '</h2>' +
    '<div class="qmeta"><span>' + qcount(spec) + '문항</span><span class="qname">이름<b></b></span></div>' +
    '<div class="qscore" aria-live="polite"></div></div>';
}

/* ---------- 페이지 나누기 (한 번 계산해서 저장) ---------- */
function paginate(spec) {
  const box = h('div', { class: 'qmeasure paper', style: '--dp:1px;width:' + PW + 'px' });
  const lay = h('div', { class: 'qlayer' }); box.append(lay); document.body.append(box);
  lay.innerHTML = headHTML(spec);
  const headH = lay.firstChild.offsetHeight + 22;
  const hs = spec.problems.map((p, i) => { lay.innerHTML = blockHTML(spec, i); return lay.firstChild.offsetHeight; });
  box.remove();
  const avail = PH - PAD_T - PAD_B - 28, GAP = 26, pages = [];
  let cur = { qi: [], head: true }, used = headH;
  hs.forEach((bh, i) => {
    if (cur.qi.length && used + bh > avail) { pages.push(cur); cur = { qi: [], head: false }; used = 0; }
    cur.qi.push(i); used += bh + GAP;
  });
  pages.push(cur);
  return pages;
}

/* ---------- 라이브러리에 추가 ---------- */
Y.addQuiz = function (spec, opts) {
  opts = opts || {};
  const id = Y.uid(), now = Date.now();
  const meta = { id, title: spec.title, cover: opts.cover || 'slate', tpl: 'quiz', kind: 'quiz', created: now, updated: now, pageCount: 0, folder: opts.folder || null, fav: false, trashed: false };
  const pages = paginate(spec).map(p => ({ ratio: Y.RATIO, tpl: 'quiz', qi: p.qi, head: p.head, strokes: [], texts: [] }));
  meta.pageCount = pages.length;
  Y.store.set('yeobaek.v2.q.' + id, spec);
  Y.store.set('yeobaek.v2.n.' + id, { pages });
  Y.lib.notes.unshift(meta); Y.saveLib();
  return meta;
};
Y.quizDoc = function (n) {
  const spec = Y.store.get('yeobaek.v2.q.' + n.id, null);
  const data = Y.store.get('yeobaek.v2.n.' + n.id, null);
  let pages = data && data.pages;
  if (!spec) return null;
  if (!pages || !pages.length || !pages[0].qi) pages = paginate(spec).map(p => ({ tpl: 'quiz', qi: p.qi, head: p.head }));
  pages = pages.map(p => ({ ratio: Y.RATIO, tpl: 'quiz', qi: p.qi || [], head: !!p.head, strokes: p.strokes || [], texts: p.texts || [] }));
  const st = Y.store.get('yeobaek.v2.qs.' + n.id, null) || {};
  return { id: n.id, type: 'quiz', title: n.title, quiz: spec, qs: { sel: st.sel || {}, rev: st.rev || {}, grade: st.grade || {}, star: st.star || {}, focus: !!st.focus }, pages };
};
const saveState = doc => {
  Y.store.set('yeobaek.v2.qs.' + doc.id, doc.qs);
  const m = Y.lib.notes.find(n => n.id === doc.id);
  if (m) { const sc = score(doc); m.score = sc.graded ? sc.right + '/' + sc.total : null; m.updated = Date.now(); Y.saveLib(); }
  Y.flashSaved && Y.flashSaved();
};

/* ---------- 채점 ---------- */
function isRight(p, sel) {
  if (p.type !== 'choice') return null;
  return +sel === +p.answer;
}
function score(doc) {
  const ps = doc.quiz.problems; let right = 0, graded = 0;
  ps.forEach((p, i) => { const g = doc.qs.grade[i]; if (g && p.type !== 'passage') { graded++; if (g === 'o') right++; } });
  return { right, graded, total: qcount(doc.quiz) };
}
Y.quizScore = score;

/* ---------- 페이지 레이어 ---------- */
Y.quizLayer = function (doc, pg) {
  const lay = h('div', { class: 'qlayer' });
  lay.innerHTML = (pg.head ? headHTML(doc.quiz) : '') + '<div class="qcol">' + pg.qi.map(i => blockHTML(doc.quiz, i)).join('') + '</div>' +
    '<div class="qfoot">' + (doc.pages.indexOf(pg) + 1) + ' / ' + doc.pages.length + '</div>';
  lay.dataset.doc = doc.id; lay.classList.toggle('focus', !!doc.qs.focus);
  pg.qi.forEach(i => paintBlock(lay, doc, i, false));
  if (pg.head) paintScore(lay, doc, false);
  lay.addEventListener('click', e => { if (performance.now() - (Y._lpAt || 0) < 500) return; onClick(e, doc); });
  // 보기(버튼) 위에서 꾹 누르기 → 정답 보기
  let lpT = null, lp0 = null;
  lay.addEventListener('pointerdown', e => {
    const ch = e.target.closest('.qch'); if (!ch) return;
    lp0 = { x: e.clientX, y: e.clientY };
    clearTimeout(lpT); lpT = setTimeout(() => { Y._lpAt = performance.now(); Y.haptic(15); Y.quizLongPress(pg, lp0.x, lp0.y); }, 450);
  });
  lay.addEventListener('pointermove', e => { if (lp0 && Math.hypot(e.clientX - lp0.x, e.clientY - lp0.y) > 10) { clearTimeout(lpT); lp0 = null; } });
  ['pointerup', 'pointercancel', 'pointerleave'].forEach(t => lay.addEventListener(t, () => { clearTimeout(lpT); lp0 = null; }));
  return lay;
};
function blocks(doc, i) { return Y.$$('.qlayer[data-doc="' + doc.id + '"] .qb[data-qi="' + i + '"]'); }

function markSVG(kind) {
  // 손으로 그린 듯한 채점 표시: 동그라미(정답) / 빗금(오답)
  return kind === 'o'
    ? '<path class="draw" d="M21 4.5c-9 0-16.5 6.5-16.5 15.8 0 9.4 7.3 15.4 15.9 15.2 9.2-.2 15.6-6.8 15.1-15.6C35.1 11 28.6 4.3 18.8 5.6"/>'
    : '<path class="draw" d="M9 33C16 24 24 14 33 5"/>';
}
function paintBlock(root, doc, i, anim) {
  const qb = root.classList && root.classList.contains('qb') ? root : root.querySelector('.qb[data-qi="' + i + '"]');
  if (!qb) return;
  const p = doc.quiz.problems[i], sel = doc.qs.sel[i], rev = doc.qs.rev[i] || {}, g = doc.qs.grade[i];
  if (p.type === 'passage') return;
  qb.classList.toggle('starred', !!(doc.qs.star && doc.qs.star[i]));
  qb.querySelectorAll('.qch').forEach(b => {
    const n = +b.dataset.i;
    b.classList.toggle('on', +sel === n);
    b.classList.toggle('correct', !!rev.ans && n === +p.answer);
    b.setAttribute('aria-pressed', +sel === n ? 'true' : 'false');
  });
  qb.querySelectorAll('.qbtn').forEach(b => b.classList.toggle('open', !!rev[b.dataset.act]));
  qb.classList.toggle('graded', !!g); qb.classList.toggle('right', g === 'o'); qb.classList.toggle('wrong', g === 'x');
  const mk = qb.querySelector('.qmark');
  const want = g ? g : '';
  if (mk.dataset.k !== want) {
    mk.dataset.k = want; mk.innerHTML = want ? markSVG(want) : '';
    if (want && anim && !Y.reduced.matches) { mk.classList.remove('anim'); void mk.offsetWidth; mk.classList.add('anim'); }
  }
  const ap = p.type === 'short' && rev.ans ? '<div class="qrv">' + tex(String(p.answer)) + '</div>' : '';
  const line = qb.querySelector('.qansline b'); if (line) line.innerHTML = ap;
}
function paintScore(root, doc, anim) {
  const el = root.querySelector('.qscore'); if (!el) return;
  const sc = score(doc);
  if (!sc.graded) { el.innerHTML = ''; return; }
  el.innerHTML = '<span class="qs-n">' + sc.right + '</span><span class="qs-d">/' + sc.total + '</span><svg viewBox="0 0 120 20" aria-hidden="true"><path class="draw" d="M4 12c22-5 52-7 112-4"/></svg>';
  if (anim && !Y.reduced.matches) { el.classList.remove('anim'); void el.offsetWidth; el.classList.add('anim'); }
}
function refresh(doc, i, anim) {
  blocks(doc, i).forEach(b => paintBlock(b, doc, i, anim));
  Y.$$('.qlayer[data-doc="' + doc.id + '"]').forEach(l => paintScore(l, doc, anim));
}

/* ---------- 정답·풀이 카드 ---------- */
function closeCard() {
  const c = $('.qcard'); if (!c) return;
  c.classList.remove('in'); setTimeout(() => c.remove(), 220);
  Y.$$('.qb.peek').forEach(b => b.classList.remove('peek'));
}
Y.closeQuizCard = closeCard;
function openCard(qb, doc, i, at) {
  closeCard();
  const p = doc.quiz.problems[i], paper = qb.closest('.paper');
  const card = h('div', { class: 'qcard', role: 'dialog', 'aria-label': p.no + '번 정답과 풀이' });
  card.append(h('div', { class: 'qc-top' }, h('span', { class: 'qc-k', text: p.no + '번 정답' }),
    h('button', { type: 'button', class: 'qc-x', 'aria-label': '닫기', html: Y.ic('close'), onclick: closeCard })));
  const big = p.type === 'choice' ? (CIRC[+p.answer - 1] || p.answer) : p.answer;
  card.append(h('div', { class: 'qc-ans', html: p.type === 'choice' ? '<span class="qc-c">' + big + '</span>' + (p.choices[+p.answer - 1] != null ? '<span class="qc-cv">' + tex(p.choices[+p.answer - 1]) + '</span>' : '') : tex(String(big)) }));
  if (p.type === 'choice') {
    const sel = doc.qs.sel[i];
    card.append(h('div', { class: 'qc-res ' + (sel ? (isRight(p, sel) ? 'o' : 'x') : ''), text: sel ? (isRight(p, sel) ? '맞았어요' : '내 답 ' + CIRC[sel - 1] + ' · 틀렸어요') : '보기를 고르지 않았어요' }));
  } else {
    const g = doc.qs.grade[i];
    const og = v => h('button', { type: 'button', class: 'qc-g' + (g === v ? ' on' : ''), text: v === 'o' ? '맞았어요' : '틀렸어요', onclick: () => {
      if (doc.qs.grade[i] === v) delete doc.qs.grade[i]; else doc.qs.grade[i] = v;
      saveState(doc); refresh(doc, i, true); closeCard(); Y.haptic(10);
    } });
    card.append(h('div', { class: 'qc-self' }, h('span', { text: '내 답과 비교해 직접 채점하세요' }), h('div', { class: 'qc-gs' }, og('o'), og('x'))));
  }
  if (p.solution) card.append(h('div', { class: 'qc-solh', text: '풀이' }), h('div', { class: 'qc-sol', html: tex(p.solution) }));
  paper.append(card);
  // 위치: 누른 부분 바로 아래, 공간이 없으면 위
  const pr = paper.getBoundingClientRect(), dp = pr.width / PW, ch = card.offsetHeight;
  const below = at.bottom - pr.top + 8 * dp;
  const up = below + ch > pr.height - 12 * dp && at.top - pr.top - ch - 8 * dp > 8 * dp;
  card.style.top = Math.max(8 * dp, up ? at.top - pr.top - ch - 8 * dp : Math.min(below, pr.height - ch - 12 * dp)) + 'px';
  card.style.transformOrigin = '50% ' + (up ? '100%' : '0');
  requestAnimationFrame(() => card.classList.add('in'));
  qb.classList.add('peek');
  if (!doc.qs.rev[i]) doc.qs.rev[i] = {};
  if (!doc.qs.rev[i].ans) {
    doc.qs.rev[i].ans = 1;
    if (p.type === 'choice' && doc.qs.sel[i]) doc.qs.grade[i] = isRight(p, doc.qs.sel[i]) ? 'o' : 'x';
    saveState(doc); refresh(doc, i, true);
  }
}

/* 꾹 누르기: 엔진이 펜/손가락을 한자리에 0.45초 머물면 호출 */
Y.quizLongPress = function (pg, x, y) {
  const P = pg.pane, doc = P.doc; if (!doc || doc.type !== 'quiz' || !pg.paper) return false;
  const inside = (el, pad) => { if (!el) return null; const r = el.getBoundingClientRect(); return x >= r.left - pad && x <= r.right + pad && y >= r.top - pad && y <= r.bottom + pad ? r : null; };
  for (const qb of pg.paper.querySelectorAll('.qb:not(.qpass)')) {
    const i = +qb.dataset.qi, p = doc.quiz.problems[i];
    const zone = inside(qb.querySelector('.qhead'), 6) || inside(qb.querySelector('.qchoices'), 4) ||
      (p.type === 'short' ? inside(qb.querySelector('.qansline'), 18) : null);
    if (!zone) continue;
    const ring = h('div', { class: 'qripple', style: 'left:' + (x - pg.paper.getBoundingClientRect().left) + 'px;top:' + (y - pg.paper.getBoundingClientRect().top) + 'px' });
    pg.paper.append(ring); setTimeout(() => ring.remove(), 600);
    openCard(qb, doc, i, zone);
    return true;
  }
  return false;
};

/* ---------- 입력 ---------- */
function onClick(e, doc) {
  const ch = e.target.closest('.qch'), no = e.target.closest('.qno');
  if (no) {
    const i = +no.closest('.qb').dataset.qi; doc.qs.star = doc.qs.star || {};
    if (doc.qs.star[i]) delete doc.qs.star[i]; else doc.qs.star[i] = 1;
    saveState(doc); refresh(doc, i, false); Y.haptic(8);
    Y.toast(doc.qs.star[i] ? '다시 볼 문제로 표시했어요' : '표시를 지웠어요', { ms: 1400 });
    return;
  }
  if (!ch) return;
  e.stopPropagation(); Y.closePopover && Y.closePopover();
  const i = +ch.closest('.qb').dataset.qi;
  {
    const n = +ch.dataset.i;
    doc.qs.sel[i] = doc.qs.sel[i] === n ? undefined : n; if (!doc.qs.sel[i]) delete doc.qs.sel[i];
    const p = doc.quiz.problems[i];
    if (doc.qs.rev[i] && doc.qs.rev[i].ans && doc.qs.sel[i]) doc.qs.grade[i] = isRight(p, doc.qs.sel[i]) ? 'o' : 'x';
    else delete doc.qs.grade[i];
    closeCard(); saveState(doc); refresh(doc, i, true); Y.haptic(8);
  }
}
document.addEventListener('pointerdown', e => {
  if (!$('.qcard')) return;
  if (e.target.closest('.qcard') || performance.now() - (Y._lpAt || 0) < 400) return;
  closeCard();
}, true);

/* ---------- 문제지 전체 동작 (홈 탭) ---------- */
Y.gradeAll = function (doc) {
  let n = 0;
  doc.quiz.problems.forEach((p, i) => {
    if (p.type !== 'choice' || !doc.qs.sel[i]) return;
    doc.qs.grade[i] = isRight(p, doc.qs.sel[i]) ? 'o' : 'x';
    doc.qs.rev[i] = Object.assign(doc.qs.rev[i] || {}, { ans: 1 }); n++;
  });
  saveState(doc); doc.quiz.problems.forEach((p, i) => refresh(doc, i, true));
  const sc = score(doc);
  if (!n && !sc.graded) Y.toast('고른 보기가 없어요. 보기를 먼저 골라 주세요');
  else Y.toast(sc.right + ' / ' + sc.total + ' 맞았어요' + (sc.graded < sc.total ? ' · ' + (sc.total - sc.graded) + '문항은 아직 채점 전' : ''), { ms: 3500 });
  Y.haptic(15);
};
Y.resetQuiz = function (doc) {
  Y.confirm({ title: '문제지를 처음 상태로 돌릴까요?', text: '고른 보기, 채점, 펼친 정답이 지워져요. 필기는 그대로예요.', ok: '초기화', danger: true }).then(ok => {
    if (!ok) return;
    doc.qs = { sel: {}, rev: {}, grade: {}, star: doc.qs.star || {}, focus: false }; closeCard(); saveState(doc); Y.quizFocus(doc, false);
    doc.quiz.problems.forEach((p, i) => refresh(doc, i, false));
  });
};
Y.answerSheet = function (anchor, doc) {
  const grid = h('div', { class: 'qsheet' });
  doc.quiz.problems.forEach((p, i) => {
    if (p.type === 'passage') return;
    const g = doc.qs.grade[i];
    const cell = h('button', { type: 'button', class: 'qs-cell' + (g ? ' ' + g : ''), 'aria-label': (i + 1) + '번으로 이동' },
      h('span', { class: 'qs-i', text: p.no + (doc.qs.star && doc.qs.star[i] ? '★' : '') }), h('span', { class: 'qs-a', html: '?' }));
    let shown = false;
    cell.addEventListener('click', () => {
      if (!shown) { shown = true; cell.querySelector('.qs-a').innerHTML = p.type === 'choice' ? (CIRC[+p.answer - 1] || p.answer) : tex(String(p.answer)); cell.classList.add('shown'); return; }
      const b = blocks(doc, i).find(x => x.closest('.pane') === Y.active.root) || blocks(doc, i)[0];
      if (b) { Y.closePopover(); b.scrollIntoView({ behavior: 'smooth', block: 'center' }); }
    });
    grid.append(cell);
  });
  Y.popover(anchor, h('div', {}, h('div', { class: 'ptitle', text: '정답표 · 한 번 누르면 정답, 두 번 누르면 이동' }), grid), { width: 300 });
};

Y.quizFocus = function (doc, on) {
  doc.qs.focus = on == null ? !doc.qs.focus : !!on; saveState(doc);
  Y.$$('.qlayer[data-doc="' + doc.id + '"]').forEach(l => l.classList.toggle('focus', doc.qs.focus));
  if (on == null) Y.toast(doc.qs.focus ? '맞은 문제를 흐리게 했어요. 틀린 문제와 별표 문제에 집중하세요' : '모든 문제를 다시 보여줘요', { ms: 2200 });
};

/* ---------- 예제 문제지 ---------- */
Y.QUIZ_SAMPLE = {
  ybq: 1,
  title: '수학 Ⅰ·Ⅱ 맛보기',
  subject: '예제 문제지 · 고2 수학',
  problems: [
    { type: 'choice', points: 2, q: '$\\sqrt[3]{8}\\times 4^{\\frac{1}{2}}$ 의 값은?', choices: ['$2$', '$4$', '$6$', '$8$', '$16$'], answer: 2,
      solution: '$\\sqrt[3]{8}=2$, $\\;4^{\\frac{1}{2}}=\\sqrt{4}=2$ 이므로\n$$\\sqrt[3]{8}\\times 4^{\\frac{1}{2}} = 2\\times 2 = 4$$' },
    { type: 'choice', points: 2, q: '$\\log_3 54 - \\log_3 2$ 의 값은?', choices: ['$1$', '$2$', '$3$', '$4$', '$5$'], answer: 3,
      solution: '로그의 뺄셈은 진수의 나눗셈이에요.\n$$\\log_3 54-\\log_3 2=\\log_3 \\frac{54}{2}=\\log_3 27=3$$' },
    { type: 'choice', points: 3, q: '등차수열 $\\{a_n\\}$ 에서 $a_2=5,\\ a_5=14$ 일 때, $a_{10}$ 의 값은?', choices: ['$26$', '$27$', '$28$', '$29$', '$30$'], answer: 4,
      solution: '공차를 $d$ 라 하면 $a_5-a_2=3d=9$ 이므로 $d=3$, $a_1=a_2-d=2$\n$$a_{10}=a_1+9d=2+27=29$$' },
    { type: 'choice', points: 3, q: '$\\sin\\dfrac{\\pi}{6}+\\cos\\dfrac{\\pi}{3}+\\tan\\dfrac{\\pi}{4}$ 의 값은?', choices: ['$1$', '$\\frac{3}{2}$', '$2$', '$\\frac{5}{2}$', '$3$'], answer: 3,
      solution: '$\\sin\\frac{\\pi}{6}=\\frac12$, $\\;\\cos\\frac{\\pi}{3}=\\frac12$, $\\;\\tan\\frac{\\pi}{4}=1$\n$$\\frac12+\\frac12+1=2$$' },
    { type: 'choice', points: 3, q: '$\\displaystyle\\lim_{x\\to 2}\\frac{x^2-4}{x-2}$ 의 값은?', choices: ['$0$', '$2$', '$4$', '$8$', '존재하지 않는다'], answer: 3,
      solution: '분자를 인수분해하면 $x^2-4=(x-2)(x+2)$\n$$\\lim_{x\\to2}\\frac{(x-2)(x+2)}{x-2}=\\lim_{x\\to2}(x+2)=4$$' },
    { type: 'choice', points: 4, space: 110, q: '함수 $f(x)=x^3-3x^2+2$ 의 극솟값은?', choices: ['$-4$', '$-2$', '$0$', '$2$', '$4$'], answer: 2,
      solution: '$f\'(x)=3x^2-6x=3x(x-2)$ 이므로 $f\'(x)=0$ 에서 $x=0$ 또는 $x=2$\n$x=2$ 의 좌우에서 $f\'(x)$ 의 부호가 $-$ 에서 $+$ 로 바뀌므로 극소예요.\n$$f(2)=8-12+2=-2$$' },
    { type: 'short', points: 3, space: 130, q: '첫째항이 $3$, 공비가 $2$ 인 등비수열의 첫째항부터 제$5$항까지의 합을 구하시오.', answer: '93',
      solution: '등비수열의 합 공식 $S_n=\\dfrac{a(r^n-1)}{r-1}$ 에 대입하면\n$$S_5=\\frac{3(2^5-1)}{2-1}=3\\times31=93$$' },
    { type: 'short', points: 4, space: 150, q: '$\\displaystyle\\int_0^2 (3x^2+2x)\\,dx$ 의 값을 구하시오.', answer: '12',
      solution: '부정적분은 $x^3+x^2$ 이므로\n$$\\int_0^2(3x^2+2x)\\,dx=\\Big[x^3+x^2\\Big]_0^2=8+4=12$$' }
  ]
};
})(window.Y);
