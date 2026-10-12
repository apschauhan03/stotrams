import { useEffect, useState } from 'react';
import { editCandidate, fetchBlockOptions, isUnauthorized } from '../../api/client';
import type { BlockOptions, Candidate } from '../../api/types';
import { useOverlay } from '../../lib/useOverlay';

/** Live items can't be re-approved (the backend answers 409); take them down instead. */
export const canApprove = (item: Candidate) => item.status === 'GENERATED' || item.status === 'REJECTED';

const MIN_INSTRUCTION = 3;
const MAX_INSTRUCTION = 2000;

/** Typing in a field must never trigger the A/R/arrow shortcuts. */
const isTyping = (e: KeyboardEvent) =>
  e.target instanceof HTMLElement && /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName);

/**
 * Full-size review of one image: approve / reject / take down with keys, and edit with a
 * prompt. An edit makes a NEW candidate (the original is untouched); when it's ready the
 * panel switches to it so the two can be compared.
 */
export function ReviewLightbox({
  items,
  index,
  onIndexChange,
  onDecide,
  onEdited,
  onUnauthorized,
  onClose,
}: {
  items: Candidate[];
  index: number;
  onIndexChange: (index: number | null) => void;
  onDecide: (item: Candidate, next: 'APPROVED' | 'REJECTED') => Promise<void>;
  onEdited: (copy: Candidate) => void;
  onUnauthorized: () => void;
  onClose: () => void;
}) {
  const close = useOverlay(onClose);
  const item = items[index];

  async function decideAndAdvance(next: 'APPROVED' | 'REJECTED') {
    if (next === 'APPROVED' ? !canApprove(item) : item.status === 'REJECTED') return;
    await onDecide(item, next);
    // The item leaves the list, so the same index now points at the next one.
    if (items.length - 1 === 0) close();
    else onIndexChange(Math.min(index, items.length - 2));
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
      else if (isTyping(e)) return;
      else if (e.key === 'ArrowLeft' && index > 0) onIndexChange(index - 1);
      else if (e.key === 'ArrowRight' && index < items.length - 1) onIndexChange(index + 1);
      else if (e.key.toLowerCase() === 'a') decideAndAdvance('APPROVED');
      else if (e.key.toLowerCase() === 'r') decideAndAdvance('REJECTED');
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  return (
    <div className="viewer viewer--review" role="dialog" aria-modal="true">
      <div className="viewer__bar">
        <span className="viewer__count">
          #{item.id} · {index + 1} / {items.length}
        </span>
        <button type="button" className="viewer__close" onClick={close}>
          ✕ Close
        </button>
      </div>
      {item.editedFromId !== null && (
        <p className="viewer__note">
          ✏️ Edit of #{item.editedFromId}: “{item.editPrompt}”
        </p>
      )}
      <div className="viewer__stage">
        <img className="viewer__img" src={item.url} alt={`Candidate ${item.id}`} />
      </div>
      <div className="viewer__actions viewer__actions--row">
        {canApprove(item) && (
          <button type="button" className="btn btn--approve" onClick={() => decideAndAdvance('APPROVED')}>
            ✓ Approve (A)
          </button>
        )}
        {item.status !== 'REJECTED' && (
          <button type="button" className="btn btn--reject" onClick={() => decideAndAdvance('REJECTED')}>
            {item.status === 'PUBLISHED' ? '✕ Take down (R)' : '✕ Reject (R)'}
          </button>
        )}
      </div>
      <EditBox key={item.id} item={item} onEdited={onEdited} onUnauthorized={onUnauthorized} />
    </div>
  );
}

function EditBox({
  item,
  onEdited,
  onUnauthorized,
}: {
  item: Candidate;
  onEdited: (copy: Candidate) => void;
  onUnauthorized: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [options, setOptions] = useState<BlockOptions | null>(null);
  const [instruction, setInstruction] = useState('');
  const [model, setModel] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || options) return;
    fetchBlockOptions()
      .then((o) => {
        setOptions(o);
        const editable = o.models.filter((m) => m.canEdit);
        setModel(editable.some((m) => m.value === o.defaultEditModel) ? o.defaultEditModel : (editable[0]?.value ?? ''));
      })
      .catch((err) => (isUnauthorized(err) ? onUnauthorized() : setError(String(err))));
  }, [open, options, onUnauthorized]);

  const editable = options?.models.filter((m) => m.canEdit) ?? [];
  const selected = editable.find((m) => m.value === model);
  const text = instruction.trim();

  async function run() {
    if (text.length < MIN_INSTRUCTION || text.length > MAX_INSTRUCTION) {
      setError(`Describe the change (${MIN_INSTRUCTION}–${MAX_INSTRUCTION} characters).`);
      return;
    }
    if (!model) {
      setError('Pick a model.');
      return;
    }
    if (selected?.paid && !confirming) {
      setConfirming(true); // a real edit costs about as much as a new image
      return;
    }
    setConfirming(false);
    setBusy(true);
    setError(null);
    try {
      onEdited(await editCandidate(item.id, text, model));
    } catch (err) {
      if (isUnauthorized(err)) onUnauthorized();
      else setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <div className="edit-box edit-box--closed">
        <button type="button" className="btn btn--ghost btn--small" onClick={() => setOpen(true)}>
          ✏️ Edit with a prompt
        </button>
      </div>
    );
  }

  return (
    <div className="edit-box">
      <textarea
        rows={2}
        value={instruction}
        onChange={(e) => {
          setInstruction(e.target.value);
          setError(null);
          setConfirming(false);
        }}
        placeholder="e.g. remove the text at the bottom · make the sky more golden · fix her hands"
        aria-label="Edit instruction"
        disabled={busy}
        autoFocus
      />
      <div className="edit-box__row">
        <select
          value={model}
          onChange={(e) => {
            setModel(e.target.value);
            setConfirming(false);
          }}
          disabled={busy || !options}
          aria-label="Model for the edit"
        >
          {editable.map((m) => (
            <option key={m.value} value={m.value}>
              {m.label}
              {m.value === options?.defaultEditModel ? ' (cheaper, default)' : ''}
              {m.available ? '' : ' (unavailable)'}
            </option>
          ))}
        </select>
        <button
          type="button"
          className={`btn btn--small ${confirming ? 'btn--warn' : 'btn--primary'}`}
          onClick={run}
          disabled={busy || !options}
        >
          {busy ? 'Editing… (30–60 s)' : confirming ? 'Confirm: make edited copy (costs money)' : 'Make edited copy'}
        </button>
        {confirming && !busy && (
          <button type="button" className="btn btn--ghost btn--small" onClick={() => setConfirming(false)}>
            Cancel
          </button>
        )}
      </div>
      <p className="edit-box__hint">
        {error ? (
          <span className="edit-box__error">{error}</span>
        ) : selected && !selected.available ? (
          <span className="edit-box__error">Can't run right now: {selected.unavailableReason}</span>
        ) : (
          'The original stays as it is; the edited copy appears in "To review" for you to compare.'
        )}
      </p>
    </div>
  );
}
