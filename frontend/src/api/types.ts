// Mirrors the backend DTOs in backend/src/main/java/com/stotram/dto. Keep in sync —
// this file is the seed of the future `shared/` package the Android app will reuse.

export type Lang = 'hi' | 'en';
export type CategorySlug = 'good-morning' | 'good-night';
export type CandidateStatus = 'GENERATED' | 'APPROVED' | 'REJECTED' | 'PUBLISHED';
/** What a content item is; the admin panel's Photos / Videos toggle. */
export type MediaType = 'image' | 'video';

/** GET /api/feed item. */
export interface FeedItem {
  id: number;
  type: MediaType;
  url: string;
  width: number | null;
  height: number | null;
  category: CategorySlug;
  lang: Lang;
  date: string | null;
}

/** GET /api/panchang: one day's panchang as codes; the site words it in Hindi/English. */
export interface Panchang {
  date: string;
  /** 1 = Monday … 7 = Sunday. */
  weekday: number;
  /** 1–30 at sunrise: 1–15 Shukla (15 Purnima), 16–30 Krishna (30 Amavasya). */
  tithi: number;
  tithiEndsAt: string;
  nextTithi: number;
  /** Purnimanta month, 0 = Chaitra … 11 = Phalguna. */
  month: number;
  amantaMonth: number;
  adhik: boolean;
  vikramSamvat: number;
  sunrise: string;
  sunset: string;
  navratri: 'sharad' | 'chaitra' | null;
  place: string;
}

/** GET /api/feed response. */
export interface FeedResponse {
  items: FeedItem[];
  nextPage: number | null;
}

/** GET /admin/candidates item. */
export interface Candidate {
  id: number;
  type: MediaType;
  url: string;
  status: CandidateStatus;
  category: CategorySlug;
  lang: Lang;
  width: number | null;
  height: number | null;
  /** For an edited copy: the image it was edited from. */
  editedFromId: number | null;
  /** For an edited copy: the instruction that produced it. */
  editPrompt: string | null;
}

/** POST /admin/login and GET /admin/me response. */
export interface AdminSession {
  email: string;
}

/** GET/PUT /admin/publish/schedule. `cron` is Spring 6-field (sec min hour day month weekday). */
export interface PublishSchedule {
  enabled: boolean;
  cron: string;
  zone: string;
  nextRunAt: string | null;
  lastRunAt: string | null;
  lastRunCount: number | null;
  lastRunTrigger: 'SCHEDULE' | 'MANUAL' | null;
}

/** POST /admin/publish response. */
export interface PublishResult {
  published: number;
  /** Approved items left unpublished because watermarking them failed. */
  failed: number;
  at: string;
}

/** A generation block: a named prompt plus how to run it (GET /admin/blocks). */
export interface Block {
  id: number;
  name: string;
  category: CategorySlug;
  language: Lang;
  prompt: string;
  /** `provider:model-id`, e.g. `openai:gpt-image-2`, or `fake`. */
  model: string;
  imageCount: number;
  active: boolean;
  /** yyyy-mm-dd the images are for (from the master prompt's date); null = any day. */
  targetDate: string | null;
  sortOrder: number;
  lastGeneratedAt: string | null;
  updatedAt: string;
}

/** POST /admin/blocks and PUT /admin/blocks/{id} body. */
export type BlockInput = Pick<
  Block,
  'name' | 'category' | 'language' | 'prompt' | 'model' | 'imageCount' | 'active' | 'targetDate'
>;

/** One entry in the image-model dropdown. `value` is what a block stores. */
export interface ModelOption {
  value: string;
  label: string;
  paid: boolean;
  /** False when the provider can't run right now (e.g. no API key on the server). */
  available: boolean;
  unavailableReason: string | null;
  /** Whether this model can edit an existing image with a prompt. */
  canEdit: boolean;
}

/** GET /admin/blocks/options: what the block editor may choose from. */
export interface BlockOptions {
  categories: { slug: CategorySlug; name: string }[];
  languages: Lang[];
  models: ModelOption[];
  maxImageCount: number;
  maxTotalPerRun: number;
  /** Preselected model for prompt-based edits (a cheaper one by default). */
  defaultEditModel: string;
}

/** GET/PUT /admin/principle: the saved master instruction for writing block prompts. */
export interface Principle {
  prompt: string;
  updatedAt: string | null;
  /** Text model that writes the prompts, e.g. gpt-5.4-mini. */
  model: string;
  /** True when this is the built-in default master prompt. */
  isDefault: boolean;
}

/** One LLM-written block prompt, not saved until the admin creates blocks from it. */
export interface PromptDraft {
  name: string;
  prompt: string;
}

/** POST /admin/blocks/bulk body: shared settings + one draft per block. */
export interface BulkBlocksInput
  extends Pick<BlockInput, 'category' | 'language' | 'model' | 'imageCount' | 'active' | 'targetDate'> {
  items: PromptDraft[];
}

/** One source's view of a day's panchang (computed on the server, or found on the web). */
export interface CalendarFacts {
  /** 1–30: 1–15 Shukla, 16–30 Krishna. Null if the web didn't say. */
  tithiNumber: number | null;
  /** e.g. "Shukla Chaturthi". */
  tithi: string | null;
  /** Purnimanta (North Indian) month, e.g. "Ashwin". */
  month: string | null;
  /** Computed side only: when the sunrise tithi ends, IST, e.g. "15 Oct, 1:13 AM". */
  tithiEnds: string | null;
  /** Computed side only: e.g. "Sharad Navratri day 4". */
  navratri: string | null;
  /** Web side only. */
  festivals: string[];
  sources: string[];
  notes: string | null;
}

/** The date's panchang, computed vs. found on the web, for the admin to check. */
export interface CalendarCheck {
  date: string;
  weekday: string;
  computed: CalendarFacts;
  /** Null when the web wasn't checked or the lookup failed (see webError). */
  web: CalendarFacts | null;
  webError: string | null;
  /** Null when there's nothing to compare. */
  tithiMatches: boolean | null;
  monthMatches: boolean | null;
}

/** POST /admin/principle/write response. `calendar` is null when no date was given. */
export interface WriteResult {
  drafts: PromptDraft[];
  calendar: CalendarCheck | null;
  /** What the master prompt asked for; '' if the model gave none. */
  researchSummary: string;
  recommendation: string;
}

/** POST /admin/blocks/{id}/generate response (202); /generate-all returns one per block. */
export interface GenerateResponse {
  batchId: number;
  blockId: number;
  blockName: string;
  category: CategorySlug;
  requestedCount: number;
  message: string;
}
