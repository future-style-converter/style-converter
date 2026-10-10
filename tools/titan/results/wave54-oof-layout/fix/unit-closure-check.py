#!/usr/bin/env python3
# unit-closure-check.py — L4 fix pass (skeptic defect 2): is every L4 revert unit CLOSED over the repo files its own tests
# read? Parses the L4 block of a land-units.sh (default: the orchestrator's installed copy; argv[1] overrides, e.g. the
# patched copy fix/land/land-units.patched.sh), expands $KT/$KTT/$SW/$SWT/$CR_KT/$L4, and for every test file in a unit's
# commit (or carried by the unit's seam patch, for a patch-borne test) collects each repo-relative path literal it names
# outside comments. A path read by a unit's test must be committed by THAT unit (or be gitignored — the wave53-final corpus,
# guarded by assume / XCTSkip). A lane-dir path committed by a unit other than its reader, or left to "$L4" inside another
# unit, is a closure FAIL: reverting the other unit would delete a file the reader needs. Exit 0 = closed, 1 = a FAIL.
import os, re, subprocess, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..'))
src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'tools/titan/results/wave54-plan/land-units.sh')
text = open(src).read()
V = {'R': 'tools/titan/results', 'KT': 'runtimes/compose/src/main/java/com/styleconverter/runtime',
     'KTT': 'runtimes/compose/src/test/java/com/styleconverter/runtime',
     'SW': 'runtimes/swiftui/Sources/StyleConverterRuntime', 'SWT': 'runtimes/swiftui/Tests/StyleConverterRuntimeTests'}
V['CR_KT'] = V['KT'] + '/core/renderer/ComponentRenderer.kt'; V['L4'] = V['R'] + '/wave54-oof-layout'
expand = lambda s: re.sub(r'\$\{?(\w+)\}?', lambda m: V[m.group(1)], s)
block = text[text.index('L4=$R/wave54-oof-layout'):text.index('# ── L3')]
units, seam_of, pending_seam = [], {}, None
lines = block.split('\n')
for i, ln in enumerate(lines):
    m = re.match(r'apply "([^"]+)"', ln)
    if m: pending_seam = expand(m.group(1))
    m = re.match(r'commit "wave54 L4 ([\w-]+):', ln)
    if m:
        j = i + 1
        while not lines[j].startswith('  "'): j += 1          # the path line follows the body line(s)
        paths = [expand(p) for p in re.findall(r'"([^"]+)"', lines[j])]
        units.append((m.group(1), paths)); seam_of[m.group(1)] = pending_seam; pending_seam = None
owner = {}
for u, ps in units:
    for p in ps: owner.setdefault(p, u)
def committed_by(path):
    # The unit whose commit holds `path`: an exact path, else the first unit whose committed DIRECTORY contains it.
    if path in owner: return owner[path]
    for u, ps in units:
        if any(os.path.isdir(os.path.join(ROOT, p)) and path.startswith(p.rstrip('/') + '/') for p in ps): return u
    return None
def ignored(path):
    return subprocess.run(['git', '-C', ROOT, 'check-ignore', '-q', path]).returncode == 0
def body(path, seam):
    f = os.path.join(ROOT, path)
    if os.path.isfile(f): return open(f).read()
    if seam:                                                  # a patch-borne test: its + lines in the seam patch
        s = open(os.path.join(ROOT, seam)).read(); k = s.find('+++ b/' + path)
        if k >= 0: return '\n'.join(l[1:] for l in s[k:].split('\n')[1:] if l.startswith('+') and not l.startswith('+++'))
    return None
lit = re.compile(r'"((?:runtimes|tools|apps|converter|schema|fixtures)/[^"\s]+)"')
fails = 0
for u, ps in units:
    tests = [p for p in ps if re.search(r'(Test|Tests)\.(kt|swift)$', p)]
    print(f'{u}: commits {len(ps)} path(s); tests {[os.path.basename(t) for t in tests]}; seam {seam_of[u] or "-"}')
    for t in tests:
        b = body(t, seam_of[u])
        if b is None: print(f'   FAIL {t}: not in the tree nor in the unit\'s seam patch'); fails += 1; continue
        code = '\n'.join(l for l in b.split('\n') if not l.lstrip().startswith(('//', '*', '/*')))
        for p in sorted(set(lit.findall(code))):
            if ignored(p): print(f'   ok   {os.path.basename(t)} reads {p} (gitignored, guarded)'); continue
            c = committed_by(p)
            if c == u: print(f'   ok   {os.path.basename(t)} reads {p} (committed by {u})')
            else: print(f'   FAIL {os.path.basename(t)} reads {p} — committed by {c or "NO unit"}, not {u}'); fails += 1
print('closure:', 'HOLDS' if not fails else f'{fails} FAIL(s)')
sys.exit(1 if fails else 0)
