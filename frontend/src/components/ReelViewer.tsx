import { useCallback, useEffect, useRef, useState } from 'react';
import type { FeedItem } from '../api/types';
import { useLang } from '../i18n';
import { downloadFile, prepareShareFile, shareFile } from '../lib/share';
import { useOverlay } from '../lib/useOverlay';
import { WhatsAppIcon } from './WhatsAppIcon';

interface Props {
  items: FeedItem[];
  startIndex: number;
  onClose: () => void;
  /** Called when the viewer gets near the end and more pages exist. */
  onReachEnd: () => void;
  hasMore: boolean;
}

/** Media stays mounted this many slides either side of the one on screen (memory on old phones). */
const KEEP = 2;
/** Ask for the next page this many slides before the end. */
const PRELOAD_PAGE_AT = 3;

/**
 * Full-screen, reels-style viewer: swipe up/down through photos and videos, one per screen.
 * The browser does the snapping (CSS scroll-snap), so swiping feels native. Videos play
 * when they're on screen (muted until the viewer turns sound on — browsers only allow
 * muted autoplay) and pause when scrolled away.
 */
export function ReelViewer({ items, startIndex, onClose, onReachEnd, hasMore }: Props) {
  const { t } = useLang();
  const close = useOverlay(onClose);
  const [active, setActive] = useState(startIndex);
  const [muted, setMuted] = useState(true);
  const [hint, setHint] = useState(items.length > 1);
  const scroller = useRef<HTMLDivElement>(null);
  const slides = useRef<(HTMLElement | null)[]>([]);

  // Open on the tapped item, without an animation.
  useEffect(() => {
    slides.current[startIndex]?.scrollIntoView({ block: 'start' });
  }, [startIndex]);

  // Whichever slide is mostly on screen is the active one.
  useEffect(() => {
    const root = scroller.current;
    if (!root) return;
    const observer = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) setActive(Number((e.target as HTMLElement).dataset.index));
        }
      },
      { root, threshold: 0.6 },
    );
    slides.current.forEach((el) => el && observer.observe(el));
    return () => observer.disconnect();
  }, [items.length]);

  useEffect(() => {
    if (active !== startIndex) setHint(false);
    if (hasMore && active >= items.length - PRELOAD_PAGE_AT) onReachEnd();
  }, [active, startIndex, hasMore, items.length, onReachEnd]);

  useEffect(() => {
    const timer = setTimeout(() => setHint(false), 4000);
    return () => clearTimeout(timer);
  }, []);

  const goTo = useCallback((index: number) => {
    slides.current[Math.max(0, Math.min(index, slides.current.length - 1))]?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
      if (e.key === 'ArrowDown' || e.key === 'PageDown') {
        e.preventDefault();
        goTo(active + 1);
      }
      if (e.key === 'ArrowUp' || e.key === 'PageUp') {
        e.preventDefault();
        goTo(active - 1);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [active, close, goTo]);

  return (
    <div className="reels" role="dialog" aria-modal="true">
      <div className="reels__bar">
        <span className="reels__count">
          {active + 1} / {items.length}
          {hasMore ? '+' : ''}
        </span>
        <button type="button" className="reels__close" onClick={close}>
          ✕ {t.close}
        </button>
      </div>

      <div className="reels__scroller" ref={scroller}>
        {items.map((item, index) => (
          <section
            key={item.id}
            className="reel"
            data-index={index}
            ref={(el) => {
              slides.current[index] = el;
            }}
          >
            {Math.abs(index - active) <= KEEP ? (
              <Reel item={item} isActive={index === active} muted={muted} onToggleMute={() => setMuted((m) => !m)} />
            ) : (
              <div className="reel__placeholder" />
            )}
          </section>
        ))}
      </div>

      {hint && (
        <p className="reels__hint" aria-hidden="true">
          ⬆ {t.swipeHint}
        </p>
      )}
    </div>
  );
}

function Reel({
  item,
  isActive,
  muted,
  onToggleMute,
}: {
  item: FeedItem;
  isActive: boolean;
  muted: boolean;
  onToggleMute: () => void;
}) {
  const { lang, t } = useLang();
  const video = useRef<HTMLVideoElement>(null);
  const [paused, setPaused] = useState(false);
  const [progress, setProgress] = useState(0);
  const [file, setFile] = useState<File | null>(null);
  const [preparing, setPreparing] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const isVideo = item.type === 'video';

  // Play only the reel on screen; leave the others paused where they were.
  useEffect(() => {
    const v = video.current;
    if (!v) return;
    if (isActive) {
      v.play().then(() => setPaused(false)).catch(() => setPaused(true));
    } else {
      v.pause();
    }
  }, [isActive]);

  // Prepare the share file once this reel is on screen, so the tap can share at once
  // (navigator.share must run inside the tap; there's no time to download then).
  useEffect(() => {
    if (!isActive || file) return;
    let cancelled = false;
    setPreparing(true);
    prepareShareFile(item.url, item.id, item.type)
      .then((f) => !cancelled && setFile(f))
      .catch(() => undefined) // the button retries on tap
      .finally(() => !cancelled && setPreparing(false));
    return () => {
      cancelled = true;
    };
  }, [isActive, file, item.url, item.id, item.type]);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  function togglePlay() {
    const v = video.current;
    if (!v) return;
    if (v.paused) v.play().then(() => setPaused(false)).catch(() => undefined);
    else {
      v.pause();
      setPaused(true);
    }
  }

  async function getFile() {
    return file ?? (await prepareShareFile(item.url, item.id, item.type).catch(() => null));
  }

  async function handleShare() {
    const f = await getFile();
    if (!f) return setToast(t.error);
    const outcome = await shareFile(f, t.shareText);
    if (outcome === 'downloaded') setToast(t.downloaded);
  }

  async function handleSave() {
    const f = await getFile();
    if (!f) return setToast(t.error);
    downloadFile(f);
    setToast(t.downloaded);
  }

  const morning = item.category === 'good-morning';
  const date = item.date
    ? new Date(`${item.date}T12:00:00`).toLocaleDateString(lang === 'hi' ? 'hi-IN' : 'en-IN', {
        day: 'numeric',
        month: 'long',
      })
    : null;

  return (
    <>
      {/* The same picture, blurred, fills the space around it — no black bars. */}
      {!isVideo && <div className="reel__backdrop" style={{ backgroundImage: `url("${item.url}")` }} />}

      <div className="reel__stage">
        {isVideo ? (
          <>
            <video
              ref={video}
              className="reel__media"
              src={item.url}
              muted={muted}
              loop
              playsInline
              preload={isActive ? 'auto' : 'metadata'}
              onClick={togglePlay}
              onTimeUpdate={(e) => {
                const v = e.currentTarget;
                setProgress(v.duration ? v.currentTime / v.duration : 0);
              }}
            />
            {paused && (
              <button type="button" className="reel__play" onClick={togglePlay} aria-label={t.play}>
                ▶
              </button>
            )}
          </>
        ) : (
          <img className="reel__media" src={item.url} alt="" />
        )}
      </div>

      <div className="reel__overlay">
        <div className="reel__info">
          <span className="reel__chip">
            {morning ? '🌅' : '🌙'} {morning ? t.morning : t.night}
          </span>
          {date && <span className="reel__chip">📅 {date}</span>}
          {isVideo && <span className="reel__chip">🎬 {t.video}</span>}
        </div>

        <div className={`reel__actions${isVideo ? ' reel__actions--video' : ''}`}>
          <button type="button" className="btn btn--whatsapp" onClick={handleShare} disabled={preparing && !file}>
            <WhatsAppIcon size={26} /> {preparing && !file ? t.preparing : t.share}
          </button>
          {isVideo && (
            <button
              type="button"
              className="reel__sound"
              onClick={onToggleMute}
              aria-label={muted ? t.soundOn : t.soundOff}
              title={muted ? t.soundOn : t.soundOff}
            >
              {muted ? '🔇' : '🔊'}
            </button>
          )}
          <button
            type="button"
            className="reel__save"
            onClick={handleSave}
            disabled={preparing && !file}
            aria-label={t.download}
            title={t.download}
          >
            ⬇
          </button>
        </div>

        {isVideo && (
          <div className="reel__progress" aria-hidden="true">
            <span style={{ width: `${progress * 100}%` }} />
          </div>
        )}
      </div>

      {toast && (
        <p className="toast reel__toast" role="status">
          {toast}
        </p>
      )}
    </>
  );
}
