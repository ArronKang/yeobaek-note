/* 여백 노트 서비스 워커 — 한 번 열면 인터넷 없이도 실행 */
const CACHE = 'yeobaek-202611211517';
const FILES = ['./', 'icons/apple-touch-icon.png', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/maskable-512.png', 'index.html', 'js/core.js', 'js/editor.js', 'js/engine.js', 'js/export.js', 'js/library.js', 'js/native.js', 'js/pdf.js', 'js/quiz.js', 'js/samples.js', 'js/templates.js', 'manifest.webmanifest', 'style.css', 'vendor/pretendard/pretendardvariable-dynamic-subset.css', 'vendor/katex/fonts/KaTeX_AMS-Regular.woff2', 'vendor/katex/fonts/KaTeX_Caligraphic-Bold.woff2', 'vendor/katex/fonts/KaTeX_Caligraphic-Regular.woff2', 'vendor/katex/fonts/KaTeX_Fraktur-Bold.woff2', 'vendor/katex/fonts/KaTeX_Fraktur-Regular.woff2', 'vendor/katex/fonts/KaTeX_Main-Bold.woff2', 'vendor/katex/fonts/KaTeX_Main-BoldItalic.woff2', 'vendor/katex/fonts/KaTeX_Main-Italic.woff2', 'vendor/katex/fonts/KaTeX_Main-Regular.woff2', 'vendor/katex/fonts/KaTeX_Math-BoldItalic.woff2', 'vendor/katex/fonts/KaTeX_Math-Italic.woff2', 'vendor/katex/fonts/KaTeX_SansSerif-Bold.woff2', 'vendor/katex/fonts/KaTeX_SansSerif-Italic.woff2', 'vendor/katex/fonts/KaTeX_SansSerif-Regular.woff2', 'vendor/katex/fonts/KaTeX_Script-Regular.woff2', 'vendor/katex/fonts/KaTeX_Size1-Regular.woff2', 'vendor/katex/fonts/KaTeX_Size2-Regular.woff2', 'vendor/katex/fonts/KaTeX_Size3-Regular.woff2', 'vendor/katex/fonts/KaTeX_Size4-Regular.woff2', 'vendor/katex/fonts/KaTeX_Typewriter-Regular.woff2', 'vendor/katex/katex.min.css', 'vendor/katex/katex.min.js', 'vendor/html2canvas/html2canvas.min.js', 'vendor/pdflib/pdf-lib.min.js', 'vendor/pdfjs/pdf.min.js', 'vendor/pdfjs/pdf.worker.min.js'];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE && k !== 'yeobaek-fonts').map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request; if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.hostname.endsWith('googleapis.com') || url.hostname.endsWith('gstatic.com')) {
    // 글꼴: 저장해 둔 것 먼저, 뒤에서 새로 받아 갱신
    e.respondWith(caches.open(CACHE + '-fonts').then(c => c.match(req).then(hit => {
      const net = fetch(req).then(r => { c.put(req, r.clone()); return r; }).catch(() => hit);
      return hit || net;
    })));
    return;
  }
  if (url.origin !== location.origin) return;
  if (url.pathname.includes('/vendor/pretendard/woff2')) {
    // 한글 글꼴 조각: 한 번 받으면 업데이트해도 계속 씀 (오프라인에서도 같은 글꼴)
    e.respondWith(caches.open('yeobaek-fonts').then(c => c.match(req).then(hit => hit || fetch(req).then(r => { if (r.ok) c.put(req, r.clone()); return r; }))));
    return;
  }
  e.respondWith(caches.match(req, { ignoreSearch: true }).then(hit => hit || fetch(req).then(r => {
    if (r.ok) { const cp = r.clone(); caches.open(CACHE).then(c => c.put(req, cp)); }
    return r;
  }).catch(() => req.mode === 'navigate' ? caches.match('./index.html') : undefined)));
});
