a = "if (shifts.length === 1 && shifts[0] === delta)"
assert s.count(a) == 1; s = s.replace(a, "if (true /* MUTATION M3: the uniform-shift check dropped */)")
