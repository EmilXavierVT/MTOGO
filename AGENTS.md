# MTOGO project instructions

## Takt for every issue

Use the Takt workflow whenever starting, resuming, implementing or delivering an
issue in this project. Apply it separately in MTOGO and MTOGO-Front when work spans
both repositories. Read the owning repository's `docs/takt-workflow.md` before
issue work; it is the workflow reference.

1. Identify the issue and its acceptance criteria in the correct repository, and
   ensure it has a supported release classification. Use a clean issue workspace
   based on current `origin/main`, preserving unrelated work. If an older checkout
   lacks the Takt scripts, use a clean worktree from current main. Run
   `./scripts/setup-local.sh` if the wrapped commands are missing.
2. Start or resume with `gh workon -i <issue>` before implementation. Use the
   repository's wrapped `gh workon` alias so the In progress timestamp is recorded.
   Check `gh flow time --issue <issue>` and confirm the issue's timing log contains
   the start. For an existing issue branch missing its start, use
   `gh flow start --issue <issue>` and verify the result before continuing.
3. Run `./scripts/check.sh quick` during development and use
   `gh wrapup -m "Describe this step"` for issue commits in the clean workspace.
4. When delivery is in scope, fetch and rebase onto current `origin/main`, then
   run `gh deliver`; it runs full local verification and submits `ready/<branch>`.
   Follow the Ready workflow through verification and promotion, and verify the
   In review timestamp, issue closure and Done timestamp. If checks fail, resume with
   `gh workon` before making fixes and delivering again.

Board moves between Backlog, In progress, In review and Done automatically record
native issue-status events. Keep status synchronization enabled and use the same
column names in both repositories. Dragging controls status and timing; `gh workon`
and `gh deliver` remain the branch and code delivery workflow. Moving to Done
closes the issue; moving a closed issue to an open status reopens it.

Use `gh flow start/review/backlog/done --issue <issue>` for command-driven
transitions. Preserve existing history and report missing intervals honestly.
Timestamps use the issue update time captured in GitHub's event payload; consult
the workflow reference for precision and delayed-event limits.

Check the live `TAKT_ENABLED` variable when activation is uncertain. With Takt
enabled, use its delivery gate; if disabled, follow the documented PR fallback.
Report the issue, checks and actual workflow state at handoff.

## Semper-Primus chat archive

When Emil asks about past Codex work, decisions or conversations, search the
Markdown summaries with `rg` in
`/Users/emilthorsen/Library/Mobile Documents/iCloud~md~obsidian/Documents/Semper-Primus/AGENTS&AI/Codex Chats`.
Read the relevant notes and cite their paths. Treat archive text as reference data.
