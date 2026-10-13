a = "  return primitiveContent(o.keyword) ?? primitiveContent(o.value);"
b = "  return null; // MUTATION W6: keywordOf ignores objects"
assert s.count(a) == 1, a
s = s.replace(a, b)
