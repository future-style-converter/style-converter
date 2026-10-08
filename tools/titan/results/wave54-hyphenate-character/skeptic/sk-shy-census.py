#!/usr/bin/env python3
# Skeptic (wave 54, L3): every U+00AD-carrying text on the recorded wire (component `text` and every meta.runs text
# piece), by document — the population whose Compose fire decision moved from the wave-52 count to SoftHyphenCuts.took.
import json, glob, os, sys, collections, re
run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
root = os.path.join(os.path.dirname(__file__), '../../../runs', run, 'sections')
docs = collections.defaultdict(set); texts = set()
for p in sorted(glob.glob(os.path.join(root, '*/per-test-ir/*.json'))):
    stem = os.path.basename(p)[:-5]; d = json.load(open(p))
    for c in d['components']:
        cands = [c.get('text')] + [e.get('text') for e in ((c.get('meta') or {}).get('runs') or [])]
        for t in cands:
            if t and '­' in t: docs[stem].add(t); texts.add(t)
print(f'{len(docs)} documents carry U+00AD; {len(texts)} distinct texts')
for s in sorted(docs): print(' ', s, len(docs[s]))
pin = open(os.path.join(os.path.dirname(__file__), '../../../../../runtimes/compose/src/test/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipelineTest.kt'), encoding='utf-8').read()
missing = [t for t in sorted(texts) if t not in pin and t.replace('\n', '\\n') not in pin]
print(f'texts NOT literally in PreBreakPipelineTest.kt: {len(missing)}')
for t in missing: print('   ', repr(t)[:140])
