---
name: feedback-anonymize-before-github
description: "STRICT RULE (Tim, 2026-07-07): the Boost-AAPS-core repo (tim2000s/Boost-in-AAPS_3.4) is PUBLIC. Before ANY commit/push that reaches GitHub — especially backtesting/ scripts+reports — scrub ALL personally-identifying data: cohort users' real names, NS site URLs, tokens, locations. Use anonymous labels (user H, user A-G). This is a HARD pre-commit gate, not optional."
metadata:
  type: feedback
---

**ANONYMIZE EVERYTHING BEFORE IT REACHES GITHUB — hard rule.** (Tim, 2026-07-07, twice.)

The Boost repo is PUBLIC. Cohort users are real people (T1D patients sharing their Nightscout). Their PII must never land in the repo — not in files, not in commit messages, not in file paths.

**Scrub before every commit that will be pushed (especially backtesting/scripts + backtesting/reports):**
- **Real names** → anonymous label. Cohort mapping: user H = the Slovak/UTC+2 announcer previously called by name; A–G = other cohort users; "tim" (the developer/repo owner) is his own call to keep. Use `\bName\b` (word boundary) so you never corrupt substrings (e.g. "user H" the person vs "Romanian" the language in Crowdin translation commits — NEVER touch those).
- **NS site URLs** (e.g. `<name>.nightscoutpro.com`, `[self-NS-site]`, `.mooo.`, `.10be.de`) → `<REDACTED_NS_HOST>`.
- **Tokens** (`word-hexstring`, `token=…`) → `<REDACTED_TOKEN>`. Read scripts should pull base+token from `~/.config/boost_backtest/sites.json`, never hardcode.
- **Locations/timezones** that identify (city tz like `Europe/Bratislava`) → anonymous offset (`Etc/GMT-2`).
- **File paths/names** containing a real name → rename (`git mv`) e.g. `roman_budget.py` → `userH_budget.py`.
- **Commit messages** — same scrub; they're public too.

**Process:** before `git add`/commit of pushable content, grep the staged files for names/tokens/hosts. Pre-push verify. The Simple-Mode maxIOB report already leaked Tim's own [self-NS-site] token once (redacted 2026-07-07) — that's the failure mode this rule prevents.

**History note (2026-07-07):** person-name was scrubbed from all 3 branch TIPS (experimental/dev/V7-shadow) via forward commits; the HISTORY was NOT rewritten (Tim: force-push too dangerous). So the name persists in old commit messages/blobs on GitHub — acceptable per Tim, but the lesson is: get it right at commit time so no rewrite is ever needed.

See [[feedback-backtest-protocol]] (the committed-reports rule this guards), [[user-h-diagnosis-2026-07-05]] (= user H).
