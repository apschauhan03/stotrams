import { useEffect, useState, type FormEvent } from 'react';
import { isUnauthorized, uploadVideo } from '../../api/client';
import type { Candidate, CategorySlug, Lang } from '../../api/types';
import { Field } from './Field';

// Manual upload stands in for video generation for now. An uploaded video goes to the
// "To review" queue like a generated image: approve it, then publish as usual.

const ACCEPT = 'video/mp4,video/webm,video/quicktime';
const MAX_BYTES = 100 * 1024 * 1024; // matches spring.servlet.multipart.max-file-size

/** Reads the video's pixel size in the browser; null if it can't be decoded here. */
function readVideoSize(file: File): Promise<{ width: number; height: number } | null> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const video = document.createElement('video');
    const done = (size: { width: number; height: number } | null) => {
      URL.revokeObjectURL(url);
      resolve(size);
    };
    video.preload = 'metadata';
    video.onloadedmetadata = () =>
      done(video.videoWidth > 0 ? { width: video.videoWidth, height: video.videoHeight } : null);
    video.onerror = () => done(null);
    setTimeout(() => done(null), 5000);
    video.src = url;
  });
}

const formatMb = (bytes: number) => `${(bytes / 1024 / 1024).toFixed(1)} MB`;

export function VideoUploadPanel({
  onUploaded,
  onUnauthorized,
}: {
  onUploaded: (video: Candidate) => void;
  onUnauthorized: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [category, setCategory] = useState<CategorySlug>('good-morning');
  const [lang, setLang] = useState<Lang>('hi');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; error?: boolean } | null>(null);
  const [inputKey, setInputKey] = useState(0); // remount the file input to clear it

  useEffect(() => {
    if (!file) return;
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  function choose(next: File | null) {
    setMessage(null);
    if (next && next.size > MAX_BYTES) {
      setMessage({ text: `That file is ${formatMb(next.size)}; the limit is ${formatMb(MAX_BYTES)}.`, error: true });
      next = null;
    }
    setFile(next);
    if (!next) setPreview(null);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!file) {
      setMessage({ text: 'Choose a video file first.', error: true });
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      const video = await uploadVideo(file, category, lang, await readVideoSize(file));
      setFile(null);
      setPreview(null);
      setInputKey((k) => k + 1);
      setMessage({ text: `Uploaded as #${video.id}. It's in "To review" below.` });
      onUploaded(video);
    } catch (err) {
      if (isUnauthorized(err)) onUnauthorized();
      else setMessage({ text: err instanceof Error ? err.message : String(err), error: true });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="admin__panel">
      <h2>Upload a video</h2>
      <p className="admin__muted">
        MP4 works on every phone (WebM and MOV are accepted too). It goes to "To review" first, then publishes
        with the photos. Videos aren't watermarked yet, so upload them already branded.
      </p>
      <form className="video-upload" onSubmit={submit} noValidate>
        <div className="block-editor__grid">
          <Field label="Video file" hint={file ? `${file.name} · ${formatMb(file.size)}` : undefined} wide>
            <input
              key={inputKey}
              type="file"
              accept={ACCEPT}
              onChange={(e) => choose(e.target.files?.[0] ?? null)}
              disabled={busy}
            />
          </Field>
          <Field label="Category">
            <select value={category} onChange={(e) => setCategory(e.target.value as CategorySlug)} disabled={busy}>
              <option value="good-morning">🌅 Good morning</option>
              <option value="good-night">🌙 Good night</option>
            </select>
          </Field>
          <Field label="Language">
            <select value={lang} onChange={(e) => setLang(e.target.value as Lang)} disabled={busy}>
              <option value="hi">Hindi</option>
              <option value="en">English</option>
            </select>
          </Field>
        </div>

        {preview && <video className="video-upload__preview" src={preview} controls muted playsInline />}

        <div className="block__actions">
          <button type="submit" className="btn btn--small btn--primary" disabled={busy || !file}>
            {busy ? 'Uploading…' : 'Upload video'}
          </button>
        </div>
        {message && <p className={message.error ? 'admin__error' : 'admin__success'}>{message.text}</p>}
      </form>
    </section>
  );
}
