# Stotram

Free devotional status app for India. Users open the app/site, see fresh good-morning and good-night devotional images, and share them straight to WhatsApp Status. No login, no payments, no spam — built as a trustworthy alternative to scammy devotional apps targeting elderly users.

## Why

Scamsters exploit the religious shraddha of older people and charge them for devotional content. Stotram is completely free and extremely simple to use. Monetization (light, non-intrusive ads / festival sponsorships) comes only after the product proves itself — launch is ad-free.

## Phases

| Phase | Deliverable | Status |
|---|---|---|
| 1 | Backend: content pipeline (generate → review → publish) + feed API | current |
| 2 | Frontend: React website on Cloudflare Pages (public grid + admin panel) | current |
| 3 | Android app (React Native), reusing the same feed API | after web launch is successful |
| 4 | Videos (FFmpeg-assembled, NOT AI-generated), festivals, more languages, ads | later |

## Repos in this folder

- `backend/` — Java Spring Boot server: public feed API, admin API, daily AI generation pipeline. **Read `backend/README.md` for the full implementation spec** (architecture, ER model, endpoints, security, hosting).
- `frontend/` — React (Vite) web app: public Pinterest-style image grid + `/admin` review panel. Deployed on Cloudflare Pages.

## Key decisions already made (do not re-litigate without reason)

- **Images only at launch.** No AI video — too expensive (₹12k–35k/mo) and AI gets deity iconography wrong. Videos later = FFmpeg Ken Burns over approved images, cost ≈ ₹0.
- **Every AI image is human-reviewed before publishing.** Non-negotiable — wrong iconography offends the audience.
- **Cloudflare R2 for media** (zero egress fees — bandwidth is the only real scaling cost). Custom domain `cdn.stotram.*`. Media never touches the VPS.
- **Web first, app later.** Same React skills, same API; Web Share API covers WhatsApp sharing on Android Chrome.
- **No login for users, ever.** One admin account, behind Cloudflare Access.
- **Feed items carry a `type` field** (`"image"` now) so videos are an append later, not a rewrite.
- **No ads at launch.** Banner eCPM in India is ~₹9–26 per 1000 impressions; at small scale ads earn pocket change and cost trust. Revisit at real scale (native in-feed ads, never aggressive banner refresh — AdMob ban risk).

## Running costs (planned)

- One-time: Play Store ₹2,200 (phase 3), domain ~₹1,000/yr
- Monthly: VPS ₹450–700 + AI images ₹200–1,500 (300–750 imgs) + R2 ~₹0 → **~₹700–2,500/month**
