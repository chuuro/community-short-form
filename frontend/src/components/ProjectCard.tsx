'use client';

import Link from 'next/link';
import { Video, Clock, ExternalLink, Trash2 } from 'lucide-react';
import type { ProjectResponse } from '@/types';

const STATUS_CONFIG = {
  CREATED:   { label: '생성됨',     color: 'text-gray-400',   bg: 'bg-gray-500/20',   dot: 'bg-gray-400' },
  PARSING:   { label: '분석 중',    color: 'text-yellow-400', bg: 'bg-yellow-500/20', dot: 'bg-yellow-400 animate-pulse' },
  PARSED:    { label: '분석 완료',  color: 'text-blue-400',   bg: 'bg-blue-500/20',   dot: 'bg-blue-400' },
  RENDERING: { label: '렌더링 중',  color: 'text-purple-400', bg: 'bg-purple-500/20', dot: 'bg-purple-400 animate-pulse' },
  COMPLETED: { label: '완료',       color: 'text-green-400',  bg: 'bg-green-500/20',  dot: 'bg-green-400' },
  FAILED:    { label: '실패',       color: 'text-red-400',    bg: 'bg-red-500/20',    dot: 'bg-red-400' },
};

interface Props {
  project: ProjectResponse;
  onDelete?: (id: number) => void;
}

export default function ProjectCard({ project, onDelete }: Props) {
  const status = STATUS_CONFIG[project.status] ?? STATUS_CONFIG.CREATED;

  const timeAgo = (dateStr: string) => {
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return '방금 전';
    if (mins < 60) return `${mins}분 전`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}시간 전`;
    return `${Math.floor(hours / 24)}일 전`;
  };

  return (
    <Link href={`/projects/${project.id}`} className="block group">
      <div className="relative rounded-2xl overflow-hidden transition-all duration-300 hover:scale-[1.02] hover:-translate-y-1"
        style={{
          background: 'rgba(19, 19, 31, 0.8)',
          border: '1px solid rgba(255,255,255,0.07)',
          boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
        }}
      >
        {/* 호버 시 그라데이션 테두리 효과 */}
        <div className="absolute inset-0 rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none"
          style={{
            background: 'linear-gradient(135deg, rgba(124,58,237,0.15), rgba(37,99,235,0.1))',
            border: '1px solid rgba(124,58,237,0.3)',
          }}
        />

        {/* 썸네일 영역 */}
        <div className="relative h-44 overflow-hidden">
          {project.thumbnailUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={project.thumbnailUrl}
              alt={project.title ?? '프로젝트'}
              className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center"
              style={{ background: 'linear-gradient(135deg, rgba(124,58,237,0.2), rgba(37,99,235,0.15))' }}
            >
              <Video size={40} className="text-purple-500/50" />
            </div>
          )}
          {/* 썸네일 그라데이션 오버레이 */}
          <div className="absolute inset-0"
            style={{ background: 'linear-gradient(to top, rgba(19,19,31,1) 0%, rgba(19,19,31,0.2) 50%, transparent 100%)' }}
          />

          {/* 상태 뱃지 */}
          <div className={`absolute top-3 right-3 flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${status.bg} ${status.color}`}>
            <div className={`w-1.5 h-1.5 rounded-full ${status.dot}`} />
            {status.label}
          </div>
        </div>

        {/* 콘텐츠 */}
        <div className="p-4 space-y-3">
          <div>
            <h3 className="font-semibold text-white text-base line-clamp-2 leading-snug group-hover:text-purple-300 transition-colors">
              {project.title ?? '제목 분석 중...'}
            </h3>
            {project.description && (
              <p className="text-gray-500 text-sm mt-1 line-clamp-2 leading-relaxed">
                {project.description}
              </p>
            )}
          </div>

          {/* 플랫폼 뱃지 */}
          <div className="flex items-center gap-2 text-xs text-gray-500">
            {project.communityType && (
              <span className="flex items-center gap-1 text-purple-400">
                <Video size={11} />
                {project.communityType}
              </span>
            )}
            {project.outputPlatform && (
              <span className="text-gray-600">{project.outputPlatform.replace('_', ' ')}</span>
            )}
          </div>

          {/* 하단 */}
          <div className="flex items-center justify-between pt-1 border-t border-white/5">
            <div className="flex items-center gap-1.5 text-xs text-gray-600">
              <Clock size={11} />
              {project.createdAt ? timeAgo(project.createdAt) : ''}
            </div>
            <div className="flex items-center gap-1">
              <a
                href={project.communityUrl}
                target="_blank"
                rel="noopener noreferrer"
                onClick={(e) => e.stopPropagation()}
                className="p-1.5 rounded-lg text-gray-600 hover:text-gray-300 hover:bg-white/5 transition-all"
              >
                <ExternalLink size={13} />
              </a>
              {onDelete && (
                <button
                  onClick={(e) => { e.preventDefault(); e.stopPropagation(); onDelete(project.id); }}
                  className="p-1.5 rounded-lg text-gray-600 hover:text-red-400 hover:bg-red-500/10 transition-all"
                >
                  <Trash2 size={13} />
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </Link>
  );
}
