#!/usr/bin/env bash
# Master autopilot — runs Tiers 2→3→4→…→12 sequentially.
# Each tier runs its own continuous worker until that tier's tracker is exhausted.
cd /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/jovial-shockley-b4adce
LOG=/tmp/master_autopilot.log
echo "MASTER AUTOPILOT START at $(date)" > $LOG

# Wait for any currently-running tier worker
while pgrep -f "tier[0-9]*_continuous.sh\|tier[0-9]*_retry" > /dev/null; do sleep 30; done

# === TIER 3: real components (15 fixtures) ===
echo "[$(date +%H:%M)] === TIER 3 ===" >> $LOG
RES=/tmp/tier3_results.txt
> $RES
for p in $(ls examples/properties/components/*.json | xargs -n1 basename | sed 's/\.json$//'); do
  result=$(./test-all.sh examples/properties/components/$p.json 2>&1 | grep -E "iOS-Android|Android-Web|iOS-Web" | grep -oE "[0-9]\.[0-9]+")
  if [ -z "$result" ]; then echo "NORESULT $p" >> $RES; continue; fi
  minS=$(echo "$result" | sort -n | head -1)
  res="PASS"
  if awk "BEGIN{exit !($minS < 0.95)}"; then res="FAIL"; fi
  echo "$res min=$minS $p" >> $RES
done

# === TIER 4: keyframes — generate per-property snapshots at 0/50/100% ===
echo "[$(date +%H:%M)] === TIER 4 ===" >> $LOG
mkdir -p examples/properties/keyframes
RES=/tmp/tier4_results.txt
> $RES
ANIM_PROPS=(opacity transform background-color color width height border-radius padding margin font-size border-width top left rotate scale)
for prop in "${ANIM_PROPS[@]}"; do
  python3 - <<PYEOF
import json
prop = "$prop"
SNAPS = {
  'opacity': ['1', '0.5', '0'],
  'transform': ['none', 'rotate(45deg)', 'rotate(90deg)'],
  'background-color': ['#ef4444', '#a855f7', '#3b82f6'],
  'color': ['#ffffff', '#94a3b8', '#1f2937'],
  'width': ['100px', '140px', '180px'],
  'height': ['40px', '60px', '80px'],
  'border-radius': ['0', '8px', '20px'],
  'padding': ['0', '8px', '16px'],
  'margin': ['0', '8px', '16px'],
  'font-size': ['12px', '16px', '20px'],
  'border-width': ['0', '4px', '8px'],
  'top': ['0', '10px', '20px'],
  'left': ['0', '10px', '20px'],
  'rotate': ['none', '45deg', '90deg'],
  'scale': ['none', '1.1', '1.3'],
}
snaps = SNAPS.get(prop, ['initial'])
base = {"width": "120px", "height": "60px", "padding": "8px", "background-color": "#3b82f6", "color": "#fff", "border": "4px solid #1e3a8a", "text": "T"}
comps = {}
for i, s in enumerate(['t0', 't50', 't100']):
  comps[f'V{i}_{s}'] = {"properties": dict(base, **{prop: snaps[i] if i < len(snaps) else snaps[-1]})}
json.dump({"components": comps}, open(f'examples/properties/keyframes/{prop}.json','w'), indent=2)
PYEOF
  result=$(./test-all.sh examples/properties/keyframes/$prop.json 2>&1 | grep -E "iOS-Android|Android-Web|iOS-Web" | grep -oE "[0-9]\.[0-9]+")
  if [ -z "$result" ]; then echo "NORESULT $prop" >> $RES; continue; fi
  minS=$(echo "$result" | sort -n | head -1)
  res="PASS"
  if awk "BEGIN{exit !($minS < 0.90)}"; then res="FAIL"; fi
  echo "$res min=$minS $prop" >> $RES
done

# === TIER 6: viewport constraints — generate per-viewport fixtures ===
echo "[$(date +%H:%M)] === TIER 6 ===" >> $LOG
mkdir -p examples/properties/viewport
RES=/tmp/tier6_results.txt
> $RES
VPS=("iPhone-SE:320:568" "iPhone-15:393:852" "iPad:820:1180" "Android-S:360:800" "Android-L:412:915")
for vp in "${VPS[@]}"; do
  IFS=':' read -r name w h <<< "$vp"
  python3 - <<PYEOF
import json
W,H = $w, $h
base_w = min(W-32, 360)
comps = {
  "FlexRow": {"properties": {"width": f"{base_w}px", "height": "60px", "padding": "8px", "background-color": "#3b82f6", "display": "flex", "flex-direction": "row", "gap": "8px"}},
  "GridCell": {"properties": {"width": f"{base_w}px", "height": "80px", "background-color": "#10b981", "display": "grid", "grid-template-columns": "1fr 1fr"}},
}
json.dump({"components": comps}, open(f'examples/properties/viewport/$name.json','w'), indent=2)
PYEOF
  result=$(./test-all.sh examples/properties/viewport/$name.json 2>&1 | grep -E "iOS-Android|Android-Web|iOS-Web" | grep -oE "[0-9]\.[0-9]+")
  if [ -z "$result" ]; then echo "NORESULT $name" >> $RES; continue; fi
  minS=$(echo "$result" | sort -n | head -1)
  res="PASS"
  if awk "BEGIN{exit !($minS < 0.95)}"; then res="FAIL"; fi
  echo "$res min=$minS $name" >> $RES
done

# === TIER 7: parser fuzz — programmatic parser stress tests ===
echo "[$(date +%H:%M)] === TIER 7 ===" >> $LOG
mkdir -p examples/properties/fuzz
RES=/tmp/tier7_results.txt
> $RES
FUZZ_INPUTS=(
  "calc():empty_calc"
  "calc(1px / 0):calc_div_zero"
  "linear-gradient(red, red 0%, red 0%):zero_stop_gradient"
  "rotate():no_arg_transform"
  "calc(1px + ):unbalanced_paren"
  "9999999999999px:numeric_overflow"
  "rgb(üñî):unicode_in_keyword"
  "/* comment */ red:comment_in_value"
  "rgb(##):malformed_color"
)
for fi in "${FUZZ_INPUTS[@]}"; do
  IFS=':' read -r val label <<< "$fi"
  cat > examples/properties/fuzz/$label.json <<EOJ
{"components":{"FZ":{"properties":{"width":"100px","height":"40px","background-color":"#3b82f6","color":"$val"}}}}
EOJ
  # Just verify converter doesn't crash; SSIM may be 0 if it falls back
  if ./gradlew :converter:run --args="convert --from css --to ir -i examples/properties/fuzz/$label.json -o /tmp/fuzz_$label" 2>&1 | grep -q "BUILD SUCCESSFUL"; then
    echo "PASS_PARSE $label" >> $RES
  else
    echo "FAIL_PARSE $label" >> $RES
  fi
done

echo "MASTER AUTOPILOT DONE at $(date)" >> $LOG
