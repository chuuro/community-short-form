import { create } from 'zustand';
import type { ProjectResponse, ProjectStatus } from '@/types';

interface ParseProgress {
  message: string;
  progress: number;
}

interface ProjectStore {
  projects: ProjectResponse[];
  currentProject: ProjectResponse | null;
  isSubmitting: boolean;
  isParsing: boolean;
  parseProgress: ParseProgress;
  error: string | null;

  setProjects: (projects: ProjectResponse[]) => void;
  addProject: (project: ProjectResponse) => void;
  setCurrentProject: (project: ProjectResponse | null) => void;
  updateProjectStatus: (id: number, status: ProjectStatus) => void;
  setSubmitting: (v: boolean) => void;
  setParsing: (v: boolean) => void;
  setParseProgress: (progress: ParseProgress) => void;
  setError: (err: string | null) => void;
  reset: () => void;
}

export const useProjectStore = create<ProjectStore>((set) => ({
  projects: [],
  currentProject: null,
  isSubmitting: false,
  isParsing: false,
  parseProgress: { message: '준비 중...', progress: 0 },
  error: null,

  setProjects: (projects) => set({ projects: Array.isArray(projects) ? projects : [] }),
  addProject: (project) =>
    set((state) => ({ projects: [project, ...(state.projects ?? [])] })),
  setCurrentProject: (project) => set({ currentProject: project }),
  updateProjectStatus: (id, status) =>
    set((state) => ({
      projects: state.projects.map((p) =>
        p.id === id ? { ...p, status } : p
      ),
      currentProject:
        state.currentProject?.id === id
          ? { ...state.currentProject, status }
          : state.currentProject,
    })),
  setSubmitting: (v) => set({ isSubmitting: v }),
  setParsing: (v) => set({ isParsing: v }),
  setParseProgress: (progress) => set({ parseProgress: progress }),
  setError: (err) => set({ error: err }),
  reset: () =>
    set({
      isSubmitting: false,
      isParsing: false,
      parseProgress: { message: '준비 중...', progress: 0 },
      error: null,
    }),
}));
