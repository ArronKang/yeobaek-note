#!/usr/bin/env python3
"""쪽 그림에서 사각형을 잘라 .ybq 문항의 img 값(JSON)으로 출력.

사용: crop.py page-3.png x0 y0 x1 y1 [--dpi 110] [--max 900]
- 좌표는 쪽 그림 픽셀 기준
- 회색조로 바꾸고 흰 여백을 다듬은 뒤 PNG로 압축
- w, h는 여백 노트 페이지 너비 560 기준 크기 (원본 PDF에서의 실제 크기를 따름)
- 확인용으로 같은 폴더에 crop_preview.png를 남김
"""
import argparse, base64, io, json, os, sys
from PIL import Image, ImageChops, ImageOps

ap = argparse.ArgumentParser()
ap.add_argument('page'); ap.add_argument('box', nargs=4, type=int)
ap.add_argument('--dpi', type=float, default=110, help='pdftoppm -r 값')
ap.add_argument('--max', type=int, default=900, help='저장할 최대 픽셀 너비')
ap.add_argument('--color', action='store_true', help='색을 유지 (기본은 회색조)')
a = ap.parse_args()

im = Image.open(a.page)
x0, y0, x1, y1 = a.box
crop = im.crop((x0, y0, x1, y1)).convert('RGB')
# 둘레의 흰 여백 다듬기
bg = Image.new('RGB', crop.size, (255, 255, 255))
bb = ImageChops.difference(crop, bg).convert('L').point(lambda v: 255 if v > 18 else 0).getbbox()
if bb:
    pad = 6
    crop = crop.crop((max(0, bb[0] - pad), max(0, bb[1] - pad), min(crop.width, bb[2] + pad), min(crop.height, bb[3] + pad)))
if not a.color:
    crop = ImageOps.grayscale(crop)

# 실제 크기: 픽셀 → pt(1/72인치). A4 너비 595pt를 여백 페이지 560으로 맞춤
pt_w = crop.width / a.dpi * 72
pt_h = crop.height / a.dpi * 72
k = 560 / 595.28
w, h = pt_w * k, pt_h * k
if w > 472:  # 본문 폭을 넘지 않게
    h *= 472 / w; w = 472

if crop.width > a.max:
    crop = crop.resize((a.max, round(crop.height * a.max / crop.width)), Image.LANCZOS)
buf = io.BytesIO()
if a.color:
    crop.save(buf, 'PNG', optimize=True)
else:
    crop.quantize(16).save(buf, 'PNG', optimize=True)   # 16단계 회색이면 글자·선이 충분하고 작음
data = buf.getvalue()
crop.save(os.path.join(os.path.dirname(os.path.abspath(a.page)), 'crop_preview.png'))
print(json.dumps({'src': 'data:image/png;base64,' + base64.b64encode(data).decode(), 'w': round(w, 1), 'h': round(h, 1)}))
print(f'# {len(data) // 1024}KB, {crop.width}x{crop.height}px, 페이지 기준 {w:.0f}x{h:.0f}', file=sys.stderr)
