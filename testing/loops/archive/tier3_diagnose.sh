#!/usr/bin/env bash
# For each Tier 3 fail, test 2 variants:
#   (a) original (with text)
#   (b) text removed
# This isolates whether the failure is text rendering vs other.
cd /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/jovial-shockley-b4adce
RES=/tmp/tier3_diagnose.txt
> $RES
FAILS=(Alert Avatar IOSSettingsRow Pill PrimaryButton StatusBar Tab)

for f in "${FAILS[@]}"; do
  fp="examples/properties/components/$f.json"
  # Backup original
  cp "$fp" "/tmp/orig_$f.json"

  # Test original
  orig=$(./test-all.sh "$fp" 2>&1 | grep -E "iOS-Android|Android-Web|iOS-Web" | grep -oE "[0-9]\.[0-9]+" | sort -n | head -1)

  # Strip text from fixture
  python3 -c "
import json
d = json.load(open('$fp'))
for c in d['components'].values():
    c['properties'].pop('text', None)
json.dump(d, open('$fp','w'), indent=2)
"
  notext=$(./test-all.sh "$fp" 2>&1 | grep -E "iOS-Android|Android-Web|iOS-Web" | grep -oE "[0-9]\.[0-9]+" | sort -n | head -1)

  # Restore original
  cp "/tmp/orig_$f.json" "$fp"

  echo "$f orig=$orig notext=$notext" >> $RES
done
echo "DONE at $(date)" >> $RES
