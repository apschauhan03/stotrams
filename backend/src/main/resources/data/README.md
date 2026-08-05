# Initial content

`initial-content.json` is the local stand-in for the generate → review → publish
pipeline. On startup `InitialContentLoader` reads it and upserts rows into Postgres
(idempotent):

- **categories** — matched by `slug`; new ones are inserted.
- **items** — matched by `r2Key` (unique); new ones are inserted as `PUBLISHED`
  so they show up in `GET /api/feed` immediately.

## Adding an image

1. Drop the file under `resources/static/images/<category>/` — e.g.
   `resources/static/images/good-morning/gm-hi-01.jpg`.
   Spring serves it at `http://localhost:9303/images/good-morning/gm-hi-01.jpg`.
2. Add an entry to `items` (the `r2Key` is the path **after** `static/`):

```json
{
  "r2Key": "images/good-morning/gm-hi-01.jpg",
  "category": "good-morning",
  "lang": "hi",
  "width": 1080,
  "height": 1350,
  "publishDate": "2026-08-06"
}
```

3. Restart the app. The item appears in `GET /api/feed?lang=hi&category=good-morning`.

The feed URL is built as `{app.media.base-url}/{r2Key}`. Moving to a CDN later means
uploading the same keys to R2 and changing `MEDIA_BASE_URL` — no code or data change.
