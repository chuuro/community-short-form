'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  ArrowLeft, Video, Image, FileText, ExternalLink,
  Clock, Play, Music, RefreshCw, AlertTriangle
} from 'lucide-react';
import Navbar from '@/components/Navbar';
import { projectApi } from '@/lib/api';
import type { ProjectResponse, MediaItemResponse, SubtitleResponse } from '@/types';

const STATUS_CONFIG = {
  CREATED:   { label: '생성됨',    color: '#94A3B8', bg: 'rgba(148,163,184,0.1)' },
  PARSING:   { label: '분석 중',   color: '#FBBF24', bg: 'rgba(251,191,36,0.1)' },
  PARSED:    { label: '분석 완료', color: '#60A5FA', bg: 'rgba(96,165,250,0.1)' },
  RENDERING: { label: '렌더링 중', color: '#A78BFA', bg: 'rgba(167,139,250,0.1)' },
  COMPLETED: { label: '완료',      color: '#34D399', bg: 'rgba(52,211,153,0.1)' },
  FAILED:    { label: '실패',      color: '#F87171', bg: 'rgba(248,113,113,0.1)' },
};

function MediaCard({ item }: { item: MediaItemResponse }) {
  const isVideo = item.mediaType === 'VIDEO';
  const isImage = item.mediaType === 'IMAGE';

  return (
    <div className="group relative rounded-xl overflow-hidden transition-all duration-300 hover:scale-[1.02]"
      style={{
        background: 'rgba(19,19,31,0.8)',
        border: '1px solid rgba(255,255,255,0.07)',
      }}
    >
      {/* 미리보기 영역 */}
      <div className="relative h-40 bg-gradient-to-br from-purple-900/20 to-blue-900/20 flex items-center justify-center overflow-hidden">
        {(item.thumbnailUrl || (isImage && item.originalUrl)) ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={item.thumbnailUrl ?? item.originalUrl}
            alt=""
            className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
            onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
          />
        ) : (
          <div className="text-purple-500/40">
            {isVideo ? <Video size={36} /> : <Image size={36} />}
          </div>
        )}

        {/* 오버레이 */}
        <div className="absolute inset-0 bg-gradient-to-t from-[#13131F] via-transparent to-transparent" />

        {/* 타입 뱃지 */}
        <div className="absolute top-2.5 left-2.5 flex items-center gap-1.5 px-2 py-1 rounded-lg text-xs font-medium"
          style={{
            background: isVideo ? 'rgba(124,58,237,0.8)' : 'rgba(37,99,235,0.8)',
            backdropFilter: 'blur(8px)',
          }}
        >
          {isVideo ? <Video size={11} /> : <Image size={11} />}
          {item.mediaType}
        </div>

        {/* 재생 버튼 (영상만) */}
        {isVideo && (
          <a
            href={item.originalUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
          >
            <div className="w-12 h-12 rounded-full flex items-center justify-center"
              style={{ background: 'rgba(124,58,237,0.9)', backdropFilter: 'blur(8px)' }}
            >
              <Play size={20} className="text-white ml-1" />
            </div>
          </a>
        )}
      </div>

      {/* 정보 */}
      <div className="p-3 space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-xs text-gray-500">#{item.orderIndex + 1}</span>
          {item.duration && (
            <span className="flex items-center gap-1 text-xs text-gray-500">
              <Clock size={10} />
              {item.duration.toFixed(1)}s
            </span>
          )}
        </div>
        <a
          href={item.originalUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-1 text-xs text-gray-500 hover:text-blue-400 transition-colors truncate"
        >
          <ExternalLink size={10} className="shrink-0" />
          <span className="truncate">{item.originalUrl}</span>
        </a>
      </div>
    </div>
  );
}

function SubtitleItem({ subtitle }: { subtitle: SubtitleResponse }) {
  return (
    <div className="flex items-start gap-4 p-4 rounded-xl transition-all hover:bg-white/3"
      style={{ border: '1px solid rgba(255,255,255,0.05)' }}
    >
      <div className="flex flex-col items-center gap-1 shrink-0 min-w-[48px]">
        <span className="text-xs font-mono text-purple-400">
          {subtitle.startTime.toFixed(1)}s
        </span>
        <div className="w-px h-4 bg-purple-500/30" />
        <span className="text-xs font-mono text-gray-600">
          {subtitle.endTime.toFixed(1)}s
        </span>
      </div>
      <p className="flex-1 text-sm text-gray-300 leading-relaxed pt-0.5">
        {subtitle.content}
      </p>
    </div>
  );
}

export default function ProjectDetailPage() {
  const params = useParams();
  const router = useRouter();
  const projectId = Number(params.id);

  const [project, setProject] = useState<ProjectResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'media' | 'subtitles'>('media');

  const fetchProject = async () => {
    try {
      setLoading(true);
      const res = await projectApi.getOne(projectId);
      setProject(res.data.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '프로젝트를 불러올 수 없습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (projectId) fetchProject();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  // PARSING 상태면 폴링
  useEffect(() => {
    if (!project || project.status !== 'PARSING') return;
    const t = setInterval(async () => {
      try {
        const res = await projectApi.getOne(projectId);
        const p = res.data.data;
        setProject(p);
        if (p.status !== 'PARSING') clearInterval(t);
      } catch {
        clearInterval(t);
      }
    }, 3000);
    return () => clearInterval(t);
  }, [project, projectId]);

  if (loading) {
    return (
      <>
        <Navbar />
        <div className="min-h-screen flex items-center justify-center">
          <div className="flex flex-col items-center gap-4">
            <div className="w-12 h-12 rounded-full border-2 border-purple-500/30 border-t-purple-500 animate-spin" />
            <p className="text-gray-500">불러오는 중...</p>
          </div>
        </div>
      </>
    );
  }

  if (error || !project) {
    return (
      <>
        <Navbar />
        <div className="min-h-screen flex items-center justify-center">
          <div className="flex flex-col items-center gap-4 text-center">
            <AlertTriangle size={40} className="text-red-400" />
            <p className="text-white font-medium">{error ?? '프로젝트를 찾을 수 없습니다.'}</p>
            <button onClick={() => router.push('/')}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium text-white"
              style={{ background: 'rgba(124,58,237,0.3)', border: '1px solid rgba(124,58,237,0.5)' }}
            >
              <ArrowLeft size={14} /> 돌아가기
            </button>
          </div>
        </div>
      </>
    );
  }

  const status = STATUS_CONFIG[project.status] ?? STATUS_CONFIG.CREATED;
  const videos = project.mediaItems?.filter((m) => m.mediaType === 'VIDEO') ?? [];
  const images = project.mediaItems?.filter((m) => m.mediaType === 'IMAGE') ?? [];
  const allMedia = project.mediaItems ?? [];
  const subtitles = project.subtitles ?? [];
  const isParsing = project.status === 'PARSING';

  return (
    <>
      <Navbar />
      <main className="min-h-screen pt-20 pb-16 px-4">
        <div className="max-w-7xl mx-auto space-y-8">

          {/* ─── 파싱 중 상태 배너 ─── */}
          {isParsing && (
            <div className="flex items-center gap-4 p-5 rounded-2xl animate-pulse"
              style={{
                background: 'rgba(251,191,36,0.07)',
                border: '1px solid rgba(251,191,36,0.25)',
              }}
            >
              <div className="w-10 h-10 rounded-full border-2 border-yellow-500/40 border-t-yellow-400 animate-spin shrink-0" />
              <div>
                <p className="font-semibold text-yellow-300">분석 진행 중...</p>
                <p className="text-sm text-yellow-600 mt-0.5">커뮤니티 게시글에서 미디어를 수집하고 있어요. 잠시만 기다려주세요.</p>
              </div>
              <button onClick={fetchProject} className="ml-auto text-yellow-500 hover:text-yellow-300 transition-colors">
                <RefreshCw size={18} />
              </button>
            </div>
          )}

          {/* ─── 헤더 ─── */}
          <div className="space-y-6">
            <button
              onClick={() => router.push('/')}
              className="flex items-center gap-2 text-gray-500 hover:text-white transition-colors text-sm"
            >
              <ArrowLeft size={16} />
              프로젝트 목록
            </button>

            <div className="flex flex-col sm:flex-row sm:items-start gap-6">
              {/* 썸네일 */}
              <div className="w-full sm:w-48 h-32 rounded-2xl overflow-hidden shrink-0 flex items-center justify-center"
                style={{ background: 'linear-gradient(135deg, rgba(124,58,237,0.2), rgba(37,99,235,0.15))' }}
              >
                {project.thumbnailUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={project.thumbnailUrl} alt="" className="w-full h-full object-cover" />
                ) : (
                  <Video size={32} className="text-purple-500/50" />
                )}
              </div>

              {/* 정보 */}
              <div className="flex-1 space-y-3">
                <div className="flex items-center gap-3 flex-wrap">
                  <span className="px-3 py-1 rounded-full text-xs font-semibold"
                    style={{ background: status.bg, color: status.color }}>
                    {status.label}
                  </span>
                  <span className="text-xs text-gray-600 bg-white/5 px-2.5 py-1 rounded-full">
                    {project.communityType}
                  </span>
                  <span className="text-xs text-gray-600 bg-white/5 px-2.5 py-1 rounded-full">
                    {project.outputPlatform?.replace('_', ' ')}
                  </span>
                </div>

                <h1 className="text-3xl font-bold text-white leading-tight">
                  {project.title ?? (isParsing ? '제목 분석 중...' : '제목 없음')}
                </h1>

                {project.description && (
                  <p className="text-gray-400 text-base leading-relaxed max-w-2xl">
                    {project.description}
                  </p>
                )}

                <a
                  href={project.communityUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 text-sm text-blue-400 hover:text-blue-300 transition-colors"
                >
                  <ExternalLink size={13} />
                  원본 게시글 보기
                </a>
              </div>

              <button
                onClick={fetchProject}
                className="shrink-0 p-2.5 rounded-xl text-gray-500 hover:text-white hover:bg-white/8 transition-all"
                title="새로고침"
              >
                <RefreshCw size={18} />
              </button>
            </div>
          </div>

          {/* ─── 통계 카드 ─── */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {[
              { label: '전체 미디어', value: allMedia.length, icon: <Video size={18} />, color: '#A78BFA' },
              { label: '영상',        value: videos.length,   icon: <Video size={18} />, color: '#7C3AED' },
              { label: '이미지',      value: images.length,   icon: <Image size={18} />, color: '#60A5FA' },
              { label: '자막',        value: subtitles.length,icon: <FileText size={18} />, color: '#34D399' },
            ].map((stat) => (
              <div key={stat.label} className="p-5 rounded-2xl space-y-2"
                style={{
                  background: 'rgba(19,19,31,0.8)',
                  border: '1px solid rgba(255,255,255,0.07)',
                }}
              >
                <div style={{ color: stat.color }}>{stat.icon}</div>
                <p className="text-3xl font-bold text-white">
                  {isParsing ? '—' : stat.value}
                </p>
                <p className="text-sm text-gray-500">{stat.label}</p>
              </div>
            ))}
          </div>

          {/* ─── 탭 ─── */}
          {!isParsing && allMedia.length > 0 && (
            <div>
              {/* 탭 헤더 */}
              <div className="flex gap-1 p-1 rounded-xl w-fit mb-6"
                style={{ background: 'rgba(255,255,255,0.04)' }}
              >
                {([
                  { key: 'media', label: '미디어', icon: <Video size={14} /> },
                  { key: 'subtitles', label: '자막', icon: <FileText size={14} /> },
                ] as const).map((tab) => (
                  <button
                    key={tab.key}
                    onClick={() => setActiveTab(tab.key)}
                    className="flex items-center gap-2 px-5 py-2.5 rounded-lg text-sm font-medium transition-all"
                    style={
                      activeTab === tab.key
                        ? { background: 'linear-gradient(135deg, #7C3AED, #2563EB)', color: '#fff' }
                        : { color: '#6B7280' }
                    }
                  >
                    {tab.icon}
                    {tab.label}
                    {tab.key === 'media' && allMedia.length > 0 && (
                      <span className="text-xs px-1.5 py-0.5 rounded-md"
                        style={{ background: 'rgba(255,255,255,0.15)' }}>
                        {allMedia.length}
                      </span>
                    )}
                    {tab.key === 'subtitles' && subtitles.length > 0 && (
                      <span className="text-xs px-1.5 py-0.5 rounded-md"
                        style={{ background: 'rgba(255,255,255,0.15)' }}>
                        {subtitles.length}
                      </span>
                    )}
                  </button>
                ))}
              </div>

              {/* 미디어 탭 */}
              {activeTab === 'media' && (
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
                  {allMedia.map((item) => (
                    <MediaCard key={item.id} item={item} />
                  ))}
                </div>
              )}

              {/* 자막 탭 */}
              {activeTab === 'subtitles' && (
                <div className="max-w-3xl space-y-2">
                  {subtitles.length > 0 ? (
                    subtitles.map((sub) => (
                      <SubtitleItem key={sub.id} subtitle={sub} />
                    ))
                  ) : (
                    <div className="flex flex-col items-center gap-3 py-16 text-center">
                      <FileText size={32} className="text-gray-700" />
                      <p className="text-gray-500">자막이 없습니다.</p>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* 파싱 완료했는데 미디어 없는 경우 */}
          {!isParsing && allMedia.length === 0 && project.status !== 'FAILED' && (
            <div className="flex flex-col items-center gap-4 py-20 text-center">
              <Music size={40} className="text-gray-700" />
              <div>
                <p className="text-gray-400 font-medium">미디어를 찾지 못했습니다</p>
                <p className="text-gray-600 text-sm mt-1">
                  해당 게시글에서 추출 가능한 영상이나 이미지가 없을 수 있습니다.
                </p>
              </div>
            </div>
          )}

          {/* 실패 상태 */}
          {project.status === 'FAILED' && (
            <div className="flex items-center gap-4 p-5 rounded-2xl"
              style={{ background: 'rgba(248,113,113,0.07)', border: '1px solid rgba(248,113,113,0.25)' }}
            >
              <AlertTriangle size={20} className="text-red-400 shrink-0" />
              <div>
                <p className="font-medium text-red-300">파싱에 실패했습니다</p>
                <p className="text-sm text-red-600 mt-0.5">
                  해당 URL의 게시글을 처리하는 중 오류가 발생했습니다.
                </p>
              </div>
            </div>
          )}

        </div>
      </main>
    </>
  );
}
