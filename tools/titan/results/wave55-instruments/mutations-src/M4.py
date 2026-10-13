a = 'else OUT="$(abs_nocreate "${POS[2]}")"; fi'
b = '[[ -n "$OUT" && "$OUT" == /* ]] || refuse "out-dir ${POS[2]} did not resolve to an absolute path"\n'
assert s.count(a) == 1 and s.count(b) == 1
s = s.replace(a, 'else OUT="${POS[2]}"; fi   # MUTATION M4: absolutization dropped').replace(b, '')
