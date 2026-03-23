'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Newspaper, ChevronRight, Loader2, AlertCircle, CheckCircle } from 'lucide-react';
import Navbar from '@/components/Navbar';
import { newsArticleApi } from '@/lib/api';
import type { NewsArticleResponse, NewsArticleStatus } from '@/types';

const STATUS_CONFIG: Record<NewsArticleStatus, { label: string; color: string; bg: string }> = {
  FETCHED: { label: '수집됨', color: '#94A3B8', bg: 'rgba(148,163,184,0.1)' },
  METADATA_EXTRACTING: { label: '메타데이터 추출 중', color: '#FBBF24', bg: 'rgba(251,191,36,0.1)' },
  METADATA_READY: { label: '메타데이터 완료', color: '#60A5FA', bg: 'rgba(96,165,250,0.1)' },
  MULTIMEDIA_FETCHING: { label: '멀티미디어 검색 중', color: '#A78BFA', bg: 'rgba(167,139,250,0.1)' },
  MULTIMEDIA_READY: { label: '편집 가능', color: '#34D399', bg: 'rgba(52,211,153,0.1)' },
  RENDER_REQUESTED: { label: '렌더 요청됨', color: '#F59E0B', bg: 'rgba(245,158,11,0.1)' },
  RENDERED: { label: '완료', color: '#34D399', bg: 'rgba(52,211,153,0.1)' },
  FAILED: { label: '실패', color: '#F87171', bg: 'rgba(248,113,113,0.1)' },
};

function formatDate(iso: string | null): string {
  if (!iso) return '-';
  try {
    const d = new Date(iso);
    return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  } catch {
    return '-';
  }
}

export default function NewsArticlesPage() {
  const router = useRouter();
  const [articles, setArticles] = useState<NewsArticleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    newsArticleApi.getAll(0, 50)
      .then((res) => setArticles(res.data.data ?? []))
      .catch((e) => setError(e instanceof Error ? e.message : '목록을 불러올 수 없습니다.'))
      .finally(() => setLoading(false));
  }, []);

  const handleRowClick = (id: number) => {
    router.push(`/news-articles/${id}`);
  };

  return (
    <>
      <Navbar />
      <main className="min-h-screen pt-24 pb-16 px-4">
        <div className="max-w-6xl mx-auto">
          {/* 헤더 */}
          <div className="flex items-center gap-3 mb-8">
            <Newspaper size={24} className="text-purple-400" />
            <h1 className="text-2xl font-bold text-white">뉴스 기사</h1>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-medium text-purple-300"
              style={{ background: 'rgba(124,58,237,0.2)' }}>
              NewsAPI + OpenAI
            </span>
          </div>

          {/* 에러 */}
          {error && (
            <div className="mb-6 flex items-center gap-2 px-4 py-3 rounded-xl text-sm text-red-300"
              style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)' }}
            >
              <AlertCircle size={18} />
              {error}
            </div>
          )}

          {/* 테이블 */}
          <div className="rounded-2xl overflow-hidden"
            style={{
              background: 'rgba(255,255,255,0.03)',
              border: '1px solid rgba(255,255,255,0.08)',
            }}
          >
            {loading ? (
              <div className="flex items-center justify-center py-16">
                <Loader2 size={24} className="animate-spin text-purple-400" />
              </div>
            ) : articles.length === 0 ? (
              <div className="py-16 text-center text-gray-500">
                <Newspaper size={48} className="mx-auto mb-8 opacity-60" />
                <p>아직 수집된 뉴스 기사가 없습니다.</p>
                <p className="text-sm mt-2">백엔드 시작 시 NewsAPI에서 자동 수집됩니다.</p>
              </div>
            ) : (
              <table className="w-full">
                <thead>
                  <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
                    <th className="text-left py-4 px-5 text-xs font-semibold text-gray-400 uppercase tracking-wider">제목</th>
                    <th className="text-left py-4 px-5 text-xs font-semibold text-gray-400 uppercase tracking-wider w-28">소스</th>
                    <th className="text-left py-4 px-5 text-xs font-semibold text-gray-400 uppercase tracking-wider w-36">상태</th>
                    <th className="text-left py-4 px-5 text-xs font-semibold text-gray-400 uppercase tracking-wider w-32">수집일</th>
                    <th className="w-10" />
                  </tr>
                </thead>
                <tbody>
                  {articles.map((a) => {
                    const statusCfg = STATUS_CONFIG[a.status] ?? STATUS_CONFIG.FETCHED;
                    return (
                      <tr
                        key={a.id}
                        onClick={() => handleRowClick(a.id)}
                        className="cursor-pointer transition-colors hover:bg-white/5"
                        style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}
                      >
                        <td className="py-4 px-5">
                          <div className="flex items-center gap-3">
                            {a.urlToImage && (
                              <img
                                src={a.urlToImage}
                                alt=""
                                className="w-12 h-12 rounded-lg object-cover shrink-0"
                              />
                            )}
                            <div>
                              <p className="font-medium text-white line-clamp-2">
                                {a.translatedTitle || a.title}
                              </p>
                              {a.translatedContent && (
                                <p className="text-sm text-gray-500 line-clamp-1 mt-0.5">
                                  {a.translatedContent}
                                </p>
                              )}
                            </div>
                          </div>
                        </td>
                        <td className="py-4 px-5 text-sm text-gray-400">
                          {a.sourceName || '-'}
                        </td>
                        <td className="py-4 px-5">
                          <span
                            className="inline-flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-full"
                            style={{ color: statusCfg.color, background: statusCfg.bg }}
                          >
                            {a.status === 'METADATA_READY' || a.status === 'MULTIMEDIA_READY' ? (
                              <CheckCircle size={12} />
                            ) : null}
                            {statusCfg.label}
                          </span>
                        </td>
                        <td className="py-4 px-5 text-sm text-gray-500">
                          {formatDate(a.publishedAt)}
                        </td>
                        <td className="py-4 px-2">
                          <ChevronRight size={18} className="text-gray-500" />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </main>
    </>
  );
}
