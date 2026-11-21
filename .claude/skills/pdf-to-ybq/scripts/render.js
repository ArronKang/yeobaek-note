// 저장소의 여백 앱을 그대로 열어 .ybq를 가져오고 쪽마다 스크린샷을 남김
// 사용: node render.js file.ybq outdir   (저장소 어디서 실행해도 됨)
const path = require('path'), fs = require('fs');
const { chromium } = require('playwright');
(async () => {
  const [file, out = 'shots'] = process.argv.slice(2);
  const root = path.resolve(__dirname, '../../../..');
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch();
  const pg = await b.newPage({ viewport: { width: 800, height: 1180 }, deviceScaleFactor: 1 });
  const errs = []; pg.on('pageerror', e => errs.push(e.message));
  await pg.goto('file://' + path.join(root, 'index.html')); await pg.waitForTimeout(1000);
  await pg.setInputFiles('#libfile', path.resolve(file));
  await pg.waitForFunction(() => Y.active && Y.active.doc && Y.active.doc.type === 'quiz', null, { timeout: 20000 });
  await pg.waitForTimeout(1500);
  await pg.click('#btn-full'); await pg.waitForTimeout(300);   // 상단 바 접기
  const n = await pg.evaluate(() => Y.active.doc.pages.length);
  for (let i = 0; i < n; i++) {
    await pg.evaluate(i => Y.active.doc.pages[i].el.scrollIntoView({ block: 'start' }), i);
    await pg.waitForTimeout(400);
    const el = await pg.evaluateHandle(i => Y.active.doc.pages[i].el, i);
    await el.asElement().screenshot({ path: path.join(out, 'p' + String(i + 1).padStart(2, '0') + '.png') });
  }
  const info = await pg.evaluate(() => {
    const d = Y.active.doc; return { pages: d.pages.length, problems: d.quiz.problems.filter(p => p.type !== 'passage').length,
      overflow: [...document.querySelectorAll('.qcol.fit')].length, katexErr: document.querySelectorAll('.katex-error').length };
  });
  console.log(`쪽 ${info.pages}, 문항 ${info.problems}, 넘쳐서 줄인 쪽 ${info.overflow}, 수식 오류 ${info.katexErr}`);
  if (errs.length) console.log('오류:', errs);
  console.log('스크린샷:', path.resolve(out));
  await b.close();
})();
