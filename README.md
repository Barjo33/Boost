# Claude Code memory — Boost V6 work (tim2000s/Boost-in-AAPS_3.4)

Orphan branch `claude-memory`: persistent Claude Code memory for the Boost V6 line.
Shares NO history with the code branches — a normal clone/checkout never touches it.

## Use on another machine
```sh
# from any clone of this repo:
git fetch origin claude-memory
# memory dir = ~/.claude/projects/<SLUG>/memory  where <SLUG> is the absolute path of the
# project dir you open Claude Code from, with '/' replaced by '-'
# (e.g. /home/you/Boost-AAPS-core -> -home-you-Boost-AAPS-core)
DEST=~/.claude/projects/<SLUG>/memory
mkdir -p "$DEST"
git --work-tree="$(mktemp -d)" checkout origin/claude-memory -- . 2>/dev/null || true
git archive origin/claude-memory memory | tar -x --strip-components=1 -C "$DEST"
```
Then start Claude Code in the project dir — MEMORY.md is the index it loads.

## Update from a machine
Copy the memory dir contents into a checkout of this branch, commit, push:
```sh
git worktree add /tmp/cm claude-memory
cp ~/.claude/projects/<SLUG>/memory/*.md /tmp/cm/memory/
cd /tmp/cm && git add -A && git commit -m "memory sync $(date +%F)" && git push origin claude-memory
```

NOTE: sanitized for the public repo — the keystore password in boost-v6-audit-2026-06-28.md
is redacted; secrets live only on the maintainer's machines.
Snapshot: 2026-07-02, through the V6 safety review + non-meal-cap + rescue-guard fixes.
