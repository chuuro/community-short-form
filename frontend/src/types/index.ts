export type ProjectStatus =
  | 'CREATED'
  | 'PARSING'
  | 'PARSED'
  | 'RENDERING'
  | 'COMPLETED'
  | 'FAILED';

export type MediaType = 'VIDEO' | 'IMAGE' | 'AUDIO' | 'TEXT';
export type CommunityType = 'REDDIT' | 'YOUTUBE' | 'NEWS' | 'KNOWLEDGE' | 'UNKNOWN';
export type OutputPlatform = 'YOUTUBE_SHORTS' | 'TIKTOK' | 'INSTAGRAM_REELS';

export interface MediaItemResponse {
  id: number;
  mediaType: MediaType;
  sourceUrl: string | null;
  localPath: string | null;
  thumbnailUrl: string | null;
  width: number | null;
  height: number | null;
  durationSeconds: number | null;
  fileSizeBytes: number | null;
  lowQuality: boolean;
  gif: boolean;
  orderIndex: number;
  exposureStartTime: number | null;
  exposureEndTime: number | null;
  included: boolean;
  popularComment: boolean;
  altText: string | null;
  createdAt: string;
}

export interface SubtitleResponse {
  id: number;
  content: string;
  startTime: number;
  endTime: number;
  orderIndex: number;
  isEdited: boolean;
}

export interface BgmTrackResponse {
  id: number;
  name: string;
  artist: string | null;
  storageUrl: string;
  duration: number | null;
}

export interface ProjectResponse {
  id: number;
  communityUrl: string;
  communityType: CommunityType;
  title: string | null;
  description: string | null;
  status: ProjectStatus;
  outputPlatform: OutputPlatform;
  thumbnailUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
  timestamp: string;
}

export interface ParseResultResponse {
  projectId: number;
  status: ProjectStatus;
  communityUrl: string;
  communityType: CommunityType;
  outputPlatform: OutputPlatform;
  title: string | null;
  description: string | null;
  thumbnailUrl: string | null;
  videoCount: number;
  imageCount: number;
  textCount: number;
  gifCount: number;
  popularCommentCount: number;
  lowQualityCount: number;
  outputFilePath?: string | null;
  mediaItems: MediaItemResponse[];
  subtitles: SubtitleResponse[];
  warnings: string[];
}

export interface CreateProjectRequest {
  communityUrl: string;
  outputPlatform: OutputPlatform;
}

export interface ScriptGenerateRequest {
  topic: string;
  sceneCount: number;
  outputPlatform: OutputPlatform;
}

// ─── News Article ─────────────────────────────────────────────
export type NewsArticleStatus =
  | 'FETCHED'
  | 'METADATA_EXTRACTING'
  | 'METADATA_READY'
  | 'MULTIMEDIA_FETCHING'
  | 'MULTIMEDIA_READY'
  | 'RENDER_REQUESTED'
  | 'RENDERED'
  | 'FAILED';

export interface NewsArticleResponse {
  id: number;
  url: string;
  title: string;
  description: string | null;
  content: string | null;
  urlToImage: string | null;
  sourceName: string | null;
  author: string | null;
  publishedAt: string | null;
  script: string | null;
  translatedTitle: string | null;
  translatedContent: string | null;
  thumbnailKeywords: string | null;
  imageSearchKeywords: string | null;
  videoSearchKeywords: string | null;
  estimatedDurationSeconds: number | null;
  status: NewsArticleStatus;
  errorMessage: string | null;
  projectId: number | null;
}

export interface KeywordItem {
  keyword: string;
  source: 'openai' | 'user';
  enabled: boolean;
}

export interface NewsArticleMediaResponse {
  id: number;
  mediaType: 'VIDEO' | 'IMAGE';
  sourceUrl: string;
  thumbnailUrl: string | null;
  orderIndex: number;
  selected: boolean;
  searchKeyword: string | null;
  width: number | null;
  height: number | null;
  durationSeconds: number | null;
  exposureDurationSeconds: number | null;
  photographerName: string | null;
  photographerUrl: string | null;
}
