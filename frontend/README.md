# Stotram frontend

React (Vite) web app, deployed on Cloudflare Pages (free). This ships **before** the Android app — same React skills, same API, and it validates content + the share flow with zero store friction. The React Native app (phase 3) reuses the feed contract and API client.

## What it contains

One React app, two faces:

1. **Public site** (`/`) — no login
   - Pinterest-style 2-column grid of devotional images (good morning / good night tabs)
   - Language toggle: Hindi + English at launch
   - Tap image → full view → **"Share to WhatsApp Status"**
   - Infinite scroll over `GET /api/feed?lang=&category=&page=`
   - Images load from `cdn.stotram.*` (R2/CDN), never from the API server
2. **Admin panel** (`/admin/*`) — behind Cloudflare Access + backend login
   - Review queue: grid of `GENERATED` candidates with approve/reject buttons
   - Publish button (runs watermark → R2 upload → feed rebuild on the backend)
   - Prompt template editor, batch trigger, cost-per-batch display

Same deployment; the `/admin` hostname/route is gated by Cloudflare Access so the public bundle never exposes anything sensitive (all admin data comes from authenticated API calls).

## Share flow (the core feature)

- **Android Chrome**: Web Share API with files — fetch the image as a blob, then
  `navigator.share({ files: [new File([blob], 'stotram.webp', { type: 'image/webp' })] })`
  → opens the native share sheet → user picks WhatsApp → "My status". Test on real low-end Android devices; this is the entire product.
- **Desktop / unsupported browsers**: fallback to a download button + hint text.
- Audience is elderly: big tap targets, minimal text, high contrast, no onboarding screens.

## Constraints

- No login for users, ever. No cookies/trackers beyond the essentials — trust is the differentiator.
- Feed contract lives in the backend README (§4); keep a shared TypeScript type for it (future `shared/` package will be consumed by React Native too).
- Privacy policy page hosted here (required later by Play Store even with no login).

## Running locally

```bash
npm install
npm run dev          # http://localhost:5173  (admin: /admin)
```

- **No backend running?** The app switches to **demo mode** automatically (dev only):
  generated sample images, in-memory approve/reject/generate, and a yellow banner saying so.
- **Admin login** (`/admin`) is checked by the backend. Start it with `ADMIN_EMAIL` and
  `ADMIN_PASSWORD` set (see backend README §5); in demo mode there's no login.
- **With the backend** (`mvn spring-boot:run` on :9303), the Vite proxy forwards
  `/backend/*` → `:9303/*`, so no CORS setup is needed. For images to load through the
  proxy too, start the backend with `MEDIA_BASE_URL=http://localhost:5173`.
- `npm run build` type-checks and builds to `dist/` (what Cloudflare Pages serves).

| Env var | Purpose |
|---|---|
| `VITE_API_BASE` | Backend origin in production, e.g. `https://api.stotram.app`. Dev default: `/backend` (proxy). |
| `VITE_USE_MOCK` | `true` forces demo mode (e.g. a preview deploy with no backend). |

## Code map

- `src/api/` — `types.ts` (mirrors backend DTOs), `client.ts` (all HTTP + demo fallback), `mock.ts` (demo content)
- `src/pages/FeedPage.tsx` — public grid, morning/night tabs (defaults by time of day), infinite scroll
- `src/components/ImageViewer.tsx` — full-screen view + **Send on WhatsApp**; Back button closes it
- `src/lib/share.ts` — converts to JPEG, then Web Share API with a file, else save the photo
- `src/pages/AdminPage.tsx` — generate (with confirm step), review queue, keyboard review (A / R / ← → / Esc)
- `src/i18n.tsx` — Hindi/English strings + weekday deity line

Production note: the CDN must send `Access-Control-Allow-Origin` for images, because sharing
fetches the image to convert it to a JPEG file.

## Build order

1. Vite + React scaffold, feed fetch + grid + language/category tabs
2. Full-screen view + Web Share flow (+ download fallback)
3. Admin review queue + approve/reject + publish
4. Deploy to Cloudflare Pages, wire custom domain, put Access in front of `/admin`
