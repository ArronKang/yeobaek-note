/* 고무줄: 맨 끝에서 더 당기면 늘어나고, 놓으면 용수철처럼 돌아옴
   - 노트 맨 아래에서 당기면 새 페이지의 틀이 아래에서 딸려 나옴
     + 가 점점 커지고 둘레 고리가 차오름 → 다 차면 틀이 진해지고 톡 (진동)
     → 놓으면 그 자리에 페이지가 생김. 덜 당기고 놓으면 다시 들어감
   - 맨 위, PDF·문제지 맨 아래는 늘어났다 돌아오기만 */
(function (Y) {
'use strict';
const h = Y.h;
const ARM = 150;                       // 이만큼(화면에 보이는 길이) 당기면 페이지 추가 준비
const RING = 2 * Math.PI * 17;

// 고무줄 곡선: 처음엔 손가락을 잘 따라오다가 멀리 갈수록 뻑뻑해짐
function band(raw, D) { const a = Math.abs(raw); return Math.sign(raw) * D * (1 - 1 / (a * 0.55 / D + 1)); }

function ui(P) {
  if (P.pullUi && P.pullUi.root.isConnected) return P.pullUi;
  const frame = h('div', { class: 'pfr' });
  const plus = h('div', { class: 'pplus', html: '<svg viewBox="0 0 40 40" aria-hidden="true"><circle class="trk" cx="20" cy="20" r="17"/><circle class="arc" cx="20" cy="20" r="17"/><path class="pl" d="M20 13v14M13 20h14"/></svg>' });
  const root = h('div', { class: 'pull', 'aria-hidden': 'true' }, frame, plus);
  P.root.append(root);
  return (P.pullUi = { root, frame, plus });
}

function paint(P, v) {
  P.pages.style.transform = v ? 'translateY(' + (-v).toFixed(1) + 'px)' : '';
  const ed = document.getElementById('editor'); if (ed) ed.classList.toggle('pulling', !!v);
  const canAdd = P.doc && P.doc.type === 'note' && v > 0 && !P.bouncing;
  if (!canAdd) { if (P.pullUi) P.pullUi.root.style.display = 'none'; P.pullArmed = false; return; }
  const U = ui(P); U.root.style.display = '';
  const k = Math.min(1, v / ARM), pad = parseFloat(getComputedStyle(P.scroll).paddingBottom) || 96;
  // 마지막 페이지 아래로 드러난 공간에 새 페이지 틀이 아래에서 올라옴
  const room = pad + v - 24;
  U.frame.style.width = P.W + 'px';
  U.frame.style.height = Math.max(0, room) + 'px';
  U.frame.style.opacity = Math.min(1, k * 1.6);
  U.plus.style.bottom = Math.max(8, room / 2 - 20) + 'px';
  U.plus.style.transform = 'translateX(-50%) scale(' + (0.45 + 0.55 * k).toFixed(3) + ')';
  U.plus.querySelector('.arc').style.strokeDashoffset = (RING * (1 - k)).toFixed(1);
  const armed = k >= 1;
  if (armed !== P.pullArmed) { P.pullArmed = armed; U.root.classList.toggle('armed', armed); if (armed) Y.haptic(12); }
}

Y.pull = function (P, raw) {
  stopSpring(P); P.bouncing = false;
  P.pullRaw = raw;
  P.pullV = raw ? band(raw, P.scroll.clientHeight * 0.6) : 0;
  paint(P, P.pullV);
};

// 용수철: 살짝 지나쳤다가 제자리 (질량 1, 강성 260, 감쇠 24)
function spring(P, x, v, done) {
  stopSpring(P);
  let last = performance.now();
  const step = now => {
    const dt = Math.min(32, now - last) / 1000; last = now;
    const a = -260 * x - 24 * v; v += a * dt; x += v * dt;
    if (Math.abs(x) < 0.4 && Math.abs(v) < 4) { P.pullV = 0; paint(P, 0); P.springRaf = 0; done && done(); return; }
    P.pullV = x; paint(P, x);
    P.springRaf = requestAnimationFrame(step);
  };
  P.springRaf = requestAnimationFrame(step);
}
function stopSpring(P) { if (P.springRaf) { cancelAnimationFrame(P.springRaf); P.springRaf = 0; } }

Y.pullRelease = function (P) {
  const v = P.pullV || 0; P.pullRaw = 0;
  if (P.pullArmed && P.doc && P.doc.type === 'note') {
    // 틀이 있던 자리에 진짜 페이지가 생김
    const U = P.pullUi, top = U.frame.getBoundingClientRect().top;
    P.pullArmed = false; U.root.classList.remove('armed');
    paint(P, 0);
    const pg = Y.addPage(P, null, { stay: true });
    const nt = pg.el.getBoundingClientRect().top;
    P.scroll.scrollTop += nt - top;
    pg.el.classList.add('born'); setTimeout(() => pg.el.classList.remove('born'), 500);
    Y.haptic(10);
    return;
  }
  if (Y.reduced.matches) { P.pullV = 0; paint(P, 0); return; }
  spring(P, v, 0);
};

// 관성으로 벽에 부딪힘: 그 속도로 눌렸다가 돌아옴 (페이지 추가는 안 함)
Y.bounce = function (P, push) {
  if (Y.reduced.matches || P.scrolling) return;
  P.pullArmed = false; P.bouncing = true;
  spring(P, 0, push * 9, () => { P.bouncing = false; });
};
})(window.Y);
