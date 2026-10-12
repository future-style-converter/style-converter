a = "new Set(['ABSOLUTE', 'FIXED'])"
b = "new Set(['ABSOLUTE'])  /* MUTATION W2 */"
assert s.count(a) == 1, a
s = s.replace(a, b)
