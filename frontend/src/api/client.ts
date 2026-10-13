// The one place that talks to the backend. Feed calls are public; /admin calls will sit
// behind Cloudflare Access + Spring Security once deployed (cookies ride along).
//
// Demo mode: in dev, if the backend can't be reached, every call transparently switches
// to the generated content in ./mock so the UI is still usable. A production build never
// falls back — it shows a real error instead.

import * as mock from './mock';
import type {
  AdminSession,
  Block,
  BlockInput,
  BlockOptions,
  BulkBlocksInput,
  Candidate,
  CandidateStatus,
  CategorySlug,
  FeedResponse,
  GenerateResponse,
  Lang,
  MediaType,
  Panchang,
  Principle,
  PublishResult,
  PublishSchedule,
  WriteResult,
} from './types';

const API_BASE: string = import.meta.env.VITE_API_BASE ?? (import.meta.env.DEV ? '/backend' : '');
const MOCK_ALLOWED = import.meta.env.DEV || import.meta.env.VITE_USE_MOCK === 'true';

let demoMode = import.meta.env.VITE_USE_MOCK === 'true';
const listeners = new Set<(demo: boolean) => void>();

export const isDemoMode = () => demoMode;

export function onDemoModeChange(listener: (demo: boolean) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function enterDemoMode() {
  if (demoMode) return;
  demoMode = true;
  listeners.forEach((l) => l(true));
}

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

/** Thrown when the backend is down/unreachable — the only case demo mode covers. */
class Unreachable extends Error {}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, { credentials: 'include', ...init });
  } catch {
    throw new Unreachable();
  }
  const isJson = res.headers.get('content-type')?.includes('application/json');
  // The Vite proxy answers 500/502/504 with no JSON when the backend is down.
  if (!isJson && res.status >= 500) throw new Unreachable();
  if (!res.ok) {
    const body = isJson ? await res.json().catch(() => null) : null;
    const message = body?.message || body?.error || `Request failed (${res.status})`;
    throw new ApiError(message, res.status);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/** True when the server says "log in first" (no session, or it expired). */
export const isUnauthorized = (err: unknown) => err instanceof ApiError && err.status === 401;

/** Runs the real call; on an unreachable backend in dev, runs the demo version instead. */
async function withFallback<T>(real: () => Promise<T>, demo: () => Promise<T>): Promise<T> {
  if (demoMode) return demo();
  try {
    return await real();
  } catch (err) {
    if (err instanceof Unreachable && MOCK_ALLOWED) {
      enterDemoMode();
      return demo();
    }
    if (err instanceof Unreachable) throw new ApiError('Server unreachable', 0);
    throw err;
  }
}

export function fetchFeed(lang: Lang, category: CategorySlug, page: number): Promise<FeedResponse> {
  // No `lang` filter on the real feed: generated images carry no text ("no text" is in every
  // prompt), so Hindi and English visitors see the same pictures. Add it back once images
  // get per-language quote overlays. The demo still draws text, so it does use `lang`.
  const qs = new URLSearchParams({ category, page: String(page) });
  return withFallback(
    () => request<FeedResponse>(`/api/feed?${qs}`),
    () => mock.mockFeed(lang, category, page),
  );
}

/**
 * A day's panchang (computed on the server); `date` yyyy-mm-dd, default today in India.
 * Null in demo mode: there's nothing to compute it.
 */
export function fetchPanchang(date?: string): Promise<Panchang | null> {
  const qs = date ? `?${new URLSearchParams({ date })}` : '';
  return withFallback(
    () => request<Panchang>(`/api/panchang${qs}`),
    async () => null,
  );
}

// ---- Admin session (POST /admin/login sets an HttpOnly session cookie) --------------

export function fetchAdminSession(): Promise<AdminSession> {
  return withFallback(
    () => request<AdminSession>('/admin/me'),
    () => mock.mockAdminSession(),
  );
}

export function login(email: string, password: string): Promise<AdminSession> {
  return withFallback(
    () =>
      request<AdminSession>('/admin/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      }),
    () => mock.mockAdminSession(),
  );
}

export function logout(): Promise<void> {
  return withFallback(
    () => request<void>('/admin/logout', { method: 'POST' }),
    async () => undefined,
  );
}

export function fetchCandidates(status: CandidateStatus, type: MediaType): Promise<Candidate[]> {
  return withFallback(
    () => request<Candidate[]>(`/admin/candidates?status=${status}&type=${type.toUpperCase()}`),
    () => mock.mockCandidates(status, type),
  );
}

/**
 * Uploads a video into the video review queue (it still needs approving and publishing).
 * `width`/`height` are read from the file in the browser, null if that failed.
 */
export function uploadVideo(
  file: File,
  category: CategorySlug,
  lang: Lang,
  size: { width: number; height: number } | null,
): Promise<Candidate> {
  const form = new FormData();
  form.append('file', file);
  form.append('category', category);
  form.append('lang', lang);
  if (size) {
    form.append('width', String(size.width));
    form.append('height', String(size.height));
  }
  return withFallback(
    () => request<Candidate>('/admin/videos', { method: 'POST', body: form }),
    () => mock.mockUploadVideo(file, category, lang, size),
  );
}

export function approveCandidate(id: number): Promise<Candidate> {
  return withFallback(
    () => request<Candidate>(`/admin/candidates/${id}/approve`, { method: 'POST' }),
    () => mock.mockSetStatus(id, 'APPROVED'),
  );
}

export function rejectCandidate(id: number): Promise<Candidate> {
  return withFallback(
    () => request<Candidate>(`/admin/candidates/${id}/reject`, { method: 'POST' }),
    () => mock.mockSetStatus(id, 'REJECTED'),
  );
}

/** Puts every approved item live now. */
export function publishNow(): Promise<PublishResult> {
  return withFallback(
    () => request<PublishResult>('/admin/publish', { method: 'POST' }),
    () => mock.mockPublishNow(),
  );
}

export function fetchPublishSchedule(): Promise<PublishSchedule> {
  return withFallback(
    () => request<PublishSchedule>('/admin/publish/schedule'),
    () => mock.mockPublishSchedule(),
  );
}

export function updatePublishSchedule(enabled: boolean, cron: string): Promise<PublishSchedule> {
  return withFallback(
    () =>
      request<PublishSchedule>('/admin/publish/schedule', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled, cron }),
      }),
    () => mock.mockUpdatePublishSchedule(enabled, cron),
  );
}

/**
 * Edits an image with a prompt and resolves with the NEW candidate (~30–60 s; the source
 * is untouched). Costs money with a real provider.
 */
export function editCandidate(id: number, prompt: string, model: string): Promise<Candidate> {
  return withFallback(
    () =>
      request<Candidate>(`/admin/candidates/${id}/edit`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt, model }),
      }),
    () => mock.mockEditCandidate(id, prompt),
  );
}

// ---- Generation blocks ---------------------------------------------------------------

const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export function fetchBlocks(): Promise<Block[]> {
  return withFallback(() => request<Block[]>('/admin/blocks'), () => mock.mockBlocks());
}

export function fetchBlockOptions(): Promise<BlockOptions> {
  return withFallback(() => request<BlockOptions>('/admin/blocks/options'), () => mock.mockBlockOptions());
}

export function createBlock(input: BlockInput): Promise<Block> {
  return withFallback(() => request<Block>('/admin/blocks', json('POST', input)), () => mock.mockSaveBlock(null, input));
}

export function updateBlock(id: number, input: BlockInput): Promise<Block> {
  return withFallback(
    () => request<Block>(`/admin/blocks/${id}`, json('PUT', input)),
    () => mock.mockSaveBlock(id, input),
  );
}

export function deleteBlock(id: number): Promise<void> {
  return withFallback(
    () => request<void>(`/admin/blocks/${id}`, { method: 'DELETE' }),
    () => mock.mockDeleteBlock(id),
  );
}

export function createBlocksBulk(input: BulkBlocksInput): Promise<Block[]> {
  return withFallback(
    () => request<Block[]>('/admin/blocks/bulk', json('POST', input)),
    () => mock.mockBulkCreate(input),
  );
}

// ---- Principle prompt ------------------------------------------------------------------

export function fetchPrinciple(): Promise<Principle> {
  return withFallback(() => request<Principle>('/admin/principle'), () => mock.mockPrinciple());
}

/** Forgets the saved master prompt; the built-in default is used again. */
export function resetPrinciple(): Promise<Principle> {
  return withFallback(() => request<Principle>('/admin/principle', { method: 'DELETE' }), () => mock.mockSavePrinciple(''));
}

export function savePrinciple(prompt: string): Promise<Principle> {
  return withFallback(
    () => request<Principle>('/admin/principle', json('PUT', { prompt })),
    () => mock.mockSavePrinciple(prompt),
  );
}

/**
 * Saves the principle and asks the text model for `count` draft prompts (nothing else saved).
 * `date` (yyyy-mm-dd, or '' for none) is the day the images are for: its panchang is computed
 * on the server and, with `checkWeb`, also looked up on the web (costs a little) to compare.
 */
export function writePromptDrafts(
  prompt: string,
  count: number,
  category: CategorySlug,
  date: string,
  checkWeb: boolean,
): Promise<WriteResult> {
  return withFallback(
    () =>
      request<WriteResult>(
        '/admin/principle/write',
        json('POST', { prompt, count, category, date: date || null, checkWeb: checkWeb && !!date }),
      ),
    async () => ({
      drafts: await mock.mockWritePrompts(prompt, count),
      calendar: null,
      researchSummary: '',
      recommendation: '',
    }),
  );
}

/** Generates one block's images. Costs money with a real provider. */
export function generateBlock(id: number): Promise<GenerateResponse> {
  return withFallback(
    () => request<GenerateResponse>(`/admin/blocks/${id}/generate`, { method: 'POST' }),
    () => mock.mockGenerateBlock(id),
  );
}

/** Generates every active block. Costs money with a real provider. */
export function generateAllBlocks(): Promise<GenerateResponse[]> {
  return withFallback(
    () => request<GenerateResponse[]>('/admin/blocks/generate-all', { method: 'POST' }),
    () => mock.mockGenerateAll(),
  );
}
