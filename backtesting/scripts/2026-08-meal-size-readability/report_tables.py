#!/usr/bin/env python3
"""Render the result JSONs as the markdown tables the report carries.

Every number in the report comes from here, so that none of them is typed by hand.
"""
import argparse, json, os

def f(x, n=3):
    return "n/a" if x is None or x != x else f"{x:.{n}f}"

def main():
    ap = argparse.ArgumentParser()
    here = os.path.dirname(os.path.abspath(__file__))
    ap.add_argument("--data", default=os.path.join(here, "out"))
    ap.add_argument("--study", default="Loop")
    a = ap.parse_args()
    R = json.load(open(os.path.join(a.data, f"results_{a.study}.json")))
    S = json.load(open(os.path.join(a.data, f"slopes_{a.study}.json")))

    print(f"## Primary endpoint: large against small, participants held out ({a.study})\n")
    for arm in sorted({r["arm"] for r in R["classification"]}):
        print(f"\n### Arm {arm}\n")
        print("| stratum | horizon | meals | participants | AUC | 95% interval | raw rise alone | model minus raw | caught at 10% FPR |")
        print("|---|---|---|---|---|---|---|---|---|")
        for r in R["classification"]:
            if r["arm"] != arm:
                continue
            print(f"| {r['stratum']} | {r['horizon']} min | {r['n']:,} | {r['subjects']} | "
                  f"{f(r['auc'])} | {f(r['lo'])} to {f(r['hi'])} | {f(r['auc_raw_rise'])} | "
                  f"{r['delta_vs_raw']:+.3f} | {f(r['tpr_at_10fpr'],2)} |")

    print("\n## Size as a quantity, against the baseline ladder\n")
    print("| arm | horizon | meals | MAE g | population median | time-of-day median | participant median | correlation |")
    print("|---|---|---|---|---|---|---|---|")
    for q in R["quantity"]:
        print(f"| {q['arm']} | {q['horizon']} min | {q['n']:,} | {f(q['mae'],1)} | "
              f"{f(q['mae_median'],1)} | {f(q['mae_tod_median'],1)} | {f(q['mae_subject_median'],1)} | "
              f"{q['corr']:+.3f} |")

    print("\n## Per-participant slope of glucose rise on announced carbohydrate\n")
    print("| stratum | horizon | participants | pooled slope | 95% interval | tau | I squared | true slopes below zero | individually below zero |")
    print("|---|---|---|---|---|---|---|---|---|")
    for p in S["pooled"]:
        if "mu" not in p:
            continue
        print(f"| {p['stratum']} | {p['horizon']} min | {p['k']} | {p['mu']:+.4f} | "
              f"{p['ci_lo']:+.4f} to {p['ci_hi']:+.4f} | {f(p['tau'],4)} | {p['i2']:.0f}% | "
              f"{p['share_true_negative']*100:.0f}% | {p['share_sig_negative']*100:.0f}% |")

    if S.get("within"):
        print("\n## Within participant: slope on unbolused meals minus slope on bolused meals\n")
        print("| horizon | participants | difference | 95% interval | unbolused | bolused | share positive |")
        print("|---|---|---|---|---|---|---|")
        for w in S["within"]:
            print(f"| {w['horizon']} min | {w['n_subjects']} | {w['mean_diff']:+.4f} | "
                  f"{w['ci_lo']:+.4f} to {w['ci_hi']:+.4f} | {w['mean_b_unbolused']:+.4f} | "
                  f"{w['mean_b_bolused']:+.4f} | {w['share_unbolused_gt_bolused']*100:.0f}% |")

if __name__ == "__main__":
    main()
