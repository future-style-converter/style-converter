#!/usr/bin/env python3
# tools/titan/results/wave53-canvas-root/mutate.py — L3's EXECUTED-mutation runner.
#
# For each mutation (id, file, old, new, expect-red test name): sha256 the
# file, apply ONE exact-string replacement (asserting it matched exactly
# once), run the focused command, check the named test is reported failing
# (red), restore the original bytes, re-check the sha256 (byte-exact), and
# append one line to mutations.log. After the batch, the command is re-run
# once on the restored tree and must be green. Usage:
#   python3 mutate.py <batch-name>        (batches defined in mutations.json)
import hashlib, json, os, subprocess, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
LOG = os.path.join(HERE, 'mutations.log')


def sha(p):
    return hashlib.sha256(open(p, 'rb').read()).hexdigest()


def run(cmd, cwd):
    r = subprocess.run(cmd, cwd=os.path.join(ROOT, cwd), shell=True, capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def main():
    batch = sys.argv[1]
    spec = json.load(open(os.path.join(HERE, 'mutations.json')))[batch]
    cmd, cwd, red_marker = spec['cmd'], spec['cwd'], spec['redMarker']
    ok = True
    for m in spec['mutations']:
        path = os.path.join(ROOT, m['file'])
        before = sha(path)
        src = open(path, 'rb').read()
        text = src.decode('utf8')
        n = text.count(m['old'])
        if n != 1:
            print(f"{m['id']} SKIP old matched {n}×"); ok = False; continue
        open(path, 'w').write(text.replace(m['old'], m['new']))
        try:
            code, out = run(cmd, cwd)
        finally:
            open(path, 'wb').write(src)                      # byte-exact restore
        after = sha(path)
        failed = [t for t in m['red'] if red_marker.format(t=t) in out]
        red = code != 0 and len(failed) == len(m['red'])
        line = (f"{time.strftime('%Y-%m-%dT%H:%M:%S')} {batch} {m['id']} {m['file']} sha256 {before[:16]} "
                f"→ mutated → restored {after[:16]} {'byte-exact' if before == after else 'MISMATCH'} | "
                f"exit {code} red={red} expected-red {m['red']} seen-red {failed}")
        print(line); open(LOG, 'a').write(line + '\n')
        ok = ok and red and before == after
    code, out = run(cmd, cwd)
    line = f"{time.strftime('%Y-%m-%dT%H:%M:%S')} {batch} GREEN-AFTER-RESTORE exit {code}"
    print(line); open(LOG, 'a').write(line + '\n')
    if code != 0:
        print(out[-3000:])
    sys.exit(0 if ok and code == 0 else 1)


main()
