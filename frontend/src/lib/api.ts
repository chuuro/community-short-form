import axios from 'axios';
import type { ApiResponse, CreateProjectRequest, ProjectResponse } from '@/types';

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
    api.get<ApiResponse<ProjectResponse>>(`/api/projects/${id}`),

  getAll: () =>
    api.get<ApiResponse<ProjectResponse[]>>('/api/projects'),

  softDelete: (id: number) =>
    api.delete<ApiResponse<void>>(`/api/projects/${id}`),
};
