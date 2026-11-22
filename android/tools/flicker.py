"""화면 녹화에서 깜박임 찾기: 한 프레임만 튀었다가 돌아오는 곳 (i-1 ≈ i+1 인데 i만 다름)
사용: python -I flicker.py video.mp4 [y0 y1]  (상태 표시줄·시계 영역은 빼고 봄)"""
import sys, cv2, numpy as np
cap = cv2.VideoCapture(sys.argv[1]); fr = []
y0 = int(sys.argv[2]) if len(sys.argv) > 2 else 60
y1 = int(sys.argv[3]) if len(sys.argv) > 3 else 10000
while True:
    ok, f = cap.read()
    if not ok: break
    g = cv2.cvtColor(f, cv2.COLOR_BGR2GRAY)[y0:y1].astype(np.int16)
    fr.append(cv2.resize(g, (g.shape[1] // 2, g.shape[0] // 2)))
hits = []
for i in range(1, len(fr) - 1):
    a, b, c = fr[i - 1], fr[i], fr[i + 1]
    dab = np.abs(a - b) > 40; dbc = np.abs(b - c) > 40; dac = np.abs(a - c) > 40
    n_ab, n_bc, n_ac = int(dab.sum()), int(dbc.sum()), int(dac.sum())
    # i가 앞뒤와 크게 다르고, 앞뒤끼리는 거의 같음 → 한 프레임 튐
    if n_ab > 300 and n_bc > 300 and n_ac < 0.25 * min(n_ab, n_bc):
        ys, xs = np.nonzero(dab & dbc)
        box = (int(xs.min()) * 2, int(ys.min()) * 2 + y0, int(xs.max()) * 2, int(ys.max()) * 2 + y0) if len(xs) else None
        hits.append((i, n_ab, n_bc, n_ac, box))
print('frames', len(fr), 'flicker', len(hits))
for h in hits[:15]: print(' frame', h[0], 'changed', h[1], h[2], 'prev-vs-next', h[3], 'box', h[4])
