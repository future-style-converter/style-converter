#!/usr/bin/env python3
"""wave 53 · L5 harness-hygiene — the EXECUTED mutations of the lane's pins.

Each mutation: sha256 the owned file, apply ONE textual edit, run the focused
suite, record which tests went red, restore the original bytes, re-check the
sha256 (byte-exact), run the suite again and record green. Appends to
mutations.log beside this file. Usage: mutate.py <id>… (or `all`).

SAFETY: the Gradle mutations run with --test-skip-pattern DISCOVERY, so every
remaining Gradle pin carries TITAN_GRADLE_PIDS (the helper only scans the test's
own fake daemons — a predicate mutated to "ours" cannot reach a host daemon);
the adb mutations run on a throwaway ANDROID_ADB_SERVER_PORT; the source-pin
mutations only edit text that the source pin reads (nothing executes them).
"""
import hashlib, re, subprocess, sys, time, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[4]
LOG = pathlib.Path(__file__).with_name('mutations.log')
OWN = 'tools/titan/own-processes.sh'
OWN_FILES = ['tools/titan/own-processes.test.mjs', 'tools/titan/own-gradle-daemons.test.mjs', 'tools/titan/own-adb-server.test.mjs']
OWN_T = ['node', '--test', '--test-skip-pattern', 'DISCOVERY'] + OWN_FILES
OWN_T_FULL = ['node', '--test'] + OWN_FILES
SMOKE = 'tools/visual/smoke-port.sh'
SMOKE_T = ['node', '--test', 'tools/visual/smoke-port.test.mjs']

M = {
  # ── T2 Gradle ──
  'G1-unconditional-kill': (OWN, '  local log="$1" dir r hit seen=0; shift\n', '  return 0\n  local log="$1" dir r hit seen=0; shift\n', OWN_T),
  'G2-prefix-without-boundary': (OWN, '[[ "$d" == "$r" || "$d" == "$r/"* ]] || return 1', '[[ "$d" == "$r"* ]] || return 1', OWN_T),
  'G3-roots-one-at-a-time': (OWN, 'if [[ -n "$ver" ]] && _op_gradle_daemon_is_ours "$log" "${roots[@]}"; then',
                             'if [[ -n "$ver" ]] && { for r in "${roots[@]}"; do _op_gradle_daemon_is_ours "$log" "$r" && break; done; }; then', OWN_T),
  'G4-no-nested-checkout-walk': (OWN, '    [[ -e "$d/.git" ]] && return 1\n', '    :\n', OWN_T),
  'G5-empty-log-counts': (OWN, '  (( seen ))\n}', '  return 0\n}', OWN_T),
  'G6-narrowing-ignored': (OWN, '    [[ -z "${TITAN_GRADLE_PIDS:-}" || " $TITAN_GRADLE_PIDS " == *" $p "* ]] || continue\n', '', OWN_T),
  # ── T3 adb ──
  'A1-any-listener-is-the-server': (OWN, 'if [[ "$cmd" == *"fork-server server"* ]]; then', 'if true; then', OWN_T),
  'A2-no-listen-filter': (OWN, 'for p in $(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null); do\n    cmd="$(ps -o command= -p "$p" 2>/dev/null)" || continue\n    if [[ "$cmd" == *"fork-server',
                          'for p in $(lsof -nP -tiTCP:"$port" 2>/dev/null); do\n    cmd="$(ps -o command= -p "$p" 2>/dev/null)" || continue\n    if [[ "$cmd" == *"fork-server', OWN_T),
  # ── source pins (text only; nothing runs these scripts) ──
  'S1-pkill-adb-restored-section-runner': ('tools/titan/section-runner.sh', 'kill-server </dev/null >/dev/null 2>&1; kill_wedged_adb_server; sleep 1',
                                           'kill-server </dev/null >/dev/null 2>&1; pkill -9 -x adb 2>/dev/null; sleep 1', OWN_T),
  'S2-pkill-adb-restored-provision': ('tools/titan/provision-devices.sh', 'kill-server </dev/null >/dev/null 2>&1; kill_wedged_adb_server; sleep 1',
                                      'kill-server </dev/null >/dev/null 2>&1; pkill -9 -x adb 2>/dev/null; sleep 1', OWN_T),
  'S3-gradlew-stop-restored': ('tools/titan/gate-driver.sh', '  while IFS= read -r line; do log "$line"; done < <(kill_own_gradle_daemons 2>&1)\n',
                               '  while IFS= read -r line; do log "$line"; done < <(kill_own_gradle_daemons 2>&1)\n  (cd "$PROJECT_ROOT" && ./gradlew --stop -q >/dev/null 2>&1) || true\n', OWN_T),
  'S4-driver-log-dropped': ('tools/titan/gate-driver.sh', '  while IFS= read -r line; do log "$line"; done < <(kill_own_gradle_daemons 2>&1)\n', '  kill_own_gradle_daemons\n', OWN_T),
  # ── T4 smoke port ──
  'P1-default-port-unconditional': (SMOKE, '  local default="${1:-3000}" new\n', '  local default="${1:-3000}" new\n  echo "${WEB_PORT:-$default}"; return 0\n', SMOKE_T),
  'P2-explicit-foreign-port-accepted': (SMOKE, '  if [[ -n "${WEB_PORT:-}" ]]; then\n', '  if false; then\n', SMOKE_T),
  'P3-strictPort-dropped': ('tools/visual/smoke.sh', '--port "$WEB_PORT" --strictPort', '--port "$WEB_PORT"', SMOKE_T),
  'P4-poll-hardwired-3000': ('tools/visual/smoke.sh', 'http://localhost:${WEB_PORT}/', 'http://localhost:3000/', SMOKE_T),
}

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def run(cmd):
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=600)
    out = r.stdout + r.stderr
    red = re.findall(r'^not ok \d+ - (.*)$', out, re.M)
    tot = re.search(r'^# tests (\d+)', out, re.M); fail = re.search(r'^# fail (\d+)', out, re.M)
    return r.returncode, red, (tot.group(1) if tot else '?'), (fail.group(1) if fail else '?')

def one(mid):
    rel, old, new, cmd = M[mid]
    p = ROOT / rel
    orig = p.read_bytes(); before = sha(p)
    text = orig.decode()
    assert text.count(old) == 1, f'{mid}: anchor not unique/absent in {rel} ({text.count(old)})'
    p.write_bytes(text.replace(old, new).encode())
    try:
        rc, red, tot, fail = run(cmd)
    finally:
        p.write_bytes(orig)
    after = sha(p)
    rc2, red2, tot2, fail2 = run(OWN_T_FULL if cmd is OWN_T else cmd)
    line = (f'{time.strftime("%Y-%m-%dT%H:%M:%S")} {mid} file={rel} sha256 before={before[:16]} after={after[:16]} '
            f'{"BYTE-EXACT" if before == after else "MISMATCH"} | MUTATED rc={rc} tests={tot} fail={fail} red={red} '
            f'| RESTORED rc={rc2} tests={tot2} fail={fail2}')
    print(line)
    with LOG.open('a') as f: f.write(line + '\n')
    assert before == after, 'restore was not byte-exact'
    return rc != 0 and rc2 == 0

if __name__ == '__main__':
    ids = list(M) if sys.argv[1:] == ['all'] else sys.argv[1:]
    ok = all([one(i) for i in ids])
    sys.exit(0 if ok else 1)
