#!/usr/bin/env python3
"""'Every line commented' heuristic over the ADDED lines of the landed range (source files only, tests excluded):
an added code line counts as commented when it carries a trailing comment, or the nearest non-blank line above it
(within the same added block) is a comment / doc line, or it is pure syntax (braces, `)`, `]`, `import`, `package`,
`case`/`default` labels already under a comment)."""
import subprocess, re, sys
base, head = sys.argv[1], sys.argv[2]
files = subprocess.run(['git','diff','--name-only',base,head],capture_output=True,text=True).stdout.split()
src = [f for f in files if re.search(r'\.(kt|swift|ts|tsx|mjs)$', f) and not re.search(r'(Test|Tests|\.test)\.|/test/|/tests/|tools/titan/results/', f)]
COMMENT = re.compile(r'^\s*(//|/\*|\*|\*/|#)')
TRAIL = re.compile(r'(//|/\*).*$')
SYNTAX = re.compile(r'^\s*([{}()\[\];,]+|\)\s*[{;]?|\}\s*(else\s*\{)?|\]\s*[,;)]?|import .*|package .*|export \{.*\}.*|\)\s*:\s*\w+.*\{|return\s*;?)\s*$')
tot_c = tot_u = 0; rows = []
for f in src:
    d = subprocess.run(['git','diff','-U0',base,head,'--',f],capture_output=True,text=True).stdout.splitlines()
    prev_comment = False; u = c = 0
    for line in d:
        if line.startswith('@@'): prev_comment = False; continue
        if not line.startswith('+') or line.startswith('+++'): continue
        t = line[1:]
        if not t.strip(): continue
        if COMMENT.match(t): prev_comment = True; continue
        if SYNTAX.match(t) or TRAIL.search(t.split('"')[0] if '"' in t else t) or prev_comment: c += 1
        else: u += 1
        prev_comment = bool(TRAIL.search(t))
    tot_c += c; tot_u += u; rows.append((u, c, f))
for u, c, f in sorted(rows, reverse=True):
    print(f'{u:4d} uncommented / {c:4d} commented-or-syntax  {f}')
print(f'TOTAL added code lines: {tot_c+tot_u}; uncommented by this heuristic: {tot_u}')
