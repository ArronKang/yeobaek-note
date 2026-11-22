#!/bin/bash
# 녹화하며 조작: rec.sh NAME SECONDS "명령1" "명령2" ... (각 명령은 t.sh 인자)  → 깜박임 분석
A="/c/Users/PC/AppData/Local/Android/Sdk/platform-tools/adb.exe"
S=$("$A" devices | grep -E "adb-R54X.*device$" | head -1 | cut -f1)
D="${YB_OUT:-$TEMP/yb}"; mkdir -p "$D"; H="$(cd "$(dirname "$0")" && pwd)"
T="bash $H/t.sh"
name=$1; secs=$2; shift 2
MSYS_NO_PATHCONV=1 "$A" -s "$S" shell rm -f /sdcard/rec.mp4
MSYS_NO_PATHCONV=1 "$A" -s "$S" shell screenrecord --time-limit "$secs" --size 800x1280 --bit-rate 8000000 /sdcard/rec.mp4 &
RP=$!
sleep 1
for c in "$@"; do
  if [[ "$c" == sleep* ]]; then eval "$c"; else eval "$T $c"; fi
done
wait $RP
MSYS_NO_PATHCONV=1 "$A" -s "$S" pull /sdcard/rec.mp4 "$D/$name.mp4" >/dev/null
python -I "$H/flicker.py" "$D/$name.mp4" 40
"$A" -s "$S" logcat -d -b crash | grep -E "FATAL|Exception:" | tail -3
