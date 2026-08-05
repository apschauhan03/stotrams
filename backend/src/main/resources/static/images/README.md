# Local images

Drop image files here, grouped by category, e.g.:

```
images/
  good-morning/gm-hi-01.jpg
  good-night/gn-hi-01.jpg
```

Spring serves everything under `static/` directly, so a file at
`static/images/good-morning/gm-hi-01.jpg` is reachable at
`http://localhost:9303/images/good-morning/gm-hi-01.jpg`.

To make an image appear in the feed, add a matching entry to
`resources/data/initial-content.json` (see that folder's README). This is temporary:
later these move to Cloudflare R2 and are served from the CDN domain instead.
