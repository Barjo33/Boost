#!/usr/bin/env python3
"""Track the primer across its configuration changes.

Three eras so far, all on Boost-V7-shadow and this user only:

  A  up to 2026-07-30 12:00   original sizing. Floored-ratio gate let jitter through, and
                              PRIMER_MAX_MULT could push the dose past the base, so it saturated.
  B  2026-07-30 12:00 onward  trigger + sizing rework: absolute delta floor of 3, and
                              dose = cap x fRise x fBg x fIob with the cap a true ceiling.
  C  2026-08-01 onward        cap raised 0.3 -> 0.5 U. Sizing unchanged.

Because the dose is linear in the cap, era C should scale era B by 5/3 and change nothing else.
Firing RATE and the delta at firing are set by the gate, not the cap, so if either moves the
cause is elsewhere and worth chasing.

Usage: primer_watch.py [days_back]
"""
import sys, re, datetime as dt, numpy as np, psycopg2, collections

ERA_B = dt.datetime(2026, 7, 30, 12, 0, tzinfo=dt.timezone.utc)
ERA_C = dt.datetime(2026, 8, 1, 0, 0, tzinfo=dt.timezone.utc)
days_back = int(sys.argv[1]) if len(sys.argv) > 1 else 30

with psycopg2.connect("dbname=oref host=127.0.0.1 port=5432") as c, c.cursor() as cur:
    cur.execute("""select ts_utc, reason_text from boost_decisions
                   where user_id='tim' and reason_text ilike '%%primer=%%'
                     and ts_utc >= now() - interval '%s days' order by ts_utc""" % days_back)
    rows = cur.fetchall()

seen, ev = set(), []
for t, txt in rows:
    k = t.replace(second=0, microsecond=0)
    if k in seen: continue                      # each cycle uploads twice
    seen.add(k)
    m = re.search(r"primer=(\w+),([0-9.]+)U", txt)
    if not m: continue
    sc = re.search(r"primerScale=d=([-0-9.]+),fR=([0-9.]+),fB=([0-9.]+),fI=([0-9.]+),tgt=([0-9.]+)", txt)
    pl = re.search(r"plateau=[^,]*,[^,]*,([0-9.]+),([-0-9.]+),", txt)
    ev.append(dict(t=t, dose=float(m.group(2)),
                   d=float(sc.group(1)) if sc else None,
                   fR=float(sc.group(2)) if sc else None,
                   fB=float(sc.group(3)) if sc else None,
                   fI=float(sc.group(4)) if sc else None,
                   tgt=float(sc.group(5)) if sc else None,
                   bg=float(pl.group(1)) if pl else None,
                   pdelta=float(pl.group(2)) if pl else None))

def era(e): return "A" if e["t"] < ERA_B else ("B" if e["t"] < ERA_C else "C")
groups = collections.defaultdict(list)
for e in ev: groups[era(e)].append(e)

print(f"{len(ev)} primer firings in the last {days_back} days\n")
print(f"  {'era':>3s} {'n':>4s} {'per day':>8s} {'median U':>9s} {'max U':>7s} {'U/day':>7s} "
      f"{'med delta':>10s} {'min delta':>10s} {'fired <=0':>10s}")
for k in ("A", "B", "C"):
    g = groups.get(k)
    if not g: continue
    span = max((max(x["t"] for x in g) - min(x["t"] for x in g)).total_seconds()/86400, 0.5)
    dl = [x["pdelta"] for x in g if x["pdelta"] is not None]
    print(f"  {k:>3s} {len(g):4d} {len(g)/span:8.1f} {np.median([x['dose'] for x in g]):9.2f} "
          f"{max(x['dose'] for x in g):7.2f} {sum(x['dose'] for x in g)/span:7.2f} "
          f"{(np.median(dl) if dl else float('nan')):10.1f} {(min(dl) if dl else float('nan')):10.1f} "
          f"{sum(1 for x in dl if x <= 0):10d}")

# implied cap, recovered from the logged scale factors — checks the setting actually took
print(f"\n  implied cap from tgt / (fRise x fBg x fIob):")
for k in ("B", "C"):
    g = [x for x in groups.get(k, []) if x["tgt"] and x["fR"] and x["fB"] and x["fI"]]
    if not g: continue
    caps = [x["tgt"]/(x["fR"]*x["fB"]*x["fI"]) for x in g]
    print(f"    era {k}: {np.median(caps):.2f} U   (n={len(g)}, range {min(caps):.2f}-{max(caps):.2f})")

recent = [e for e in ev if e["t"] >= ERA_C]
if recent:
    print(f"\n  era C firings so far:")
    for e in recent:
        s = (f"d={e['d']:.1f} fR={e['fR']:.2f} fB={e['fB']:.2f} fI={e['fI']:.2f}"
             if e["d"] is not None else "(no scale detail)")
        print(f"    {e['t']:%m-%d %H:%M}  {e['dose']:.2f}U  {s}  BG {e['bg'] if e['bg'] else float('nan'):.0f}")
else:
    print(f"\n  no era C firings yet.")
print("\nPROVISIONAL — single user, observational, no counterfactual.")
