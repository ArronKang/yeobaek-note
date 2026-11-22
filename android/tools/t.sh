#!/bin/bash
# 패드 원격 조작: t.sh shot NAME | t.sh <adb shell input 인자...> | t.sh crash | t.sh install
A="/c/Users/PC/AppData/Local/Android/Sdk/platform-tools/adb.exe"
S=$("$A" devices | grep -E "adb-R54X302P6WK.*device$" | head -1 | cut -f1)
D="${YB_OUT:-$TEMP/yb}"; mkdir -p "$D"
case "$1" in
  shot) sleep "${3:-1}"; "$A" -s "$S" exec-out screencap -p > "$D/$2.png"; python -c "
from PIL import Image; im=Image.open(r'$D/$2.png'); im.thumbnail((900,900)); im.save(r'$D/$2_s.png')"; echo "$D/$2_s.png";;
  crash) "$A" -s "$S" logcat -d -b crash | tail -40;;
  install) "$A" -s "$S" install -r /c/Users/PC/yeobaek-note/android/app/build/outputs/apk/debug/app-debug.apk | tail -1; "$A" -s "$S" logcat -c; "$A" -s "$S" shell am start -n kr.yeobaek.note/.MainActivity >/dev/null;;
  log) "$A" -s "$S" logcat -d | grep -iE "yeobaek|AndroidRuntime|FATAL" | tail -30;;
  sh) shift; "$A" -s "$S" shell "$@";;
  *) "$A" -s "$S" shell input "$@";;
esac
