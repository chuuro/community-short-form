'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  ArrowLeft, Video, Image, FileText, ExternalLink,
  Clock, Play, Music, RefreshCw, AlertTriangle, Hash,
  MessageSquare, Layers
} from 'lucide-react';
import Navbar from '@/components/Navbar';
import { projectApi } from '@/lib/api';
import type { ParseResultResponse, MediaItemResponse } from '@/types';

const STATUS_CONFIG = {
  CREATED:   { label: '생성됨',    color: '#94A3B8', bg: 'rgba(148,163,184,0.1)' },
  PARSING:   { label: '분석 중',   color: '#FBBF24', bg: 'rgba(251,191,36,0.1)' },
  PARSED:    { label: '분석 완료', color: '#60A5FA', bg: 'rgba(96,165,250,0.1)' },
  RENDERING: { label: '렌더링 중', color: '#A78BFA', bg: 'rgba(167,139,250,0.1)' },
  COMPLETED: { label: '완료',      color: '#34D399', bg: 'rgba(52,211,153,0.1)' },
  FAILED:    { label: '실패',      color: '#F87171', bg: 'rgba(248,113,113,0.1)' },
} as const;

type FilterType = 'ALL' | 'VIDEO' | 'IMAGE' | 'GIF' | 'TEXT';

function getYouTubeId(url: string | null): string | null {
  if (!url) return null;
  const m = url.match(/(?:v=|youtu\.be\/)([a-zA-Z0-9_-]{11})/);
  return m ? m[1] : null;
}

function MediaCard({ item }: { item: MediaItemResponse }) {
  const [playing, setPlaying] = useState(false);
  const isVideo = item.mediaType === 'VIDEO';
  const isImage = item.mediaType === 'IMAGE';
  const isText  = item.mediaType === 'TEXT';
  const isGif   = item.gif;
  const ytId    = isVideo ? getYouTubeId(item.sourceUrl) : null;

  const previewUrl = item.thumbnailUrl || (isImage ? item.sourceUrl : null);

  const typeColor = isGif
    ? 'rgba(251,146,60,0.85)'
    : isText
      ? 'rgba(52,211,153,0.85)'
      : isVideo
        ? 'rgba(124,58,237,0.85)'
        : 'rgba(37,99,235,0.85)';

  const typeIcon = isGif
    ? <span className="text-xs font-bold">GIF</span>
    : isText
      ? <MessageSquare size={11} />
      : isVideo
        ? <Video size={11} />
        : <Image size={11} />;

  const typeLabel = isGif ? 'GIF' : item.mediaType;

  // TEXT 타입 (댓글/본문) - 텍스트 카드 렌더링
  if (isText) {
    return (
      <div className="rounded-xl p-4 space-y-2 h-full min-h-[120px] flex flex-col"
        style={{
          background: 'rgba(19,19,31,0.8)',
          border: '1px solid rgba(52,211,153,0.15)',
        }}
      >
        <div className="flex items-center gap-1.5">
          <span className="flex items-center gap-1 px-2 py-0.5 rounded-lg text-xs font-medium"
            style={{ background: typeColor, color: '#fff' }}
          >
            <MessageSquare size={10} />
            {item.popularComment ? '인기댓글' : 'TEXT'}
          </span>
          <span className="text-xs text-gray-600">#{item.orderIndex + 1}</span>
        </div>
        <p className="text-sm text-gray-300 leading-relaxed flex-1 line-clamp-6">
          {item.altText ?? '(내용 없음)'}
        </p>
      </div>
    );
  }

  return (
    <div className="group relative rounded-xl overflow-hidden transition-all duration-300 hover:scale-[1.02]"
      style={{
        background: 'rgba(19,19,31,0.8)',
        border: '1px solid rgba(255,255,255,0.07)',
      }}
    >
      {/* 미리보기 영역 */}
      <div className="relative h-40 bg-gradient-to-br from-purple-900/20 to-blue-900/20 flex items-center justify-center overflow-hidden">

        {/* YouTube 임베드 플레이어 */}
        {ytId && playing ? (
          <iframe
            className="w-full h-full"
            src={`https://www.youtube.com/embed/${ytId}?autoplay=1`}
            allow="autoplay; encrypted-media"
            allowFullScreen
          />
        ) : previewUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={previewUrl}
            alt=""
            className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
            onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
          />
        ) : (
          <div className="text-purple-500/40">
            {isVideo ? <Video size={36} /> : isGif ? <span className="text-2xl font-black text-orange-400/40">GIF</span> : <Image size={36} />}
          </div>
        )}

        {/* 그라디언트 오버레이 */}
        <div className="absolute inset-0 bg-gradient-to-t from-[#13131F] via-transparent to-transparent" />

        {/* 타입 뱃지 */}
        <div className="absolute top-2.5 left-2.5 flex items-center gap-1.5 px-2 py-1 rounded-lg text-xs font-medium text-white"
          style={{ background: typeColor, backdropFilter: 'blur(8px)' }}
        >
          {typeIcon}
          {typeLabel}
        </div>

        {/* GIF 뱃지 */}
        {isGif && !playing && (
          <div className="absolute top-2.5 right-2.5 px-2 py-0.5 rounded text-xs font-black text-orange-300"
            style={{ background: 'rgba(0,0,0,0.6)' }}
          >
            ANIMATED
          </div>
        )}

        {/* 재생 버튼 */}
        {(isVideo || isGif) && !playing && (
          <button
            onClick={() => ytId ? setPlaying(true) : window.open(item.sourceUrl ?? '', '_blank')}
            className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
          >
            <div className="w-12 h-12 rounded-full flex items-center justify-center"
              style={{ background: 'rgba(124,58,237,0.9)', backdropFilter: 'blur(8px)' }}
            >
              <Play size={20} className="text-white ml-1" />
            </div>
          </button>
        )}
      </div>

      {/* 정보 영역 */}
      <div className="p-3 space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-xs text-gray-500">#{item.orderIndex + 1}</span>
          <div className="flex items-center gap-2">
            {item.durationSeconds != null && (
              <span className="flex items-center gap-1 text-xs text-gray-500">
                <Clock size={10} />
                {item.durationSeconds.toFixed(1)}s
              </span>
            )}
            {item.width && item.height && (
              <span className="text-xs text-gray-600">{item.width}×{item.height}</span>
            )}
          </div>
        </div>
        {item.sourceUrl && (
          <a
            href={item.sourceUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-1 text-xs text-gray-500 hover:text-blue-400 transition-colors truncate"
          >
            <ExternalLink size={10} className="shrink-0" />
            <span className="truncate">{item.sourceUrl}</span>
          </a>
        )}
        {item.lowQuality && (
          <span className="inline-flex items-center gap-1 text-xs text-yellow-600">
            <AlertTriangle size={10} /> 저화질
          </span>
        )}
      </div>
    </div>
  );
}

function SubtitleItem({ subtitle }: { subtitle: { id: number; content: string; startTime: number; endTime: number; orderIndex: number } }) {
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

  const [project, setProject] = useState<ParseResultResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'media' | 'subtitles'>('media');
  const [filter, setFilter] = useState<FilterType>('ALL');
  const [isRendering, setIsRendering] = useState(false);

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

  const handleRender = async (preview: boolean) => {
    if (!project || isRendering) return;
    try {
      setIsRendering(true);
      await projectApi.render(projectId, { preview, includeWatermark: false });
      setProject((p) => p ? { ...p, status: 'RENDERING' } : p);
      fetchProject();
    } catch (err) {
      setError(err instanceof Error ? err.message : '렌더 요청에 실패했습니다.');
    } finally {
      setIsRendering(false);
    }
  };

  useEffect(() => {
    if (projectId) fetchProject();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  // PARSING 또는 RENDERING 상태면 3초마다 폴링
  useEffect(() => {
    if (!project || (project.status !== 'PARSING' && project.status !== 'RENDERING')) return;
    const t = setInterval(async () => {
      try {
        const res = await projectApi.getOne(projectId);
        const p = res.data.data;
        setProject(p);
        if (p.status !== 'PARSING' && p.status !== 'RENDERING') clearInterval(t);
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
  const allMedia = project.mediaItems ?? [];
  const subtitles = project.subtitles ?? [];
  const isParsing = project.status === 'PARSING';

  const videos  = allMedia.filter((m) => m.mediaType === 'VIDEO' && !m.gif);
  const images  = allMedia.filter((m) => m.mediaType === 'IMAGE' && !m.gif);
  const gifs    = allMedia.filter((m) => m.gif);
  const texts   = allMedia.filter((m) => m.mediaType === 'TEXT');

  const filteredMedia: MediaItemResponse[] = (() => {
    switch (filter) {
      case 'VIDEO': return videos;
      case 'IMAGE': return images;
      case 'GIF':   return gifs;
      case 'TEXT':  return texts;
      default:      return allMedia;
    }
  })();

  const FILTERS: { key: FilterType; label: string; count: number; color: string }[] = [
    { key: 'ALL',   label: '전체',  count: allMedia.length, color: '#A78BFA' },
    { key: 'VIDEO', label: '영상',  count: videos.length,   color: '#7C3AED' },
    { key: 'IMAGE', label: '이미지',count: images.length,   color: '#60A5FA' },
    { key: 'GIF',   label: 'GIF',   count: gifs.length,     color: '#FB923C' },
    { key: 'TEXT',  label: '텍스트',count: texts.length,    color: '#34D399' },
  ].filter((f) => f.key === 'ALL' || f.count > 0);

  return (
    <>
      <Navbar />
      <main className="min-h-screen pt-20 pb-16 px-4">
        <div className="max-w-7xl mx-auto space-y-8">

          {/* ─── 렌더링 중 배너 ─── */}
          {project.status === 'RENDERING' && (
            <div className="flex items-center gap-4 p-5 rounded-2xl animate-pulse"
              style={{ background: 'rgba(167,139,250,0.07)', border: '1px solid rgba(167,139,250,0.25)' }}
            >
              <div className="w-10 h-10 rounded-full border-2 border-purple-500/40 border-t-purple-400 animate-spin shrink-0" />
              <div>
                <p className="font-semibold text-purple-300">렌더링 진행 중...</p>
                <p className="text-sm text-purple-600 mt-0.5">숏폼 영상을 생성하고 있어요. 완료되면 자동으로 갱신됩니다.</p>
              </div>
              <button onClick={fetchProject} className="ml-auto text-purple-500 hover:text-purple-300 transition-colors">
                <RefreshCw size={18} />
              </button>
            </div>
          )}

          {/* ─── 파싱 중 배너 ─── */}
          {isParsing && (
            <div className="flex items-center gap-4 p-5 rounded-2xl animate-pulse"
              style={{ background: 'rgba(251,191,36,0.07)', border: '1px solid rgba(251,191,36,0.25)' }}
            >
              <div className="w-10 h-10 rounded-full border-2 border-yellow-500/40 border-t-yellow-400 animate-spin shrink-0" />
              <div>
                <p className="font-semibold text-yellow-300">분석 진행 중...</p>
                <p className="text-sm text-yellow-600 mt-0.5">커뮤니티 게시글에서 미디어를 수집하고 있어요.</p>
              </div>
              <button onClick={fetchProject} className="ml-auto text-yellow-500 hover:text-yellow-300 transition-colors">
                <RefreshCw size={18} />
              </button>
            </div>
          )}

          {/* ─── 헤더 ─── */}
          <div className="space-y-6">
            <button onClick={() => router.push('/')}
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
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="px-3 py-1 rounded-full text-xs font-semibold"
                    style={{ background: status.bg, color: status.color }}>
                    {status.label}
                  </span>
                  {project.communityType && (
                    <span className="text-xs text-gray-600 bg-white/5 px-2.5 py-1 rounded-full">
                      {project.communityType}
                    </span>
                  )}
                  {project.outputPlatform && (
                    <span className="text-xs text-gray-600 bg-white/5 px-2.5 py-1 rounded-full">
                      {project.outputPlatform.replace('_', ' ')}
                    </span>
                  )}
                </div>

                <h1 className="text-3xl font-bold text-white leading-tight">
                  {project.title ?? (isParsing ? '제목 분석 중...' : '제목 없음')}
                </h1>

                {project.description && (
                  <p className="text-gray-400 text-base leading-relaxed max-w-2xl line-clamp-3">
                    {project.description}
                  </p>
                )}

                {project.communityUrl && (
                  <a href={project.communityUrl} target="_blank" rel="noopener noreferrer"
                    className="inline-flex items-center gap-1.5 text-sm text-blue-400 hover:text-blue-300 transition-colors"
                  >
                    <ExternalLink size={13} />
                    원본 게시글 보기
                  </a>
                )}
              </div>

              <div className="flex items-center gap-2 shrink-0">
                {!isParsing && allMedia.length > 0 && (project.status === 'PARSED' || project.status === 'COMPLETED') && (
                  <>
                    <button
                      onClick={() => handleRender(true)}
                      disabled={isRendering}
                      className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium text-white transition-all disabled:opacity-50"
                      style={{ background: 'linear-gradient(135deg, #7C3AED, #2563EB)' }}
                    >
                      <Play size={16} />
                      {isRendering ? '요청 중...' : '미리보기 렌더'}
                    </button>
                    <button
                      onClick={() => handleRender(false)}
                      disabled={isRendering}
                      className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium transition-all disabled:opacity-50"
                      style={{ background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.15)', color: '#fff' }}
                    >
                      <Video size={16} />
                      최종 렌더
                    </button>
                  </>
                )}
                <button onClick={fetchProject}
                  className="p-2.5 rounded-xl text-gray-500 hover:text-white hover:bg-white/8 transition-all"
                  title="새로고침"
                >
                  <RefreshCw size={18} />
                </button>
              </div>
            </div>
          </div>

          {/* ─── 통계 카드 ─── */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
            {[
              { label: '전체',   value: allMedia.length,     icon: <Layers size={18} />,      color: '#A78BFA' },
              { label: '영상',   value: videos.length,        icon: <Video size={18} />,       color: '#7C3AED' },
              { label: '이미지', value: images.length,        icon: <Image size={18} />,       color: '#60A5FA' },
              { label: 'GIF',    value: gifs.length,          icon: <Hash size={18} />,        color: '#FB923C' },
              { label: '텍스트', value: texts.length,         icon: <FileText size={18} />,    color: '#34D399' },
            ].map((stat) => (
              <div key={stat.label} className="p-4 rounded-2xl space-y-1.5"
                style={{ background: 'rgba(19,19,31,0.8)', border: '1px solid rgba(255,255,255,0.07)' }}
              >
                <div style={{ color: stat.color }}>{stat.icon}</div>
                <p className="text-2xl font-bold text-white">{isParsing ? '—' : stat.value}</p>
                <p className="text-xs text-gray-500">{stat.label}</p>
              </div>
            ))}
          </div>

          {/* ─── 경고 배너 ─── */}
          {project.warnings && project.warnings.length > 0 && (
            <div className="flex flex-col gap-2">
              {project.warnings.map((w, i) => (
                <div key={i} className="flex items-center gap-3 px-4 py-3 rounded-xl text-sm text-yellow-400"
                  style={{ background: 'rgba(251,191,36,0.06)', border: '1px solid rgba(251,191,36,0.2)' }}
                >
                  <AlertTriangle size={14} className="shrink-0" />
                  {w}
                </div>
              ))}
            </div>
          )}

          {/* ─── 탭 ─── */}
          {!isParsing && allMedia.length > 0 && (
            <div>
              {/* 탭 헤더 */}
              <div className="flex gap-1 p-1 rounded-xl w-fit mb-5"
                style={{ background: 'rgba(255,255,255,0.04)' }}
              >
                {([
                  { key: 'media' as const,     label: '미디어', icon: <Video size={14} />,    count: allMedia.length },
                  { key: 'subtitles' as const, label: '자막',   icon: <FileText size={14} />, count: subtitles.length },
                ]).map((tab) => (
                  <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                    className="flex items-center gap-2 px-5 py-2.5 rounded-lg text-sm font-medium transition-all"
                    style={
                      activeTab === tab.key
                        ? { background: 'linear-gradient(135deg, #7C3AED, #2563EB)', color: '#fff' }
                        : { color: '#6B7280' }
                    }
                  >
                    {tab.icon}
                    {tab.label}
                    {tab.count > 0 && (
                      <span className="text-xs px-1.5 py-0.5 rounded-md"
                        style={{ background: 'rgba(255,255,255,0.15)' }}>
                        {tab.count}
                      </span>
                    )}
                  </button>
                ))}
              </div>

              {/* ── 미디어 탭 ── */}
              {activeTab === 'media' && (
                <div className="space-y-5">
                  {/* 타입 필터 */}
                  {FILTERS.length > 2 && (
                    <div className="flex flex-wrap gap-2">
                      {FILTERS.map((f) => (
                        <button key={f.key} onClick={() => setFilter(f.key)}
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium transition-all"
                          style={
                            filter === f.key
                              ? { background: f.color, color: '#fff' }
                              : { background: 'rgba(255,255,255,0.05)', color: '#9CA3AF',
                                  border: '1px solid rgba(255,255,255,0.08)' }
                          }
                        >
                          {f.label}
                          <span className="px-1.5 py-0.5 rounded text-xs"
                            style={{ background: 'rgba(0,0,0,0.25)' }}>
                            {f.count}
                          </span>
                        </button>
                      ))}
                    </div>
                  )}

                  {filteredMedia.length > 0 ? (
                    <div className={`grid gap-4 ${
                      filter === 'TEXT'
                        ? 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3'
                        : 'grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5'
                    }`}>
                      {filteredMedia.map((item) => (
                        <MediaCard key={item.id} item={item} />
                      ))}
                    </div>
                  ) : (
                    <div className="flex flex-col items-center gap-3 py-12 text-center">
                      <Layers size={32} className="text-gray-700" />
                      <p className="text-gray-500">해당 타입의 미디어가 없습니다.</p>
                    </div>
                  )}
                </div>
              )}

              {/* ── 자막 탭 ── */}
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
                <p className="text-gray-600 text-sm mt-1">해당 게시글에서 추출 가능한 미디어가 없을 수 있습니다.</p>
              </div>
            </div>
          )}

          {/* 렌더 완료 - 영상 표시 */}
          {project.status === 'COMPLETED' && project.outputFilePath && (
            <div className="space-y-4 p-6 rounded-2xl"
              style={{ background: 'rgba(52,211,153,0.06)', border: '1px solid rgba(52,211,153,0.2)' }}
            >
              <p className="font-semibold text-green-300 flex items-center gap-2">
                <Video size={18} />
                렌더 완료
              </p>
              <video
                src={project.outputFilePath}
                controls
                className="w-full max-w-2xl rounded-xl"
                playsInline
              />
              <a
                href={project.outputFilePath}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-white"
                style={{ background: 'rgba(52,211,153,0.3)' }}
              >
                <ExternalLink size={14} />
                새 탭에서 보기 / 다운로드
              </a>
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
                <p className="text-sm text-red-600 mt-0.5">해당 URL의 게시글을 처리하는 중 오류가 발생했습니다.</p>
              </div>
            </div>
          )}

        </div>
      </main>
    </>
  );
}
