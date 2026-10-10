#!/usr/bin/env python3
# wave54-S1/fix/hooks-check.py — the new stayDegenerateEvenIfPass pin must not break the probe-decision hook: runs the
# HEAD and the fixed plan-build.py on every recorded `--reverted` hook input (fix-r2 / fix-r3 hooks) in a mini export,
# and compares exit code + stdout. Expected: identical, hook by hook (the u2ios hooks revert L3's U2-ios, which is
# still a unit; L5's held U2-ios is a different unit).
# Usage: python3 tools/titan/results/wave54-S1/fix/hooks-check.py <scratch-export-dir>
import glob, os, shutil, subprocess, sys
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..'))   # repo root
EXP = os.path.abspath(sys.argv[1]); PLAN = f'{EXP}/tools/titan/results/wave54-plan'
shutil.rmtree(EXP, ignore_errors=True)
shutil.copytree(f'{R}/tools/titan/results/wave54-plan', PLAN, symlinks=True)                # HERE-relative inputs
os.symlink(f'{R}/tools/titan/runs', f'{EXP}/tools/titan/runs')                               # RUNDIR (capture existence)
head = subprocess.run(['git', 'show', 'HEAD:tools/titan/results/wave54-plan/plan-build.py'], cwd=R,
                      capture_output=True, text=True).stdout
open(f'{PLAN}/plan-build.head.py', 'w').write(head)                                          # same dir => same HERE
def run(script, hook, tag):
    """(rc, stdout, last stderr line) of one plan-build run with --reverted <hook> into a fresh --out dir."""
    out = f'{EXP}/out-{tag}'; shutil.rmtree(out, ignore_errors=True); os.makedirs(out)
    p = subprocess.run(['python3', f'{PLAN}/{script}', '--out', out, '--reverted', hook], capture_output=True, text=True)
    return p.returncode, p.stdout, (p.stderr.strip().splitlines() or [''])[-1][:160]
for hook in sorted(glob.glob(f'{PLAN}/fix-r[23]/hooks/*.reverted.json')):                     # every recorded hook input
    name = os.path.relpath(hook, PLAN)
    a, b = run('plan-build.head.py', hook, 'head'), run('plan-build.py', hook, 'new')
    same = a[0] == b[0] and a[1] == b[1]
    print(f'{name}: HEAD rc {a[0]} · fixed rc {b[0]} · stdout equal {a[1] == b[1]} -> {"SAME" if same else "DIFFERS"}'
          + (f' | stderr: {b[2]}' if b[0] else ''))
