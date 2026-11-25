# SF Pro 글꼴 (app/src/main/assets/sfpro.ttf) 만들기

앱 아이콘(SF Symbols)과 라틴 글자는 Apple iOS 27 UI 키트에 들어 있는 SF Pro 글꼴을 써요. 공개 저장소에는 올리지 않아서(`.gitignore`) 새로 clone했으면 아래처럼 한 번 만들어 넣어요. 파일이 없어도 앱은 돌아가고 예전 선 아이콘과 Pretendard로 보여요.

1. 키트 받기: Sketch 공유 `04c24d8b-38fb-4afb-8836-36617e022f02`를 공개 GraphQL(`https://graphql.sketch.cloud/api`)에 `share(shortId){version{document{url}}}`로 물어 .sketch(zip)를 받고, 안의 `fonts/SF_Pro_*.ttf` 중 Regular(이탤릭 아닌 것)를 꺼냄
2. 줄이기 (fontTools):
   ```bash
   python -m fontTools.subset SF_Pro.ttf --unicodes="U+0020-007E,U+00A0-00FF,U+2010-2027,U+2030-205E,U+2190-21FF,U+2212,U+2460-2473,U+25A0-25FF,U+100000-100BFF" --layout-features='*' --no-hinting --output-file=sf.ttf
   python -m fontTools.varLib.instancer sf.ttf wdth=100 opsz=17 -o sfpro.ttf
   ```
3. `app/src/main/assets/sfpro.ttf`로 복사

아이콘 이름과 글리프 번호는 `ui/Sym.kt`. 번호는 글꼴에서 글리프 표를 그려 눈으로 확인한 값이에요.
