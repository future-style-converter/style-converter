#!/usr/bin/env bash
# verify-seam-3.sh — the PLAN §0 seam sequence for seam-3.patch, which also
# appends a pin to L11's own post-load-extract.test.mjs: lock extract-fixture.mjs,
# sha both files, apply, run (a) the post-load suite and the extract-fixture
# uaLink pins, (b) the executed mutation (guard removed → the new pin must
# FAIL), restore extract-fixture.mjs from HEAD and the test file from its saved
# copy, re-sha both (must equal), unlock. Appends to seam-verification.log /
# mutations.log.
set -u
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd); cd "$ROOT"
E=tools/titan/extract-fixture.mjs; T=tools/titan/post-load-extract.test.mjs
R=tools/titan/results/wave52-all-reset-postload-colour
LOCK=tools/titan/runs/wave52-lock/extract-fixture.mjs
GOT=0; for i in $(seq 1 40); do if mkdir "$LOCK" 2>/dev/null; then GOT=1; break; fi; sleep 30; done
[ $GOT = 1 ] || { echo "seam verification blocked by lock extract-fixture.mjs" | tee -a $R/seam-verification.log; exit 3; }
SE0=$(shasum -a 256 $E | cut -d' ' -f1); ST0=$(shasum -a 256 $T | cut -d' ' -f1)
[ "$SE0" = "$(git show HEAD:$E | shasum -a 256 | cut -d' ' -f1)" ] || { rmdir "$LOCK"; echo "extract-fixture.mjs not at HEAD — abort" | tee -a $R/seam-verification.log; exit 5; }
SAVE=$(mktemp); cp $T "$SAVE"
git apply $R/seam-3.patch || { rmdir "$LOCK"; echo "apply FAILED seam-3" | tee -a $R/seam-verification.log; exit 4; }
node --test $T > /tmp/l11-s3a.out 2>&1; RA=$?
node --test --test-name-pattern='uaLink|A7|ua-link' tools/titan/extract-fixture.test.mjs > /tmp/l11-s3b.out 2>&1; RB=$?
# Executed mutation: drop the guard line, re-run the new pin only.
python3 - $E <<'PY'
import sys; p=sys.argv[1]; s=open(p,encoding='utf-8',errors='surrogateescape').read()
o="  if (authorAll !== '' && authorAll !== 'revert' && authorAll !== 'revert-layer') return null;\n"
assert s.count(o)==1; open(p,'w',encoding='utf-8',errors='surrogateescape').write(s.replace(o,''))
PY
node --test --test-name-pattern='suppresses the UA link bake' $T > /tmp/l11-s3m.out 2>&1; RM=$?
git show HEAD:$E > $E; cp "$SAVE" $T; rm -f "$SAVE"
SE1=$(shasum -a 256 $E | cut -d' ' -f1); ST1=$(shasum -a 256 $T | cut -d' ' -f1)
rmdir "$LOCK"
OK=$( [ "$SE0" = "$SE1" ] && [ "$ST0" = "$ST1" ] && echo IDENTICAL || echo MISMATCH )
{ echo "== $(date -u +%FT%TZ) seam-3.patch on $E (+ $T): post-load suite exit $RA, extract-fixture ua-link pins exit $RB; sha256 $E $SE0→$SE1, $T $ST0→$ST1 $OK";
  grep -E '^# (tests|pass|fail|skipped)' /tmp/l11-s3a.out | tr '\n' ' '; echo; grep -E '^# (tests|pass|fail)' /tmp/l11-s3b.out | tr '\n' ' '; echo; } >> $R/seam-verification.log
{ echo "== seam-3 S1 guard removed (node --test exit $RM)"; grep -E '^not ok' /tmp/l11-s3m.out; grep -E '^# (pass|fail)' /tmp/l11-s3m.out; echo "# restored: $OK"; } >> $R/mutations.log
[ $RA = 0 ] && [ $RB = 0 ] && [ $RM != 0 ]
