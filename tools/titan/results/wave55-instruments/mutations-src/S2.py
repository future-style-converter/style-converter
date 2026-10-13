a = '            return s.uppercased().split(separator: " ").contains { claims.contains(String($0)) }\n'
assert s.count(a) == 1; s = s.replace(a, '            _ = s; return false // MUTATION S2: the string branch answers false\n')
