'use client';

import { useEffect, useState } from 'react';
import { useProjectStore } from '@/store/projectStore';

const STEPS = [
  { label: 'URL 분석 중', icon: '🔍' },
  { label: '페이지 크롤링 중', icon: '🕷️' },
  { label: '미디어 추출 중', icon: '🎬' },
  { label: 'AI 자막 생성 중', icon: '✨' },
  { label: '메타데이터 저장 중', icon: '💾' },
];

export default function ScrapingOverlay() {
  const { isParsing, parseProgress } = useProjectStore();
  const [stepIndex, setStepIndex] = useState(0);
  const [dots, setDots] = useState('');

  useEffect(() => {
    if (!isParsing) {
      setStepIndex(0);
      return;
    }
    const stepTimer = setInterval(() => {
      setStepIndex((i) => (i + 1) % STEPS.length);
    }, 2200);
    const dotTimer = setInterval(() => {
      setDots((d) => (d.length >= 3 ? '' : d + '.'));
    }, 500);
    return () => {
      clearInterval(stepTimer);
      clearInterval(dotTimer);
    };
  }, [isParsing]);

  if (!isParsing) return null;

  const progress = parseProgress.progress || 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* 배경 블러 */}
      <div className="absolute inset-0 bg-[#07070F]/90 backdrop-blur-md" />

      {/* 메인 카드 */}
      <div className="relative z-10 flex flex-col items-center gap-8 px-8 py-12 max-w-lg w-full mx-4">

        {/* 애니메이션 링 */}
        <div className="relative w-36 h-36 flex items-center justify-center">
          {/* 외부 회전 링 */}
          <div className="absolute inset-0 rounded-full border-2 border-transparent"
            style={{
              background: 'linear-gradient(#07070F, #07070F) padding-box, linear-gradient(135deg, #7C3AED, #2563EB, #06B6D4, #7C3AED) border-box',
              animation: 'spin 2s linear infinite',
            }}
          />
          {/* 중간 링 */}
          <div className="absolute inset-3 rounded-full border border-purple-500/20"
            style={{ animation: 'spin 3s linear infinite reverse' }}
          />
          {/* 중앙 아이콘 */}
          <div className="relative w-20 h-20 rounded-full flex items-center justify-center"
            style={{
              background: 'linear-gradient(135deg, rgba(124,58,237,0.3), rgba(37,99,235,0.3))',
              boxShadow: '0 0 40px rgba(124,58,237,0.4)',
            }}
          >
            <span className="text-4xl" style={{ animation: 'pulse 2s ease-in-out infinite' }}>
              {STEPS[stepIndex].icon}
            </span>
          </div>
        </div>

        {/* 텍스트 */}
        <div className="text-center space-y-3">
          <h2 className="text-3xl font-bold text-white">
            {STEPS[stepIndex].label}{dots}
          </h2>
          <p className="text-gray-400 text-base">
            {parseProgress.message || '커뮤니티 게시글을 분석하고 있어요'}
          </p>
        </div>

        {/* 진행률 바 */}
        <div className="w-full space-y-2">
          <div className="flex justify-between text-sm">
            <span className="text-gray-500">진행률</span>
            <span className="text-purple-400 font-medium">{progress}%</span>
          </div>
          <div className="w-full h-2 bg-white/5 rounded-full overflow-hidden">
            <div
              className="h-full rounded-full transition-all duration-700 ease-out"
              style={{
                width: `${Math.max(progress, 5)}%`,
                background: 'linear-gradient(90deg, #7C3AED, #2563EB, #06B6D4)',
              }}
            />
          </div>
        </div>

        {/* 단계 표시 */}
        <div className="flex gap-2">
          {STEPS.map((step, i) => (
            <div
              key={i}
              className="flex flex-col items-center gap-1.5"
            >
              <div
                className={`w-8 h-8 rounded-full flex items-center justify-center text-sm transition-all duration-300 ${
                  i < stepIndex
                    ? 'bg-purple-600 text-white scale-95'
                    : i === stepIndex
                    ? 'text-white scale-110'
                    : 'bg-white/5 text-gray-600'
                }`}
                style={
                  i === stepIndex
                    ? { background: 'linear-gradient(135deg, #7C3AED, #2563EB)', boxShadow: '0 0 16px rgba(124,58,237,0.6)' }
                    : {}
                }
              >
                {i < stepIndex ? '✓' : step.icon}
              </div>
            </div>
          ))}
        </div>

        <p className="text-xs text-gray-600 text-center">
          게시글 크기에 따라 20~60초 소요될 수 있습니다
        </p>
      </div>
    </div>
  );
}
