#!/usr/bin/env python3
# S1 fix pass: the S1 skeptic's "every line commented" heuristic (wave54-S1/comments.py, same
# regexes) over THIS pass's added source lines — working tree vs HEAD, the new module whole.
import subprocess, re
COMMENT = re.compile(r'^\s*(//|/\*|\*|\*/|#)')
TRAIL = re.compile(r'(//|/\*).*$')
SYNTAX = re.compile(r'^\s*([{}()\[\];,]+|\)\s*[{;]?|\}\s*(else\s*\{)?|\]\s*[,;)]?|import .*|package .*|export \{.*\}.*|\)\s*:\s*\w+.*\{|return\s*;?)\s*$')
def added(f):
    # The new module is untracked: every line of it is "added".
    if f == 'tools/titan/bidi-marker-paint.mjs':
        return ['+' + l for l in open(f, encoding='utf-8').read().splitlines()]
    return subprocess.run(['git', 'diff', '-U0', 'HEAD', '--', f], capture_output=True, text=True).stdout.splitlines()
tot = 0
for f in ['tools/titan/bidi-marker-paint.mjs', 'tools/titan/bidi-marker-bake.mjs', 'tools/titan/bidi-bake.mjs']:
    u = c = 0; un = []; prev = False
    for line in added(f):
        if line.startswith('@@'): prev = False; continue
        if not line.startswith('+') or line.startswith('+++'): continue
        t = line[1:]
        if not t.strip(): continue
        if COMMENT.match(t): prev = True; continue
        if SYNTAX.match(t) or TRAIL.search(t.split('"')[0] if '"' in t else t) or prev: c += 1
        else: u += 1; un.append(t.strip()[:90])
        prev = bool(TRAIL.search(t))
    tot += u
    print(f'{u:3d} uncommented / {c:3d} commented-or-syntax  {f}')
    for x in un: print('      ·', x)
print('TOTAL uncommented by this heuristic:', tot)
