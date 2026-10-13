import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchFeed } from '../api/client';
import type { CategorySlug, FeedItem } from '../api/types';
import { CategoryTabs } from '../components/CategoryTabs';
import { Header } from '../components/Header';
import { ImageGrid } from '../components/ImageGrid';
import { BottomNav } from '../components/BottomNav';
import { ReelViewer } from '../components/ReelViewer';
import { useLang } from '../i18n';

/** Morning images until 4 PM, night images after — what people are looking for right now. */
function categoryForNow(date = new Date()): CategorySlug {
  return date.getHours() < 16 ? 'good-morning' : 'good-night';
}

type Status = 'loading' | 'idle' | 'error';

export function FeedPage() {
  const { lang, t } = useLang();
  const [category, setCategory] = useState<CategorySlug>(categoryForNow);
  const [items, setItems] = useState<FeedItem[]>([]);
  const [nextPage, setNextPage] = useState<number | null>(0);
  const [status, setStatus] = useState<Status>('loading');
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  // Responses for an old tab/language must not land in the new one.
  const generation = useRef(0);

  const loadPage = useCallback(
    async (page: number, gen: number) => {
      setStatus('loading');
      try {
        const res = await fetchFeed(lang, category, page);
        if (gen !== generation.current) return;
        setItems((prev) => (page === 0 ? res.items : [...prev, ...res.items]));
        setNextPage(res.nextPage);
        setStatus('idle');
      } catch {
        if (gen === generation.current) setStatus('error');
      }
    },
    [lang, category],
  );

  useEffect(() => {
    const gen = ++generation.current;
    setItems([]);
    setNextPage(0);
    setOpenIndex(null);
    loadPage(0, gen);
  }, [loadPage]);

  const loadMore = () => {
    if (status === 'idle' && nextPage !== null) loadPage(nextPage, generation.current);
  };

  const retry = () => loadPage(nextPage ?? 0, generation.current);

  return (
    <>
      <Header />
      <main className="page page--with-nav">
        <CategoryTabs value={category} onChange={setCategory} />

        <ImageGrid items={items} onOpen={setOpenIndex} onReachEnd={loadMore} hasMore={nextPage !== null} />

        {status === 'loading' && <p className="feed-status">{t.loading}</p>}
        {status === 'error' && (
          <div className="feed-status">
            <p>{t.error}</p>
            <button type="button" className="btn btn--primary" onClick={retry}>
              {t.retry}
            </button>
          </div>
        )}
        {status === 'idle' && items.length === 0 && <p className="feed-status">{t.empty}</p>}
        {status === 'idle' && items.length > 0 && nextPage === null && <p className="feed-status">{t.end}</p>}
      </main>

      <footer className="site-footer site-footer--with-nav">{t.footer}</footer>
      <BottomNav />

      {openIndex !== null && items[openIndex] && (
        <ReelViewer
          items={items}
          startIndex={openIndex}
          onClose={() => setOpenIndex(null)}
          onReachEnd={loadMore}
          hasMore={nextPage !== null}
        />
      )}
    </>
  );
}
