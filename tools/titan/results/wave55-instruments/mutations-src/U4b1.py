import re
i = s.index('**Renderer latitude'); j = s.index('\n\n## 5.', i)
para = s[i:j]; s = s[:i].rstrip('\n') + s[j:]          # the unnumbered paragraph removed …
for k in range(7, 2, -1): s = s.replace(f'\n{k}. **', f'\n{k + 1}. **', 1)   # rules 3-7 → 4-8
body = '\n   '.join(para.split('\n'))
s = s.replace('\n4. **Additive, not a break.**', '\n3. ' + body + '\n4. **Additive, not a break.**', 1)   # … inserted as rule 3
