#!/usr/bin/env python3
# tools/titan/results/wave53-canvas-root/skeptic-mutate.py — the L3 SKEPTIC's own
# mutation runner (independent of the lane's mutate.py). Red is read from the
# STRUCTURED test report, never from a grep of console text, and a mutation that
# breaks compilation is reported as COMPILE (never counted as red):
#   vitest  → --reporter=json, assertionResults[].title + status
#   gradle  → JUnit XML written AFTER the run started (stale XML ignored)
#   xcode   → "Test Case '-[<Bundle>.<Class> <name>]' failed/passed" lines, and
#             a build that never reached a test is COMPILE
# Usage: python3 skeptic-mutate.py <spec.json> [ids…]   (spec: list of mutations)
# Each mutation: {id, file, old, new, kind, cmd, cwd, tests:[names], expect:'red'|'probe'}
# 'probe' = an exploratory mutation of an unpinned-looking branch: the outcome
# (which tests, if any, went red) is recorded, not asserted.
import glob, hashlib, json, os, re, subprocess, sys, time
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
LOG = os.path.join(HERE, 'skeptic-mutations.log')
SCR = os.environ.get('SK_TMP', '/tmp')                     # throwaway report files only

def sha(p): return hashlib.sha256(open(p, 'rb').read()).hexdigest()

def run(cmd, cwd):
    cmd = cmd.replace('{DD}', os.environ.get('SK_DD', '/tmp/sk-dd'))   # build cache dir, never evidence
    r = subprocess.run(cmd, cwd=os.path.join(ROOT, cwd), shell=True, capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr

def results(kind, cmd, cwd, t0):
    """Run and return (exit, {test: 'passed'|'failed'}, compileError?)."""
    if kind == 'vitest':
        out_json = os.path.join(SCR, 'vitest.json')
        if os.path.exists(out_json): os.remove(out_json)
        code, out = run(f"{cmd} --reporter=json --outputFile={out_json}", cwd)
        st = {}
        if not os.path.exists(out_json): return code, st, True, out
        j = json.load(open(out_json))
        collect_err = any(tr.get('status') == 'failed' and not tr.get('assertionResults') for tr in j['testResults'])
        for tr in j['testResults']:
            for a in tr.get('assertionResults', []): st[a['title']] = a['status']
        return code, st, collect_err, out
    if kind == 'gradle':
        code, out = run(cmd, cwd)
        st = {}
        for d in ['apps/android-harness/app/build/test-results/testDebugUnitTest',
                  'runtimes/compose/build/test-results/testDebugUnitTest']:
            for x in glob.glob(os.path.join(ROOT, d, 'TEST-*.xml')):
                if os.path.getmtime(x) < t0: continue        # stale: not from this run
                s = open(x).read()
                for m in re.finditer(r'<testcase name="([^"]+)"[^>]*?(/>|>(.*?)</testcase>)', s, re.S):
                    st[m.group(1)] = 'failed' if m.group(3) and '<failure' in m.group(3) else 'passed'
        compile_err = bool(re.search(r"Compilation error|^e: file://|compileDebug\w*Kotlin' FAILED|Execution failed for task ':\w+:compile", out, re.M))
        return code, st, compile_err, out
    if kind == 'xcode':
        code, out = run(cmd, cwd)
        st = {}
        for m in re.finditer(r"Test Case '-\[\w+\.\w+ (\w+)\]' (passed|failed)", out): st[m.group(1)] = m.group(2)
        compile_err = not st and code != 0
        return code, st, compile_err, out
    raise SystemExit(f'unknown kind {kind}')

def main():
    spec = json.load(open(sys.argv[1])); only = set(sys.argv[2:])
    for m in spec:
        if only and m['id'] not in only: continue
        path = os.path.join(ROOT, m['file']); src = open(path, 'rb').read(); before = sha(path)
        text = src.decode('utf8'); n = text.count(m['old'])
        if n != 1:
            line = f"{m['id']} SKIP old matched {n}x"; print(line); open(LOG, 'a').write(line + '\n'); continue
        open(path, 'w').write(text.replace(m['old'], m['new'])); t0 = time.time() - 1
        try: code, st, cerr, out = results(m['kind'], m['cmd'], m['cwd'], t0)
        finally: open(path, 'wb').write(src)               # byte-exact restore
        after = sha(path)
        failed = sorted(k for k, v in st.items() if v == 'failed')
        named_red = [t for t in m['tests'] if st.get(t) == 'failed']
        if cerr: verdict = 'COMPILE'
        elif m['expect'] == 'red': verdict = 'RED' if len(named_red) == len(m['tests']) and code != 0 else 'NOT-RED'
        else: verdict = 'PROBE-RED' if failed else 'PROBE-GREEN(unpinned)'
        line = (f"{time.strftime('%Y-%m-%dT%H:%M:%S')} {m['id']} {m['file'].split('/')[-1]} sha256 {before[:16]}→{after[:16]} "
                f"{'byte-exact' if before == after else 'MISMATCH'} | exit {code} | {verdict} | ran {len(st)} | failed {failed}")
        print(line, flush=True); open(LOG, 'a').write(line + '\n')
        if cerr: open(LOG, 'a').write('   compile output tail: ' + out[-800:].replace('\n', ' | ') + '\n')

main()
