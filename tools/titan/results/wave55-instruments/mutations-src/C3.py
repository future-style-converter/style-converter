a = "return walk(text, lines, hyphenChar) ?: legacyCount(text, lines, hyphenChar)"
assert s.count(a) == 1; s = s.replace(a, "return walk(text, lines, hyphenChar) ?: false // MUTATION C3")
