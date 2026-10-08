#!/usr/bin/env python3
# S1 pointer audit: every path-shaped token in the lane notes must resolve in the landed tree
# (repo-relative, or relative to the lane dir for bare file names); `…`-abbreviated paths are
# resolved by glob on their visible prefix/suffix.
import re, os, sys, glob, subprocess
ROOT = sys.argv[1]
lanes = ['lists-bakes', 'soft-hyphen', 'canvas-root', 'float-avoid', 'harness-hygiene']
tracked = set(subprocess.run(['git', '-C', ROOT, 'ls-files'], capture_output=True, text=True).stdout.split('\n'))
EXT = r'(?:mjs|js|ts|tsx|kt|swift|py|sh|json|md|txt|log|patch|yml|png|tmpl|out|pbxproj|html)'
pat = re.compile(r'(?<![\w./-])((?:[\w.@-]+/)*[\w.@…{},*-]+\.' + EXT + r')(?![\w])')
for lane in lanes:
    d = f'tools/titan/results/wave53-{lane}'
    for note in ('_note.md', 'skeptic.md'):
        p = f'{ROOT}/{d}/{note}'
        if not os.path.exists(p): continue
        txt = open(p, encoding='utf8').read()
        miss = []; seen = set(); n = 0
        for m in pat.finditer(txt):
            tok = m.group(1).strip('.,;:)')
            if tok in seen or tok.startswith(('/tmp', 'http')) or '{' in tok or '*' in tok or '<' in tok: continue
            seen.add(tok); n += 1
            cands = [tok, f'{d}/{tok}', f'tools/titan/results/{tok}', f'tools/titan/{tok}', f'tools/titan/results/wave53-plan/{tok}']
            ok = any(os.path.exists(f'{ROOT}/{c}') for c in cands)
            if not ok and '…' in tok:
                pre, _, suf = tok.partition('…')
                ok = any(t.startswith(pre.rstrip('/')) and t.endswith(suf.lstrip('/')) for t in tracked if t) or bool(glob.glob(f'{ROOT}/**/*{suf.lstrip("/")}', recursive=False))
                if not ok: ok = any(t.endswith(suf.lstrip('/')) for t in tracked)
            if not ok:
                # bare basename anywhere in the tracked tree or the runs dir
                b = os.path.basename(tok)
                ok = any(t.endswith('/' + b) or t == b for t in tracked)
                if ok: continue
                miss.append(tok)
        print(f'{lane}/{note}: {n} path tokens, {len(miss)} unresolved: {miss}')
