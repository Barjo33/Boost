#!/usr/bin/env python3
"""House-style gate on the generated report.

Calm and factual British prose. No em-dashes, no bold, no rhetorical triplets, no
sensationalist intensifiers.
"""
import re, sys, os
p = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                  "..", "..", "reports", "2026-07_cgm_cadence_report.md"))
text = open(p).read()
lines_all = text.split("\n")
body, in_code = [], False
for l in lines_all:
    if l.strip().startswith("```"): in_code = not in_code; continue
    if in_code or l.strip().startswith("|"): continue
    body.append(l)
fails = []
def flag(name, pattern, lines=None, flags=0):
    hits = []
    for i, l in enumerate(lines if lines is not None else body, 1):
        for m in re.finditer(pattern, l, flags):
            hits.append((i, l.strip()[:100]))
    if hits: fails.append((name, hits))

flag("em-dash or en-dash used as punctuation", r"[—–]", text.split("\n"))
flag("bold markup", r"\*\*")
flag("sensationalist intensifier",
     r"\b(dramatic\w*|striking\w*|remarkabl\w*|stunning\w*|huge|massive|decisive\w*|"
     r"settles it|beautiful\w*|crucial\w*|vastly|enormous\w*)\b", flags=re.I)
flag("rhetorical triplet (X, Y and Z)",
     r"\b\w+(?:\s+\w+){0,2}, \w+(?:\s+\w+){0,2},? and \w+")
flag("American spelling", r"\b(normalize\w*|analyze\w*|behavior\w*|favor\w*|color\w*)\b", flags=re.I)

if fails:
    print("STYLE CHECK FAILED\n")
    for name, hits in fails:
        print(f"  {name}: {len(hits)}")
        for i, l in hits[:6]: print(f"    line {i}: {l}")
        print()
    sys.exit(1)
print(f"STYLE CHECK PASSED  ({len(text.split(chr(10)))} lines, "
      f"{len(text.split())} words)")
