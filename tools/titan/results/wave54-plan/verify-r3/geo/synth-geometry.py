#!/usr/bin/env python3
# fix r3 (plan-skeptic round 3, R3-S2): geometry-gate JSONs for adjudicate.mjs --geometry, built from REAL probe output.
# Base: fix-r3/geogate-wave53-final.json (geometry-gate.py wave53-final --base wave53-open --json, this pass). Its run/base
# are relabelled wave54-final / wave54-open (the pair of the synthetic closing records, fix-r3/synth-closing-r3.mjs), and its
# nine hyphenate-character.geometry.py rows are replaced by the probe's OWN lines on a planning replay
# (`hyphenate-character.geometry.py --replay <tag>`, decoding hyphenate-character-replay/*.png), the verdict recomputed
# with geometry-gate.py's rule (the normalised line ends in the key's expect string). Every other row stays wave53-final's
# real row: adjudicate reads only the hyphenate rows, and nothing here claims those other pictures.
#   geometry-allmet.json  GB  = U1 + U2 + U3 (the correct L3 picture): 9/9 "→ GEOMETRY OK"
#   geometry-u3only.json  B   = U3 alone (the DEGENERATE -003 ios pass of replay B 0.9566): 001/003 ios WRONG
#   geometry-u2ios.json   web/android from GB, ios from B (U1 + U2-android + U3 with U2-ios reverted)
#   geometry-otherpair.json  the base JSON unrelabelled (wave53-final / wave53-open): adjudicate must refuse it
import copy, json, os, re, subprocess, sys
HERE = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave54-plan/verify-r3/geo'
PLAN = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave54-plan'
base = json.load(open(f'{HERE}/geogate-wave53-final.json'))
EXP = json.load(open(f'{PLAN}/expectations.json'))
probe = next(p for p in EXP['lanes']['L3-hyphenate-character']['geometryProbes'] if 'hyphenate-character.geometry.py' in p['cmd'])
norm = lambda s: re.sub(r'\s+', ' ', s.strip())


def replay(tag):
    out = subprocess.run(['nice', '-n', '19', 'python3', f'{PLAN}/hyphenate-character.geometry.py', '--replay', tag],
                         capture_output=True, text=True, check=True).stdout
    return {' '.join(norm(l).split()[:2]): norm(l) for l in out.splitlines() if l.strip()}


lines = {tag: replay(tag) for tag in ('GB', 'B')}


def build(name, tag_for, relabel=True):
    g = copy.deepcopy(base)
    if relabel:
        g['run'], g['base'] = 'wave54-final', 'wave54-open'
        g['synthetic'] = (f'fix-r3/synth-geometry.py {name}: wave53-final rows, hyphenate-character.geometry.py rows from '
                          f'--replay {{{", ".join(f"{p}: {t}" for p, t in tag_for.items())}}}')
    n = 0
    for r in g['rows']:
        if r['script'] != 'hyphenate-character.geometry.py' or not relabel:
            continue
        plat = r['key'].split()[-1]
        r['line'] = lines[tag_for[plat]][r['key']]
        r['verdict'] = 'PASS' if r['line'].endswith(norm(probe['expect'][r['key']])) else 'FAIL'
        n += 1
    json.dump(g, open(f'{HERE}/{name}.json', 'w'), indent=1, ensure_ascii=False)
    hy = [f"{r['key']} {r['verdict']}" for r in g['rows'] if r['script'] == 'hyphenate-character.geometry.py']
    print(f"{name}: run {g['run']} base {g['base']} · hyphenate rows replaced {n}: " + '; '.join(hy))


build('geometry-allmet', {'web': 'GB', 'ios': 'GB', 'android': 'GB'})
build('geometry-u3only', {'web': 'B', 'ios': 'B', 'android': 'B'})
build('geometry-u2ios', {'web': 'GB', 'ios': 'B', 'android': 'GB'})
build('geometry-otherpair', {}, relabel=False)
