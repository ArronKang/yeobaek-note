"""원본 PDF를 그대로 두고 답안 정보 층을 얹은 .ybq 2 만들기.

    python make_v2.py input.pdf answers.json out.ybq [--boxes boxes.json] [--preview DIR]

answers.json: {"title": "...", "subject": "...", "answers": {"1": "3", "5": "광합성"}, "solutions": {"1": "..."}}
  - 객관식 정답은 1부터 세는 보기 번호, 단답은 글자 그대로. 번호는 시험지에 인쇄된 문항 번호.
boxes.json (글자 층이 없는 스캔본일 때만): {"dpi": 110, "problems": [{"no": 1, "page": 1, "num": [x0,y0,x1,y1], "choices": [[...],...]}]}
  - 쪽은 1부터, 좌표는 pdftoppm -r dpi 그림의 픽셀.

찾는 방법 (글자 층이 있을 때):
  - 문항 번호: 줄의 첫 낱말이 "12." "12)" "12" 처럼 숫자이고, 1, 2, 3 … 순서를 이루는 것 (두 단이면 왼쪽 단 먼저)
  - 보기 칸: ①~⑧ 글자. 문항 영역(이 번호부터 같은 단의 다음 번호 전까지) 안에서 같은 숫자가 여러 번이면 마지막 것 (지문 속 밑줄 번호보다 아래 선택지)
  - 결과 좌표는 쪽 폭·높이에 대한 비율
"""
import json, re, sys, zipfile, argparse, os
import fitz  # PyMuPDF

# 동그라미 숫자는 PDF 글꼴마다 다른 문자로 나옴: ①(2460) ➀(2780) ➊(278A) ❶(2776) ⓵(24F5)
CIRC_RANGES = [(0x2460, 20), (0x2780, 10), (0x278A, 10), (0x2776, 10), (0x24F5, 10)]
def circ_no(ch):
    o = ord(ch)
    for base, n in CIRC_RANGES:
        if base <= o < base + n:
            return o - base + 1
    return 0


def page_items(page):
    W, H = page.rect.width, page.rect.height
    nums, circs = [], []
    d = page.get_text("rawdict")
    for b in d["blocks"]:
        for l in b.get("lines", []):
            chars = [c for s in l["spans"] for c in s["chars"]]
            if not chars:
                continue
            # 줄 첫 낱말
            text = "".join(c["c"] for c in chars)
            m = re.match(r"\s*(\d{1,3})\s*([.)]|\s|$)", text)
            if m:
                k = len(m.group(0).rstrip())
                cs = [c for c in chars[: max(1, k)] if c["c"].strip()]
                if cs:
                    x0 = min(c["bbox"][0] for c in cs); y0 = min(c["bbox"][1] for c in cs)
                    x1 = max(c["bbox"][2] for c in cs); y1 = max(c["bbox"][3] for c in cs)
                    nums.append({"no": int(m.group(1)), "box": [x0, y0, x1, y1]})
            for c in chars:
                k = circ_no(c["c"])
                if 0 < k <= 8:
                    circs.append({"n": k, "box": list(c["bbox"])})
    return W, H, nums, circs


def detect(doc):
    pages = [page_items(p) for p in doc]
    # 두 단인지: 번호 후보의 절반 넘게가 오른쪽 절반에도 있으면
    def col(W, b):
        return 0 if b[0] < W * 0.5 else 1
    cands = []
    for pi, (W, H, nums, _) in enumerate(pages):
        for n in nums:
            cands.append((pi, col(W, n["box"]), n["box"][1], n))
    cands.sort(key=lambda t: (t[0], t[1], t[2]))
    # 1, 2, 3 … 순서를 이루는 것만 (가장 긴 증가 수열을 앞에서부터 탐욕적으로)
    probs, want = [], 1
    for pi, c, y, n in cands:
        if n["no"] == want:
            probs.append({"no": n["no"], "page": pi, "col": c, "num": n["box"]})
            want += 1
    # 문항 영역: 같은 쪽·같은 단의 다음 번호 전까지
    for i, p in enumerate(probs):
        W, H, _, circs = pages[p["page"]]
        nxt = next((q for q in probs[i + 1:] if q["page"] == p["page"] and q["col"] == p["col"]), None)
        y0 = p["num"][1] - 2
        y1 = nxt["num"][1] - 2 if nxt else H
        x0, x1 = (0, W * 0.5) if p["col"] == 0 else (W * 0.5, W)
        two = any(q["col"] == 1 for q in probs if q["page"] == p["page"])
        if not two:
            x0, x1 = 0, W
        mine = [c for c in circs if x0 <= c["box"][0] < x1 and y0 <= c["box"][1] < y1]
        last = {}
        for c in mine:
            last[c["n"]] = c["box"]          # 같은 숫자는 마지막 것
        ks = sorted(last)
        # 1부터 이어지는 것만
        seq = []
        for k in ks:
            if k == len(seq) + 1:
                seq.append(last[k])
        p["choices"] = seq
        p["area"] = [x0, y0, x1, y1]
    return pages, probs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pdf"); ap.add_argument("answers"); ap.add_argument("out")
    ap.add_argument("--boxes"); ap.add_argument("--preview")
    a = ap.parse_args()
    doc = fitz.open(a.pdf)
    ans = json.load(open(a.answers, encoding="utf-8"))
    A = {str(k): str(v) for k, v in ans.get("answers", {}).items()}
    S = {str(k): str(v) for k, v in ans.get("solutions", {}).items()}
    out = []
    if a.boxes:
        bx = json.load(open(a.boxes, encoding="utf-8")); k = 72.0 / bx.get("dpi", 110)
        for p in bx["problems"]:
            pg = doc[p["page"] - 1]; W, H = pg.rect.width, pg.rect.height
            f = lambda r: [round(r[0] * k / W, 5), round(r[1] * k / H, 5), round(r[2] * k / W, 5), round(r[3] * k / H, 5)]
            ch = [f(c) for c in p.get("choices", [])]
            out.append({"no": p["no"], "page": p["page"] - 1, "num": f(p["num"]), "type": "choice" if ch else "short", "choices": ch,
                        "answer": A.get(str(p["no"]), ""), "solution": S.get(str(p["no"]), "")})
    else:
        pages, probs = detect(doc)
        for p in probs:
            W, H = pages[p["page"]][0], pages[p["page"]][1]
            f = lambda r: [round(r[0] / W, 5), round(r[1] / H, 5), round(r[2] / W, 5), round(r[3] / H, 5)]
            ch = [f(c) for c in p["choices"]]
            out.append({"no": p["no"], "page": p["page"], "num": f(p["num"]), "type": "choice" if ch else "short", "choices": ch,
                        "answer": A.get(str(p["no"]), ""), "solution": S.get(str(p["no"]), ""), "area": f(p["area"])})
    q = {"ybq": 2, "title": ans.get("title", os.path.splitext(os.path.basename(a.pdf))[0]), "subject": ans.get("subject", ""), "problems": out}
    with zipfile.ZipFile(a.out, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(a.pdf, "original.pdf")
        z.writestr("quiz.json", json.dumps(q, ensure_ascii=False, indent=1))
    miss = [p["no"] for p in out if not p["answer"]]
    print(f"문항 {len(out)}개 (객관식 {sum(1 for p in out if p['type']=='choice')}), 정답 없는 문항: {miss or '없음'}")
    for p in out:
        if p["type"] == "choice" and len(p["choices"]) < 4:
            print(f"  확인 필요: {p['no']}번 보기 칸 {len(p['choices'])}개")
    if a.preview:
        # 쪽마다 번호 칸(파랑)·보기 칸(초록)을 그린 그림: 눈으로 확인용
        os.makedirs(a.preview, exist_ok=True)
        for pi, pg in enumerate(doc):
            W, H = pg.rect.width, pg.rect.height
            for p in out:
                if p["page"] != pi:
                    continue
                r = p["num"]; pg.draw_rect(fitz.Rect(r[0] * W, r[1] * H, r[2] * W, r[3] * H), color=(0, .5, 1), width=1.2)
                for c in p["choices"]:
                    pg.draw_rect(fitz.Rect(c[0] * W, c[1] * H, c[2] * W, c[3] * H), color=(0, .7, .2), width=1)
            pg.get_pixmap(dpi=90).save(os.path.join(a.preview, f"p{pi + 1}.png"))


if __name__ == "__main__":
    main()
