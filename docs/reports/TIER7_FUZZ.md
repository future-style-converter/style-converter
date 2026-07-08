# Tier 7 — CSS parser fuzz testing

Random + malformed CSS input → parser fails gracefully (returns null,
never panics). Valid edge cases don't crash any renderer.

| # | case | css input | expected | status | notes |
|---|---|---|---|---|---|
| 1 | empty_calc | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested |
| 2 | calc_div_zero | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested |
| 3 | zero_stop_gradient | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested |
| 4 | no_arg_transform | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested |
| 5 | unbalanced_paren | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested |
| 6 | numeric_overflow | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested |
| 7 | unicode_in_keyword | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested |
| 8 | comment_in_value | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested |
| 9 | css_var_recursive | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested via gradle convert |
| 10 | mixed_units_in_calc | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested via gradle convert |
| 11 | negative_clamp | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested via gradle convert |
| 12 | malformed_color | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested |
| 13 | over_long_string | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested via gradle convert |
| 14 | empty_content | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested via gradle convert |
| 15 | javascript_url_scheme | (see fixture) | parse-or-null | **parsed-or-rejected (no crash)** | auto-tested via gradle convert |
