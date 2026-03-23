'use client';

import { useState, useRef, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { Sparkles, ChevronRight, AlertCircle, X, Layers, Wand2 } from 'lucide-react';
import Navbar from '@/components/Navbar';
import { scriptApi, projectApi } from '@/lib/api';
import type { OutputPlatform } from '@/types';

const PLATFORM_OPTIONS: { value: OutputPlatform; label: string; desc: string }[] = [
  { value: 'YOUTUBE_SHORTS', label: 'YouTube Shorts', desc: '9:16 · 1080×1920' },
  { value: 'TIKTOK',         label: 'TikTok',         desc: '9:16 · 1080×1920' },
  { value: 'INSTAGRAM_REELS',label: 'Instagram Reels',desc: '9:16 · 1080×1920' },
];

const EXAMPLE_TOPICS = [
  '블록체인 기술의 핵심 개념',
  '파이썬 프로그래밍의 장점',
  '양자컴퓨터란 무엇인가',
  '인공지능이 바꾸는 미래',
  '비타민D 부족의 7가지 증상',
];

const GENERATION_STEPS = [
  { icon: '🧠', label: 'GPT-4o 스크립트 작성 중' },
  { icon: '🎨', label: 'DALL-E 3 이미지 생성 중' },
  { icon: '☁️', label: 'MinIO 저장 중' },
  { icon: '✅', label: '프로젝트 준비 완료' },
];

export default function AiScriptPage() {
  const router = useRouter();

  const [topic, setTopic]           = useState('');
  const [sceneCount, setSceneCount] = useState(5);
  const [platform, setPlatform]     = useState<OutputPlatform>('YOUTUBE_SHORTS');
  const [error, setError]           = useState<string | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [genStep, setGenStep]       = useState(0);
  const [genDots, setGenDots]       = useState('');
  const stepTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const dotTimerRef  = useRef<ReturnType<typeof setInterval> | null>(null);

  const startGeneratingOverlay = useCallback(() => {
    setGenStep(0);
    setGenDots('');
    stepTimerRef.current = setInterval(() => {
      setGenStep((s) => Math.min(s + 1, GENERATION_STEPS.length - 1));
    }, 5000);
    dotTimerRef.current = setInterval(() => {
      setGenDots((d) => (d.length >= 3 ? '' : d + '.'));
    }, 500);
  }, []);

  const stopGeneratingOverlay = useCallback(() => {
    if (stepTimerRef.current) clearInterval(stepTimerRef.current);
    if (dotTimerRef.current)  clearInterval(dotTimerRef.current);
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = topic.trim();
    if (!trimmed) return;

    setError(null);
    setIsGenerating(true);
    startGeneratingOverlay();

    try {
      // 1. 프로젝트 생성 + 스크립트 생성 요청 (HTTP 202)
      const createRes = await scriptApi.generate({ topic: trimmed, sceneCount, outputPlatform: platform });
      const project = createRes.data.data;

      // 2. PARSED 상태까지 폴링 (GPT + DALL-E 완료 대기)
      const MAX_POLLS = 60;
      let count = 0;
      await new Promise<void>((resolve, reject) => {
        const timer = setInterval(async () => {
          count++;
          try {
            const res = await projectApi.getOne(project.id);
            const status = res.data.data?.status;
            if (status === 'PARSED' || status === 'COMPLETED') {
              clearInterval(timer);
              resolve();
            } else if (status === 'FAILED') {
              clearInterval(timer);
              reject(new Error('스크립트 생성에 실패했습니다.'));
            } else if (count >= MAX_POLLS) {
              clearInterval(timer);
              resolve(); // timeout → navigate anyway
            }
          } catch {
            // ignore transient errors
          }
        }, 2000);
      });

      stopGeneratingOverlay();
      setIsGenerating(false);
      router.push(`/projects/${project.id}`);
    } catch (err) {
      stopGeneratingOverlay();
      setIsGenerating(false);
      setError(err instanceof Error ? err.message : '오류가 발생했습니다.');
    }
  };

  return (
    <>
      <Navbar />

      {/* ─── Generation Overlay ─────────────────────────── */}
      {isGenerating && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-[#07070F]/90 backdrop-blur-md" />
          <div className="relative z-10 flex flex-col items-center gap-8 px-8 py-12 max-w-lg w-full mx-4">
            {/* 애니메이션 링 */}
            <div className="relative w-36 h-36 flex items-center justify-center">
              <div className="absolute inset-0 rounded-full border-2 border-transparent"
                style={{
                  background: 'linear-gradient(#07070F, #07070F) padding-box, linear-gradient(135deg, #7C3AED, #2563EB, #06B6D4, #7C3AED) border-box',
                  animation: 'spin 2s linear infinite',
                }}
              />
              <div className="absolute inset-3 rounded-full border border-purple-500/20"
                style={{ animation: 'spin 3s linear infinite reverse' }}
              />
              <div className="relative w-20 h-20 rounded-full flex items-center justify-center"
                style={{
                  background: 'linear-gradient(135deg, rgba(124,58,237,0.3), rgba(37,99,235,0.3))',
                  boxShadow: '0 0 40px rgba(124,58,237,0.4)',
                }}
              >
                <span className="text-4xl" style={{ animation: 'pulse 2s ease-in-out infinite' }}>
                  {GENERATION_STEPS[genStep].icon}
                </span>
              </div>
            </div>

            <div className="text-center space-y-3">
              <h2 className="text-3xl font-bold text-white">
                {GENERATION_STEPS[genStep].label}{genDots}
              </h2>
              <p className="text-gray-400 text-base">토픽 "{topic.trim()}"</p>
            </div>

            {/* 단계 표시 */}
            <div className="flex gap-3">
              {GENERATION_STEPS.map((step, i) => (
                <div
                  key={i}
                  title={step.label}
                  className="w-2.5 h-2.5 rounded-full transition-all duration-500"
                  style={{
                    background: i <= genStep
                      ? 'linear-gradient(135deg, #7C3AED, #2563EB)'
                      : 'rgba(255,255,255,0.15)',
                    boxShadow: i === genStep ? '0 0 8px rgba(124,58,237,0.8)' : 'none',
                  }}
                />
              ))}
            </div>
          </div>
        </div>
      )}

      <main className="min-h-screen pt-28 pb-16 px-4">
        <div className="max-w-2xl mx-auto space-y-8">

          {/* ─── 헤더 ────────────────────────────────────── */}
          <div className="space-y-3">
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium"
              style={{ background: 'rgba(124,58,237,0.15)', border: '1px solid rgba(124,58,237,0.3)' }}
            >
              <Sparkles size={14} className="text-purple-400" />
              <span className="text-purple-300">GPT-4o · DALL-E 3</span>
            </div>
            <h1 className="text-4xl font-black text-white">AI 지식 영상 만들기</h1>
            <p className="text-gray-400 leading-relaxed">
              주제만 입력하면 GPT-4o가 씬별 스크립트를 작성하고,
              DALL-E 3가 각 씬에 맞는 이미지를 자동 생성합니다.
            </p>
          </div>

          {/* ─── 에러 메시지 ─────────────────────────────── */}
          {error && (
            <div className="flex items-center justify-between gap-3 px-4 py-3 rounded-xl text-sm text-red-300"
              style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)' }}
            >
              <div className="flex items-center gap-2">
                <AlertCircle size={16} />
                {error}
              </div>
              <button onClick={() => setError(null)} className="text-red-400 hover:text-red-300">
                <X size={16} />
              </button>
            </div>
          )}

          {/* ─── 입력 폼 ─────────────────────────────────── */}
          <form onSubmit={handleSubmit} className="space-y-6">

            {/* 주제 입력 */}
            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-300">
                영상 주제
              </label>
              <textarea
                value={topic}
                onChange={(e) => setTopic(e.target.value)}
                placeholder="예: 파이썬 프로그래밍의 장점"
                rows={3}
                maxLength={200}
                required
                className="w-full px-4 py-3 rounded-xl text-white placeholder-gray-600 resize-none focus:outline-none transition-all"
                style={{
                  background: 'rgba(255,255,255,0.04)',
                  border: topic ? '1px solid rgba(124,58,237,0.5)' : '1px solid rgba(255,255,255,0.1)',
                  boxShadow: topic ? '0 0 0 3px rgba(124,58,237,0.1)' : 'none',
                }}
              />
              <div className="flex items-center justify-between text-xs text-gray-600">
                <span>자세할수록 더 좋은 영상이 만들어집니다</span>
                <span>{topic.length}/200</span>
              </div>
            </div>

            {/* 예시 토픽 */}
            <div className="space-y-2">
              <p className="text-xs text-gray-500">예시 주제</p>
              <div className="flex flex-wrap gap-2">
                {EXAMPLE_TOPICS.map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setTopic(t)}
                    className="px-3 py-1.5 rounded-lg text-xs text-gray-400 hover:text-white transition-all"
                    style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}
                  >
                    {t}
                  </button>
                ))}
              </div>
            </div>

            {/* 씬 수 슬라이더 */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <label className="text-sm font-medium text-gray-300">씬 수</label>
                <div className="flex items-center gap-1.5 px-3 py-1 rounded-lg"
                  style={{ background: 'rgba(124,58,237,0.2)', border: '1px solid rgba(124,58,237,0.3)' }}
                >
                  <Layers size={13} className="text-purple-400" />
                  <span className="text-sm font-bold text-purple-300">{sceneCount}개</span>
                </div>
              </div>
              <input
                type="range"
                min={2}
                max={10}
                step={1}
                value={sceneCount}
                onChange={(e) => setSceneCount(Number(e.target.value))}
                className="w-full accent-purple-500"
                style={{ cursor: 'pointer' }}
              />
              <div className="flex justify-between text-xs text-gray-600">
                <span>2개 (짧게)</span>
                <span>10개 (길게)</span>
              </div>
              <p className="text-xs text-gray-500">
                씬 1개 ≈ 약 5~8초 → {sceneCount}개 = 약 {sceneCount * 6}~{sceneCount * 8}초 영상
              </p>
            </div>

            {/* 출력 플랫폼 */}
            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-300">출력 플랫폼</label>
              <div className="grid grid-cols-3 gap-2">
                {PLATFORM_OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => setPlatform(opt.value)}
                    className="flex flex-col items-center gap-1 px-3 py-3 rounded-xl text-sm transition-all"
                    style={{
                      background: platform === opt.value
                        ? 'rgba(124,58,237,0.2)'
                        : 'rgba(255,255,255,0.04)',
                      border: platform === opt.value
                        ? '1px solid rgba(124,58,237,0.5)'
                        : '1px solid rgba(255,255,255,0.08)',
                      color: platform === opt.value ? '#A78BFA' : '#9CA3AF',
                    }}
                  >
                    <span className="font-medium">{opt.label}</span>
                    <span className="text-xs opacity-60">{opt.desc}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* 생성 버튼 */}
            <button
              type="submit"
              disabled={!topic.trim() || isGenerating}
              className="w-full flex items-center justify-center gap-3 px-6 py-4 rounded-xl font-semibold text-base transition-all disabled:opacity-40 disabled:cursor-not-allowed"
              style={{
                background: topic.trim()
                  ? 'linear-gradient(135deg, #7C3AED, #2563EB)'
                  : 'rgba(255,255,255,0.06)',
                boxShadow: topic.trim() ? '0 8px 32px rgba(124,58,237,0.35)' : 'none',
              }}
            >
              <Wand2 size={20} />
              AI 영상 생성 시작
              <ChevronRight size={18} />
            </button>
          </form>

          {/* ─── 흐름 설명 ────────────────────────────────── */}
          <div className="rounded-2xl p-5 space-y-4"
            style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.07)' }}
          >
            <p className="text-sm font-medium text-gray-400">생성 흐름</p>
            <div className="grid grid-cols-2 gap-3 text-sm">
              {[
                { icon: '🧠', title: 'GPT-4o 스크립트', desc: '씬별 한국어 나레이션 + 이미지 프롬프트 생성' },
                { icon: '🎨', title: 'DALL-E 3 이미지', desc: '각 씬에 맞는 9:16 세로형 이미지 자동 생성' },
                { icon: '🎙️', title: 'Edge TTS 음성', desc: '한국어 나레이션 자동 음성 합성' },
                { icon: '🎬', title: 'FFmpeg 렌더링', desc: 'Whisper 자막 정렬 + 최종 영상 합성' },
              ].map((step) => (
                <div key={step.title} className="flex items-start gap-3 p-3 rounded-xl"
                  style={{ background: 'rgba(255,255,255,0.03)' }}
                >
                  <span className="text-xl">{step.icon}</span>
                  <div>
                    <p className="text-white font-medium text-xs">{step.title}</p>
                    <p className="text-gray-500 text-xs mt-0.5 leading-relaxed">{step.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>

        </div>
      </main>
    </>
  );
}
