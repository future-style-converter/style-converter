#!/usr/bin/env python3
# Skeptic's own mutation runner for L5 (independent of the lane's mutate.py).
# Works on a byte-identical SCRATCH COPY of the owned files (never the shared tree):
# mutate -> focused test -> record the red test names -> restore -> sha256 check -> green.
import hashlib, os, re, shutil, subprocess, sys, json
TREE = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
R = sys.argv[1]
FILES = ['test-all.sh', 'tools/titan/own-processes.sh', 'tools/titan/own-processes.test.mjs', 'tools/titan/own-gradle-daemons.test.mjs',
         'tools/titan/own-adb-server.test.mjs', 'tools/titan/gate-driver.sh', 'tools/titan/section-runner.sh', 'tools/titan/provision-devices.sh',
         'tools/visual/smoke.sh', 'tools/visual/smoke-port.sh', 'tools/visual/smoke-port.test.mjs', 'tools/visual/web-port-guard.sh']
sha = lambda b: hashlib.sha256(b).hexdigest()
shutil.rmtree(R, ignore_errors=True)
for f in FILES:
    os.makedirs(os.path.dirname(os.path.join(R, f)), exist_ok=True)
    shutil.copy2(os.path.join(TREE, f), os.path.join(R, f))
    assert sha(open(os.path.join(TREE, f), 'rb').read()) == sha(open(os.path.join(R, f), 'rb').read())
os.makedirs(os.path.join(R, 'apps', 'web-harness'), exist_ok=True)
GD = ['tools/titan/own-gradle-daemons.test.mjs']; AD = ['tools/titan/own-adb-server.test.mjs']; SP = ['tools/visual/smoke-port.test.mjs']
OP = 'tools/titan/own-processes.sh'
M = [
 ('G1', OP, '  local log="$1" dir r hit seen=0; shift\n', '  local log="$1" dir r hit seen=0; shift\n  return 0\n', GD),
 ('G2', OP, '[[ "$d" == "$r" || "$d" == "$r/"* ]] || return 1', '[[ "$d" == "$r"* ]] || return 1', GD),
 ('G3', OP, '_op_gradle_daemon_is_ours "$log" "${roots[@]}"; then', '{ _g3=1; for _r in "${roots[@]}"; do _op_gradle_daemon_is_ours "$log" "$_r" && _g3=0; done; (( _g3 == 0 )); }; then', GD),
 ('G4', OP, '  while [[ "$d" == "$r/"* ]]; do\n    [[ -e "$d/.git" ]] && return 1\n    d="${d%/*}"\n  done\n', '', GD),
 ('G5', OP, '  (( seen ))\n}', '  return 0\n}', GD),
 ('G6', OP, '    [[ -z "${TITAN_GRADLE_PIDS:-}" || " $TITAN_GRADLE_PIDS " == *" $p "* ]] || continue\n', '', GD),
 ('A1', OP, 'if [[ "$cmd" == *"fork-server server"* ]]; then', 'if true; then', AD),
 ('A2', OP, 'lsof -nP -tiTCP:"$port" -sTCP:LISTEN', 'lsof -nP -tiTCP:"$port"', AD),
 ('S1', 'tools/titan/section-runner.sh', 'kill-server </dev/null >/dev/null 2>&1; kill_wedged_adb_server; sleep 1', 'kill-server </dev/null >/dev/null 2>&1; pkill -9 -x adb 2>/dev/null; sleep 1', AD),
 ('S2', 'tools/titan/provision-devices.sh', 'kill-server </dev/null >/dev/null 2>&1; kill_wedged_adb_server; sleep 1', 'kill-server </dev/null >/dev/null 2>&1; pkill -9 -x adb 2>/dev/null; sleep 1', AD),
 ('S3', 'tools/titan/gate-driver.sh', '  while IFS= read -r line; do log "$line"; done < <(kill_own_gradle_daemons 2>&1)\n', '  while IFS= read -r line; do log "$line"; done < <(kill_own_gradle_daemons 2>&1)\n  (cd "$PROJECT_ROOT" && ./gradlew --stop -q >/dev/null 2>&1) || true\n', GD),
 ('S4', 'tools/titan/gate-driver.sh', 'while IFS= read -r line; do log "$line"; done < <(kill_own_gradle_daemons 2>&1)', 'kill_own_gradle_daemons', GD),
 ('P1', 'tools/visual/smoke-port.sh', '  local default="${1:-3000}" new\n', '  local default="${1:-3000}" new\n  echo "$default"; return 0\n', SP),
 ('P2', 'tools/visual/smoke-port.sh', 'if [[ -n "${WEB_PORT:-}" ]]; then', 'if false; then', SP),
 ('P3', 'tools/visual/smoke.sh', 'exec npx vite --port "$WEB_PORT" --strictPort >', 'exec npx vite --port "$WEB_PORT" >', SP),
 ('P4', 'tools/visual/smoke.sh', '"http://localhost:${WEB_PORT}/"', 'http://localhost:3000/', SP),
 # skeptic extras (untested-branch probes)
 ('X1-export-dropped', 'tools/visual/smoke.sh', '    export WEB_PORT\n', '', SP),
 ('X2-vite-exit-check-dropped', 'tools/visual/smoke.sh', '        kill -0 "$VITE_PID" 2>/dev/null || break\n', '', SP),
 ('X3-pwdP-dropped', OP, 'roots+=("$(cd "$r" 2>/dev/null && pwd -P || echo "${r%/}")")', 'roots+=("${r%/}")', GD),
 ('X4-args-ignored', OP, 'for r in "${@:-$PROJECT_ROOT}"; do', 'for r in "$PROJECT_ROOT"; do', GD),
 ('X5-range-overlaps-test-all', 'tools/visual/smoke-port.sh', 'wpg_free_port 3400 3499)', 'wpg_free_port 3300 3399)', SP),
 ('X6-no-signal', OP, '      kill "$p" 2>/dev/null || true; stopped+=" $p"', '      kill -0 "$p" 2>/dev/null || true; stopped+=" $p"', GD),
 ('X7-sr-source-dropped', 'tools/titan/section-runner.sh', 'source "$PROJECT_ROOT/tools/titan/own-processes.sh"\n', '', AD),
 ('X8-pd-source-dropped', 'tools/titan/provision-devices.sh', 'source "$SCRIPT_DIR/own-processes.sh"\n', '', AD),
 ('X9-smoke-pick-not-sourced', 'tools/visual/smoke.sh', 'source "$PROJECT_ROOT/tools/visual/smoke-port.sh"\n', '', SP),
]
def run(tests):
    args = ['node', '--test', '--test-skip-pattern', 'DISCOVERY', *tests]
    p = subprocess.run(args, cwd=R, capture_output=True, text=True)
    assert 'DISCOVERY' not in p.stdout, 'DISCOVERY ran'
    notok = re.findall(r'^not ok \d+ - (.*)$', p.stdout, re.M)
    cnt = {k: int(v) for k, v in re.findall(r'^# (tests|pass|fail|skipped) (\d+)$', p.stdout, re.M)}
    return notok, cnt
only = sys.argv[2:] or None
base = run(GD + AD + SP)
print('BASELINE', base[1], 'not-ok:', base[0]); assert base[1]['fail'] == 0 and base[1]['tests'] == 16, 'DISCOVERY must be filtered out (17 - 1)'
for mid, f, old, new, tests in M:
    if only and mid not in only: continue
    path = os.path.join(R, f); orig = open(path, 'rb').read(); s0 = sha(orig)
    n = orig.decode().count(old); assert n == 1, f'{mid}: anchor occurs {n}x'
    open(path, 'w').write(orig.decode().replace(old, new))
    red, cnt = run(tests)
    open(path, 'wb').write(orig); s1 = sha(open(path, 'rb').read())
    green, cnt2 = run(tests)
    print(json.dumps({'id': mid, 'file': f, 'red_tests': red, 'counts_mutated': cnt, 'restore_sha256': s1[:16], 'byte_exact': s0 == s1, 'green_after': cnt2.get('fail') == 0 and not green}))
