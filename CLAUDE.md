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

## Secrets — never commit, never push (the repo is PUBLIC)

- Real keys live only in **`backend/src/main/resources/application-local.yml`** (OpenAI key
  etc.). It is gitignored, loaded by `spring.config.import` in `application.yml`, and
  excluded from the packaged jar by `maven-jar-plugin` in `pom.xml`. Never put a key in
  `application.yml` or any tracked file.
- Git hooks in `.githooks/` block it: `pre-commit` refuses `application-local.*`, `.env`
  files and key-shaped strings; `pre-push` re-checks every outgoing commit and fails closed.
  They're active only after `git config core.hooksPath .githooks` (once per clone).
- Before any commit/push: run `git status` and make sure no secrets file is staged. Never
  bypass the hooks (`--no-verify`).

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

# Current architecture (as of 2026-10-13)

A plain-English snapshot of how the system fits together right now. For *why* decisions
were made and the day-by-day history, read `backend/update/`.

## What it is

**Stotrams / स्तोत्रम्** — a free devotional-image platform (WhatsApp-style "good morning /
good night" deity images, Hindi/English) for older users, with no ads and no user accounts.
Two apps in this repo:

- `backend/` — one Spring Boot server: the **public read-only feed** and **panchang**, the
  **admin API** (login, generation blocks, review, edit, video upload, publish), the **AI
  image pipeline** and the **calendar-aware prompt writer**.
- `frontend/` — one React (Vite) app: the **public site** (`/` feed, `/panchang`, bottom
  nav) and the **admin panel** (`/admin`). Falls back to built-in demo content in dev when
  the backend is down.

Lifecycle of an image: a **block** (prompt) is generated → `GENERATED` → human review →
`APPROVED` → **published** (watermarked JPEG, now or on the daily schedule) → `PUBLISHED`,
visible on the site. Rejecting takes anything down. Edits create a new `GENERATED` copy.
Videos are uploaded by hand into the same lifecycle (`type = VIDEO`, not watermarked).

## Stack

- **Backend:** Java 21, Spring Boot 3.5.4, Spring MVC (blocking, embedded Tomcat), Spring
  Security (session login), PostgreSQL via Spring Data JPA, **Flyway** owns the schema
  (`ddl-auto: none`, migrations V1–V8). Local Postgres: `backend/docker-compose.yml` on host
  port **5433**. App on **:9303**.
- **Frontend:** React 18 + TypeScript + Vite 5 on **:5173**, react-router. Dev proxy:
  `/backend/*` → :9303 (prefix stripped), `/images/*` and `/videos/*` → :9303. Fonts self-hosted via
  @fontsource (Noto Sans Devanagari, Libre Baskerville, Tiro Devanagari Hindi).
- **OpenAI:** images (`gpt-image-*`, generate + edit), text (`gpt-5.4-mini` for the master
  prompt, Chat Completions) and an optional web-search panchang check (Responses API +
  `web_search`). Key in `application-local.yml` (see Secrets above). No video API (Sora
  was shut down 24 Sep 2026).

## How the backend is organised (`com.stotram`)

- `controller/` — `FeedController` and `PanchangController` (public), `AuthController`
  (login/me), `AdminController` (review by type, edit, `POST /admin/videos`),
  `BlockController` (blocks, generate, bulk), `PrincipleController` (master prompt: get /
  save / reset to default / write), `PublishController` (publish now + schedule),
  `ApiErrorHandler` (`{message}` errors, 413 on too-large uploads).
- `security/` — `AdminUserDetailsService`, `AdminBootstrap` (admin from env vars),
  `LoginRateLimiter` (5 failures/15 min per client).
- `service/` — `FeedService`, `AdminReviewService`, `BlockService`, `VideoUploadService`.
- `service/generation/` — `ImageGenerator` (vendor seam: generate, edit, models, paid,
  `unavailableReason`), `GenerationService` (runs blocks on the bounded `imageExecutor`),
  `ImageEditService`, `ImageStorage`/`LocalImageStorage`, `provider/` (`OpenAiImageGenerator`,
  `FakeImageGenerator` — free placeholder for testing).
- `service/prompting/` — `PromptWriter` (master prompt → N prompts + research summary +
  recommendation; negative prompt appended as "Avoid: …"; strict JSON schema), `Panchang`
  (tithi/paksha/month/Navratri/samvat/sunrise for New Delhi, computed astronomically),
  `CalendarLookup` (independent web-search panchang check), `DateContext` (combines both
  into the writer's context + the computed-vs-web `CalendarCheck`). Default master prompt:
  `resources/prompts/master-prompt.txt`; `{{TARGET_DATE}}` / `{{NUMBER_OF_PROMPTS}}` are
  filled per run.
- `service/publish/` — `PublishService`, `PublishScheduler` (DB-driven cron, re-registered
  live), `Watermarker`, `WatermarkBackfill`.
- `config/` — `SecurityConfig`, `AsyncConfig` (`imageExecutor`, `publishTaskScheduler`),
  `LocalMediaConfig` (serves `/images/**` and `/videos/**` from the storage dir on disk).

## Data model

`content_item` is the heart (status, `r2_key` = what's served, `original_key` = clean image,
`edited_from_id`/`edit_prompt` for edits, `publish_date`). `prompt_template` = a generation
**block** (name, category, language, prompt, model, image_count, active, archived,
`target_date` = the day it was written for). `content_item.type` is `IMAGE` or `VIDEO`.
Also `category`, `generation_batch` (one run of a block, cost), `admin_user`,
`publish_schedule` (singleton: enabled + cron + last run), `app_setting` (key/value:
`principle_prompt` — absent means "use the default", `watermark_version`).

## What works today

- **Public site:** bottom nav (चित्र · पंचांग). Feed: Pinterest-style grid, morning/night
  tabs (defaults by time of day), Hindi/English (brand name स्तोत्रम् / Stotrams). Tapping an
  item opens the **reels viewer** (`ReelViewer`: vertical swipe, one per screen, videos
  autoplay muted with tap-to-pause / sound / progress, info chips, **Send on WhatsApp** via
  Web Share with a JPEG or the MP4; save fallback; Back closes it).
  Feed = `GET /api/feed?category=&page=` (no language filter: images are shared).
  Panchang page = `GET /api/panchang?date=` (computed, free, cached 1 h), day by day.
- **Master prompt** (admin): defaults to the Stotrams calendar prompt; date + count fill its
  placeholders; panchang computed and optionally web-checked, shown as two boxes with ✓/⚠;
  research summary + recommendation shown; created blocks carry a 📅 date.
- **Admin panel** (login required): 🖼️ Photos / 🎬 Videos toggle; video upload (MP4/WebM/MOV,
  ≤100 MB) into the review queue; generation blocks (add/edit/pause/delete, generate one
  or all, model dropdown), principle prompt → drafts → bulk-create blocks, review queue
  (To review / Approved / Live / Rejected, keys A/R/←/→), prompt-based edit, publish now +
  daily auto-publish schedule.
- **Watermark:** every published image gets lotus + "स्तोत्रम्" top-right (`Watermarker`,
  Tiro Devanagari Hindi bundled in `resources/fonts`, OFL), re-encoded as ~450 KB JPEG under
  `images/published/<Watermarker.VERSION>/...`. Fails → item stays APPROVED. **To change the
  design: edit `Watermarker` and bump `VERSION`** — startup re-renders all live images (new
  URLs, so no stale caches) and records it in `app_setting.watermark_version`.
- **Image edits:** `POST /admin/candidates/{id}/edit` → OpenAI `/images/edits` with the clean
  original (multipart via plain `MultiValueMap`; `MultipartBodyBuilder` needs Reactive
  Streams, which isn't on the classpath). Default edit model gpt-image-1-mini (cheap, but it
  redraws faces/details — use gpt-image-2 for iconography).

## The load-bearing indirection

Image URLs are `app.media.base-url` + `r2_key`. Locally the base is `http://localhost:5173`
(through the Vite proxy) and images are files under `backend/src/main/resources/static`.
Moving to Cloudflare R2/CDN means an `R2ImageStorage` with the same keys and one env var —
no feed or data change.

## Stubbed / not built yet

- No **R2/CDN** yet; local files stand in (`LocalImageStorage` writes into
  `src/main/resources/static`, fine from source, not from a jar). Clean originals are
  reachable by direct URL until they move to a private bucket.
- No **deployment** (VPS, domain, Cloudflare Access, `SESSION_COOKIE_SECURE=true`,
  `CF-Connecting-IP` for the rate limiter).
- No **scheduled generation** (publishing is scheduled; generating is manual).
- Sessions are in memory — a backend restart logs the admin out.
- `generation_batch.cost_usd` records $0 until `OPENAI_COST_PER_IMAGE` is set.
- Images stay 1024×1536 (no 4:5 crop, no WebP).
- `status`/`type`/`language` are plain strings (enums proposed, not done).
- No **video generation** (needs a new vendor, e.g. fal.ai → Kling/Seedance/Veo) and no
  **video watermark** (needs ffmpeg). Panchang is New Delhi only, no nakshatra/yoga/karana.

