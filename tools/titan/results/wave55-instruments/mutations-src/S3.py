a = "        case .object: return true"
assert s.count(a) == 1; s = s.replace(a, "        case .object: return false // MUTATION S3")
