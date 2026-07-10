# Boost Exercise & Recovery Mode Review — 2026-05-06

**Window**: 2026-04-06 → 2026-05-06 (30 days)
**Source**: Nightscout `https://[self-NS-site]`
**Raw data**: `/tmp/boost_30d_exercise/` (devicestatus.json 13,701 cycles, entries.json 8,529 SGV, treatments.json 5,079, parsed_cycles.json, events.json, effectiveness.json)
**Charts (Drive)**: `~/Library/CloudStorage/GoogleDrive-[user-Drive-account]/My Drive/Boost-v2-Analysis/exercise_review_2026-05-06/`
- `overview_30d.png` — full 30-day BG/HR/steps overlay with mode bands
- `event_NN_YYYYMMDD_HHMM_<MODE>.png` — 45 per-event detail charts (±2 h)

## TASK 1 — HR + steps presence

HR and step data are **NOT** uploaded as standalone NS objects (no `entries[].type='hr'`, no `pump.heartRate`, `/api/v1/activity.json` returns `[]`, no `recentSteps*` field on `openaps.suggested`).

They are **embedded in `openaps.suggested.consoleError[]` as text**, e.g.:
```
"HR: HR: avg=77.0 bpm | HRR=11.2% | zone=zone1 | steps15m=265 => LIGHT_AEROBIC (MEDIUM)"
"steps: 5m=31 15m=265 30m=326 60m=1797"
```

| Metric | Coverage | min | median | max |
|---|---|---|---|---|
| HR avg (bpm) | 5,849/13,701 cycles (42.7%) | 65 | 87 | 111 |
| steps15m | 5,849 cycles | 0 | 70 | 1,508 |
| steps5m / steps30m / steps60m | 12,983 cycles (94.8%) | — | — | — |

Coverage is consistent ~190 cycles/day for HR — matches the ~35-45% gating noted in user instructions for ML/sensor-derived fields. Daily counts are stable across all 30 days (not a v4.4.1-only feature).

## TASK 2 — Modes detected

The activity logic produces **HR-class labels** (LIGHT_AEROBIC 3009, INACTIVE 2120, MODERATE_AEROBIC 446, STRESS 267, RESTING 3, VIGOROUS_AEROBIC 2, RESISTANCE 2 cycles) and a **BOOST tier** sub-state per cycle (`✓ BOOST ACTIVE (sub)` or `✗ BOOST INACTIVE: reason`).

The terms "Recovery", "post-exercise", "step burst", "HR-based", "workout" do **not** appear anywhere in 30 days of consoleError. There is **no explicit recovery mode** by name — what plays that role is `BOOST ACTIVE (INACTIVE)` ("Inactivity detected (60m steps X < 400) → profile 130%").

Discrete firing events (state-runs):

| Mode (sub-state) | Firings (n) | Median dur | Trigger source |
|---|---|---|---|
| ACTIVE | 43 | ~5 min | "Activity detected → profile 80%, target 150" — 60m-step rolling window OR HR moderate+ |
| INACTIVE (recovery proxy) | 269 | ~38 min avg | "Inactivity detected (60m steps < 400) → profile 130%" |
| VIGOROUS_AEROBIC | 1 | 0.3 min | HR 111, zone3, steps15m 696 (2026-04-12 17:21) |
| RESISTANCE | 1 | 5.5 min | HR 111, zone3, steps15m 0 (2026-04-12 17:56, HR-only) |

Treatments contained 1 user-entered exercise event: `Temporary Target / Activity / target=135 / dur=172m` on 2026-04-11 13:22.

Outside-trigger states (NOT firings, just gating):
- 5,229 cycles `Outside boost time window`
- 331 cycles `Sleep-in (Xm steps Y < threshold)`
- 159 cycles `High temp target`

## TASK 3 — Overlay visualization

Built and copied to Drive (48 files):
- `overview_30d.png` (1920×800): BG green primary, HR red, steps15m purple bars; orange/red/purple/blue bands for each ACTIVE/VIG/RES/INACTIVE firing.
- `event_01..45_*.png` (1200×600): one per exercise-tier firing (43 ACTIVE + 1 VIG + 1 RES). INACTIVE not chart-ed individually (269 events would be excessive); they're visible as blue bands on the overview.

## TASK 4 — Effectiveness scoring

Per-mode aggregate (BG before = mean of 30 min preceding firing; BG after = mean of 60 min following end):

| Mode | n | Avg ΔBG (mg/dL, 60 min) | Median Δ | BG before | BG after | Avg dur (min) | Hypos in +90 min |
|---|---|---|---|---|---|---|---|
| ACTIVE | 43 | **+2.0** | +5.9 | 128.7 | 130.7 | 12.6 | 7 (16%) |
| INACTIVE (recovery proxy) | 269 | +1.1 | +4.2 | 125.1 | 126.2 | 37.8 | 58 (22%) |
| VIGOROUS_AEROBIC | 1 | +44.3 | — | 105.2 | 149.5 | 0.3 | 0 |
| RESISTANCE | 1 | +10.0 | — | 150.0 | 160.0 | 5.5 | 0 |

**Detection quality** (ground-truth runs = HR≥100 OR steps15m≥500 sustained ≥2 cycles ~10 min):
- 148 sustained exercise-signal runs identified.
- **Captured by an ACTIVE/VIG/RES firing: 39/148 = 26 %**.
- **Missed (false negatives): 109/148 = 74 %.** Top misses: 70 min run with HR up to 107, steps15m 691 (2026-04-25 11:27); 45 min with HR 105 (2026-04-12 13:36); 40 min HR 100 (2026-04-16 14:46). Many missed runs were step bursts at sub-100 HR (76-90 bpm) but steps15m 700+ — these are walks, not exercise per the heuristic.
- **False positives: 14/45 = 31 %.** All are `ACTIVE` firings where the cycle-instant HR/steps had dropped below threshold; the trigger uses 60-min rolling step total, so it's lag rather than spurious. 6/14 had no HR sample at all (HR=0) yet still fired — these would have used the steps-only path.

**Hypo-avoidance estimate**: insufficient signal — baseline hypo rate during high-step periods without firing is similar (38/186 elevated-step runs without firing → 20 %, vs. ACTIVE 16 %, INACTIVE 22 %). No statistically meaningful protective effect is visible at this n.

## RECOMMENDATIONS for v4.5+

1. **Lower the ACTIVE/VIGOROUS detection threshold** — 74 % miss rate against a permissive HR≥100 OR steps15m≥500 ground-truth means the current rule is too conservative. Tim's max HR in 30 days is 111 (peak) and median 87, so HR thresholds calibrated to a typical adult (zone3 = HRR≥40 %) almost never trigger. Recommend **HRR≥25 % OR steps15m≥400 OR steps60m≥2000** as the ACTIVE entry criterion.

2. **VIGOROUS_AEROBIC and RESISTANCE fired exactly once each in 30 days** with HRR=40.5 % at HR 111. If Tim's resting HR is ~70 and HRmax is plausibly ~150-160, HRR 40 % requires ~106-110 bpm — so he literally only crosses it during peak walks. Either lower zone3 threshold to HRR≥30 % or treat the modes as informational only.

3. **Add an explicit "RECOVERY" / "post-exercise" mode**. Today the 269 INACTIVE firings raise profile to 130 % and target to 99 mg/dL whenever 60m-steps <400, even at 9 PM sitting on the couch — which is just normal evening sedentary, not recovery. ΔBG +1.1 mg/dL says it does almost nothing, and the 22 % hypo rate in the +90 min window is the highest of any mode (driven by aggressive SMBs at low target). Recommend: gate INACTIVE recovery on **prior 2-h activity** (require an ACTIVE firing within the last 120 min) rather than firing on any sedentary period.

4. **Embed structured HR/steps in `openaps.suggested` as numeric fields**, not just consoleError text. Parsing 5849 records out of regex-of-text is fragile and any tool downstream of NS (Tidepool, Glooko, this analysis) cannot use it. Add `suggested.hrAvg`, `suggested.hrZone`, `suggested.steps15m`, `suggested.activityClass`. (Also enables Nightscout heart-rate plugin to render HR alongside BG.)

5. **Remove the double "HR: HR:" prefix** in the line — minor cosmetic, but suggests stringly-typed format-chaining bug.

6. **Activity TT from treatments (2026-04-11 13:22, target 135, dur 172 min) was NOT acknowledged** in any visible mode firing during that window. Boost should treat user-entered Activity TT as authoritative override and report `BOOST ACTIVE (USER_TT_ACTIVITY)`.

7. **VIGOROUS_AEROBIC ΔBG +44 mg/dL in 60 min after a 0.3-min firing** is concerning — it suggests the brief mode flip (target 150, profile 70 %) happened *before* the actual exercise glycemic effect, and the system did not sustain the elevated target long enough. Recommend a **minimum-duration latch** (e.g., once VIGOROUS_AEROBIC fires, hold target=150 for ≥30 min regardless of subsequent HR drop).

## Drive paths
- Overview chart: `My Drive/Boost-v2-Analysis/exercise_review_2026-05-06/overview_30d.png`
- All 45 event charts: same dir, `event_NN_YYYYMMDD_HHMM_<MODE>.png`
- Raw scoring data: `effectiveness.json`, `events.json` (also in `/tmp/boost_30d_exercise/`)
