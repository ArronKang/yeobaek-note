#!/usr/bin/env python3
""".ybq 문제지 검사. 오류가 있으면 종료 코드 1.

사용: check.py file.ybq
"""
import json, re, sys, collections

path = sys.argv[1]
raw = open(path, 'rb').read()
err, warn = [], []
try:
    q = json.loads(raw.decode('utf-8'))
except Exception as e:
    print('오류: JSON을 읽을 수 없어요:', e); sys.exit(1)

if q.get('ybq') != 1: warn.append('"ybq": 1 이 없어요')
if not q.get('title'): warn.append('title이 비어 있어요')
for k in ('title', 'subject'):
    if '·' in str(q.get(k, '')): err.append(f'{k}에 가운뎃점(·)이 있어요')
ps = q.get('problems')
if not isinstance(ps, list) or not ps: print('오류: problems가 없어요'); sys.exit(1)

def texts(p):
    for k in ('q', 'text', 'solution'):
        if isinstance(p.get(k), str): yield k, p[k]
    for i, c in enumerate(p.get('choices') or []):
        yield f'choices[{i}]', str(c)

no, answers, imgs = 0, [], 0
for i, p in enumerate(ps):
    t = p.get('type')
    tag = f'#{i}' + ('' if t == 'passage' else f' ({no + 1}번)')
    if t not in ('passage', 'choice', 'short'): err.append(f'{tag}: type이 passage/choice/short가 아니에요'); continue
    for k, s in texts(p):
        body = s.replace('$$', '')
        if body.count('$') % 2: err.append(f'{tag} {k}: $ 짝이 안 맞아요')
        if re.search(r'(?<!\\)\\(?![a-zA-Z{}\[\]()|,;:!. \\%&#_^$])', s): warn.append(f'{tag} {k}: 수상한 역슬래시')
    if 'img' in p and p['img'] is not None:
        m = p['img']; imgs += 1
        if not (isinstance(m, dict) and str(m.get('src', '')).startswith('data:image/') and m.get('w', 0) > 0 and m.get('h', 0) > 0):
            err.append(f'{tag}: img는 {{src: data:image/..., w, h}} 형식이어야 해요')
        elif len(m['src']) > 200_000: warn.append(f'{tag}: 그림이 커요 ({len(m["src"]) // 1024}KB)')
    if t == 'passage':
        if not p.get('text') and not p.get('img'): warn.append(f'{tag}: 지문이 비어 있어요')
        continue
    no += 1
    if not p.get('q') and not p.get('img'): err.append(f'{tag}: 문제(q)가 비어 있어요')
    if t == 'choice':
        ch = p.get('choices') or []
        if not 2 <= len(ch) <= 8: err.append(f'{tag}: 보기는 2~8개여야 해요 ({len(ch)}개)')
        a = p.get('answer')
        if not isinstance(a, int) or not 1 <= a <= len(ch): err.append(f'{tag}: answer는 1~{len(ch)} 사이 숫자여야 해요 (지금 {a!r})')
        else: answers.append(a)
    else:
        if p.get('answer') in (None, ''): err.append(f'{tag}: 단답 정답(answer)이 없어요')
    if not p.get('solution'): warn.append(f'{tag}: 풀이가 없어요')

size = len(raw)
if size > 2_500_000: err.append(f'파일이 너무 커요 ({size // 1024}KB). 그림을 줄이세요')
elif size > 1_500_000: warn.append(f'파일이 커요 ({size // 1024}KB)')
if answers:
    c = collections.Counter(answers)
    top, n = c.most_common(1)[0]
    if len(answers) >= 8 and n / len(answers) > 0.45: warn.append(f'정답이 {top}번에 몰려 있어요 ({n}/{len(answers)}) — 원본이 그렇다면 괜찮아요')

print(f'문항 {no}개, 지문 {sum(1 for p in ps if p.get("type") == "passage")}개, 그림 {imgs}개, {size // 1024}KB')
if answers: print('객관식 정답 분포:', dict(sorted(collections.Counter(answers).items())))
for w in warn: print('경고:', w)
for e in err: print('오류:', e)
sys.exit(1 if err else 0)
