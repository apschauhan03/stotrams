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
- **Keep the "Current architecture" section below up to date.** Whenever the architecture
  materially changes, refresh that section and bump its "as of" date. It's the fast
  human-readable orientation for a developer; the detailed day-by-day trail is in
  `backend/update/`.

---

# Current architecture (as of 2026-08-06)

A plain-English snapshot of how the system fits together right now. For *why* decisions
were made and the day-by-day history, read `backend/update/`.

## What it is

The `backend/` server for **Stotram**, a free devotional-status platform (WhatsApp-style
"good morning / good night" deity images, Hindi/English). One Spring Boot app does three
jobs: a **public read-only feed**, an **admin review workflow**, and (later) a **daily AI
image pipeline**. There is no user system and no PII — the public surface is read-only.

## Stack

- Java 21, Spring Boot 3.5.4, **Spring MVC** (embedded Tomcat, thread-per-request/blocking —
  not reactive). Concurrency comes from the Tomcat thread pool + Hikari DB connection pool.
- **PostgreSQL** via Spring Data JPA; **Flyway** owns the schema (`ddl-auto: none`).
- Local Postgres runs via `backend/docker-compose.yml` on host port **5433**
  (5432 is taken by a local install). App listens on **:9303**.

## How the code is organised (layered MVC)

`com.stotram` →
- `controller/` — HTTP endpoints (`FeedController`, `AdminController`, `HelloController`).
- `service/` — business logic (`FeedService`, `AdminReviewService`).
- `repository/` — Spring Data JPA interfaces, one per table.
- `model/` — JPA entities (one per table; Lombok for accessors).
- `dto/` — API response shapes (`FeedResponse`, `CandidateResponse`).
- `bootstrap/` — `InitialContentLoader`, loads starter content on boot.

## Data model (5 tables)

`category`, `prompt_template`, `generation_batch`, `admin_user`, and `content_item` — the
heart. A content item moves `GENERATED → APPROVED → PUBLISHED` (or `REJECTED`). Its `type`
field (`IMAGE`, later `VIDEO`) is why videos can be added without schema surgery.

## What works today

- **Public feed** — `GET /api/feed?lang=&category=&page=` returns published items, newest
  first, paged. Each image `url` is built as `app.media.base-url` + `r2_key`.
- **Admin review (the human gate)** — `GET /admin/candidates?status=`,
  `POST /admin/candidates/{id}/approve`, `POST /admin/candidates/{id}/reject`.
- **Local content flow** — drop images in `backend/src/main/resources/static/images/`
  (served at `/images/...`), describe them in `resources/data/initial-content.json`;
  `InitialContentLoader` upserts them as `PUBLISHED` on startup (idempotent).

## The load-bearing indirection

Image URLs are `app.media.base-url` + `r2_key`. Today the base is `http://localhost:9303`
and images are local static files. Moving to Cloudflare R2/CDN later means uploading the
same keys and changing one env var — **no code or data change**.

## Stubbed / not built yet

- `POST /admin/generate` and `POST /admin/publish` return **501** — they need the AI image
  client and the R2 + watermark + WebP publish step (deferred).
- No **security** on `/admin/**` yet (Spring Security + Cloudflare Access come later).
- No **R2/CDN** yet (local static files stand in).
- `status`/`type`/`language` are plain strings (enums proposed, not yet done).
