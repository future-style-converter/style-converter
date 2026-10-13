a = "  return a <= 0;\n"
b = "  return a < 0.5; // MUTATION W4\n"
assert s.count(a) == 1, a
s = s.replace(a, b)
