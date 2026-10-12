import { useEffect, useRef, useState } from 'react';
import type { FeedItem } from '../api/types';
import { useLang } from '../i18n';
import { downloadFile, prepareShareFile, shareFile } from '../lib/share';
import { useOverlay } from '../lib/useOverlay';
import { WhatsAppIcon } from './WhatsAppIcon';

interface Props {
  items: FeedItem[];
  index: number;
  onIndexChange: (index: number) => void;
  onClose: () => void;
}

const SWIPE_THRESHOLD_PX = 50;

/** Full-screen view of one image with the big "Send on WhatsApp" button. */
export function ImageViewer({ items, index, onIndexChange, onClose }: Props) {
  const { t } = useLang();
  const close = useOverlay(onClose);
  const item = items[index];

  const [file, setFile] = useState<File | null>(null);
  const [preparing, setPreparing] = useState(true);
  const [toast, setToast] = useState<string | null>(null);
  const touchStartX = useRef<number | null>(null);

  const hasPrev = index > 0;
  const hasNext = index < items.length - 1;
  const goPrev = () => hasPrev && onIndexChange(index - 1);
  const goNext = () => hasNext && onIndexChange(index + 1);

  // Prepare the JPEG as soon as an image is shown, so the tap can share instantly.
  useEffect(() => {
    let cancelled = false;
    setFile(null);
    setPreparing(true);
    prepareShareFile(item.url, item.id)
      .then((f) => !cancelled && setFile(f))
      .catch(() => {
        // Leave file null — the button retries on tap.
      })
      .finally(() => !cancelled && setPreparing(false));
    return () => {
      cancelled = true;
    };
  }, [item.url, item.id]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
      if (e.key === 'ArrowLeft') goPrev();
      if (e.key === 'ArrowRight') goNext();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  async function handleShare() {
    const f = file ?? (await prepareShareFile(item.url, item.id).catch(() => null));
    if (!f) {
      setToast(t.error);
      return;
    }
    const outcome = await shareFile(f, t.shareText);
    if (outcome === 'downloaded') setToast(t.downloaded);
  }

  async function handleSave() {
    const f = file ?? (await prepareShareFile(item.url, item.id).catch(() => null));
    if (!f) {
      setToast(t.error);
      return;
    }
    downloadFile(f);
    setToast(t.downloaded);
  }

  return (
    <div
      className="viewer"
      role="dialog"
      aria-modal="true"
      onTouchStart={(e) => (touchStartX.current = e.touches[0].clientX)}
      onTouchEnd={(e) => {
        if (touchStartX.current === null) return;
        const dx = e.changedTouches[0].clientX - touchStartX.current;
        if (dx > SWIPE_THRESHOLD_PX) goPrev();
        if (dx < -SWIPE_THRESHOLD_PX) goNext();
        touchStartX.current = null;
      }}
    >
      <div className="viewer__bar">
        <span className="viewer__count">
          {index + 1} / {items.length}
        </span>
        <button type="button" className="viewer__close" onClick={close}>
          ✕ {t.close}
        </button>
      </div>

      <div className="viewer__stage">
        <button
          type="button"
          className="viewer__nav viewer__nav--prev"
          onClick={goPrev}
          disabled={!hasPrev}
          aria-label={t.previous}
        >
          ‹
        </button>
        <img key={item.id} className="viewer__img" src={item.url} alt="" />
        <button
          type="button"
          className="viewer__nav viewer__nav--next"
          onClick={goNext}
          disabled={!hasNext}
          aria-label={t.next}
        >
          ›
        </button>
      </div>

      <div className="viewer__actions">
        <button type="button" className="btn btn--whatsapp" onClick={handleShare} disabled={preparing}>
          <WhatsAppIcon size={28} /> {preparing ? t.preparing : t.share}
        </button>
        <button type="button" className="btn btn--ghost" onClick={handleSave} disabled={preparing}>
          ⬇ {t.download}
        </button>
      </div>

      {toast && (
        <p className="toast" role="status">
          {toast}
        </p>
      )}
    </div>
  );
}
