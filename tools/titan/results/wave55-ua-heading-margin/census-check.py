#!/usr/bin/env python3
# Wave 55 lane L4 — cross-check a census read-out (census-jvm.<run>.out.txt, or the Swift twin's
# census-swift.<run>.out.txt) against expectations.json lanes["L4-ua-heading-margin"]: the U1 /
# U2 carrier documents the shipped code fires on vs captureCarriers and each revertUnits entry.
# A census that GROWS is a plan-regeneration need (PLAN §0 "Skeptic census"); exit 1 on any
# difference so the note cannot call a grown census identical.
#   census-check.py <census.out.txt> [platform: android|ios, default from the file name]
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
EXP = os.path.join(HERE, '..', 'wave55-plan', 'expectations.json')
path = sys.argv[1]
platform = sys.argv[2] if len(sys.argv) > 2 else ('ios' if 'swift' in os.path.basename(path) else 'android')
lane = json.load(open(EXP))['lanes']['L4-ua-heading-margin']
lines = open(path, encoding='utf-8').read().splitlines()

def stems(prefix):
    # "U1-DOC css-text-decor/wpt__…" → the capture stem (basename), sorted.
    return sorted(l.split(' ', 1)[1].split('/')[-1] for l in lines if l.startswith(prefix + ' '))

got = {'U1': stems('U1-DOC'), 'U2': stems('U2-DOC')}
units = {'U1': f'U1-{platform}', 'U2': f'U2-{platform}'}
bad = 0
print(f'census {os.path.basename(path)} platform={platform}')
print(next((l for l in lines if l.startswith('SUMMARY')), 'SUMMARY missing'))
for u, unit in units.items():
    want = sorted(lane['revertUnits'][unit]['captures'][platform])
    grew, shrank = sorted(set(got[u]) - set(want)), sorted(set(want) - set(got[u]))
    verdict = 'IDENTICAL' if not grew and not shrank else f'GREW {grew} SHRANK {shrank}'
    bad += verdict != 'IDENTICAL'
    print(f'{unit}: census {len(got[u])} vs expectations {len(want)} → {verdict}')
union = sorted(set(got['U1']) | set(got['U2']))
want_union = sorted(lane['captureCarriers'][platform])
uv = 'IDENTICAL' if union == want_union else f'DIFFERS census {union} vs {want_union}'
bad += uv != 'IDENTICAL'
print(f'captureCarriers.{platform}: {len(union)} vs {len(want_union)} → {uv}')
print(f'wireCarriers: expectations {lane["wireCarriers"]} (runtime-only units: no converter / extractor byte moves)')
# The out-of-flow UA-margin components the step saw, by outcome.
oof = [l for l in lines if l.startswith('OOF ')]
fires = [l for l in oof if ' fires=true ' in l]
print(f'out-of-flow UA-margin-tag components: {len(oof)} in {len({l.split()[1] for l in oof})} documents; '
      f'firing {len(fires)} in {len({l.split()[1] for l in fires})} documents')
for kind in ('decl=TB', 'root=true', 'consumed=true', 'ANOMALY', 'faceIndependent=false'):
    print(f'  {kind}: {sum(kind in l for l in oof)}')
print('  non-heading tags:', sorted({re.search(r"tag=(\S+)", l).group(1) for l in oof} - {f"h{i}" for i in range(1, 7)}))
for tag in ('UNEXPECTED-FIRE', 'DECODE-FAIL'):
    n = sum(l.startswith(tag) for l in lines)
    bad += n
    print(f'{tag}: {n}')
print('VERDICT', 'IDENTICAL to expectations.json' if not bad else f'{bad} difference(s) — plan regeneration need')
sys.exit(1 if bad else 0)
