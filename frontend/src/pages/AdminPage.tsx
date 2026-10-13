import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import {
  approveCandidate,
  fetchAdminSession,
  fetchCandidates,
  isDemoMode,
  isUnauthorized,
  login,
  logout,
  onDemoModeChange,
  fetchPublishSchedule,
  publishNow,
  rejectCandidate,
  updatePublishSchedule,
} from '../api/client';
import type { AdminSession, Candidate, CandidateStatus, MediaType, PublishSchedule } from '../api/types';
import { LotusMark } from '../components/LotusMark';
import { BlocksPanel } from './admin/BlocksPanel';
import { canApprove, ReviewLightbox } from './admin/ReviewLightbox';
import { VideoUploadPanel } from './admin/VideoUploadPanel';

// Admin is an internal tool for one person, so it's English-only and denser than the
// public site. Access is enforced by the backend (Spring Security session on /admin/**);
// this page only shows a login form until the server accepts the credentials.

// The lifecycle, in order: review -> approved (queued) -> live on the public site.
const STATUSES: CandidateStatus[] = ['GENERATED', 'APPROVED', 'PUBLISHED', 'REJECTED'];
const STATUS_LABEL: Record<CandidateStatus, string> = {
  GENERATED: 'To review',
  APPROVED: 'Approved, waiting',
  PUBLISHED: 'Live on site',
  REJECTED: 'Rejected',
};
const MEDIA_TYPES: { type: MediaType; label: string }[] = [
  { type: 'image', label: '🖼️ Photos' },
  { type: 'video', label: '🎬 Videos' },
];
const MEDIA_TYPE_KEY = 'stotram.admin.mediaType';

function savedMediaType(): MediaType {
  try {
    return localStorage.getItem(MEDIA_TYPE_KEY) === 'video' ? 'video' : 'image';
  } catch {
    return 'image';
  }
}

const POLL_MS = 3000;
const POLL_FOR_MS = 3 * 60_000;

/** Decides between the login screen and the panel, based on the server's session. */
export function AdminPage() {
  const [session, setSession] = useState<AdminSession | null | 'checking'>('checking');

  useEffect(() => {
    fetchAdminSession()
      .then(setSession)
      .catch(() => setSession(null));
  }, []);

  if (session === 'checking') {
    return (
      <div className="login">
        <p className="admin__muted">Checking session…</p>
      </div>
    );
  }
  if (session === null) return <LoginScreen onLoggedIn={setSession} />;
  return (
    <AdminPanel
      session={session}
      onSignedOut={() => setSession(null)}
    />
  );
}

function LoginScreen({ onLoggedIn }: { onLoggedIn: (session: AdminSession) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!email.trim() || !password) {
      setError('Enter your email and password.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      onLoggedIn(await login(email.trim(), password));
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      setPassword('');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login">
      <form className="login__card" onSubmit={submit} noValidate>
        <LotusMark size={72} gap="#FFFDF8" />
        <h1 className="login__title">Stotrams</h1>
        <p className="login__subtitle">Admin sign in</p>

        <label className="login__field">
          Email
          <input
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              setError(null);
            }}
            autoFocus
          />
        </label>
        <label className="login__field">
          Password
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              setError(null);
            }}
          />
        </label>

        {error && (
          <p className="admin__error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="btn btn--primary login__submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <a href="/" className="login__back">
          ← Back to the public site
        </a>
      </form>
    </div>
  );
}

function AdminPanel({ session, onSignedOut }: { session: AdminSession; onSignedOut: () => void }) {
  const [mediaType, setMediaTypeState] = useState<MediaType>(savedMediaType);
  const setMediaType = (next: MediaType) => {
    setMediaTypeState(next);
    try {
      localStorage.setItem(MEDIA_TYPE_KEY, next);
    } catch {
      // Not remembered across reloads; the toggle still works.
    }
  };
  const [status, setStatus] = useState<CandidateStatus>('GENERATED');
  const [items, setItems] = useState<Candidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pollUntil, setPollUntil] = useState(0);
  const [previewIndex, setPreviewIndex] = useState<number | null>(null);
  const openAfterLoad = useRef<number | null>(null);

  function showEditedCopy(copy: Candidate) {
    openAfterLoad.current = copy.id;
    setPreviewIndex(null);
    if (status !== 'GENERATED') setStatus('GENERATED'); // reloads via the effect below
    else load(true);
  }
  const [demo, setDemo] = useState(isDemoMode);
  useEffect(() => onDemoModeChange(setDemo), []);

  const [counts, setCounts] = useState<Partial<Record<CandidateStatus, number>>>({});
  // "Publish now" publishes approved photos AND videos, so its count covers both.
  const [otherApproved, setOtherApproved] = useState(0);

  /** A 401 mid-session (expired, or logged out elsewhere) sends us back to the login form. */
  const fail = useCallback(
    (err: unknown) => {
      if (isUnauthorized(err)) onSignedOut();
      else setError(err instanceof Error ? err.message : String(err));
    },
    [onSignedOut],
  );

  async function signOut() {
    await logout().catch(() => undefined);
    onSignedOut();
  }

  const load = useCallback(
    async (quiet = false) => {
      if (!quiet) setLoading(true);
      try {
        // All four queues at once: the current tab's items, plus counts for every tab badge.
        const otherType: MediaType = mediaType === 'image' ? 'video' : 'image';
        const [lists, other] = await Promise.all([
          Promise.all(STATUSES.map((s) => fetchCandidates(s, mediaType))),
          fetchCandidates('APPROVED', otherType),
        ]);
        setCounts(Object.fromEntries(STATUSES.map((s, i) => [s, lists[i].length])));
        setOtherApproved(other.length);
        const list = lists[STATUSES.indexOf(status)];
        setItems(list);
        setError(null);
        // Just made an edited copy? Open it, so it can be compared and approved straight away.
        if (openAfterLoad.current !== null) {
          const i = list.findIndex((c) => c.id === openAfterLoad.current);
          if (i >= 0) {
            setPreviewIndex(i);
            openAfterLoad.current = null;
          }
        }
      } catch (err) {
        fail(err);
      } finally {
        setLoading(false);
      }
    },
    [status, mediaType, fail],
  );

  useEffect(() => {
    setPreviewIndex(null);
    load();
  }, [load]);

  // After starting a batch, keep refreshing so images appear as they land.
  useEffect(() => {
    if (pollUntil < Date.now()) return;
    const timer = setInterval(() => {
      if (Date.now() > pollUntil) clearInterval(timer);
      else load(true);
    }, POLL_MS);
    return () => clearInterval(timer);
  }, [pollUntil, load]);

  async function decide(item: Candidate, next: 'APPROVED' | 'REJECTED') {
    const from = item.status;
    try {
      if (next === 'APPROVED') await approveCandidate(item.id);
      else await rejectCandidate(item.id);
      // It now belongs to another tab — drop it from this one.
      setItems((prev) => prev.filter((c) => c.id !== item.id));
      setCounts((prev) => ({
        ...prev,
        [from]: Math.max(0, (prev[from] ?? 1) - 1),
        [next]: (prev[next] ?? 0) + 1,
      }));
    } catch (err) {
      fail(err);
    }
  }

  return (
    <div className="admin">
      <header className="admin-bar">
        <div className="admin-bar__inner">
          <div className="brand">
            <LotusMark size={40} gap="#3B1A0E" />
            <span className="admin-bar__name">Stotrams</span>
            <span className="admin-bar__pill">Admin</span>
          </div>
          <div className="admin-bar__actions">
            <a href="/" className="admin-bar__link">
              Public site →
            </a>
            <span className="admin-bar__user" title="Signed in">
              {session.email}
            </span>
            <button type="button" className="admin-bar__logout" onClick={signOut}>
              Log out
            </button>
          </div>
        </div>
      </header>

      <div className="admin__body">
        {demo && <p className="demo-banner">Demo mode: backend not reachable, changes are in-memory only.</p>}

        <nav className="media-toggle" aria-label="Content type">
          {MEDIA_TYPES.map(({ type, label }) => (
            <button
              key={type}
              type="button"
              className={`media-toggle__option${type === mediaType ? ' media-toggle__option--active' : ''}`}
              aria-pressed={type === mediaType}
              onClick={() => setMediaType(type)}
            >
              {label}
            </button>
          ))}
        </nav>

        {mediaType === 'image' ? (
          <BlocksPanel
            onUnauthorized={onSignedOut}
            onGenerated={() => {
              setStatus('GENERATED');
              setPollUntil(Date.now() + POLL_FOR_MS);
            }}
          />
        ) : (
          <VideoUploadPanel
            onUnauthorized={onSignedOut}
            onUploaded={() => {
              if (status !== 'GENERATED') setStatus('GENERATED'); // reloads via the effect
              else load(true);
            }}
          />
        )}

        <PublishPanel
          approvedCount={(counts.APPROVED ?? 0) + otherApproved}
          onPublished={() => load(true)}
          onUnauthorized={onSignedOut}
        />

        <section className="admin__panel">
          <div className="admin__toolbar">
            <nav className="admin__tabs">
              {STATUSES.map((s) => (
                <button
                  key={s}
                  type="button"
                  className={`admin__tab${s === status ? ' admin__tab--active' : ''}`}
                  onClick={() => setStatus(s)}
                >
                  {STATUS_LABEL[s]}
                  {counts[s] !== undefined && <span className="admin__count">{counts[s]}</span>}
                </button>
              ))}
            </nav>
            <div className="admin__toolbar-actions">
              {pollUntil > Date.now() && <span className="admin__muted">Auto-refreshing…</span>}
              <button type="button" className="btn btn--small" onClick={() => load()}>
                Refresh
              </button>
            </div>
          </div>

          {error && <p className="admin__error">{error}</p>}
          {loading && items.length === 0 && <p className="admin__muted">Loading…</p>}
          {!loading && items.length === 0 && !error && <p className="admin__muted">Nothing here.</p>}

          {status === 'GENERATED' && items.length > 0 && (
            <p className="admin__muted">
              Tip: open {mediaType === 'video' ? 'a video to watch it' : 'an image to check details'} full-size. Keys: <kbd>A</kbd> approve, <kbd>R</kbd> reject,{' '}
              <kbd>←</kbd>/<kbd>→</kbd> move, <kbd>Esc</kbd> close.
            </p>
          )}

          <ul className="admin__grid">
            {items.map((item, index) => (
              <li key={item.id} className="admin__card">
                <button type="button" className="admin__thumb" onClick={() => setPreviewIndex(index)}>
                  {item.type === 'video' ? (
                    <>
                      {/* #t=0.1 makes phones paint the first frame as the thumbnail. */}
                      <video src={`${item.url}#t=0.1`} muted playsInline preload="metadata" />
                      <span className="admin__play" aria-hidden="true">
                        ▶
                      </span>
                    </>
                  ) : (
                    <img src={item.url} alt={`Candidate ${item.id}`} loading="lazy" />
                  )}
                </button>
                <div className="admin__meta">
                  <span>
                    {item.category === 'good-morning' ? '🌅 Morning' : '🌙 Night'} · {item.lang.toUpperCase()}
                  </span>
                  <span className="admin__id">#{item.id}</span>
                </div>
                {item.editedFromId !== null && (
                  <div className="admin__edited" title={item.editPrompt ?? ''}>
                    ✏️ Edit of #{item.editedFromId}
                  </div>
                )}
                <CandidateActions item={item} onDecide={decide} />
              </li>
            ))}
          </ul>
        </section>
      </div>

      {previewIndex !== null && items[previewIndex] && (
        <ReviewLightbox
          items={items}
          index={previewIndex}
          onIndexChange={setPreviewIndex}
          onDecide={decide}
          onEdited={showEditedCopy}
          onUnauthorized={onSignedOut}
          onClose={() => setPreviewIndex(null)}
        />
      )}
    </div>
  );
}

function CandidateActions({
  item,
  onDecide,
}: {
  item: Candidate;
  onDecide: (item: Candidate, next: 'APPROVED' | 'REJECTED') => void;
}) {
  const live = item.status === 'PUBLISHED';
  return (
    <div className={`admin__actions${live ? ' admin__actions--single' : ''}`}>
      {canApprove(item) && (
        <button type="button" className="btn btn--small btn--approve" onClick={() => onDecide(item, 'APPROVED')}>
          ✓ Approve
        </button>
      )}
      {item.status !== 'REJECTED' && (
        <button type="button" className="btn btn--small btn--reject" onClick={() => onDecide(item, 'REJECTED')}>
          {live ? '✕ Take down from site' : '✕ Reject'}
        </button>
      )}
    </div>
  );
}

/** "0 30 5 * * *" (daily at 05:30) <-> "05:30". Anything else is a custom cron. */
function cronToTime(cron: string): string | null {
  const m = /^0 (\d{1,2}) (\d{1,2}) \* \* \*$/.exec(cron.trim());
  if (!m) return null;
  return `${m[2].padStart(2, '0')}:${m[1].padStart(2, '0')}`;
}

function timeToCron(time: string): string {
  const [h, m] = time.split(':').map(Number);
  return `0 ${m} ${h} * * *`;
}

const IST = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});
const formatIst = (iso: string) => `${IST.format(new Date(iso))} IST`;

function PublishPanel({
  approvedCount,
  onPublished,
  onUnauthorized,
}: {
  approvedCount: number;
  onPublished: () => void;
  onUnauthorized: () => void;
}) {
  const [schedule, setSchedule] = useState<PublishSchedule | null>(null);
  const [enabled, setEnabled] = useState(true);
  const [time, setTime] = useState('05:00');
  const [publishing, setPublishing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [publishMessage, setPublishMessage] = useState<{ text: string; error?: boolean } | null>(null);
  const [scheduleMessage, setScheduleMessage] = useState<{ text: string; error?: boolean } | null>(null);

  const showSchedule = useCallback((s: PublishSchedule) => {
    setSchedule(s);
    setEnabled(s.enabled);
    const t = cronToTime(s.cron);
    if (t) setTime(t);
  }, []);

  const report = useCallback(
    (err: unknown, set: (m: { text: string; error: boolean }) => void) => {
      if (isUnauthorized(err)) onUnauthorized();
      else set({ text: err instanceof Error ? err.message : String(err), error: true });
    },
    [onUnauthorized],
  );

  useEffect(() => {
    fetchPublishSchedule()
      .then(showSchedule)
      .catch((err) => report(err, setScheduleMessage));
  }, [showSchedule, report]);

  async function publish() {
    setPublishing(true);
    setPublishMessage(null);
    try {
      const res = await publishNow();
      const live =
        res.published === 0
          ? 'Nothing was published.'
          : `${res.published} item${res.published === 1 ? ' is' : 's are'} now live (photos with the स्तोत्रम् watermark).`;
      setPublishMessage(
        res.failed > 0
          ? {
              text: `${live} ${res.failed} couldn't be watermarked, so they stayed in "Approved" (see server log).`,
              error: true,
            }
          : { text: live },
      );
      onPublished();
      showSchedule(await fetchPublishSchedule());
    } catch (err) {
      report(err, setPublishMessage);
    } finally {
      setPublishing(false);
    }
  }

  const customCron = schedule && !cronToTime(schedule.cron) ? schedule.cron : null;
  const validTime = /^\d{2}:\d{2}$/.test(time);
  const dirty =
    schedule !== null && (enabled !== schedule.enabled || (validTime && timeToCron(time) !== schedule.cron));

  async function saveSchedule(e: FormEvent) {
    e.preventDefault();
    if (!validTime) {
      setScheduleMessage({ text: 'Pick a time.', error: true });
      return;
    }
    setSaving(true);
    setScheduleMessage(null);
    try {
      showSchedule(await updatePublishSchedule(enabled, timeToCron(time)));
      setScheduleMessage({ text: 'Schedule saved.' });
    } catch (err) {
      report(err, setScheduleMessage);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="admin__panel">
      <h2>Publish to the website</h2>
      <div className="publish">
        <div className="publish__now">
          <p className="admin__muted">
            Puts every approved photo and video on the public site as today's content. Photos are stamped
            with the स्तोत्रम् watermark; videos go up as uploaded.
          </p>
          <button
            type="button"
            className="btn btn--primary"
            onClick={publish}
            disabled={publishing || approvedCount === 0}
          >
            {publishing ? 'Publishing…' : `Publish now (${approvedCount} approved)`}
          </button>
          {approvedCount === 0 && !publishMessage && (
            <p className="admin__muted">Approve photos or videos first, then publish them here.</p>
          )}
          {publishMessage && (
            <p className={publishMessage.error ? 'admin__error' : 'admin__success'}>{publishMessage.text}</p>
          )}
        </div>

        <form className="publish__schedule" onSubmit={saveSchedule}>
          <label className="switch">
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
            <span className="switch__track" aria-hidden="true" />
            Auto-publish every day
          </label>
          <div className="publish__row">
            <label className="admin__inline-field">
              at
              <input
                type="time"
                value={time}
                onChange={(e) => {
                  setTime(e.target.value);
                  setScheduleMessage(null);
                }}
                disabled={!enabled}
                aria-invalid={!validTime}
              />
              <span className="admin__muted">IST</span>
            </label>
            <button type="submit" className="btn btn--small" disabled={saving || !dirty}>
              {saving ? 'Saving…' : 'Save schedule'}
            </button>
          </div>
          {customCron && (
            <p className="admin__muted">
              Currently a custom schedule (<code>{customCron}</code>). Saving replaces it with a daily time.
            </p>
          )}
          {schedule && (
            <p className="admin__muted">
              {schedule.nextRunAt ? `Next run: ${formatIst(schedule.nextRunAt)}` : 'Auto-publish is off.'}
              {schedule.lastRunAt && (
                <>
                  <br />
                  Last publish: {formatIst(schedule.lastRunAt)}, {schedule.lastRunCount ?? 0} image(s) (
                  {schedule.lastRunTrigger === 'SCHEDULE' ? 'scheduled' : 'manual'})
                </>
              )}
            </p>
          )}
          {scheduleMessage && (
            <p className={scheduleMessage.error ? 'admin__error' : 'admin__success'}>{scheduleMessage.text}</p>
          )}
        </form>
      </div>
    </section>
  );
}
