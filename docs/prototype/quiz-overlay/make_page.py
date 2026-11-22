# 문제지 PDF 한 쪽을 그림으로 만들고, 문제 번호·보기 기호 위치(quiz.json 형태)를 함께 뽑음
import json, sys
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1]
U = 560; H = 792; S = 3                    # 페이지 단위 560x792, 3배로 그림
F = "C:/Windows/Fonts/"
def font(name, size, idx=0): return ImageFont.truetype(F + name, int(size * S), index=idx)
body = font("batang.ttc", 11.6)
bodyS = font("batang.ttc", 10.6)
numF = font("malgunbd.ttf", 12.5)
titleF = font("malgunbd.ttf", 19)
subF = font("malgun.ttf", 9.5)
headF = font("malgunbd.ttf", 10)

im = Image.new("RGB", (U * S, H * S), "white")
d = ImageDraw.Draw(im)
INK = (34, 34, 34)
L, R = 50, 512
problems = []

def txt(x, y, s, f, fill=INK):
    d.text((x * S, y * S), s, font=f, fill=fill)
def bbox(x, y, s, f):
    b = d.textbbox((x * S, y * S), s, font=f)
    return [b[0] / S, b[1] / S, b[2] / S, b[3] / S]
def width(s, f): return d.textlength(s, font=f) / S
def wrap(s, f, w):
    lines, cur = [], ""
    for word in s.split(" "):
        t = (cur + " " + word).strip()
        if width(t, f) <= w: cur = t
        else:
            if cur: lines.append(cur)
            cur = word
    if cur: lines.append(cur)
    return lines
LH = 18

# 머리
txt(L, 30, "2027학년도 한국사 확인 문제", titleF)
txt(L, 56, "근현대사 단원 점검  |  3회", subF, (110, 110, 110))
tb = bbox(R - 70, 33, "제 4 교시", headF); d.rounded_rectangle([(R - 78) * S, 28 * S, R * S, 50 * S], radius=4 * S, outline=INK, width=int(1.2 * S))
txt(R - 70, 31.5, "제 4 교시", headF)
d.rectangle([L * S, 74 * S, R * S, int(75.6 * S)], fill=INK)

def problem(y, n, q, choices=None, cols=2, box=None, kind="choice", ans=None, ex="", ox=False, short=False):
    nb = bbox(L, y - 2.2, f"{n}.", numF)
    txt(L, y - 2.2, f"{n}.", numF)
    qx = L + 20
    lines = wrap(q, body, R - qx)
    for i, ln in enumerate(lines): txt(qx, y + i * LH, ln, body)
    yy = y + len(lines) * LH + 4
    p = {"n": n, "type": kind, "num": [nb[0] - 3, nb[1] - 3, nb[2] + 3, nb[3] + 3], "choices": [], "answer": ans, "explain": ex}
    if ox:
        # 마지막 줄 끝에 (O / X)
        last = lines[-1]; lx = qx + width(last + " ", body); ly = y + (len(lines) - 1) * LH
        s1 = "("; txt(lx, ly, s1, body); x = lx + width(s1, body)
        ob = bbox(x, ly, "O", body); txt(x, ly, "O", body); txt(x + width("O ", body), ly, "/", body); x += width("O / ", body); xb = bbox(x, ly, "X", body); txt(x, ly, "X", body)
        txt(x + width("X", body), ly, ")", body)
        p["choices"] = [{"v": "O", "box": ob}, {"v": "X", "box": xb}]
        yy = y + len(lines) * LH + 4
    if box:
        bl = wrap(box, bodyS, R - qx - 24)
        h = len(bl) * 16 + 12
        d.rectangle([(qx + 4) * S, yy * S, (R - 4) * S, (yy + h) * S], outline=(120, 120, 120), width=int(1 * S))
        for i, ln in enumerate(bl): txt(qx + 14, yy + 6 + i * 16, ln, bodyS)
        yy += h + 8
    if choices:
        colw = (R - qx - 10) / cols
        for i, c in enumerate(choices):
            r, cidx = divmod(i, cols)
            cx = qx + 10 + cidx * colw; cy = yy + r * LH
            g = "①②③④⑤"[i]
            gb = bbox(cx, cy, g, body)
            txt(cx, cy, g, body); txt(cx + width(g + " ", body), cy, c, body)
            p["choices"].append({"v": str(i + 1), "box": gb})
        yy += ((len(choices) + cols - 1) // cols) * LH
    if short:
        txt(qx + 10, yy + 8, "답 :", body)
        d.line([(qx + 42) * S, (yy + 24) * S, (qx + 230) * S, (yy + 24) * S], fill=INK, width=int(1 * S))
        yy += 32
    problems.append(p)
    return yy + 17

y = 92
y = problem(y, 1, "3·1 운동 이후인 1920년대에 일제가 내세운 통치 방식으로 옳은 것은?",
            ["무단 통치", "문화 통치", "민족 말살 통치", "병참 기지화 정책", "헌병 경찰 통치"], cols=2, ans="2",
            ex="3·1 운동으로 무력 통치의 한계를 느낀 일제는 보통 경찰제, 제한된 언론 허용 같은 이른바 문화 통치로 바꿨어요. 실제로는 친일파를 키워 민족을 나누려는 것이었어요.")
y = problem(y, 2, "다음 자료의 선거에 대한 설명으로 옳은 것은?",
            ["대통령을 직접 뽑았다.", "북한 지역에서도 실시되었다.", "제헌 국회가 구성되었다.", "여성은 투표하지 못했다.", "유신 헌법이 확정되었다."], cols=2,
            box="1948년 5월 10일, 우리 역사상 처음으로 보통 선거가 실시되어 만 21세 이상의 모든 국민이 투표에 참여하였고, 198명의 국회의원이 뽑혔다.",
            ans="3", ex="5·10 총선거로 뽑힌 국회의원들이 제헌 국회를 구성해 헌법을 만들고 대통령을 간접 선거로 뽑았어요. 북한 지역은 참여하지 않았어요.")
y = problem(y, 3, "다음 사건을 일어난 순서대로 바르게 나열한 것은?",
            ["(가) - (나) - (다)", "(가) - (다) - (나)", "(나) - (가) - (다)", "(다) - (가) - (나)", "(다) - (나) - (가)"], cols=3,
            box="(가) 6·25 전쟁 발발        (나) 4·19 혁명        (다) 대한민국 정부 수립",
            ans="4", ex="대한민국 정부 수립(1948) → 6·25 전쟁 발발(1950) → 4·19 혁명(1960) 순서예요.")
y = problem(y, 4, "밑줄 친 '이 단체'에 대한 설명으로 옳은 것은?",
            ["물산 장려 운동을 주도하였다.", "광주 학생 항일 운동에 조사단을 보냈다.", "105인 사건으로 해체되었다.", "한인 애국단을 조직하였다.", "복벽주의를 내세웠다."], cols=1,
            box="1927년, 민족주의 세력과 사회주의 세력이 손잡고 이 단체를 결성하였다. 전국에 지회를 두고 강연회를 열었으며 민족 협동 전선을 이루었다.",
            ans="2", ex="신간회는 1929년 광주 학생 항일 운동이 일어나자 진상 조사단을 보내고 민중 대회를 열려고 했어요. 105인 사건은 신민회, 한인 애국단은 임시 정부의 김구예요.")
y = problem(y, 5, "김구는 상하이에서 한인 애국단을 조직하여 의열 투쟁을 펼쳤다.", kind="ox", ox=True, ans="O",
            ex="김구가 1931년 조직한 한인 애국단에서 이봉창, 윤봉길 의거가 일어났어요.")
y = problem(y, 6, "1919년 상하이에서 수립되어 삼권 분립에 바탕을 둔 민주 공화제 정부의 이름을 쓰시오.", kind="short", short=True,
            ans="대한민국 임시 정부", ex="여러 지역의 임시 정부를 하나로 합쳐 상하이에서 출범했어요.")

txt(U / 2 - 8, H - 34, "- 7 -", subF, (120, 120, 120))
print("bottom y", y)
im.save(OUT + "/page.png", optimize=True)
json.dump({"page": {"w": U, "h": H}, "problems": problems}, open(OUT + "/quiz.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
