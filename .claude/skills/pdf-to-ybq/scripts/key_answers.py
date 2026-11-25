"""정답과 해설 PDF → answers.json (단원별)

    python key_answers.py key.pdf answers.json --section '정규식' [--title T] [--subject S]

이런 모양의 해설집을 읽음 (EBS·학원 실전연습 해설에 흔한 형식):
    3과 본문 1          ← 단원 이름 (--section 정규식)
    1 ②                 ← 정답 목록
    2 ⑤ …
    1 빈칸              ← 해설 묶음 머리 (번호 + 유형)
    ②                   ← 정답
    해설 글 …           ← 다음 머리 전까지가 해설
다른 모양이면 이 파일을 고치거나 answers.json을 직접 쓴다.
"""
import json, re, argparse
import fitz

CIRC_RANGES = [(0x2460, 20), (0x2780, 10), (0x278A, 10), (0x2776, 10), (0x24F5, 10)]


def circ_no(ch):
    o = ord(ch)
    for base, n in CIRC_RANGES:
        if base <= o < base + n:
            return o - base + 1
    return 0


def norm_ans(t):
    t = t.strip()
    if len(t) == 1 and circ_no(t):
        return str(circ_no(t))
    return t


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("key"); ap.add_argument("out")
    ap.add_argument("--section", required=True); ap.add_argument("--title", default=""); ap.add_argument("--subject", default="")
    a = ap.parse_args()
    rx = re.compile(a.section)
    lines = [l.strip() for p in fitz.open(a.key) for l in p.get_text().split("\n")]
    secs, cur, mode, no, buf = {}, None, None, None, []

    def flush():
        nonlocal buf
        if cur is not None and no is not None and buf:
            txt = " ".join(buf)
            txt = re.sub(r"\s*·\s*", ", ", txt)          # 화면에 가운뎃점 금지
            secs[cur]["solutions"][str(no)] = txt.strip()
        buf = []

    for l in lines:
        if not l or re.fullmatch(r"\d{1,3}", l) or l in ("해설", "정답과 해설"):
            continue
        if rx.fullmatch(l):
            flush(); cur = l; mode = "list"; no = None
            secs.setdefault(cur, {"answers": {}, "solutions": {}})
            continue
        if cur is None:
            continue
        m = re.fullmatch(r"(\d{1,2})\s+(.+)", l)
        if m:
            n, rest = int(m.group(1)), m.group(2).strip()
            if len(rest) == 1 and circ_no(rest):                 # 정답 목록 "1 ②"
                secs[cur]["answers"][str(n)] = str(circ_no(rest)); mode = "list"; continue
            if mode in ("list", "exp") and len(rest) <= 8 and re.fullmatch(r"[가-힣 ]+", rest):   # 해설 머리 "1 빈칸"
                flush(); no = n; mode = "exp_ans"; continue
            if mode == "list" and str(n) not in secs[cur]["answers"]:  # 단답 정답 "3 nature"
                secs[cur]["answers"][str(n)] = rest; continue
        if mode == "exp_ans":
            if str(no) not in secs[cur]["answers"]:
                secs[cur]["answers"][str(no)] = norm_ans(l)
            mode = "exp"; continue
        if mode == "exp":
            buf.append(l)
    flush()
    json.dump({"title": a.title, "subject": a.subject, "sections": secs}, open(a.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    n_a = sum(len(v["answers"]) for v in secs.values()); n_s = sum(len(v["solutions"]) for v in secs.values())
    print(f"단원 {len(secs)}개, 정답 {n_a}개, 해설 {n_s}개")


if __name__ == "__main__":
    main()
