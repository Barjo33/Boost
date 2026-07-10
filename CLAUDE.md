# CLAUDE.md — Boost development guide

Working guide for Claude sessions on the Boost AndroidAPS fork. Consolidates the rules, methodology, and mistakes-with-corrections built up over the work to date. Personal/operational specifics (Drive destination, site registry, device details) live in the private memory, not here — this file is committed to a public repo, so it carries no names, tokens, site URLs, or personal identifiers.

## Hard rules (non-negotiable)

1. **Anonymise before anything goes to GitHub.** Public repo. Scrub names, tokens, site URLs, and locations from files, commit messages, and paths before every commit. Use word boundaries so you don't touch unrelated words (e.g. Crowdin, Romanian strings). Read site details from the private registry, never hardcode them. Run a scan before committing — this has caught real leaks (user names in a doc, a hardcoded token in a script).
2. **No training or online inference in the dose path.** The shipping controller is deterministic. Learned components are pre-trained models applied at inference, or robust statistics computed offline/periodically. Anything that would learn-and-dose in the loop is gated behind shadow-logging first.
3. **Absolute safety floors sit under every statistical decision.** The time-below-range kill-switches (consensus absolutes: TBR<70 >4%, <54 >1%) can only tighten. Statistics rank options; they never override a safety floor. Kill-switches key on absolutes, never on relative doubling.
4. **Nothing that changes dosing ships without the two-test bar**: absolute TBR gates plus relative pricing, and for a real dosing change, a pre-registered within-user trial.

## The identification constraint (shapes all analysis)

There is no glucodynamic simulator, so we cannot generate the counterfactual BG trajectory for a dosing change. A "simulate policy A vs B" backtest is not available.

- **Prediction/detection** questions are validated out-of-sample — clean, no counterfactual.
- **Policy** questions are priced against observed outcomes, with the counterfactual caveat stated, plus within-subject and matched-baseline designs.
- An observational effect size is associational unless a within-user or randomised design backs it.
- The bottleneck is identification, not modelling. Keep models modest.

## Methodology

- **DB-first.** Use the local TimescaleDB (`oref.boost_decisions`) for analyses. Pull from Nightscout only for what the DB lacks, and wire missing fields into the extractor rather than re-pulling. NS pulls: chunk to ~7-day windows and retry with backoff (the site 502s on long windows).
- **Backtest protocol.** Refresh the DB to t=now first. Commit scripts and reports to `backtesting/`; the scratchpad holds intermediates only, not deliverables.
- **Out-of-sample everything.** `GroupKFold` with the *user* as the group to prevent leakage — cross-user generalisation is the honest test, not per-person memorisation. Temporal splits for time-ordered data.
- **Matched baselines before believing an effect size.** Multiple large-looking findings dissolved under a proper baseline. Un-baselined and un-leakage-checked effect sizes are provisional.
- **Within-subject > between-subject.** The population is small (single digits to ~30) and self-selected; cross-user results are mostly hypothesis-generating.
- **Proximate ≠ causal.** Attribution names the mechanism at an event; it doesn't prove causation. Audit by outcome before acting on it.
- **Don't re-litigate settled questions.** `backtesting/RELATIONSHIPS_REGISTER.md` lists what's been tested and discarded. Check it before proposing a lever.
- **Code leads.** Verify against the live code and DB before recommending. Memory and prior notes can be stale; if a memory names a file/field/flag, confirm it still exists. Auto-config not applying an expected value has bitten us more than once.

## Lab vs loop

What doses is deterministic (state machine, multipliers, caps, composed brake-floor, rule-based sleep detector, a deterministic per-user auto-config derivation) plus two pre-trained ML models at inference. All the Bayesian/inferential machinery is offline decision-support that decides what gets built. See `backtesting/STATISTICAL_METHODS.md`.

## Branch and commit workflow

- **Two separate repositories exist and must not be crossed.** All Boost V5/V6/V7 work lives in the Boost fork on its **vehicle branch** (the V7-shadow line) — that is what APKs are built from and flashed. A second repository holds a separate port tracking upstream AAPS (a different generation); **Boost feature or dosing work never goes there.** Sessions often start in the upstream-port working directory because it is the default cwd — that does not make it the target. Confirm the repo and branch before creating a branch or committing. Local paths/branch names are in the private notes.
- Land Boost changes on the vehicle branch first; don't push straight to dev. If a change belongs on both the vehicle and the experimental line, cherry-pick (`-x`) across and push both so they keep a common base.
- Commit messages end with the required Co-Authored-By and session trailer. Keep them factual.
- After a force-push upstream, sync your local branch to origin before running any analysis against it — a stale local branch has inverted conclusions before.

## Build and deploy

- **Roll-out sequence (feature → flashed APK):** prototype in the Boost fork (not the upstream-port repo) → commit (the app build has a no-uncommitted-changes gate; a dirty tree fails it) → land on the vehicle branch, cherry-pick (`-x`) to the sibling line if it belongs there → validate the Dagger graph (`:app` KSP) → build the signed release APK → verify it (SHA/timestamp, signature, and that the new class is actually in the dex) → rename descriptively and copy to the private Drive folder (per local notes) → flash.
- Verify every APK by timestamp/SHA before shipping it. A silent `BUILD FAILED` (hidden by `-q`) once led to an old APK being copied out as if fresh. Never trust an APK you didn't just watch build.
- Background builds: check the exit status and the output, not just that the command returned.
- Wear/phone flashing can be done over adb wireless debugging (pair port + code, then connect port); watch for background sensor permissions and the doze/battery whitelist, which are what let overnight sensing survive.

## Mistakes made, and the rules that came from them

- **Prototyped a feature in the wrong repository** (a smoothing plugin built in the upstream-port repo instead of the Boost fork's vehicle branch, because that was the session's default cwd) → all Boost work goes in the Boost fork on the vehicle branch; confirm the repo/branch before branching or committing, then cherry-pick if a prototype ended up misplaced.
- **Trusted a stale APK** after `-q` hid a build failure → always verify APK timestamp/SHA, and confirm the *new* class is actually in the dex (`unzip -p apk classes*.dex | strings | grep <Class>`), not just that a file exists.
- **A backgrounded `gradle … | tail` reported exit 0** — that was the pipe's last command (`tail`), not gradle; a `BUILD FAILED` (clean-tree gate) was masked and a stale APK was nearly shipped. → Capture gradle's real exit code (`gradlew … > log 2>&1; echo $?`), don't read it through a pipe.
- **Edited a tracked file while a background build ran** → dirtied the tree and tripped the no-uncommitted-changes gate mid-build. Commit (or hold edits) before starting a build; don't touch the tree until it finishes.
- **Ran `git add -A` while a subagent was writing the same repo** → swept the agent's in-progress files into unrelated commits and left the tree dirty. → Stage explicit paths, never `-A`, when a subagent shares the working copy; or give the agent an isolated worktree.
- **Ran analysis against a stale local branch** 24 commits behind origin after a force-push → sync local to origin first.
- **Repeated mis-diagnosis of a single user** from a contaminated aggregate window → verify era detection (`boostv5_active`), don't trust raw day-count windows.
- **Called V1 "oref"**, wrongly deflating a finding → **V1 is Boost** (the V1 generation is a Boost dosing algorithm); `boostv5_active` marks only the V5/V6-generation slice.
- **Leaked real names and a token into committed files** → the anonymise scan is a mandatory pre-commit gate, not a formality.
- **Over-attributed effect sizes** (a brake "34%" that was 90% correct on audit; a cohort "+13pp" that was mostly selection; a recovery "2×" that was a window-length artefact) → matched baseline first.
- **Wrote in a sensational, over-emphatic register** → plain, balanced, British spelling; state limitations without dwelling on them as virtue.
- **Mis-read an ambiguous instruction** (a "watch on the network" as an HTTP server, not adb wireless debugging) → confirm device and protocol before acting on terse infra notes.

## Domain facts that are easy to get wrong

- **V1 is Boost.** Not oref. See above.
- The high tail is **high-IOB**; adding insulin into recovering highs or late overnight bounces is the repeated source of lows. The dosing guards exist for this.
- **Online knob-tuning does not beat static per-user auto-config.** Caps and sliders, both directions, all converged on the policy auto-config already ships. Don't rebuild it as an online loop.
- **TING** = time in 3.5–7.8 mmol/L (63–140 mg/dL); report alongside TIR.
- The user doses in **U200** (roughly 2× mass per unit); flag cross-user absolute-unit comparisons.
- The composed brake is ~90% correct — don't loosen it.

## How the user works

- Terse and action-oriented. "off you go", "do both", "go" mean proceed — don't ask for permission on the obvious next step. Reserve questions for genuine forks.
- Tool use is auto-approved; don't prompt for confirmation on routine commands.
- Prefers plain, understated writing over emphasis and salesmanship.
- Is the domain expert and the developer of this fork; corrections from the user override prior analysis.

## Key reference docs (in `backtesting/`)

- `RELATIONSHIPS_REGISTER.md` — what's been tested: used, discarded, unproven. Read before proposing a lever.
- `STATISTICAL_METHODS.md` — the methods, and where each sits (lab vs loop).
- `protocols/` — pre-registered analysis plans.
- Per-investigation folders under `backtesting/scripts/` — scripts + reports for each study.
