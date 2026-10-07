#!/usr/bin/env python3
"""wave 53 · L5 — per-target build-phase diff of two XcodeGen-generated pbxprojs.

Usage: pbxproj-phases.py <base.pbxproj> <after.pbxproj>. Prints, per native
target and phase type, SAME (with the file count) or CHANGED (both lists).
Path-independent: it compares the build-file NAMES each phase holds, so two
projects generated in different directories compare cleanly. Exit 1 when the
APP target (StyleConverterTest) changed in any phase — the gate builds only it.
"""
import re, sys
def phases(p):
    s = open(p).read()
    out = {}
    for m in re.finditer(r'(\w{24}) /\* (\w+) \*/ = \{\s*isa = (PBX\w+BuildPhase);.*?files = \((.*?)\);', s, re.S):
        out[m.group(1)] = (m.group(3), sorted(re.findall(r'/\* (.*?) in \w+ \*/', m.group(4))))
    tg = {}
    for m in re.finditer(r'\w{24} /\* (\S+) \*/ = \{\s*isa = PBXNativeTarget;.*?buildPhases = \((.*?)\);', s, re.S):
        tg[m.group(1)] = [out[i] for i in re.findall(r'(\w{24})', m.group(2)) if i in out]
    return tg
b, a = phases(sys.argv[1]), phases(sys.argv[2])
app_changed = False
for t in sorted(set(a) | set(b)):
    kinds = sorted({k for k, _ in a.get(t, [])} | {k for k, _ in b.get(t, [])})
    for k in kinds:
        fa = [f for (i, f) in a.get(t, []) if i == k]; fb = [f for (i, f) in b.get(t, []) if i == k]
        same = fa == fb
        if not same and t == 'StyleConverterTest': app_changed = True
        print(f'{t:26} {k:32} ' + (f'SAME ({len(fa[0])} files)' if same else f'CHANGED base={fb} after={fa}'))
sys.exit(1 if app_changed else 0)
