#!/usr/bin/env python3
"""Refresh boost_decisions for every registered site, incrementally.

Site base URL and token are read at RUNTIME from the private registry and never printed.
Each site resumes from its own latest row in the DB, less a small overlap so a partially
written window is repaired rather than left with a hole.
"""
import json, os, subprocess, sys, datetime as dt, psycopg2

REG = os.path.expanduser("~/.config/boost_backtest/sites.json")
DSN = "dbname=oref host=127.0.0.1 port=5432"
HERE = os.path.dirname(os.path.abspath(__file__))
OVERLAP_H = 6
FALLBACK_DAYS = 14

sites = json.load(open(REG))["sites"]
only = sys.argv[1:] or None
with psycopg2.connect(DSN) as c, c.cursor() as cur:
    cur.execute("select user_id, max(ts_utc) from boost_decisions group by 1")
    latest = dict(cur.fetchall())

for s in sites:
    tag = s["tag"]
    uid = "tim" if tag == "self" else tag
    if only and tag not in only and uid not in only:
        continue
    last = latest.get(uid)
    since = ((last - dt.timedelta(hours=OVERLAP_H)) if last
             else dt.datetime.now(dt.UTC) - dt.timedelta(days=FALLBACK_DAYS))
    since_s = since.astimezone(dt.UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
    print(f"[{uid}] from {since_s} (had {last})", flush=True)
    r = subprocess.run(
        [sys.executable, os.path.join(HERE, "boost_extractor.py"),
         "--url", s["base"], "--token", s.get("token", ""),
         "--user-id", uid, "--since", since_s],
        capture_output=True, text=True)
    tail = [l for l in (r.stdout + r.stderr).splitlines() if l.strip()][-2:]
    for l in tail:
        # never echo a URL or token back out
        line = l.replace(s["base"], "<site>")
        tok = s.get("token") or ""
        if tok:                       # an empty token would match at every character boundary
            line = line.replace(tok, "<token>")
        print("   ", line)
    if r.returncode != 0:
        print(f"    FAILED rc={r.returncode}")
