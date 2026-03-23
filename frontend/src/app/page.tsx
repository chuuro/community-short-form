'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowRight, Sparkles, Globe, Zap, TrendingUp, ChevronRight, AlertCircle, X } from 'lucide-react';
import Navbar from '@/components/Navbar';
import ProjectCard from '@/components/ProjectCard';
import ScrapingOverlay from '@/components/ScrapingOverlay';
import { useProjectStore } from '@/store/projectStore';
import { projectApi } from '@/lib/api';
import { connectWebSocket } from '@/lib/websocket';
import type { ProjectResponse } from '@/types';

const EXAMPLE_URLS = [
  'https://www.reddit.com/r/funny/comments/...',
  'https://www.reddit.com/r/gaming/comments/...',
  'https://www.youtube.com/watch?v=...',
];

const FEATURES = [
  { icon: '🕷️', title: '자동 크롤링', desc: '커뮤니티 URL만 입력하면 영상·이미지·댓글을 자동 수집' },
  { icon: '✨', title: 'AI 자막 생성', desc: 'Whisper & GPT-4o로 자동 자막·대본 생성' },
  { icon: '🎬', title: '숏폼 자동 편집', desc: 'FFmpeg 기반 자동 편집으로 숏폼 영상 즉시 완성' },
];

export default function HomePage() {
  const router = useRouter();
  const { projects, setProjects, addProject, isParsing, setParsing, setParseProgress, setSubmitting, isSubmitting, error, setError } = useProjectStore();
  const [url, setUrl] = useState('');
  const [inputFocused, setInputFocused] = useState(false);
  const [placeholderIdx, setPlaceholderIdx] = useState(0);
  const disconnectWsRef = useRef<(() => void) | null>(null);

  // 예시 URL 플레이스홀더 순환
  useEffect(() => {
    const t = setInterval(() => {
      setPlaceholderIdx((i) => (i + 1) % EXAMPLE_URLS.length);
    }, 3000);
    return () => clearInterval(t);
  }, []);

  // 프로젝트 목록 로드
  useEffect(() => {
    projectApi.getAll()
      .then((res) => setProjects(res.data.data ?? []))
      .catch(() => setProjects([]));
  }, [setProjects]);

  // 폴링: PARSING 상태인 프로젝트 완료 감지
  const pollProjectStatus = useCallback(async (projectId: number) => {
    const MAX = 60;
    let count = 0;
    const timer = setInterval(async () => {
      count++;
      try {
        const res = await projectApi.getOne(projectId);
        const p = res.data.data;
        const prog = Math.min(10 + count * 1.5, 90);
        setParseProgress({ message: '게시글 분석 중...', progress: Math.round(prog) });

        if (p.status === 'FAILED') {
          clearInterval(timer);
          setParsing(false);
          disconnectWsRef.current?.();
          setError('파싱에 실패했습니다. 지원되는 URL인지 확인해주세요. (Reddit, YouTube만 지원)');
        } else if (p.status === 'PARSED' || p.status === 'COMPLETED') {
          clearInterval(timer);
          setParseProgress({ message: '완료!', progress: 100 });
          setTimeout(() => {
            setParsing(false);
            disconnectWsRef.current?.();
            router.push(`/projects/${projectId}`);
          }, 600);
        }
      } catch {
        // ignore polling errors
      }
      if (count >= MAX) {
        clearInterval(timer);
        setParsing(false);
        setError('파싱 시간이 초과되었습니다. 프로젝트 페이지에서 확인해보세요.');
        router.push(`/projects/${projectId}`);
      }
    }, 2000);
    return () => clearInterval(timer);
  }, [router, setParsing, setParseProgress, setError]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = url.trim();
    if (!trimmed) return;

    setError(null);
    setSubmitting(true);

    try {
      // 1. 프로젝트 생성
      const createRes = await projectApi.create({
        communityUrl: trimmed,
        outputPlatform: 'YOUTUBE_SHORTS',
      });
      const project: ProjectResponse = createRes.data.data;
      addProject(project);

      // 2. 파싱 시작
      await projectApi.parse(project.id);
      setParsing(true);
      setParseProgress({ message: 'URL을 분석하고 있어요...', progress: 5 });
      setUrl('');

      // 3. WebSocket 구독 (진행률 수신 시도)
      disconnectWsRef.current = connectWebSocket(
        project.id,
        (msg) => {
          setParseProgress({ message: msg.message, progress: msg.progress });
          if (msg.status === 'PARSED' || msg.status === 'COMPLETED') {
            setTimeout(() => {
              setParsing(false);
              router.push(`/projects/${project.id}`);
            }, 600);
          }
        }
      );

      // 4. 폴링 병행 (WebSocket 불안정 대비)
      pollProjectStatus(project.id);

    } catch (err) {
      setError(err instanceof Error ? err.message : '오류가 발생했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await projectApi.softDelete(id);
      setProjects(projects.filter((p) => p.id !== id));
    } catch {
      setError('삭제에 실패했습니다.');
    }
  };

  const isLoading = isSubmitting || isParsing;

  return (
    <>
      <Navbar />
      <ScrapingOverlay />

      <main className="min-h-screen">
        {/* ─── Hero Section ──────────────────────────────────── */}
        <section className="relative pt-32 pb-20 px-4 overflow-hidden">
          {/* 배경 글로우 */}
          <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[900px] h-[500px] pointer-events-none"
            style={{
              background: 'radial-gradient(ellipse at center top, rgba(124,58,237,0.28) 0%, rgba(37,99,235,0.15) 40%, transparent 70%)',
            }}
          />
          <div className="absolute top-40 left-1/4 w-64 h-64 rounded-full pointer-events-none"
            style={{ background: 'radial-gradient(circle, rgba(6,182,212,0.1) 0%, transparent 70%)' }}
          />

          <div className="relative max-w-4xl mx-auto text-center space-y-8">
            {/* 뱃지 */}
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium"
              style={{ background: 'rgba(124,58,237,0.15)', border: '1px solid rgba(124,58,237,0.3)' }}
            >
              <Sparkles size={14} className="text-purple-400" />
              <span className="text-purple-300">AI 기반 자동 숏폼 제작</span>
            </div>

            {/* 헤드라인 */}
            <div className="space-y-4">
              <h1 className="text-6xl sm:text-7xl font-black leading-[1.05] tracking-tight">
                <span className="text-white">커뮤니티를</span>
                <br />
                <span style={{
                  background: 'linear-gradient(135deg, #A78BFA 0%, #60A5FA 50%, #34D399 100%)',
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                  backgroundClip: 'text',
                }}>
                  숏폼으로
                </span>
              </h1>
              <p className="text-xl text-gray-400 max-w-2xl mx-auto leading-relaxed font-light">
                Reddit, YouTube 등 커뮤니티 URL을 붙여넣으면
                <br />
                AI가 자동으로 숏폼 영상을 만들어드립니다.
              </p>
            </div>

            {/* URL 입력 폼 */}
            <form onSubmit={handleSubmit} className="relative max-w-3xl mx-auto">
              <div
                className="relative flex items-center gap-3 p-2.5 rounded-2xl transition-all duration-300"
                style={{
                  background: 'rgba(255,255,255,0.04)',
                  border: inputFocused
                    ? '1px solid rgba(124,58,237,0.6)'
                    : '1px solid rgba(255,255,255,0.1)',
                  boxShadow: inputFocused
                    ? '0 0 0 4px rgba(124,58,237,0.12), 0 8px 32px rgba(0,0,0,0.4)'
                    : '0 8px 32px rgba(0,0,0,0.3)',
                }}
              >
                <div className="pl-2 text-gray-500 shrink-0">
                  <Globe size={22} />
                </div>
                <input
                  type="url"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  onFocus={() => setInputFocused(true)}
                  onBlur={() => setInputFocused(false)}
                  placeholder={EXAMPLE_URLS[placeholderIdx]}
                  disabled={isLoading}
                  className="flex-1 bg-transparent text-white text-lg placeholder-gray-600 outline-none min-w-0 py-2"
                  style={{ fontFamily: 'Inter, sans-serif' }}
                />
                <button
                  type="submit"
                  disabled={isLoading || !url.trim()}
                  className="flex items-center gap-2.5 px-7 py-3.5 rounded-xl font-semibold text-base text-white shrink-0 transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed"
                  style={{
                    background: url.trim() && !isLoading
                      ? 'linear-gradient(135deg, #7C3AED, #2563EB)'
                      : 'rgba(255,255,255,0.08)',
                    boxShadow: url.trim() && !isLoading
                      ? '0 4px 20px rgba(124,58,237,0.4)'
                      : 'none',
                  }}
                >
                  {isSubmitting ? (
                    <div className="w-5 h-5 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                  ) : (
                    <>
                      <Zap size={17} />
                      숏폼 만들기
                      <ArrowRight size={16} />
                    </>
                  )}
                </button>
              </div>

              {/* 에러 메시지 */}
              {error && (
                <div className="mt-3 flex items-center gap-2 px-4 py-3 rounded-xl text-sm text-red-300"
                  style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)' }}
                >
                  <AlertCircle size={15} className="shrink-0" />
                  <span className="flex-1">{error}</span>
                  <button onClick={() => setError(null)} className="text-red-400 hover:text-red-300">
                    <X size={14} />
                  </button>
                </div>
              )}
            </form>

            {/* 지원 플랫폼 */}
            <div className="flex items-center justify-center gap-4 text-sm text-gray-600">
              <span>지원:</span>
              {['Reddit', 'YouTube', '더보기...'].map((p, i) => (
                <span key={i} className="flex items-center gap-1 text-gray-500">
                  {i < 2 && <ChevronRight size={12} className="text-purple-600" />}
                  {p}
                </span>
              ))}
            </div>
          </div>
        </section>

        {/* ─── 기능 소개 ──────────────────────────────────────── */}
        <section className="py-8 px-4">
          <div className="max-w-4xl mx-auto grid grid-cols-1 sm:grid-cols-3 gap-4">
            {FEATURES.map((f) => (
              <div key={f.title}
                className="flex items-start gap-4 p-5 rounded-2xl"
                style={{
                  background: 'rgba(255,255,255,0.02)',
                  border: '1px solid rgba(255,255,255,0.06)',
                }}
              >
                <span className="text-2xl shrink-0">{f.icon}</span>
                <div>
                  <p className="font-semibold text-white text-sm">{f.title}</p>
                  <p className="text-xs text-gray-500 mt-0.5 leading-relaxed">{f.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* ─── 프로젝트 목록 ──────────────────────────────────── */}
        {projects.length > 0 && (
          <section className="py-12 px-4">
            <div className="max-w-7xl mx-auto">
              <div className="flex items-center justify-between mb-8">
                <div className="flex items-center gap-3">
                  <TrendingUp size={20} className="text-purple-400" />
                  <h2 className="text-2xl font-bold text-white">최근 프로젝트</h2>
                  <span className="px-2.5 py-0.5 rounded-full text-xs font-medium text-purple-300"
                    style={{ background: 'rgba(124,58,237,0.2)' }}>
                    {projects.length}
                  </span>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                {projects.map((project) => (
                  <ProjectCard
                    key={project.id}
                    project={project}
                    onDelete={handleDelete}
                  />
                ))}
              </div>
            </div>
          </section>
        )}

        {/* 프로젝트 없을 때 */}
        {projects.length === 0 && (
          <section className="py-16 px-4 text-center">
            <div className="max-w-sm mx-auto space-y-3">
              <p className="text-5xl">🎬</p>
              <p className="text-gray-400 text-base">
                아직 프로젝트가 없어요.
                <br />위에 URL을 입력해서 첫 숏폼을 만들어보세요!
              </p>
            </div>
          </section>
        )}
      </main>
    </>
  );
}
