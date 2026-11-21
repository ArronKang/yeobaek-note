/* 여백 노트 — 웹 흔적 없애기: 뒤로가기, 꾹 누름 메뉴, 화면 꺼짐 방지 */
(function () {
  'use strict';
  const $ = Y.$;

  /* ---------- 안드로이드 뒤로가기 ----------
     history에 '지킴이' 칸을 하나 두고, 뒤로가기가 오면 앱 안에서 한 단계만 되돌린 뒤 다시 채움.
     순서: 팝오버 → 정답 카드 → 모달 → 편집 화면 → 사이드바/선택 → (라이브러리면) 앱 나가기 */
  const editorOpen = () => !$('#editor').hidden && $('#editor').classList.contains('shown');
  function stepBack() {
    if (Y._pop) { Y.closePopover(); return true; }
    if (Y.closeCard && Y.closeCard()) return true;
    if (Y._modals.length) { Y._modals[Y._modals.length - 1](); return true; }
    if (editorOpen()) { Y.closeEditor(); return true; }
    if (Y.libBack && Y.libBack()) return true;
    return false;
  }
  function guard() { if (!history.state || !history.state.yg) history.pushState({ yg: 1 }, ''); }
  history.replaceState({ yb: 1 }, '');
  // 크롬은 터치 전에 넣은 칸을 뒤로가기에서 건너뛰므로 첫 터치 때 넣음
  document.addEventListener('pointerdown', guard, { once: true, capture: true });
  window.addEventListener('popstate', () => {
    if (stepBack()) { Y.haptic(6); guard(); return; }
    // 라이브러리 첫 화면: 지킴이를 비워 두면 다음 뒤로가기에 앱이 닫힘. 다른 걸 만지면 다시 지킴
    Y.toast('한 번 더 누르면 닫혀요');
    document.addEventListener('pointerdown', guard, { once: true, capture: true });
  });

  /* ---------- 브라우저 기본 메뉴 막기 ----------
     글자 입력칸만 빼고 꾹 누름 메뉴·이미지 끌기·두 번 눌러 확대를 막음 */
  const editable = t => t && t.closest && t.closest('input, textarea, [contenteditable="true"], [contenteditable=""]');
  document.addEventListener('contextmenu', e => { if (!editable(e.target)) e.preventDefault(); });
  document.addEventListener('dragstart', e => { if (!editable(e.target)) e.preventDefault(); });
  document.addEventListener('selectstart', e => { if (!editable(e.target)) e.preventDefault(); });
  document.addEventListener('dblclick', e => { if (!editable(e.target)) e.preventDefault(); }, { passive: false });
  // 두 손가락 바깥(브라우저) 확대 막기. 페이지 핀치 줌은 엔진이 직접 처리
  document.addEventListener('gesturestart', e => e.preventDefault());
  document.addEventListener('touchmove', e => { if (e.touches.length > 1 && !e.target.closest('.scroll')) e.preventDefault(); }, { passive: false });

  /* ---------- 공부하는 동안 화면 안 꺼지게 ---------- */
  let lock = null;
  async function wake() {
    if (!('wakeLock' in navigator) || lock || document.visibilityState !== 'visible' || !editorOpen()) return;
    try { lock = await navigator.wakeLock.request('screen'); lock.addEventListener('release', () => { lock = null; }); } catch (e) { lock = null; }
  }
  function sleep() { if (lock) { lock.release().catch(() => {}); lock = null; } }
  new MutationObserver(() => (editorOpen() ? wake() : sleep())).observe($('#editor'), { attributes: true, attributeFilter: ['class', 'hidden'] });
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') wake(); });
})();
