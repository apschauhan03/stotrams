import { useCallback, useEffect, useRef } from 'react';

/**
 * Makes a full-screen overlay behave like a page:
 * - the phone's Back button closes it (instead of leaving the site — the #1 way elderly
 *   users "lose" an app),
 * - the page underneath doesn't scroll while it's open.
 *
 * Returns `close`, which the overlay's own close button should call.
 */
export function useOverlay(onClose: () => void): () => void {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    // Guarded so React StrictMode's double-mount in dev doesn't push two entries.
    if (!history.state?.overlay) history.pushState({ ...history.state, overlay: true }, '');
    const onPop = () => onCloseRef.current();
    window.addEventListener('popstate', onPop);

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      window.removeEventListener('popstate', onPop);
      document.body.style.overflow = previousOverflow;
    };
  }, []);

  return useCallback(() => {
    // Pop our history entry; the popstate listener then calls onClose.
    if (history.state?.overlay) history.back();
    else onCloseRef.current();
  }, []);
}
