// Mirrors the backend DTOs in backend/src/main/java/com/stotram/dto. Keep in sync —
// this file is the seed of the future `shared/` package the Android app will reuse.

export type Lang = 'hi' | 'en';
export type CategorySlug = 'good-morning' | 'good-night';
export type CandidateStatus = 'GENERATED' | 'APPROVED' | 'REJECTED' | 'PUBLISHED';

/** GET /api/feed item. */
export interface FeedItem {
  id: number;
  type: 'image';
  url: string;
  width: number | null;
  height: number | null;
  category: CategorySlug;
  lang: Lang;
  date: string | null;
}

/** GET /api/feed response. */
export interface FeedResponse {
  items: FeedItem[];
  nextPage: number | null;
}

/** GET /admin/candidates item. */
export interface Candidate {
  id: number;
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
  sortOrder: number;
  lastGeneratedAt: string | null;
  updatedAt: string;
}

/** POST /admin/blocks and PUT /admin/blocks/{id} body. */
export type BlockInput = Pick<Block, 'name' | 'category' | 'language' | 'prompt' | 'model' | 'imageCount' | 'active'>;

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
}

/** One LLM-written block prompt, not saved until the admin creates blocks from it. */
export interface PromptDraft {
  name: string;
  prompt: string;
}

/** POST /admin/blocks/bulk body: shared settings + one draft per block. */
export interface BulkBlocksInput extends Pick<BlockInput, 'category' | 'language' | 'model' | 'imageCount' | 'active'> {
  items: PromptDraft[];
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
