import { useEffect, useRef } from 'react';
import type { FeedItem } from '../api/types';
import { useLang } from '../i18n';

interface Props {
  items: FeedItem[];
  onOpen: (index: number) => void;
  /** Called when the user scrolls near the end and more pages exist. */
  onReachEnd: () => void;
  hasMore: boolean;
}

export function ImageGrid({ items, onOpen, onReachEnd, hasMore }: Props) {
  const { t } = useLang();
  const sentinel = useRef<HTMLDivElement>(null);
  const reachEnd = useRef(onReachEnd);
  reachEnd.current = onReachEnd;

  useEffect(() => {
    const el = sentinel.current;
    if (!el || !hasMore) return;
    // Start loading the next page well before the user hits the bottom.
    const observer = new IntersectionObserver(
      (entries) => entries[0].isIntersecting && reachEnd.current(),
      { rootMargin: '600px 0px' },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [hasMore, items.length]);

  return (
    <>
      <ul className="grid">
        {items.map((item, index) => (
          <li key={item.id} className="grid__cell">
            <button
              type="button"
              className="grid__item"
              onClick={() => onOpen(index)}
              aria-label={t.openImage}
            >
              {item.type === 'video' ? (
                <>
                  {/* #t=0.1 makes phones paint the first frame as the thumbnail. */}
                  <video src={`${item.url}#t=0.1`} muted playsInline preload="metadata" />
                  <span className="grid__play" aria-hidden="true">
                    ▶
                  </span>
                </>
              ) : (
                <img
                  src={item.url}
                  alt=""
                  loading="lazy"
                  decoding="async"
                  width={item.width ?? 1080}
                  height={item.height ?? 1350}
                />
              )}
            </button>
          </li>
        ))}
      </ul>
      <div ref={sentinel} aria-hidden="true" />
    </>
  );
}
