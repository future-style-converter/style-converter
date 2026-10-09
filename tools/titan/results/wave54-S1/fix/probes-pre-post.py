#!/usr/bin/env python3
# wave54-S1/fix/probes-pre-post.py — the S1-fix left-edge rule must change NO recorded verdict: runs the HEAD
# (pre-fix, `git show HEAD:`) and the working-tree (fixed) [M] probes on the six recorded invocations and diffs them.
# Pre copies go to <scratch>; geometry_common is the tree's (its ROOT = the real repo, so captures + refs resolve).
# Usage: python3 tools/titan/results/wave54-S1/fix/probes-pre-post.py <scratch-dir>
import os, subprocess, sys
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..'))     # repo root
SCR = os.path.abspath(sys.argv[1])
PROBES = (('tools/titan/results/wave54-plan/rtl-marker-bake.geometry.py',                      # [M] probe 1 + its runs
           ('wave53-open wave53-open', 'wave53-probe wave53-open', 'wave53-final wave53-open')),
          ('tools/titan/results/wave53-plan/lists-bakes.geometry.py',                          # [M] probe 2 + its runs
           ('wave52-ship', 'wave53-probe wave53-open', 'wave53-final wave53-open')))
env = dict(os.environ, PYTHONPATH=f'{R}/tools/titan/results/wave53-plan')                       # the pre copy finds helpers
def run(script, a):
    """stdout + exit code of one probe invocation (cwd = repo root, as geometry-gate.py runs it)."""
    p = subprocess.run(['python3', script] + a.split(), cwd=R, env=env, capture_output=True, text=True)
    return p.stdout + p.stderr + f'exit {p.returncode}\n'
pre_all, post_all = '', ''
for rel, runs in PROBES:
    pre = f'{SCR}/pre/{rel}'; os.makedirs(os.path.dirname(pre), exist_ok=True)
    open(pre, 'w').write(subprocess.run(['git', 'show', f'HEAD:{rel}'], cwd=R, capture_output=True, text=True).stdout)
    for a in runs:
        head = f'== {os.path.basename(rel)} {a}\n'                                          # one block per invocation
        pre_all += head + run(pre, a); post_all += head + run(f'{R}/{rel}', a)
print(post_all, end='')                                                                      # the fixed probes' lines
print(f'PRE == POST over the six invocations: {pre_all == post_all} ({post_all.count(chr(10))} lines)')
