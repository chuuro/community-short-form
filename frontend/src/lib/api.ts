import axios from 'axios';
import type { ApiResponse, CreateProjectRequest, KeywordItem, ProjectResponse, ParseResultResponse, NewsArticleResponse, NewsArticleMediaResponse, ScriptGenerateRequest } from '@/types';

export interface RenderRequest {
  preview?: boolean;
  includeWatermark?: boolean;
  outputPlatform?: string;
  bgmTrackId?: number;
}

export interface RenderJobResponse {
  id: number;
  projectId: number;
  status: string;
  isPreview: boolean;
  progress: number;
  outputFilePath?: string;
  errorMessage?: string;
  retryCount: number;
  createdAt: string;
  updatedAt: string;
}

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const message =
      err.response?.data?.message || err.message || '서버 오류가 발생했습니다.';
    return Promise.reject(new Error(message));
  }
);

export const projectApi = {
  create: (data: CreateProjectRequest) =>
    api.post<ApiResponse<ProjectResponse>>('/api/projects', data),

  parse: (id: number) =>
    api.post<ApiResponse<ProjectResponse>>(`/api/projects/${id}/parse`),

  getOne: (id: number) =>
    api.get<ApiResponse<ParseResultResponse>>(`/api/projects/${id}`),

  getAll: () =>
    api.get<ApiResponse<ProjectResponse[]>>('/api/projects'),

  softDelete: (id: number) =>
    api.delete<ApiResponse<void>>(`/api/projects/${id}`),

  render: (projectId: number, data: RenderRequest) =>
    api.post<ApiResponse<RenderJobResponse>>(`/api/projects/${projectId}/render`, data),

  getRenderHistory: (projectId: number) =>
    api.get<ApiResponse<RenderJobResponse[]>>(`/api/projects/${projectId}/render/history`),

  getRenderStatus: (renderJobId: number) =>
    api.get<ApiResponse<RenderJobResponse>>(`/api/render/${renderJobId}/status`),

  getOutputUrl: (projectId: number) =>
    api.get<ApiResponse<string>>(`/api/projects/${projectId}/output-url`),
};

export const scriptApi = {
  generate: (data: ScriptGenerateRequest) =>
    api.post<ApiResponse<ProjectResponse>>('/api/script/generate', data),
};

export const newsArticleApi = {
  getAll: (page = 0, size = 20) =>
    api.get<ApiResponse<NewsArticleResponse[]>>(`/api/news-articles?page=${page}&size=${size}`),

  getOne: (id: number) =>
    api.get<ApiResponse<NewsArticleResponse>>(`/api/news-articles/${id}`),

  extractMetadata: (id: number) =>
    api.post<ApiResponse<NewsArticleResponse>>(`/api/news-articles/${id}/extract-metadata`),

  fetchMultimedia: (id: number) =>
    api.post<ApiResponse<NewsArticleResponse>>(`/api/news-articles/${id}/fetch-multimedia`),

  getMedia: (id: number) =>
    api.get<ApiResponse<NewsArticleMediaResponse[]>>(`/api/news-articles/${id}/media`),

  updateMediaSelection: (id: number, selectedIds: number[]) =>
    api.put<ApiResponse<void>>(`/api/news-articles/${id}/media/selection`, selectedIds),

  updateKeywords: (id: number, data: { imageSearchKeywords: KeywordItem[]; videoSearchKeywords: KeywordItem[] }) =>
    api.put<ApiResponse<NewsArticleResponse>>(`/api/news-articles/${id}/keywords`, data),

  addMedia: (id: number, data: { sourceUrl: string; mediaType: 'VIDEO' | 'IMAGE'; thumbnailUrl?: string }) =>
    api.post<ApiResponse<NewsArticleMediaResponse>>(`/api/news-articles/${id}/media`, data),

  uploadMedia: (id: number, file: File, mediaType: 'VIDEO' | 'IMAGE') => {
    const form = new FormData();
    form.append('file', file);
    form.append('mediaType', mediaType);
    return api.post<ApiResponse<NewsArticleMediaResponse>>(`/api/news-articles/${id}/media/upload`, form);
  },

  updateMediaExposure: (id: number, items: { mediaId: number; exposureDurationSeconds: number }[]) =>
    api.put<ApiResponse<void>>(`/api/news-articles/${id}/media/exposure`, { items }),

  requestRender: (id: number, data?: RenderRequest) =>
    api.post<ApiResponse<RenderJobResponse>>(`/api/news-articles/${id}/render`, data ?? {}),
};
