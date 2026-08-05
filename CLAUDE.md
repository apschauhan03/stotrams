# Stotram — working agreement (read this first)

This file is the standing instruction set for any agent working in this repo. Follow it
on every session unless the user says otherwise in the moment.

## Repo facts

- Monorepo. `backend/` is the Spring Boot server (the active work). `frontend/` is a
  placeholder for now.
- Default branch: `main`. Remote: `origin` → https://github.com/apschauhan03/stotrams
- Daily context reports live in `backend/update/` (one file per day, `YYYY-MM-DD.md`).
- GitHub CLI: `gh`. If it isn't on PATH in the shell, use the full path
  `"C:\Program Files\GitHub CLI\gh.exe"`.

## 1. At the start of every session

- **Read the last two update reports** in `backend/update/` (the two most recent
  `YYYY-MM-DD.md` files, by date — ignore `README.md`). That's the working context; don't
  re-derive it from scratch.

## 2. At the start of a new day's work

- **Create a new branch for the day** off `main`: `day/YYYY-MM-DD`
  (e.g. `day/2026-08-07`). If today's branch already exists, just switch to it.
- Do the day's work on that branch. Never commit day-to-day work directly to `main`.

## 3. When the user says "done for the day" (or equivalent)

Run this sequence:

1. **Write that day's report** in `backend/update/YYYY-MM-DD.md` following the format in
   `backend/update/README.md` (what was done · why · what each portion does now ·
   stubbed/deferred · open items/next).
2. **Commit** all of the day's work *including* the report, on the day branch.
3. **Push** the branch to `origin`.
4. **Open a proper PR** into `main` with `gh` — a clear title and a body summarizing the
   day's work (link the update report). Open it for review; do **not** merge it yourself
   unless the user asks.
5. Add the PR link into that day's update report, then commit and push that follow-up.

## Notes

- "A proper PR" = descriptive title + body, targeting `main`, left open for the user to
  review/merge.
- Keep commits and PRs scoped to the day's branch so `main` stays clean and reviewable.
- End git commit messages with the required trailer:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
