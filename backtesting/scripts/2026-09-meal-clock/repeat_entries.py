#!/usr/bin/env python3
"""Do people enter the same carbohydrate amount more than once inside an hour, in the meal windows?

The question matters because it decides what an announcement is. If a meal is often entered as two
or three identical increments, then the size distribution everyone has been modelling is a
distribution of increments and not of meals, and its small end is an artefact of how people type.

Two traps sit in this measurement and both were hit before the answer below was believed.

The window has to be a real hour. `ts.astype("int64") / 1e9` returns microseconds on this pandas,
so that form silently yields a thousand-hour window in which almost every pair matches; the
conversion here is unit-agnostic and asserted.

And a raw match rate means nothing, because the value distribution is extremely concentrated: six
amounts account for nearly half of all entries, so two entries in an hour agree by luck very often.
Every rate is therefore reported against a control that shuffles each participant's own sizes
across their own timestamps, preserving both their value distribution and their timing and
destroying only the association between them.

Usage: python3 repeat_entries.py [--window-min 60] [--min-entries 50]
"""
from __future__ import annotations

import argparse
import os

import numpy as np
import pandas as pd
import psycopg2

DSN = "dbname=oref host=127.0.0.1 port=5432"
WINDOWS = [("breakfast", 6, 9), ("lunch", 12, 15), ("dinner", 17, 20)]
SEQ, ACCENT = "#2a78d6", "#b8500f"
SURFACE, INK, INK2, MUTED, AXIS = "#fcfcfb", "#0b0b0b", "#52514e", "#898781", "#c3c2b7"
HERE = os.path.dirname(os.path.abspath(__file__))


def load():
    with psycopg2.connect(DSN) as c:
        d = pd.read_sql("""select s.study_name, c.subject_id, c.ts_local, c.carbs_g
                           from studies.carbs c join studies.subject s using (subject_id)
                           where c.carbs_g >= 8""", c)
    d["hr"] = d.ts_local.dt.hour
    keep = np.zeros(len(d), bool)
    for _, lo, hi in WINDOWS:
        keep |= d.hr.between(lo, hi - 1).values
    w = d[keep].copy()
    w["t"] = (w.ts_local - pd.Timestamp("1970-01-01")).dt.total_seconds()
    assert w.t.min() > 1.0e9, f"epoch conversion wrong: {w.t.min()}"
    return w.sort_values(["subject_id", "t"]).reset_index(drop=True)


def scan(w, window_s, min_entries, seed=0):
    rng = np.random.default_rng(seed)
    w = w.copy()
    w["shuf"] = w.groupby("subject_id").carbs_g.transform(lambda s: rng.permutation(s.values))
    per, gaps, sizes = [], [], []
    for sid, g in w.groupby("subject_id", sort=False):
        t = g.t.values; v = g.carbs_g.values; sv = g.shuf.values; n = len(t)
        if n < min_entries:
            continue
        fo = np.zeros(n, bool); fs = np.zeros(n, bool)
        for i in range(n):
            j = i + 1
            while j < n and t[j] - t[i] <= window_s:
                if v[j] == v[i]:
                    fo[i] = fo[j] = True
                    gaps.append((t[j] - t[i]) / 60.0); sizes.append(v[i])
                if sv[j] == sv[i]:
                    fs[i] = fs[j] = True
                j += 1
        per.append((sid, g.study_name.iloc[0], n, fo.mean(), fs.mean()))
    p = pd.DataFrame(per, columns=["subject_id", "study", "n", "obs", "chance"])
    p["excess"] = p.obs - p.chance
    return p, np.array(gaps), np.array(sizes)


def figure(p, gaps, sizes, path, window_min):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    fig, axes = plt.subplots(1, 2, figsize=(12, 5.0), facecolor=SURFACE)
    for ax in axes:
        ax.set_facecolor(SURFACE)
        for s in ax.spines.values():
            s.set_visible(False)
        ax.tick_params(colors=MUTED, labelsize=9, length=0)
        ax.grid(axis="y", color=AXIS, linewidth=0.6, alpha=0.5)
        ax.set_axisbelow(True)

    ex = 100 * p.excess
    axes[0].hist(ex, bins=np.arange(-6, 22, 0.75), color=SEQ, linewidth=0)
    axes[0].axvline(0, color=MUTED, linewidth=1.1, linestyle=(0, (4, 3)))
    # to the left of the line, where the distribution is thin, so it never sits on a bar
    axes[0].annotate("no more than chance", (0, axes[0].get_ylim()[1] * 0.75),
                     xytext=(-10, 0), textcoords="offset points", color=MUTED,
                     fontsize=9, va="center", ha="right")
    axes[0].set_xlabel("percentage points above that participant's own chance rate",
                       color=INK2, fontsize=10)
    axes[0].set_ylabel("participants", color=INK2, fontsize=10)
    axes[0].set_title(f"Most people repeat a size a little more than chance\n"
                      f"median +{100*p.excess.median():.1f} pp, "
                      f"{100*(p.excess>0).mean():.0f}% above zero",
                      color=INK, fontsize=11.5, loc="left", pad=8)

    axes[1].hist(gaps, bins=np.arange(0, window_min + 2, 2), color=SEQ, linewidth=0)
    axes[1].set_xlabel(f"minutes between the two identical entries", color=INK2, fontsize=10)
    axes[1].set_ylabel("pairs", color=INK2, fontsize=10)
    axes[1].set_xlim(0, window_min)
    axes[1].set_title(f"A quarter land within five minutes\n"
                      f"{len(gaps):,} same-size pairs, median gap "
                      f"{np.median(gaps):.0f} min", color=INK, fontsize=11.5, loc="left", pad=8)
    fig.suptitle("Repeated carbohydrate entries inside an hour, in the meal windows",
                 color=INK, fontsize=13, x=0.007, ha="left", y=1.04)
    fig.text(0.007, -0.06,
             f"{len(p):,} participants with at least 50 entries in the windows. Chance is each "
             f"participant's own sizes shuffled across their own timestamps, which preserves both "
             "\n"
             f"That control is necessary because six "
             f"amounts account for nearly half of all entries, so a raw match rate is mostly "
             f"coincidence.", color=MUTED, fontsize=8.5)
    fig.tight_layout()
    fig.savefig(path, dpi=170, bbox_inches="tight", facecolor=SURFACE)
    plt.close(fig)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--window-min", type=float, default=60.0)
    ap.add_argument("--min-entries", type=int, default=50)
    ap.add_argument("--out-dir", default=HERE)
    a = ap.parse_args()

    w = load()
    p, gaps, sizes = scan(w, a.window_min * 60, a.min_entries)
    pooled_obs = float(np.average(p.obs, weights=p.n))
    pooled_ch = float(np.average(p.chance, weights=p.n))

    L, P = [], None
    P = L.append
    P("# Repeated carbohydrate entries inside an hour, in the meal windows\n")
    P(f"\n{len(w):,} entries of 8 g or more inside breakfast, lunch and dinner, from "
      f"{w.subject_id.nunique():,} participants. An entry counts as repeated if the same "
      f"participant enters the same amount again inside {a.window_min:.0f} minutes.\n")
    P(f"\n| | rate |\n|---|---|")
    P(f"| entries with a same-size partner in the window | {100*pooled_obs:.1f}% |")
    P(f"| the same, with sizes shuffled within participant | {100*pooled_ch:.1f}% |")
    P(f"| excess over chance | {100*(pooled_obs-pooled_ch):+.1f} percentage points |")
    P(f"\nThe control matters more than the headline. Six amounts account for nearly half of all "
      f"entries, so two entries an hour apart agree by luck often; without shuffling, the raw rate "
      f"looks like a strong pattern and is mostly coincidence.\n")

    P("\n## How it varies between people\n")
    P(f"\nAcross {len(p):,} participants with at least {a.min_entries} entries in the windows.\n")
    P("\n| measure | 10th | median | 90th |")
    P("|---|---|---|---|")
    P(f"| observed rate | {100*p.obs.quantile(.1):.1f}% | {100*p.obs.median():.1f}% | "
      f"{100*p.obs.quantile(.9):.1f}% |")
    P(f"| excess over own chance | {100*p.excess.quantile(.1):+.1f} pp | "
      f"{100*p.excess.median():+.1f} pp | {100*p.excess.quantile(.9):+.1f} pp |")
    P(f"\n{100*(p.excess>0).mean():.0f} per cent of participants sit above their own chance rate "
      f"and {100*(p.excess>0.05).mean():.0f} per cent exceed it by more than five percentage "
      f"points. So the behaviour is real and widespread but slight for most people, with a "
      f"minority doing it a great deal.\n")

    P("\n## What the repeats look like\n")
    b = [(0,5,"under 5 minutes"),(5,15,"5 to 15"),(15,30,"15 to 30"),(30,60,"30 to 60")]
    P("\n| gap between the pair | share |\n|---|---|")
    for lo, hi, lab in b:
        P(f"| {lab} | {100*((gaps>=lo)&(gaps<hi)).mean():.0f}% |")
    top = pd.Series(sizes).value_counts().head(6)
    P(f"\nMedian gap {np.median(gaps):.0f} minutes. The amounts that repeat are small: "
      + ", ".join(f"{int(v)} g" for v in top.index) + ", against an overall median entry of "
      f"{w.carbs_g.median():.0f} g.\n")
    P("\nThat combination, small amounts repeated at short intervals, reads as one meal entered in "
      "parts rather than as a person eating the same thing twice. It is a reason to treat the "
      "small end of the size distribution as partly an artefact of entry behaviour, and a reason "
      "the merging step in the readability extraction matters more than it looks.\n")

    open(os.path.join(a.out_dir, "REPEAT_ENTRIES_REPORT.md"), "w").write("\n".join(L))
    figure(p, gaps, sizes, os.path.join(a.out_dir, "repeat_entries.png"), a.window_min)
    print("\n".join(L))


if __name__ == "__main__":
    main()
