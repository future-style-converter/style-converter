#!/usr/bin/env bash
# Verify all currently-passing Tier 1 fixtures actually pass — no stale tracker entries.
cd /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/jovial-shockley-b4adce
RES=/tmp/tier1_verify.txt
> $RES
echo "VERIFY START at $(date)" >> $RES

# Test all fixtures whose tracker says passing AND actual variants > 4 (i.e., ones changed by recent expansion)
ROWS=$(awk -F'|' 'NR>10 && NF>=10 {s=$7; gsub(/ /,"",s); a=$8+0; if(s=="passing" && a>=5) print $3"|"$4}' testing/TIER1_VARIANT_DEPTH.md | tr -d ' ' | sort -u)
COUNT=$(echo "$ROWS" | wc -l | tr -d ' ')
echo "verifying $COUNT fixtures" >> $RES

i=0
for row in $ROWS; do
  i=$((i+1))
  cat=${row%|*}; prop=${row#*|}
  fp="examples/properties/perfect/$cat/$prop.json"
  [ ! -f "$fp" ] && { echo "[$i/$COUNT] NOFILE $cat/$prop" >> $RES; continue; }
  # Retry up to 3 times if NORESULT (lock contention or transient issue)
  result=""
  for attempt in 1 2 3; do
    # Wait for lock (lock is a directory created by test-all.sh's mkdir)
    while [ -d /tmp/style-converter-testall.lock ] || [ -f /tmp/style-converter-testall.lock ]; do sleep 10; done
    result=$(./test-all.sh "$fp" 2>&1 | grep -E "iOS-Android|Android-Web|iOS-Web" | grep -oE "[0-9]\.[0-9]+")
    [ -n "$result" ] && break
    sleep 5
  done
  if [ -z "$result" ]; then echo "[$i/$COUNT] NORESULT $cat/$prop (3 retries)" >> $RES; continue; fi
  minS=$(echo "$result" | sort -n | head -1)
  out="PASS"
  if awk "BEGIN{exit !($minS < 0.95)}"; then out="FAIL"; fi
  echo "[$i/$COUNT] $out min=$minS $cat/$prop" >> $RES
done
echo "VERIFY DONE at $(date)" >> $RES
