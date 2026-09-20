# Divergent-history incident: root cause

This repo has hit "unrelated/divergent history" merge conflicts twice, and both times a session
patched around it with a merge commit rather than tracing why it kept happening. This doc traces
it, using the actual commit graph (`git log --all --graph --oneline`, parent hashes via `git log
-1 --pretty="%H %P"`) rather than guessing.

## The actual sequence

1. `dcdc01b` "Add ProcureFlow..." (2026-09-16 09:07:55, root commit, no parent) — Session 1's own
   first commit, made locally against what it believed was an empty repo.
2. `19e9a6d` "Initial commit" (2026-09-16 09:14:39, **also a root commit, no parent**) — a
   *second*, unrelated root commit that ended up on GitHub's `main`. Two independent root commits
   for the same repo is the underlying condition for every "unrelated histories" conflict that
   follows; nothing after this point can be a clean fast-forward.
3. `f4b61d4` "Merge initial empty main to establish shared history for PR" (parents: `dcdc01b`,
   `19e9a6d`) — Session 1's patch: merge the two unrelated roots with `--allow-unrelated-histories`
   so a PR could be opened at all. This got Session 1 unblocked but didn't address *why* two roots
   existed (root cause unknown/undocumented at the time).
4. `65e72ca`, `674f9ac` — further work on Session 1's feature branch, on top of `f4b61d4`.
5. PR #1 merged into `main` as `1825759` "Add ProcureFlow: Spring Boot + Angular + PostgreSQL
   enterprise scaffold" — **same commit message as `dcdc01b`, but a different hash, and only one
   parent: `19e9a6d`** (not `674f9ac`, not a merge of the two). That parentage is only possible if
   PR #1 was merged with **"Squash and merge"** (or a rebase-merge with an unrelated-histories
   base): GitHub synthesizes one brand-new commit on top of `main`'s then-tip (`19e9a6d`) and
   discards the feature branch's actual commit graph, including the very merge commit (`f4b61d4`)
   that had just reconciled the two root histories.
6. Session 2 branched for the security/concurrency work **from `674f9ac`** (the old feature-branch
   tip) rather than from `origin/main` post-merge (`1825759`), and added `15e37b2` on top of it.
   Because of step 5, `674f9ac`'s lineage and `main`'s actual lineage (`1825759`) share no commit
   Git recognizes as common — even though their tree contents are identical — so the two branches
   were divergent again, for the same underlying reason as step 2/3, one PR later.
7. `06c9e2c` "Merge main into security/concurrency branch, resolving history-divergence
   conflicts" — Session 2's patch, structurally identical to step 3: another manual merge to paper
   over divergence, again without recording why it recurred.
8. PR #2 merged as `81b4f29`, this time as an actual two-parent merge commit (not a squash), which
   is why *this* merge didn't orphan its own history further.

## Root cause

Two compounding causes, not one:

1. **The repository's initial "shared" root came from two independent root commits**
   (`dcdc01b`/`19e9a6d`) rather than one session pushing to a truly empty `main`. Whatever created
   `19e9a6d` (a GitHub UI action — e.g. repo creation with auto-init, or a separate empty first
   push to `main` — happened outside any session's shell history, so it can't be pinned down more
   precisely than "not the same origin as `dcdc01b`" from the commit graph alone).
2. **Squash-merge is enabled/used as the merge strategy for this repository's PRs**, and PR #1
   used it. Squash-merging is well-known to orphan a feature branch's own commit history: anyone
   (a session, a contributor) who keeps working from the pre-squash branch tip instead of
   resetting onto `main` post-merge will diverge from `main`, because `main` now contains a
   commit Git has never seen before, with none of the feature branch's own commits as ancestors.
   Session 2 hit exactly this by branching from `674f9ac` instead of fresh `origin/main`.

(1) explains the *first* incident; (2) explains why the *same class* of incident recurred on the
very next PR even after (1) had already been "fixed" once. Root cause of the recurrence
specifically is (2), not a repeat of (1) — the second incident wasn't two unrelated roots again,
it was the standard squash-merge-orphans-the-branch problem.

## What's fixable from a worker session vs. admin-only

- **Fixable here, and already the practice this session followed:** always `git fetch origin
  <default-branch>` and branch/reset from `origin/<default-branch>` before starting new work,
  never from a previously-fetched or previously-used local branch tip. This alone prevents
  Session 2's specific mistake (step 6) regardless of merge strategy. This session's own task
  instructions already called for exactly this check, which is what surfaced this root-cause
  analysis in the first place.
- **Not fixable from a worker session (GitHub repository Settings, admin-only, not a committable
  file):**
  - **Disable "Allow squash merging"** under Settings → General → Pull Requests (or, at minimum,
    adopt "Rebase and merge" or "Create a merge commit" as the standing convention), so a merged
    PR's commits remain reachable from `main` and a branch built on the pre-merge tip stays
    fast-forwardable or cleanly rebaseable instead of orphaned.
  - **Branch protection on `main`** (require PRs, require the branch to be up to date before
    merging) would also reduce the chance of a session building on a stale base going unnoticed
    until merge time — but per this session's standing instructions, branch protection changes
    are explicitly not something to configure from here; only documented as needed.

No branch protection rules or repository settings were modified by this session. Both are flagged
above for an admin to action; the worker-side mitigation (fetch-and-reset-from-origin before
branching) has already been applied in this session's own workflow.
