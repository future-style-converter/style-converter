a = "GreedyLineBreaker.clampLines(lines, GreedyLineBreaker.Clamp(it), wrapWidthPx, measure, source = text, hyphenChar = hyphenChar)"
assert s.count(a) == 1; s = s.replace(a, "GreedyLineBreaker.clampLines(lines, GreedyLineBreaker.Clamp(it), wrapWidthPx, measure, source = text) // MUTATION C2")
