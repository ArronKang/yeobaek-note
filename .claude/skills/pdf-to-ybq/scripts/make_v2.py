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


def page_section(page, rx):
    """쪽 위쪽 몇 줄에서 단원 이름 (예: 3과 본문 1, 부교재 3-3, 10모 20번)"""
    if rx is None:
        return None
    for line in page.get_text().split(chr(10))[:6]:
        m = rx.match(line.strip())
        if m:
            return m.group(0)
    return None


def detect(doc, sec_rx=None):
    pages = [page_items(p) for p in doc]
    secs = [page_section(p, sec_rx) for p in doc]
    def col(W, b):
        return 0 if b[0] < W * 0.5 else 1
    two = [any(col(W, n["box"]) == 1 for n in nums) and any(col(W, n["box"]) == 0 for n in nums) for (W, H, nums, _) in pages]
    cands = []
    for pi, (W, H, nums, _) in enumerate(pages):
        for n in nums:
            cands.append((pi, col(W, n["box"]) if two[pi] else 0, n["box"][1], n))
    cands.sort(key=lambda t: (t[0], t[1], t[2]))
    # 1, 2, 3 … 순서. 단원 이름이 바뀌거나(있으면) 1번이 다시 나오면 새 단원
    probs, want, sec, k = [], 1, None, 0
    seen = set()
    for pi, c, y, n in cands:
        ps = secs[pi]
        if ps is not None and ps != sec:
            # 이미 끝난 단원 이름이 다시 나오면(맨 뒤 답안지 등) 건너뜀
            if ps in seen:
                sec = "__skip__"; continue
            sec = ps; want = 1; k += 1; seen.add(ps)
        if sec_rx is not None and (sec is None or sec == "__skip__"):
            continue
        if n["no"] == want:
            pass
        elif n["no"] == 1 and want > 1 and sec_rx is None:
            k += 1; want = 1
        else:
            continue
        probs.append({"no": n["no"], "page": pi, "col": c, "num": n["box"], "sec": sec if sec is not None else (str(k) if k > 1 else "")})
        want += 1
    # 문항 영역: 번호부터 같은 쪽·같은 단의 다음 번호 전까지. 단·쪽의 마지막 문항은 다음 단(쪽) 맨 위 번호 전까지 이어짐
    def regions(i, p):
        W, H = pages[p["page"]][0], pages[p["page"]][1]
        x0, x1 = (0, W) if not two[p["page"]] else ((0, W * .5) if p["col"] == 0 else (W * .5, W))
        nxt = probs[i + 1] if i + 1 < len(probs) else None
        same = nxt is not None and nxt["page"] == p["page"] and nxt["col"] == p["col"]
        out = [(p["page"], x0, p["num"][1] - 2, x1, nxt["num"][1] - 2 if same else H)]
        if not same and nxt is not None and nxt["sec"] == p["sec"]:
            # 다음 문항이 다른 단·쪽이면 그 단 맨 위(번호 전)까지도
            W2, H2 = pages[nxt["page"]][0], pages[nxt["page"]][1]
            a0, a1 = (0, W2) if not two[nxt["page"]] else ((0, W2 * .5) if nxt["col"] == 0 else (W2 * .5, W2))
            out.append((nxt["page"], a0, 0, a1, nxt["num"][1] - 2))
        return out
    for i, p in enumerate(probs):
        last = {}
        rs = regions(i, p)
        for (pg, x0, y0, x1, y1) in rs:
            for c in pages[pg][3]:
                if x0 <= c["box"][0] < x1 and y0 <= c["box"][1] < y1:
                    last[c["n"]] = (pg, c["box"])          # 같은 숫자는 마지막 것
        seq = []
        for kk in sorted(last):
            if kk == len(seq) + 1:
                seq.append(last[kk])
        # 보기가 다른 쪽에 있으면 그 쪽 기준 (앱은 문항 쪽만 봄): 같은 쪽 것만 남김
        p["choices"] = [b for (pg, b) in seq if pg == p["page"]] if all(pg == p["page"] for (pg, b) in seq) else [b for (pg, b) in seq]
        p["choice_page"] = seq[0][0] if seq else p["page"]
        p["area"] = list(rs[0][1:])
    return pages, probs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pdf"); ap.add_argument("answers"); ap.add_argument("out")
    ap.add_argument("--boxes"); ap.add_argument("--preview")
    ap.add_argument("--section", help="단원 이름 정규식 (예: '\d+과 본문 \d+|부교재 [\d~-]+|10모 [\d~]+번')")
    a = ap.parse_args()
    doc = fitz.open(a.pdf)
    ans = json.load(open(a.answers, encoding="utf-8"))
    A = {str(k): str(v) for k, v in ans.get("answers", {}).items()}
    S = {str(k): str(v) for k, v in ans.get("solutions", {}).items()}
    SEC = ans.get("sections", {})       # {단원 이름: {"answers": {...}, "solutions": {...}}}
    def ga(sec, no):
        if sec and sec in SEC:
            return str(SEC[sec].get("answers", {}).get(str(no), "")), str(SEC[sec].get("solutions", {}).get(str(no), ""))
        return A.get(str(no), ""), S.get(str(no), "")
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
        pages, probs = detect(doc, re.compile(a.section) if a.section else None)
        for p in probs:
            W, H = pages[p["page"]][0], pages[p["page"]][1]
            f = lambda r: [round(r[0] / W, 5), round(r[1] / H, 5), round(r[2] / W, 5), round(r[3] / H, 5)]
            ch = [f(c) for c in p["choices"]]
            an, so = ga(p["sec"], p["no"])
            q = {"no": p["no"], "page": p["page"], "num": f(p["num"]), "type": "choice" if ch else "short", "choices": ch,
                 "answer": an, "solution": so, "area": f(p["area"])}
            if p["sec"]: q["sec"] = p["sec"]
            if p["choice_page"] != p["page"]: q["cpage"] = p["choice_page"]
            out.append(q)
    q = {"ybq": 2, "title": ans.get("title", os.path.splitext(os.path.basename(a.pdf))[0]), "subject": ans.get("subject", ""), "problems": out}
    with zipfile.ZipFile(a.out, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(a.pdf, "original.pdf")
        z.writestr("quiz.json", json.dumps(q, ensure_ascii=False, indent=1))
    miss = [(p.get("sec", "") + " " + str(p["no"])).strip() for p in out if not p["answer"]]
    print(f"문항 {len(out)}개 (객관식 {sum(1 for p in out if p['type']=='choice')}), 정답 없는 문항: {miss or '없음'}")
    for p in out:
        if p["type"] == "choice" and len(p["choices"]) < 4:
            print(f"  확인 필요: {p.get('sec', '')} {p['no']}번 보기 칸 {len(p['choices'])}개")
    if a.preview:
        # 쪽마다 번호 칸(파랑)·보기 칸(초록)을 그린 그림: 눈으로 확인용
        os.makedirs(a.preview, exist_ok=True)
        for pi, pg in enumerate(doc):
            W, H = pg.rect.width, pg.rect.height
            for p in out:
                if p["page"] == pi:
                    r = p["num"]; pg.draw_rect(fitz.Rect(r[0] * W, r[1] * H, r[2] * W, r[3] * H), color=(0, .5, 1), width=1.2)
                if p.get("cpage", p["page"]) != pi:
                    continue
                for c in p["choices"]:
                    pg.draw_rect(fitz.Rect(c[0] * W, c[1] * H, c[2] * W, c[3] * H), color=(0, .7, .2), width=1)
            pg.get_pixmap(dpi=90).save(os.path.join(a.preview, f"p{pi + 1}.png"))


if __name__ == "__main__":
    main()
