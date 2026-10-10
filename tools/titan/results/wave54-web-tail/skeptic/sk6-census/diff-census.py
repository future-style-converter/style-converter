# Skeptic L6 sk6: diff two own-census JSONs; per changed doc, report the separator delta and the W1 shape.
import json, sys, re, difflib
a = json.load(open(sys.argv[1])); b = json.load(open(sys.argv[2]))
for kind in ('perTest', 'canvas'):
    A, B = a[kind], b[kind]
    assert set(A) == set(B), 'key sets differ'
    ch = sorted(k for k in A if A[k] != B[k])
    print(f'{kind}: {len(A)} docs, changed {len(ch)}')
    for k in ch:
        x, y = A[k], B[k]
        # Characterize: count of '> <' (a root-level single space between two elements) delta and length delta.
        sp = y.count('> <') - x.count('> <')
        print(f'   {k}  len{len(y)-len(x):+d}  "> <"{sp:+d}' + ('  THROW' if y.startswith('THROW') or x.startswith('THROW') else ''))
