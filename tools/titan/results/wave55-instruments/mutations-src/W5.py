a = "    : (typeof raw === 'string' && /^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$/.test(raw)) ? Number(raw) : NaN;"
b = "    : NaN; // MUTATION W5: the decimal-string branch dropped"
assert s.count(a) == 1, a
s = s.replace(a, b)
