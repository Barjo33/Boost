---
name: memory-backup-mechanism-2026-07-10
description: "How the AndroidAPS/Boost memories get backed up to GitHub — MANUAL, sanitised, to the claude-memory branch. The auto-hook is UNRELATED (syncs a README-only Trio repo). Don't auto-push (Tim's call)."
metadata: 
  node_type: memory
  type: reference
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**Memory backup = MANUAL, sanitised, to `origin/claude-memory` on Boost-AAPS-core (github.com/tim2000s/Boost-in-AAPS_3.4). Do it when Tim asks.**

**The trap (found 2026-07-10):** backups had silently STOPPED at 2026-07-02 — ~2 weeks of memories were local-only. Two mechanisms, and the confusion is why it broke:
- **The auto-hook** `~/StudioProjects/trio-claude-memory/sync.sh` (registered in `~/.claude/settings.json`) commits+pushes the **`trio-claude-memory` repo** — which contains **only a README**, NOT the AndroidAPS memories. It also does **NO sanitisation** (`git add -A`+push), so it must NEVER carry the Boost memories. Leave it alone (Tim: don't auto-push).
- **The real backup** = the **`claude-memory` branch** on Boost-AAPS-core, updated MANUALLY with sanitisation. This is the one to keep current.

**How to bring it current (the 2026-07-10 procedure that worked):**
1. `git worktree add /tmp/wt origin/claude-memory` then `git checkout -B claude-memory origin/claude-memory` in it.
2. `cp <memory-dir>/*.md wt/memory/` (source = `~/.claude/projects/-Users-timstreet-StudioProjects-AndroidAPS/memory/`).
3. **SANITISE** (public repo + medical data — HARD gate [[feedback-anonymize-before-github]]). Ordered replacements (URLs/emails BEFORE names to avoid partial hits): cohort user names→tags (user H→"user H", user A→"user A"; also rename the user H-* file→user-h-diagnosis + fix `[[wikilinks]]`), site hosts + NS-token-site + `[trio-site]`→placeholders, Drive account (`[user-Drive-account]`, `[user-Drive-account]@…`)→`[user-Drive-account]`, surname `[user]`→`[user]`, domain `[user-domain]`→`[user-domain]`. **KEEP**: first name "Tim", `/Users/timstreet` machine paths (existing branch convention). Watch word-boundaries (don't hit "Romanian").
4. HARD leak scan (grep the full name/token/site/email set) — must be empty.
5. `git add -A && commit && git push origin claude-memory`. Remove the worktree.

Current identifiers present in memories: user H(user H), user A(user A), [self-NS-site](self NS site), [trio-site](trio site), [user-H-site](user-H site), [user-Drive-account]/[user-Drive-account](Drive), [user-domain](domain). Simon(F)/user E(E) names NOT in current AndroidAPS memories. Re-scan each time — the set grows.
