import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  createBlock,
  deleteBlock,
  fetchBlockOptions,
  fetchBlocks,
  generateAllBlocks,
  generateBlock,
  isUnauthorized,
  updateBlock,
} from '../../api/client';
import type { Block, BlockInput, BlockOptions } from '../../api/types';
import { Field } from './Field';
import { PrincipleSection } from './PrincipleSection';

// Generation blocks: each is a prompt plus how to run it (category, language, image model,
// images per run). Managed only from here — the backend stores them in prompt_template.

const MIN_PROMPT = 10;
const MAX_PROMPT = 4000;

const isFree = (model: string) => model.trim() === 'fake' || model.startsWith('fake:');

type Message = { text: string; error?: boolean } | null;

const NEW_BLOCK: BlockInput = {
  name: '',
  category: 'good-morning',
  language: 'hi',
  prompt: '',
  model: '', // filled with the first dropdown option when the editor opens
  imageCount: 10,
  active: true,
};

const DATE = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

export function BlocksPanel({ onGenerated, onUnauthorized }: { onGenerated: () => void; onUnauthorized: () => void }) {
  const [blocks, setBlocks] = useState<Block[] | null>(null);
  const [options, setOptions] = useState<BlockOptions | null>(null);
  /** null = no editor open; 'new' = adding; a number = editing that block. */
  const [editing, setEditing] = useState<number | 'new' | null>(null);
  const [message, setMessage] = useState<Message>(null);
  const [confirmAll, setConfirmAll] = useState(false);
  const [busyAll, setBusyAll] = useState(false);
  const [principleOpen, setPrincipleOpen] = useState(false);

  const fail = useCallback(
    (err: unknown) => {
      if (isUnauthorized(err)) onUnauthorized();
      else setMessage({ text: err instanceof Error ? err.message : String(err), error: true });
    },
    [onUnauthorized],
  );

  const reload = useCallback(async () => {
    try {
      setBlocks(await fetchBlocks());
    } catch (err) {
      fail(err);
    }
  }, [fail]);

  useEffect(() => {
    reload();
    fetchBlockOptions().then(setOptions).catch(fail);
  }, [reload, fail]);

  const active = (blocks ?? []).filter((b) => b.active);
  const activeTotal = active.reduce((sum, b) => sum + b.imageCount, 0);
  const allFree = active.every((b) => isFree(b.model));
  const overLimit = options !== null && activeTotal > options.maxTotalPerRun;

  async function runAll() {
    if (!allFree && !confirmAll) {
      setConfirmAll(true); // real providers cost money — make the second click deliberate
      return;
    }
    setConfirmAll(false);
    setBusyAll(true);
    setMessage(null);
    try {
      const started = await generateAllBlocks();
      const total = started.reduce((sum, s) => sum + s.requestedCount, 0);
      setMessage({ text: `Started ${started.length} block(s), ${total} image(s). They'll appear in "To review".` });
      onGenerated();
      reload();
    } catch (err) {
      fail(err);
    } finally {
      setBusyAll(false);
    }
  }

  return (
    <section className="admin__panel">
      <div className="blocks__head">
        <div>
          <h2>Generation blocks</h2>
          <p className="admin__muted">
            Each block is a prompt and how many images it makes per run. Images go to "To review".
          </p>
        </div>
        <div className="blocks__head-actions">
          <button
            type="button"
            className={`btn btn--small${principleOpen ? ' btn--active' : ''}`}
            onClick={() => setPrincipleOpen((o) => !o)}
            aria-expanded={principleOpen}
          >
            ✨ From principle prompt
          </button>
          <button
            type="button"
            className="btn btn--small"
            onClick={() => {
              setEditing('new');
              setMessage(null);
            }}
            disabled={editing === 'new'}
          >
            + Add block
          </button>
          <button
            type="button"
            className={`btn btn--small ${confirmAll ? 'btn--warn' : 'btn--primary'}`}
            onClick={runAll}
            disabled={busyAll || active.length === 0 || overLimit}
            title={overLimit ? `Active blocks exceed ${options?.maxTotalPerRun} images per run` : undefined}
          >
            {busyAll
              ? 'Starting…'
              : confirmAll
                ? `Confirm: ${activeTotal} images (costs money)`
                : `Generate all active (${activeTotal} images)`}
          </button>
          {confirmAll && (
            <button type="button" className="btn btn--small" onClick={() => setConfirmAll(false)}>
              Cancel
            </button>
          )}
        </div>
      </div>

      {overLimit && (
        <p className="admin__error">
          Active blocks add up to {activeTotal} images; one run may make at most {options?.maxTotalPerRun}. Pause a
          block or lower its count.
        </p>
      )}
      {message && <p className={message.error ? 'admin__error' : 'admin__success'}>{message.text}</p>}

      {principleOpen && options && (
        <PrincipleSection options={options} onCreated={() => reload()} onUnauthorized={onUnauthorized} />
      )}

      {editing === 'new' && options && (
        <BlockEditor
          initial={NEW_BLOCK}
          options={options}
          onCancel={() => setEditing(null)}
          onSave={async (input) => {
            await createBlock(input);
            setEditing(null);
            setMessage({ text: `Block "${input.name}" added.` });
            reload();
          }}
          onUnauthorized={onUnauthorized}
        />
      )}

      {blocks === null && <p className="admin__muted">Loading blocks…</p>}
      {blocks?.length === 0 && editing !== 'new' && (
        <p className="admin__muted">No blocks yet. Add one to start generating images.</p>
      )}

      <ul className="blocks">
        {blocks?.map((block) =>
          editing === block.id && options ? (
            <li key={block.id}>
              <BlockEditor
                initial={block}
                options={options}
                onCancel={() => setEditing(null)}
                onSave={async (input) => {
                  await updateBlock(block.id, input);
                  setEditing(null);
                  setMessage({ text: `Block "${input.name}" saved.` });
                  reload();
                }}
                onUnauthorized={onUnauthorized}
              />
            </li>
          ) : (
            <BlockCard
              key={block.id}
              block={block}
              options={options}
              onEdit={() => {
                setEditing(block.id);
                setMessage(null);
              }}
              onChanged={reload}
              onGenerated={(text) => {
                setMessage({ text });
                onGenerated();
                reload();
              }}
              onError={fail}
            />
          ),
        )}
      </ul>
    </section>
  );
}

function BlockCard({
  block,
  options,
  onEdit,
  onChanged,
  onGenerated,
  onError,
}: {
  block: Block;
  options: BlockOptions | null;
  onEdit: () => void;
  onChanged: () => void;
  onGenerated: (message: string) => void;
  onError: (err: unknown) => void;
}) {
  const [confirm, setConfirm] = useState<'generate' | 'delete' | null>(null);
  const [busy, setBusy] = useState(false);
  const free = isFree(block.model);
  const categoryName = options?.categories.find((c) => c.slug === block.category)?.name ?? block.category;

  async function act(action: () => Promise<void>) {
    setBusy(true);
    try {
      await action();
    } catch (err) {
      onError(err);
    } finally {
      setBusy(false);
      setConfirm(null);
    }
  }

  function generate() {
    if (!free && confirm !== 'generate') return setConfirm('generate');
    act(async () => {
      const res = await generateBlock(block.id);
      onGenerated(`"${res.blockName}": generating ${res.requestedCount} image(s). They'll appear in "To review".`);
    });
  }

  function remove() {
    if (confirm !== 'delete') return setConfirm('delete');
    act(async () => {
      await deleteBlock(block.id);
      onChanged();
    });
  }

  function toggleActive() {
    const { name, category, language, prompt, model, imageCount } = block;
    act(async () => {
      await updateBlock(block.id, { name, category, language, prompt, model, imageCount, active: !block.active });
      onChanged();
    });
  }

  return (
    <li className={`block${block.active ? '' : ' block--paused'}`}>
      <div className="block__top">
        <h3 className="block__name">{block.name}</h3>
        <label className="switch switch--small" title={block.active ? 'Included in "Generate all"' : 'Paused'}>
          <input type="checkbox" checked={block.active} onChange={toggleActive} disabled={busy} />
          <span className="switch__track" aria-hidden="true" />
          {block.active ? 'Active' : 'Paused'}
        </label>
      </div>

      <div className="block__chips">
        <span className="chip">{block.category === 'good-night' ? '🌙' : '🌅'} {categoryName}</span>
        <span className="chip">{block.language.toUpperCase()}</span>
        <span className={`chip${free ? '' : ' chip--paid'}`}>
          {options?.models.find((m) => m.value === block.model)?.label ?? block.model}
        </span>
        <span className="chip chip--count">{block.imageCount} images / run</span>
      </div>

      <p className="block__prompt" title={block.prompt}>
        {block.prompt}
      </p>

      <p className="admin__muted block__last">
        {block.lastGeneratedAt ? `Last run: ${DATE.format(new Date(block.lastGeneratedAt))} IST` : 'Never run'}
      </p>

      <div className="block__actions">
        <button
          type="button"
          className={`btn btn--small ${confirm === 'generate' ? 'btn--warn' : 'btn--primary'}`}
          onClick={generate}
          disabled={busy}
        >
          {confirm === 'generate' ? `Confirm: ${block.imageCount} images (costs money)` : `Generate ${block.imageCount}`}
        </button>
        <button type="button" className="btn btn--small" onClick={onEdit} disabled={busy}>
          Edit
        </button>
        <button
          type="button"
          className={`btn btn--small${confirm === 'delete' ? ' btn--reject' : ''}`}
          onClick={remove}
          disabled={busy}
        >
          {confirm === 'delete' ? 'Confirm delete' : 'Delete'}
        </button>
        {confirm && (
          <button type="button" className="btn btn--small" onClick={() => setConfirm(null)} disabled={busy}>
            Cancel
          </button>
        )}
      </div>
    </li>
  );
}

function BlockEditor({
  initial,
  options,
  onSave,
  onCancel,
  onUnauthorized,
}: {
  initial: BlockInput;
  options: BlockOptions;
  onSave: (input: BlockInput) => Promise<void>;
  onCancel: () => void;
  onUnauthorized: () => void;
}) {
  const [form, setForm] = useState<BlockInput>({
    name: initial.name,
    category: initial.category,
    language: initial.language,
    prompt: initial.prompt,
    model: initial.model || options.models[0]?.value || '',
    imageCount: initial.imageCount,
    active: initial.active,
  });
  // A block saved with a model that's no longer offered still shows (and keeps) it.
  const modelChoices = options.models.some((m) => m.value === form.model)
    ? options.models
    : [
        ...options.models,
        { value: form.model, label: `${form.model} (current, not in list)`, paid: true, available: true, unavailableReason: null },
      ];
  const selectedModel = modelChoices.find((m) => m.value === form.model);
  const [errors, setErrors] = useState<Partial<Record<keyof BlockInput | 'form', string>>>({});
  const [saving, setSaving] = useState(false);

  const set = <K extends keyof BlockInput>(key: K, value: BlockInput[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined, form: undefined }));
  };

  function validate(): boolean {
    const next: typeof errors = {};
    if (!form.name.trim()) next.name = 'Give the block a name.';
    const prompt = form.prompt.trim();
    if (prompt.length < MIN_PROMPT) next.prompt = `Write a prompt (at least ${MIN_PROMPT} characters).`;
    if (prompt.length > MAX_PROMPT) next.prompt = `Keep the prompt under ${MAX_PROMPT} characters.`;
    if (!Number.isInteger(form.imageCount) || form.imageCount < 1 || form.imageCount > options.maxImageCount) {
      next.imageCount = `Between 1 and ${options.maxImageCount}.`;
    }
    if (!form.model) next.model = 'Pick an image model.';
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    setSaving(true);
    try {
      await onSave({ ...form, name: form.name.trim(), prompt: form.prompt.trim(), model: form.model.trim() });
    } catch (err) {
      if (isUnauthorized(err)) onUnauthorized();
      else setErrors({ form: err instanceof Error ? err.message : String(err) });
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="block-editor" onSubmit={submit} noValidate>
      <div className="block-editor__grid">
        <Field label="Name" error={errors.name} wide>
          <input
            value={form.name}
            onChange={(e) => set('name', e.target.value)}
            placeholder="e.g. Monday — Shiv ji at sunrise"
            maxLength={128}
            aria-invalid={!!errors.name}
            autoFocus
          />
        </Field>
        <Field label="Category">
          <select value={form.category} onChange={(e) => set('category', e.target.value as BlockInput['category'])}>
            {options.categories.map((c) => (
              <option key={c.slug} value={c.slug}>
                {c.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Language">
          <select value={form.language} onChange={(e) => set('language', e.target.value as BlockInput['language'])}>
            {options.languages.map((l) => (
              <option key={l} value={l}>
                {l === 'hi' ? 'Hindi' : l === 'en' ? 'English' : l}
              </option>
            ))}
          </select>
        </Field>
        <Field label={`Images per run (1–${options.maxImageCount})`} error={errors.imageCount}>
          <input
            type="number"
            min={1}
            max={options.maxImageCount}
            value={Number.isNaN(form.imageCount) ? '' : form.imageCount}
            onChange={(e) => set('imageCount', e.target.valueAsNumber)}
            aria-invalid={!!errors.imageCount}
          />
        </Field>
        <Field
          label="Image model"
          error={
            errors.model ??
            (selectedModel && !selectedModel.available
              ? `Can't run right now: ${selectedModel.unavailableReason}`
              : undefined)
          }
          hint={
            selectedModel && !selectedModel.paid
              ? 'Free: draws a placeholder picture, for testing only.'
              : 'Costs money per image.'
          }
        >
          <select value={form.model} onChange={(e) => set('model', e.target.value)} aria-invalid={!!errors.model}>
            {modelChoices.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
                {m.available ? '' : ' (unavailable)'}
              </option>
            ))}
          </select>
        </Field>
        <Field
          label={`Prompt (${form.prompt.trim().length}/${MAX_PROMPT})`}
          error={errors.prompt}
          hint='Tip: end with "no text, no watermark" — text is added later, per language.'
          wide
        >
          <textarea
            rows={5}
            value={form.prompt}
            onChange={(e) => set('prompt', e.target.value)}
            placeholder="Describe the image: deity, setting, light, mood, style…"
            aria-invalid={!!errors.prompt}
          />
        </Field>
      </div>

      <label className="switch">
        <input type="checkbox" checked={form.active} onChange={(e) => set('active', e.target.checked)} />
        <span className="switch__track" aria-hidden="true" />
        Active (included in "Generate all")
      </label>

      {errors.form && <p className="admin__error">{errors.form}</p>}

      <div className="block__actions">
        <button type="submit" className="btn btn--small btn--primary" disabled={saving}>
          {saving ? 'Saving…' : 'Save block'}
        </button>
        <button type="button" className="btn btn--small" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
      </div>
    </form>
  );
}
