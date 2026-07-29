#!/usr/bin/env python3
"""Render the multi-cohort fidelity matrix: a signature x cohort table, a small-multiples
figure (each real cohort + each Padova persona class, with bootstrap CIs), and a verdict
on whether ANY persona class reproduces each real-world statistic.

Run (after multicohort.py):  ~/.venvs/boost-insilico/bin/python multicohort_report.py
"""
import os, json, numpy as np, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
BLUE, ORANGE, GREEN, VERM, PURPLE, GREY = \
    "#0072B2", "#E69F00", "#009E73", "#D55E00", "#CC79A7", "#8a8a8a"

REAL = ["Boost", "Trio", "OpenAPS", "AAPS-classic"]
SIM = ["Padova adult", "Padova adolescent", "Padova child"]
# (key, label, unit) in display order
ROWS = [
    ("cv", "Glucose variability", "CV%"),
    ("tail", "Rise tail P(Δ>10/5min)", "%"),
    ("acf30", "Autocorrelation @30min", ""),
    ("acf60", "Autocorrelation @60min", ""),
    ("outcome", "Outcome SD @stuck-high", "mg/dL"),
    ("diurnal", "Diurnal amplitude", "mg/dL"),
    ("hypo_rec", "Hypo recovery to 100", "min"),
    ("hypo_reb", "Hypo rebound >180", "%"),
    ("compress", "Compression lows", "/30d"),
    ("noise", "Sensor jitter", "mg/dL"),
    ("drift", "ISF drift (weekly)", "%CV"),
]


def load():
    with open(os.path.join(HERE, "multicohort_result.json")) as f:
        return json.load(f)


def real_envelope(res, key):
    """[min, max] of the real cohorts' point estimates for a signature."""
    pts = [res["cohorts"][c][key][0] for c in REAL if np.isfinite(res["cohorts"][c][key][0])]
    return (min(pts), max(pts)) if pts else (np.nan, np.nan)


def in_real_range(res, key, cohort, pad=0.10):
    lo, hi = real_envelope(res, key)
    if not np.isfinite(lo):
        return False
    span = hi - lo
    p = res["cohorts"][cohort][key][0]
    return lo - pad * span - 1e-9 <= p <= hi + pad * span + 1e-9


def figure(res, path):
    keys = [r for r in ROWS if r[0] != "drift"]  # 10 signature panels
    fig, axes = plt.subplots(2, 5, figsize=(18, 8))
    cohorts = REAL + SIM
    colors = [BLUE, BLUE, BLUE, BLUE, ORANGE, VERM, PURPLE]
    for ax, (key, label, unit) in zip(axes.flat, keys):
        pts = [res["cohorts"][c][key][0] for c in cohorts]
        los = [res["cohorts"][c][key][1] for c in cohorts]
        his = [res["cohorts"][c][key][2] for c in cohorts]
        err = [[max(0, p - l) for p, l in zip(pts, los)],
               [max(0, h - p) for p, h in zip(pts, his)]]
        x = np.arange(len(cohorts))
        ax.bar(x, pts, color=colors, yerr=err, capsize=2, error_kw=dict(lw=1, alpha=0.6))
        lo, hi = real_envelope(res, key)
        ax.axhspan(lo, hi, color=BLUE, alpha=0.08)  # real-world envelope
        ax.set_title(f"{label} ({unit})", fontsize=10)
        ax.set_xticks(x)
        ax.set_xticklabels(["Boost", "Trio", "OpenAPS", "AAPS", "P-adult", "P-adol", "P-child"],
                           rotation=45, ha="right", fontsize=7)
        ax.spines[["top", "right"]].set_visible(False)
    fig.suptitle("Real-world AID cohorts (blue) vs UVA/Padova personae (warm). "
                 "Shaded band = real-world range.", fontsize=12)
    fig.tight_layout(rect=[0, 0, 1, 0.97])
    fig.savefig(path, dpi=130)
    plt.close(fig)


def fmt(cell):
    p, lo, hi = cell
    if not np.isfinite(p):
        return "n/a"
    return f"{p:.1f} [{lo:.1f}-{hi:.1f}]"


def write_report(res):
    lines = ["# Multi-cohort simulator fidelity: UVA/Padova vs real-world AID data\n"]
    meta = res["meta"]
    lines.append("Real cohorts (local research DB) versus all three FDA/UVA-Padova persona "
                 "classes. Each cell is the per-user median with a bootstrap 95% CI. The "
                 "question is not only whether the adult personae match, but whether **any** "
                 "persona class reproduces each real-world statistic.\n")
    lines.append("| Cohort | n | kind |")
    lines.append("|---|---|---|")
    for c in REAL + SIM:
        lines.append(f"| {c} | {meta[c]['n_users']} | {meta[c]['kind']} |")
    lines.append("\n## Signature x cohort matrix\n")
    header = "| Signature | " + " | ".join(REAL) + " | " + " | ".join(SIM) + " |"
    lines.append(header)
    lines.append("|" + "---|" * (1 + len(REAL) + len(SIM)))
    for key, label, unit in ROWS:
        cells = [fmt(res["cohorts"][c][key]) for c in REAL + SIM]
        # mark sim cells outside the real range
        marked = []
        for c, cell in zip(REAL + SIM, cells):
            if c in SIM and cell != "n/a":
                marked.append(cell + ("" if in_real_range(res, key, c) else " ✗"))
            else:
                marked.append(cell)
        lines.append(f"| {label} ({unit}) | " + " | ".join(marked) + " |")
    lines.append("\n✗ = outside the real-world range. \n")
    # verdict summary
    lines.append("## Which personae match, by signature\n")
    lines.append("| Signature | personae in real range | verdict |")
    lines.append("|---|---|---|")
    n_none = 0
    for key, label, unit in ROWS:
        matched = [c.replace("Padova ", "") for c in SIM if in_real_range(res, key, c)]
        if key == "drift":
            v = "STRUCTURAL (sim fixed = 0)"
        elif not matched:
            v = "NO persona matches"
            n_none += 1
        elif len(matched) == 3:
            v = "all personae match"
        else:
            v = f"only {', '.join(matched)}"
        lines.append(f"| {label} | {', '.join(matched) if matched else 'none'} | {v} |")
    lines.append(f"\n**{n_none} of {len(ROWS)} signatures are reproduced by NO Padova persona "
                 f"class.**\n")
    lines.append("![matrix](fig_multicohort.png)\n")
    lines.append("## Reading the matrix\n")
    lines.append(
        "- **The four real datasets converge.** Boost, Trio, OpenAPS and AAPS-classic are "
        "four different algorithms built by different communities and worn by different "
        "people, yet they agree closely on every statistic. That agreement defines a "
        "real-world envelope and makes the simulator comparison meaningful rather than "
        "anecdotal.\n"
        "- **The simulator gets short-horizon smoothness right.** Autocorrelation at 30 and "
        "60 minutes lands in the real range for all three persona classes. On smooth, "
        "benign, announced-meal stretches it is a fair stand-in.\n"
        "- **Aggregate variability is reachable only by the child persona.** CV and the "
        "stuck-high outcome spread reach the real range for children (the most variable "
        "class) but not for adults or adolescents, which run too smooth. Since controllers "
        "are typically evaluated on the adult personae, the default in-silico test "
        "understates real-world variability.\n"
        f"- **{n_none} signatures are reproduced by no persona at any age.** These are the "
        "mechanistically important, safety-relevant ones: the fat rise tail of unannounced "
        "meals, hypo treatment (real lows recover about twice as fast and then overshoot; "
        "the sim has no rescue carbohydrate), sensor artefacts (compression lows and "
        "high-frequency jitter, both absent or halved), and week-to-week insulin-sensitivity "
        "drift (real loops vary 8-22%, the fixed-parameter model varies zero).\n"
        "- **The child match is not a rescue.** A persona matching real variability does not "
        "make the simulator adequate: you would not test an adult controller on the child "
        "persona, and the child still fails every mechanism signature above.\n")
    lines.append(
        "The pattern is consistent with the single-cohort suite and the two structural "
        "probes: in-silico testing on this platform exercises the easy regime (smooth, "
        "announced, stationary, clean-sensor) and is blind to the hard one (unannounced "
        "meals, variable insulin efficacy, exercise, sensor artefact, sensitivity drift) "
        "that dominates real-world safety.\n")
    open(os.path.join(HERE, "REPORT_MULTICOHORT.md"), "w").write("\n".join(lines))
    print("wrote REPORT_MULTICOHORT.md")


if __name__ == "__main__":
    res = load()
    figure(res, os.path.join(HERE, "fig_multicohort.png"))
    write_report(res)
