#!/usr/bin/env bash
# EXACT loop header from tools/titan/provision-devices.sh:235 (unfixed),
# body scaled 10:1 -- sleep 1 stands for `_bounded 10 adb ... pm path` that
# hits its 10s bound, sleep 0.5 stands for the loop's own `sleep 5`.
# Claimed budget in the comment above it: "capped at 120s".
t0=$SECONDS; n=0
for (( t=0; t<120; t+=5 )); do
  n=$((n+1)); sleep 1; sleep 0.5
done
echo "UNFIXED header  for((t=0;t<120;t+=5)) : iterations=$n  scaled_wall=$(( (SECONDS-t0)*10 ))s  (claimed cap 120s)"
t0=$SECONDS; n=0; _T0=$SECONDS
while (( (SECONDS-_T0)*10 < 120 )); do
  n=$((n+1)); sleep 1; sleep 0.5
done
echo "FIXED   header  while((SECONDS-_T0<120))  : iterations=$n  scaled_wall=$(( (SECONDS-t0)*10 ))s  (claimed cap 120s)"
