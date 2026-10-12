// Demo content for when the backend isn't reachable (dev only). Draws simple devotional
// placeholder images as SVG so the UI can be seen and tested without Postgres, the
// Spring Boot server, or an OpenAI key. Never used in a production build.

import type {
  AdminSession,
  Block,
  BlockInput,
  BlockOptions,
  BulkBlocksInput,
  Candidate,
  CandidateStatus,
  CategorySlug,
  FeedItem,
  FeedResponse,
  GenerateResponse,
  Lang,
  Principle,
  PromptDraft,
  PublishResult,
  PublishSchedule,
} from './types';

const PAGE_SIZE = 8;
const ITEMS_PER_FEED = 20;

interface Deity {
  hi: string;
  en: string;
  /** Morning sky: top, bottom. */
  day: [string, string];
  /** Night sky: top, bottom. */
  night: [string, string];
}

const DEITIES: Deity[] = [
  { hi: 'ॐ नमः शिवाय', en: 'Om Namah Shivaya', day: ['#FFB75E', '#ED8F03'], night: ['#0F2027', '#2C5364'] },
  { hi: 'जय श्री राम', en: 'Jai Shri Ram', day: ['#FDC830', '#F37335'], night: ['#141E30', '#243B55'] },
  { hi: 'जय हनुमान', en: 'Jai Hanuman', day: ['#FF9A44', '#C2410C'], night: ['#1A1037', '#4A2C6F'] },
  { hi: 'जय श्री कृष्ण', en: 'Jai Shri Krishna', day: ['#FFD194', '#D1913C'], night: ['#0B1E4A', '#1F4E8C'] },
  { hi: 'जय माता दी', en: 'Jai Mata Di', day: ['#FF8C69', '#B91C1C'], night: ['#2B1036', '#5B1E4D'] },
  { hi: 'ॐ गं गणपतये नमः', en: 'Om Gan Ganapataye Namah', day: ['#FFE29F', '#FF7E5F'], night: ['#16222A', '#3A6073'] },
  { hi: 'जय साईं राम', en: 'Jai Sai Ram', day: ['#F6D365', '#FDA085'], night: ['#1F1C2C', '#4B3F72'] },
  { hi: 'जय माँ लक्ष्मी', en: 'Jai Maa Lakshmi', day: ['#FBD786', '#C6721C'], night: ['#200122', '#6F0000'] },
  { hi: 'ॐ सूर्याय नमः', en: 'Om Suryaya Namah', day: ['#FFC371', '#FF5F6D'], night: ['#0F0C29', '#302B63'] },
  { hi: 'ॐ नमो नारायणाय', en: 'Om Namo Narayanaya', day: ['#FCE38A', '#F38181'], night: ['#000046', '#1CB5E0'] },
];

const COPY = {
  'good-morning': {
    hi: { greeting: 'सुप्रभात', blessing: 'आपका दिन मंगलमय हो' },
    en: { greeting: 'Good Morning', blessing: 'Have a blessed day' },
  },
  'good-night': {
    hi: { greeting: 'शुभ रात्रि', blessing: 'प्रभु आपकी रक्षा करें' },
    en: { greeting: 'Good Night', blessing: 'Sleep in His grace' },
  },
} as const;

/** Builds one 1080×1350 (4:5) devotional placeholder as an SVG data URL. */
function drawImage(category: CategorySlug, lang: Lang, deity: Deity): string {
  const night = category === 'good-night';
  const [top, bottom] = night ? deity.night : deity.day;
  const { greeting, blessing } = COPY[category][lang];
  const chant = lang === 'hi' ? deity.hi : deity.en;
  const silhouette = night ? '#070B1F' : '#4A1606';

  const sky = night
    ? `<circle cx="540" cy="470" r="230" fill="#FFFFFF" opacity="0.08"/>
       <circle cx="540" cy="470" r="140" fill="#FFF6D8"/>
       <circle cx="595" cy="430" r="125" fill="${top}"/>
       ${[[160, 380], [880, 300], [260, 640], [820, 620], [700, 200], [380, 240]]
         .map(([x, y]) => `<circle cx="${x}" cy="${y}" r="5" fill="#FFF6D8" opacity="0.85"/>`)
         .join('')}`
    : `<circle cx="540" cy="520" r="260" fill="#FFF3C4" opacity="0.25"/>
       <circle cx="540" cy="520" r="160" fill="#FFE08A"/>`;

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1080 1350" width="1080" height="1350">
  <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="${top}"/><stop offset="1" stop-color="${bottom}"/>
  </linearGradient></defs>
  <rect width="1080" height="1350" fill="url(#g)"/>
  ${sky}
  <g fill="${silhouette}">
    <path d="M440 1080V860l30-40 30-120 40-100 40 100 30 120 30 40v220z"/>
    <path d="M300 1080V940l40-50 40 50v140zM700 1080V940l40-50 40 50v140z"/>
    <rect x="537" y="520" width="6" height="80"/>
    <path d="M543 522l58 18-58 18z"/>
    <rect x="0" y="1060" width="1080" height="290"/>
  </g>
  <g font-family="'Noto Sans Devanagari','Nirmala UI','Mangal',sans-serif" text-anchor="middle" fill="#FFFFFF">
    <text x="540" y="200" font-size="${lang === 'hi' ? 132 : 112}" font-weight="700"
      stroke="${silhouette}" stroke-opacity="0.35" stroke-width="6" paint-order="stroke">${greeting}</text>
    <text x="540" y="1180" font-size="${chant.length > 18 ? 54 : 64}" font-weight="700" fill="#FFD27A">${chant}</text>
    <text x="540" y="1262" font-size="50">${blessing}</text>
    <text x="1040" y="1320" font-size="30" text-anchor="end" opacity="0.6">stotrams</text>
  </g>
</svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

function todayIso(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() - offsetDays);
  return d.toISOString().slice(0, 10);
}

function buildFeed(category: CategorySlug, lang: Lang): FeedItem[] {
  const base = category === 'good-morning' ? 1 : 2;
  return Array.from({ length: ITEMS_PER_FEED }, (_, i) => ({
    id: base * 10_000 + (lang === 'hi' ? 0 : 5_000) + i,
    type: 'image' as const,
    url: drawImage(category, lang, DEITIES[i % DEITIES.length]),
    width: 1080,
    height: 1350,
    category,
    lang,
    date: todayIso(Math.floor(i / 10)),
  }));
}

const feedCache = new Map<string, FeedItem[]>();

export async function mockFeed(lang: Lang, category: CategorySlug, page: number): Promise<FeedResponse> {
  await sleep(350);
  const key = `${category}:${lang}`;
  if (!feedCache.has(key)) feedCache.set(key, buildFeed(category, lang));
  const all = feedCache.get(key)!;
  const items = all.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const nextPage = (page + 1) * PAGE_SIZE < all.length ? page + 1 : null;
  return { items, nextPage };
}

// ---- Admin -------------------------------------------------------------------------

/** Demo mode has no real login; the panel opens as a placeholder admin. */
export async function mockAdminSession(): Promise<AdminSession> {
  await sleep(150);
  return { email: 'demo@stotrams.local' };
}

let nextId = 900;
let nextBatchId = 1;

function candidate(category: CategorySlug, lang: Lang, status: CandidateStatus): Candidate {
  const id = nextId++;
  return {
    id,
    url: drawImage(category, lang, DEITIES[id % DEITIES.length]),
    status,
    category,
    lang,
    width: 1080,
    height: 1350,
    editedFromId: null,
    editPrompt: null,
  };
}

const candidates: Candidate[] = [
  ...Array.from({ length: 6 }, (_, i) => candidate(i % 2 ? 'good-night' : 'good-morning', 'hi', 'GENERATED')),
  ...Array.from({ length: 3 }, () => candidate('good-morning', 'hi', 'APPROVED')),
  ...Array.from({ length: 2 }, () => candidate('good-night', 'en', 'REJECTED')),
];

export async function mockCandidates(status: CandidateStatus): Promise<Candidate[]> {
  await sleep(250);
  // Copies, like a real HTTP response — callers must never share objects with the store.
  return candidates
    .filter((c) => c.status === status)
    .sort((a, b) => b.id - a.id)
    .map((c) => ({ ...c }));
}

/** Demo edit: a new candidate (a fresh placeholder) linked to its source. */
export async function mockEditCandidate(id: number, prompt: string): Promise<Candidate> {
  await sleep(1500);
  const source = candidates.find((c) => c.id === id);
  if (!source) throw new Error(`No content item with id ${id}`);
  const copy = { ...candidate(source.category, source.lang, 'GENERATED'), editedFromId: id, editPrompt: prompt };
  candidates.push(copy);
  return { ...copy };
}

export async function mockSetStatus(id: number, status: CandidateStatus): Promise<Candidate> {
  await sleep(200);
  const item = candidates.find((c) => c.id === id);
  if (!item) throw new Error(`No content item with id ${id}`);
  item.status = status;
  return { ...item };
}

const schedule: PublishSchedule = {
  enabled: true,
  cron: '0 0 5 * * *',
  zone: 'Asia/Kolkata',
  nextRunAt: null,
  lastRunAt: null,
  lastRunCount: null,
  lastRunTrigger: null,
};

/** Next run for the simple "0 M H * * *" daily crons the panel produces. */
function nextDailyRun(cron: string): string | null {
  const m = /^0 (\d{1,2}) (\d{1,2}) \* \* \*$/.exec(cron);
  if (!m) return null;
  const next = new Date();
  next.setHours(Number(m[2]), Number(m[1]), 0, 0);
  if (next <= new Date()) next.setDate(next.getDate() + 1);
  return next.toISOString();
}

export async function mockPublishSchedule(): Promise<PublishSchedule> {
  await sleep(150);
  return { ...schedule, nextRunAt: schedule.enabled ? nextDailyRun(schedule.cron) : null };
}

export async function mockUpdatePublishSchedule(enabled: boolean, cron: string): Promise<PublishSchedule> {
  schedule.enabled = enabled;
  schedule.cron = cron;
  return mockPublishSchedule();
}

export async function mockPublishNow(): Promise<PublishResult> {
  await sleep(300);
  const approved = candidates.filter((c) => c.status === 'APPROVED');
  approved.forEach((c) => (c.status = 'PUBLISHED'));
  const at = new Date().toISOString();
  Object.assign(schedule, { lastRunAt: at, lastRunCount: approved.length, lastRunTrigger: 'MANUAL' });
  return { published: approved.length, failed: 0, at };
}

/** Mimics the backend: returns at once, then images "land" one by one. */
function startDemoBatch(block: Block): GenerateResponse {
  for (let i = 0; i < block.imageCount; i++) {
    setTimeout(() => candidates.push(candidate(block.category, block.language, 'GENERATED')), 800 * (i + 1));
  }
  block.lastGeneratedAt = new Date().toISOString();
  return {
    batchId: nextBatchId++,
    blockId: block.id,
    blockName: block.name,
    category: block.category,
    requestedCount: block.imageCount,
    message: 'Generation started (demo). Images will appear in the review queue as they land.',
  };
}

let nextBlockId = 3;
const blocks: Block[] = [
  {
    id: 1,
    name: 'Good Morning',
    category: 'good-morning',
    language: 'hi',
    prompt:
      'A serene Indian devotional good-morning scene at sunrise: warm golden light over a temple, marigold garlands, brass diya lamps, no text.',
    model: 'fake',
    imageCount: 3,
    active: true,
    sortOrder: 1,
    lastGeneratedAt: null,
    updatedAt: new Date().toISOString(),
  },
  {
    id: 2,
    name: 'Good Night',
    category: 'good-night',
    language: 'hi',
    prompt:
      'A peaceful Indian devotional good-night scene: moonlit temple silhouette, deep indigo night sky, glowing oil lamps, no text.',
    model: 'fake',
    imageCount: 2,
    active: true,
    sortOrder: 2,
    lastGeneratedAt: null,
    updatedAt: new Date().toISOString(),
  },
];

export async function mockBlocks(): Promise<Block[]> {
  await sleep(150);
  return blocks.map((b) => ({ ...b }));
}

export async function mockBlockOptions(): Promise<BlockOptions> {
  return {
    categories: [
      { slug: 'good-morning', name: 'Good Morning' },
      { slug: 'good-night', name: 'Good Night' },
    ],
    languages: ['en', 'hi'],
    models: [
      { value: 'openai:gpt-image-2', label: 'OpenAI · gpt-image-2', paid: true, available: false, unavailableReason: 'Demo mode: no backend.', canEdit: true },
      { value: 'fake', label: 'Fake placeholder (free, for testing)', paid: false, available: true, unavailableReason: null, canEdit: true },
    ],
    maxImageCount: 50,
    maxTotalPerRun: 200,
    defaultEditModel: 'fake',
  };
}

export async function mockSaveBlock(id: number | null, input: BlockInput): Promise<Block> {
  await sleep(200);
  const now = new Date().toISOString();
  if (id === null) {
    const block: Block = { ...input, id: nextBlockId++, sortOrder: blocks.length + 1, lastGeneratedAt: null, updatedAt: now };
    blocks.push(block);
    return { ...block };
  }
  const block = blocks.find((b) => b.id === id);
  if (!block) throw new Error(`No block with id ${id}`);
  Object.assign(block, input, { updatedAt: now });
  return { ...block };
}

export async function mockDeleteBlock(id: number): Promise<void> {
  await sleep(150);
  const index = blocks.findIndex((b) => b.id === id);
  if (index >= 0) blocks.splice(index, 1);
}

export async function mockBulkCreate(input: BulkBlocksInput): Promise<Block[]> {
  const { items, ...shared } = input;
  const created: Block[] = [];
  for (const item of items) created.push(await mockSaveBlock(null, { ...shared, ...item }));
  return created;
}

let principle: Principle = { prompt: '', updatedAt: null, model: 'gpt-5.4-mini (demo)' };

export async function mockPrinciple(): Promise<Principle> {
  return { ...principle };
}

export async function mockSavePrinciple(prompt: string): Promise<Principle> {
  principle = { ...principle, prompt, updatedAt: new Date().toISOString() };
  return { ...principle };
}

/** Demo drafts: one per deity, so the review/edit/create flow can be tried offline. */
export async function mockWritePrompts(prompt: string, count: number): Promise<PromptDraft[]> {
  await mockSavePrinciple(prompt);
  await sleep(1200);
  return Array.from({ length: count }, (_, i) => {
    const deity = DEITIES[i % DEITIES.length].en;
    return {
      name: `${deity} — demo draft ${i + 1}`,
      prompt: `${deity}: a serene devotional temple scene at sunrise, warm golden light, marigolds and diyas, vertical composition, no text, no watermark.`,
    };
  });
}

export async function mockGenerateBlock(id: number): Promise<GenerateResponse> {
  await sleep(300);
  const block = blocks.find((b) => b.id === id);
  if (!block) throw new Error(`No block with id ${id}`);
  return startDemoBatch(block);
}

export async function mockGenerateAll(): Promise<GenerateResponse[]> {
  await sleep(300);
  return blocks.filter((b) => b.active).map(startDemoBatch);
}
