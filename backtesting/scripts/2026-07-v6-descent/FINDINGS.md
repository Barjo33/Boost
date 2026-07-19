# Descent lever — is the post-meal plateau safely dosable? Per-user: SOMETIMES

*2026-07-19. `dr1_plateau.py`. The under-recovery is real (foundation ff1); this asks whether there's
HEADROOM to dose it, or whether V6 is correctly restrained (ff2-style anchor). Plateau cycles = BG>140,
flat/falling, +90..210min post-onset. For each: V6 dose, IOB, minGuardBG (V6's low forecast), and the
GROUND-TRUTH forward nadir over the next 3h. Per-user JSON gitignored.*

## Result — headroom is per-user, and does NOT align with who has the problem
Pooled (3266 plateau cycles): V6 doses ~0 in 77%; of those only 29% have minGuard≥80 (V6 sees no low).
On those "safe-looking" dosable cells (n=721): forward nadir goes **<70 14%, <80 28%, stays ≥90 60%.**
So even where V6's own forecast said safe, dosing would feed a low ~1-in-7 times — NOT clean headroom.

**But it splits sharply by user, and it splits the two worst under-recoverers OPPOSITE ways:**
| user | under-recovery | plateau→low <70 (safe cells) | verdict |
|---|---|---|---|
| **A** | severe (plat 148) | **0%** (n=45) | genuinely STUCK → dosable |
| **H** | mild | **3%** (n=91) | dosable |
| **F** | severe (plat 149) | **22%** (n=117) | slow-DECLINE → dosing crashes it |
| **B** | mod | 23% (n=94) | trap |
| tim | mod | 16% (n=249) | marginal/trap |

## Interpretation — TWO kinds of plateau
1. **Genuinely stuck** (A, H): glucose sits at 148 and won't come down on its own (forward nadir stays
   100-109) → real headroom, V6 truly under-doses. The descent lever WOULD help.
2. **Slow decline / insulin already catching up** (F, B, tim): glucose sits at 148 but is drifting
   down and will reach target — or overshoot low (22% <70). V6 is CORRECTLY holding; dosing overshoots.

**V6's minGuard cannot separate these** (14% lows even where it looked safe). So a blanket "dose the
descent" helps A and crashes F. The lever is real but needs a BETTER low-discriminator than minGuard —
which is exactly where the KAIROS Twin `lo30` floor (validated at ⅓–½ minGuard's false-alarm rate) is
the natural candidate. That is the one genuine place the sensor programme and the dosing programme meet.

## Next (dr2, proposed) — TEST it, don't assume
Does `lo30` (or the Twin's forecast slope) separate STUCK from SLOW-DECLINE plateaus where minGuard
can't? If it flags F's 22%-low cells while clearing A's 0%-low cells, the descent lever becomes
Twin-gated and viable; if not, the plateau is a per-user trait, not a dosable moment.
