export type ProjectStatus =
  | 'CREATED'
  | 'PARSING'
  | 'PARSED'
  | 'RENDERING'
  | 'COMPLETED'
  | 'FAILED';

export type MediaType = 'VIDEO' | 'IMAGE' | 'GIF';
export type CommunityType = 'REDDIT' | 'YOUTUBE' | 'UNKNOWN';
export type OutputPlatform = 'YOUTUBE_SHORTS' | 'TIKTOK' | 'INSTAGRAM_REELS';

export interface MediaItemResponse {
  id: number;
  mediaType: MediaType;
  originalUrl: string;
  storageUrl: string | null;
  thumbnailUrl: string | null;
  duration: number | null;
  orderIndex: number;
  startTime: number | null;
  endTime: number | null;
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
  outputFilePath: string | null;
  previewFilePath: string | null;
  mediaItems: MediaItemResponse[];
  subtitles: SubtitleResponse[];
  bgmTrack: BgmTrackResponse | null;
  createdAt: string;
  updatedAt: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
  timestamp: string;
}

export interface CreateProjectRequest {
  communityUrl: string;
  outputPlatform: OutputPlatform;
}
