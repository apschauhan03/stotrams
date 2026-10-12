import { useEffect, useState } from 'react';
import { createBlocksBulk, fetchPrinciple, isUnauthorized, writePromptDrafts } from '../../api/client';
import type { BlockOptions, BulkBlocksInput, Principle, PromptDraft } from '../../api/types';
import { Field } from './Field';

// The principle prompt: one saved master instruction that a text model expands into N
// distinct image prompts. The drafts are reviewed and edited here, then become N blocks
// with shared settings. Nothing is saved as blocks until "Create blocks".

const MIN_PRINCIPLE = 10;
const MAX_PRINCIPLE = 8000;
const MAX_DRAFTS = 50;

const PLACEHOLDER = `e.g. Navratri: one image for each of the 9 forms of Maa Durga (Shailputri to Siddhidatri), each with her correct iconography and vahana. Warm sunrise temple settings, marigolds and diyas. Write the goddess's name in elegant English text at the bottom.`;

type Message = { text: string; error?: boolean } | null;
type Row = PromptDraft & { keep: boolean };

export function PrincipleSection({
  options,
  onCreated,
  onUnauthorized,
}: {
  options: BlockOptions;
  onCreated: (count: number) => void;
  onUnauthorized: () => void;
}) {
  const [principle, setPrinciple] = useState<Principle | null>(null);
  const [text, setText] = useState('');
  const [count, setCount] = useState(10);
  const [rows, setRows] = useState<Row[] | null>(null);
  const [writing, setWriting] = useState(false);
  const [creating, setCreating] = useState(false);
  const [message, setMessage] = useState<Message>(null);
  const [textError, setTextError] = useState<string | null>(null);
  // Shared settings for every created block. One image per block by default:
  // "20 blocks, 20 different images".
  const [shared, setShared] = useState<Omit<BulkBlocksInput, 'items'>>({
    category: options.categories[0]?.slug ?? 'good-morning',
    language: 'hi',
    model: options.models[0]?.value ?? '',
    imageCount: 1,
    active: true,
  });

  const fail = (err: unknown) => {
    if (isUnauthorized(err)) onUnauthorized();
    else setMessage({ text: err instanceof Error ? err.message : String(err), error: true });
  };

  useEffect(() => {
    fetchPrinciple()
      .then((p) => {
        setPrinciple(p);
        setText(p.prompt);
      })
      .catch(fail);
  }, []); // once, on open

  const validCount = Number.isInteger(count) && count >= 1 && count <= MAX_DRAFTS;

  async function write() {
    const t = text.trim();
    if (t.length < MIN_PRINCIPLE || t.length > MAX_PRINCIPLE) {
      setTextError(`Write the principle (${MIN_PRINCIPLE}–${MAX_PRINCIPLE} characters).`);
      return;
    }
    if (!validCount) return;
    setWriting(true);
    setMessage(null);
    try {
      const drafts = await writePromptDrafts(t, count, shared.category);
      setRows(drafts.map((d) => ({ ...d, keep: true })));
      setPrinciple((p) => (p ? { ...p, prompt: t, updatedAt: new Date().toISOString() } : p));
      setMessage({
        text:
          drafts.length === count
            ? `${drafts.length} prompts written. Review and edit them, then create the blocks.`
            : `Only ${drafts.length} of ${count} prompts came back. Write again, or create these.`,
        error: drafts.length !== count,
      });
    } catch (err) {
      fail(err);
    } finally {
      setWriting(false);
    }
  }

  const kept = (rows ?? []).filter((r) => r.keep);
  const invalidRow = kept.findIndex((r) => !r.name.trim() || r.prompt.trim().length < 10);
  const model = options.models.find((m) => m.value === shared.model);
  const totalImages = kept.length * shared.imageCount;

  async function create() {
    if (kept.length === 0 || invalidRow >= 0) return;
    setCreating(true);
    setMessage(null);
    try {
      const created = await createBlocksBulk({
        ...shared,
        items: kept.map(({ name, prompt }) => ({ name: name.trim(), prompt: prompt.trim() })),
      });
      setRows(null);
      setMessage({ text: `${created.length} blocks created. They're in the list below.` });
      onCreated(created.length);
    } catch (err) {
      fail(err);
    } finally {
      setCreating(false);
    }
  }

  const updateRow = (i: number, patch: Partial<Row>) =>
    setRows((rs) => rs && rs.map((r, j) => (j === i ? { ...r, ...patch } : r)));

  return (
    <div className="principle">
      <div className="principle__intro">
        <h3>✨ Write blocks from a principle prompt</h3>
        <p className="admin__muted">
          Describe what you want once. {principle?.model ?? 'A text model'} writes that many different image prompts;
          you review them, then they become blocks.
        </p>
      </div>

      <Field
        label={`Principle prompt (${text.trim().length}/${MAX_PRINCIPLE})`}
        error={textError ?? undefined}
        hint={principle?.updatedAt ? `Saved. Last saved ${new Date(principle.updatedAt).toLocaleString('en-IN')}.` : 'Saved automatically when you write prompts.'}
        wide
      >
        <textarea
          rows={5}
          value={text}
          placeholder={PLACEHOLDER}
          onChange={(e) => {
            setText(e.target.value);
            setTextError(null);
          }}
          aria-invalid={!!textError}
        />
      </Field>

      <div className="principle__row">
        <Field label={`How many prompts (1–${MAX_DRAFTS})`} error={validCount ? undefined : `Between 1 and ${MAX_DRAFTS}.`}>
          <input
            type="number"
            min={1}
            max={MAX_DRAFTS}
            value={Number.isNaN(count) ? '' : count}
            onChange={(e) => setCount(e.target.valueAsNumber)}
            aria-invalid={!validCount}
          />
        </Field>
        <Field label="For category">
          <select
            value={shared.category}
            onChange={(e) => setShared((s) => ({ ...s, category: e.target.value as BulkBlocksInput['category'] }))}
          >
            {options.categories.map((c) => (
              <option key={c.slug} value={c.slug}>
                {c.name}
              </option>
            ))}
          </select>
        </Field>
        <button type="button" className="btn btn--small btn--primary" onClick={write} disabled={writing || !validCount}>
          {writing ? `Writing ${count} prompts… (up to a minute)` : rows ? `Write ${count} again` : `Write ${count} prompts`}
        </button>
      </div>

      {message && <p className={message.error ? 'admin__error' : 'admin__success'}>{message.text}</p>}

      {rows && (
        <div className="drafts">
          <ol className="drafts__list">
            {rows.map((row, i) => (
              <li key={i} className={`draft${row.keep ? '' : ' draft--dropped'}`}>
                <div className="draft__head">
                  <label className="draft__keep">
                    <input type="checkbox" checked={row.keep} onChange={(e) => updateRow(i, { keep: e.target.checked })} />
                    Keep
                  </label>
                  <input
                    className="draft__name"
                    value={row.name}
                    maxLength={128}
                    onChange={(e) => updateRow(i, { name: e.target.value })}
                    aria-label={`Name of prompt ${i + 1}`}
                    aria-invalid={row.keep && !row.name.trim()}
                    disabled={!row.keep}
                  />
                </div>
                <textarea
                  className="draft__prompt"
                  rows={3}
                  value={row.prompt}
                  onChange={(e) => updateRow(i, { prompt: e.target.value })}
                  aria-label={`Prompt ${i + 1}`}
                  aria-invalid={row.keep && row.prompt.trim().length < 10}
                  disabled={!row.keep}
                />
              </li>
            ))}
          </ol>

          <div className="principle__shared">
            <h4>Settings for the new blocks</h4>
            <div className="block-editor__grid">
              <Field label="Language">
                <select
                  value={shared.language}
                  onChange={(e) => setShared((s) => ({ ...s, language: e.target.value as BulkBlocksInput['language'] }))}
                >
                  {options.languages.map((l) => (
                    <option key={l} value={l}>
                      {l === 'hi' ? 'Hindi' : l === 'en' ? 'English' : l}
                    </option>
                  ))}
                </select>
              </Field>
              <Field
                label="Image model"
                error={model && !model.available ? `Can't run right now: ${model.unavailableReason}` : undefined}
              >
                <select value={shared.model} onChange={(e) => setShared((s) => ({ ...s, model: e.target.value }))}>
                  {options.models.map((m) => (
                    <option key={m.value} value={m.value}>
                      {m.label}
                      {m.available ? '' : ' (unavailable)'}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={`Images per block (1–${options.maxImageCount})`}>
                <input
                  type="number"
                  min={1}
                  max={options.maxImageCount}
                  value={Number.isNaN(shared.imageCount) ? '' : shared.imageCount}
                  onChange={(e) => setShared((s) => ({ ...s, imageCount: e.target.valueAsNumber }))}
                />
              </Field>
            </div>
            <label className="switch">
              <input
                type="checkbox"
                checked={shared.active}
                onChange={(e) => setShared((s) => ({ ...s, active: e.target.checked }))}
              />
              <span className="switch__track" aria-hidden="true" />
              Active (included in "Generate all")
            </label>
          </div>

          {invalidRow >= 0 && (
            <p className="admin__error">Draft {rows.indexOf(kept[invalidRow]) + 1} needs a name and a prompt.</p>
          )}
          <div className="block__actions">
            <button
              type="button"
              className="btn btn--small btn--primary"
              onClick={create}
              disabled={
                creating ||
                kept.length === 0 ||
                invalidRow >= 0 ||
                !Number.isInteger(shared.imageCount) ||
                shared.imageCount < 1 ||
                shared.imageCount > options.maxImageCount
              }
            >
              {creating
                ? 'Creating…'
                : `Create ${kept.length} blocks · ${shared.imageCount} image${shared.imageCount === 1 ? '' : 's'} each (${totalImages} total)`}
            </button>
            <button type="button" className="btn btn--small" onClick={() => setRows(null)} disabled={creating}>
              Discard drafts
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
