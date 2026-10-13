// Sharing an image to WhatsApp (Status or a chat) — the core feature of the product.
//
// Android Chrome: Web Share API with a *file*, so the picture itself lands in WhatsApp,
// not a link. Elsewhere (desktop, old browsers): save the photo instead.
//
// Everything is converted to JPEG first. WhatsApp Status on older Android phones handles
// WebP/SVG unreliably, and JPEG is the one format every phone accepts.
//
// The file is prepared *before* the tap (see ReelViewer): navigator.share() must run
// within the tap's user-activation window, so there's no time to download then.

import type { MediaType } from '../api/types';

const JPEG_QUALITY = 0.9;

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('Image failed to load'));
    img.src = src;
  });
}

async function toJpeg(blob: Blob): Promise<Blob> {
  if (blob.type === 'image/jpeg') return blob;
  const objectUrl = URL.createObjectURL(blob);
  try {
    const img = await loadImage(objectUrl);
    const canvas = document.createElement('canvas');
    canvas.width = img.naturalWidth || 1080;
    canvas.height = img.naturalHeight || 1350;
    const ctx = canvas.getContext('2d');
    if (!ctx) return blob;
    ctx.fillStyle = '#FFFFFF'; // JPEG has no transparency
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('JPEG encode failed'))), 'image/jpeg', JPEG_QUALITY),
    );
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

const VIDEO_EXTENSIONS: Record<string, string> = {
  'video/mp4': 'mp4',
  'video/webm': 'webm',
  'video/quicktime': 'mov',
};

/**
 * Downloads the item and turns it into a shareable file: images become JPEG, videos are
 * shared as they are (MP4 is what WhatsApp plays everywhere).
 * Note: production media on the CDN must send CORS headers for this fetch to succeed.
 */
export async function prepareShareFile(url: string, id: number, type: MediaType = 'image'): Promise<File> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Download failed (${res.status})`);
  const blob = await res.blob();
  if (type === 'video') {
    const mime = blob.type in VIDEO_EXTENSIONS ? blob.type : 'video/mp4';
    return new File([blob], `stotram-${id}.${VIDEO_EXTENSIONS[mime]}`, { type: mime });
  }
  const jpeg = await toJpeg(blob);
  return new File([jpeg], `stotram-${id}.jpg`, { type: 'image/jpeg' });
}

export function canShareFiles(file: File): boolean {
  return typeof navigator.canShare === 'function' && navigator.canShare({ files: [file] });
}

export type ShareOutcome = 'shared' | 'cancelled' | 'downloaded';

export async function shareFile(file: File, text: string): Promise<ShareOutcome> {
  if (canShareFiles(file)) {
    try {
      await navigator.share({ files: [file], text });
      return 'shared';
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return 'cancelled';
      // Share sheet failed for another reason — fall back to saving the photo.
    }
  }
  downloadFile(file);
  return 'downloaded';
}

export function downloadFile(file: File) {
  const url = URL.createObjectURL(file);
  const a = document.createElement('a');
  a.href = url;
  a.download = file.name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
