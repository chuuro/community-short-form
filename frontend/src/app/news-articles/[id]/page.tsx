'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  ArrowLeft, FileText, Image, Video, CheckCircle, Loader2,
  ExternalLink, Search, Edit3, AlertCircle, Play, Film, Plus, X
} from 'lucide-react';
import Navbar from '@/components/Navbar';
import { newsArticleApi, projectApi } from '@/lib/api';
import type { NewsArticleResponse, NewsArticleStatus, NewsArticleMediaResponse, KeywordItem } from '@/types';

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

function parseKeywords(json: string | null): KeywordItem[] {
  if (!json) return [];
  try {
    const arr = JSON.parse(json);
    if (!Array.isArray(arr)) return [];
    return arr.map((item) => {
      if (typeof item === 'string') {
        return { keyword: item, source: 'openai' as const, enabled: true };
      }
      if (item && typeof item === 'object' && 'keyword' in item) {
        return {
          keyword: String(item.keyword ?? ''),
          source: (item.source === 'user' ? 'user' : 'openai') as 'openai' | 'user',
          enabled: item.enabled !== false,
        };
      }
      return null;
    }).filter((k): k is KeywordItem => k != null && k.keyword.trim() !== '');
  } catch {
    return [];
  }
}

export default function NewsArticleDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = Number(params.id);

  const [article, setArticle] = useState<NewsArticleResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirmLoading, setConfirmLoading] = useState(false);
  const [scriptEdit, setScriptEdit] = useState('');
  const [media, setMedia] = useState<NewsArticleMediaResponse[]>([]);
  const [mediaLoading, setMediaLoading] = useState(false);
  const [renderLoading, setRenderLoading] = useState(false);
  const [outputUrl, setOutputUrl] = useState<string | null>(null);
  const [imageKeywordsEdit, setImageKeywordsEdit] = useState<KeywordItem[]>([]);
  const [videoKeywordsEdit, setVideoKeywordsEdit] = useState<KeywordItem[]>([]);
  const [keywordsSaving, setKeywordsSaving] = useState(false);
  const [addMediaUrl, setAddMediaUrl] = useState('');
  const [addMediaType, setAddMediaType] = useState<'VIDEO' | 'IMAGE'>('IMAGE');
  const [addMediaMode, setAddMediaMode] = useState<'url' | 'file'>('url');
  const [addMediaFile, setAddMediaFile] = useState<File | null>(null);
  const [addMediaLoading, setAddMediaLoading] = useState(false);
  const [exposureEdits, setExposureEdits] = useState<Record<number, number>>({});
  const [exposureSaving, setExposureSaving] = useState(false);

  const fetchArticle = useCallback(() => {
    if (!id || isNaN(id)) return;
    newsArticleApi.getOne(id)
      .then((res) => {
        const a = res.data.data;
        setArticle(a);
        setScriptEdit(a?.script ?? '');
      })
      .catch((e) => setError(e instanceof Error ? e.message : '기사를 불러올 수 없습니다.'));
  }, [id]);

  useEffect(() => {
    if (!id || isNaN(id)) return;
    setLoading(true);
    newsArticleApi.getOne(id)
      .then((res) => {
        const a = res.data.data;
        setArticle(a);
        setScriptEdit(a?.script ?? '');
      })
      .catch((e) => setError(e instanceof Error ? e.message : '기사를 불러올 수 없습니다.'))
      .finally(() => setLoading(false));
  }, [id]);

  // MULTIMEDIA_READY 이후 미디어 목록 로드
  useEffect(() => {
    if (!id || !article || (article.status !== 'MULTIMEDIA_READY' && article.status !== 'RENDER_REQUESTED' && article.status !== 'RENDERED')) return;
    setMediaLoading(true);
    newsArticleApi.getMedia(id)
      .then((res) => {
        const list = res.data.data ?? [];
        setMedia(list);
        const edits: Record<number, number> = {};
        list.forEach((m) => {
          if (m.exposureDurationSeconds != null) edits[m.id] = m.exposureDurationSeconds;
        });
        setExposureEdits(edits);
      })
      .catch(() => setMedia([]))
      .finally(() => setMediaLoading(false));
  }, [id, article?.status]);

  // article 변경 시 키워드 편집 상태 동기화
  useEffect(() => {
    if (article) {
      setImageKeywordsEdit(parseKeywords(article.imageSearchKeywords));
      setVideoKeywordsEdit(parseKeywords(article.videoSearchKeywords));
    }
  }, [article?.imageSearchKeywords, article?.videoSearchKeywords]);

  // RENDERED 시 출력 영상 URL 로드 (브라우저 재생용 Presigned URL)
  useEffect(() => {
    if (!article?.projectId || article.status !== 'RENDERED') return;
    projectApi.getOutputUrl(article.projectId)
      .then((res) => {
        const url = res.data.data;
        if (url) setOutputUrl(url);
      })
      .catch(() => {
        projectApi.getOne(article.projectId)
          .then((r) => {
            const fp = r.data.data?.outputFilePath;
            if (fp && !fp.includes('minio:9000')) setOutputUrl(fp);
          })
          .catch(() => {});
      });
  }, [article?.projectId, article?.status]);

  const handleConfirm = async () => {
    if (!article || article.status !== 'METADATA_READY') return;
    setConfirmLoading(true);
    setError(null);
    try {
      const res = await newsArticleApi.fetchMultimedia(id);
      if (res.data?.data) {
        setArticle(res.data.data);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '멀티미디어 검색 요청 실패');
    } finally {
      setConfirmLoading(false);
    }
  };

  const handleToggleMedia = async (mediaId: number) => {
    const next = media.map((m) =>
      m.id === mediaId ? { ...m, selected: !m.selected } : m
    );
    const selectedIds = next.filter((m) => m.selected).map((m) => m.id);
    setMedia(next);
    try {
      await newsArticleApi.updateMediaSelection(id, selectedIds);
    } catch {
      setMedia(media);
    }
  };

  const handleSaveKeywords = async () => {
    if (!article) return;
    const imgValid = imageKeywordsEdit.filter((k) => k.keyword.trim());
    const vidValid = videoKeywordsEdit.filter((k) => k.keyword.trim());
    const userOver = [...imgValid, ...vidValid].filter((k) => k.source === 'user' && k.keyword.length > 20);
    if (userOver.length > 0) {
      setError('사용자 추가 검색어는 20자 이내로 입력해 주세요.');
      return;
    }
    setKeywordsSaving(true);
    setError(null);
    try {
      const res = await newsArticleApi.updateKeywords(id, {
        imageSearchKeywords: imgValid,
        videoSearchKeywords: vidValid,
      });
      if (res.data?.data) setArticle(res.data.data);
    } catch (e) {
      setError(e instanceof Error ? e.message : '키워드 저장 실패');
    } finally {
      setKeywordsSaving(false);
    }
  };

  const handleToggleKeywordEnabled = (type: 'image' | 'video', index: number) => {
    if (type === 'image') {
      setImageKeywordsEdit((p) => p.map((k, i) => (i === index ? { ...k, enabled: !k.enabled } : k)));
    } else {
      setVideoKeywordsEdit((p) => p.map((k, i) => (i === index ? { ...k, enabled: !k.enabled } : k)));
    }
  };

  const handleRemoveUserKeyword = (type: 'image' | 'video', index: number) => {
    const removeIfUser = (p: KeywordItem[], i: number) => {
      if (p[i]?.source !== 'user') return p;
      return p.filter((_, idx) => idx !== i);
    };
    if (type === 'image') {
      setImageKeywordsEdit((p) => removeIfUser(p, index));
    } else {
      setVideoKeywordsEdit((p) => removeIfUser(p, index));
    }
  };

  const handleAddUserKeyword = (type: 'image' | 'video', value: string) => {
    const trimmed = value.trim();
    if (!trimmed || trimmed.length > 20) return;
    const item: KeywordItem = { keyword: trimmed, source: 'user', enabled: true };
    if (type === 'image') {
      setImageKeywordsEdit((p) => [...p, item]);
    } else {
      setVideoKeywordsEdit((p) => [...p, item]);
    }
  };

  const handleAddMedia = async () => {
    if (addMediaMode === 'file') {
      if (!addMediaFile) return;
      setAddMediaLoading(true);
      setError(null);
      try {
        const res = await newsArticleApi.uploadMedia(id, addMediaFile, addMediaType);
        if (res.data?.data) {
          setMedia((prev) => [...prev, res.data.data]);
          setAddMediaFile(null);
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : '미디어 업로드 실패');
      } finally {
        setAddMediaLoading(false);
      }
      return;
    }
    const url = addMediaUrl.trim();
    if (!url) return;
    setAddMediaLoading(true);
    setError(null);
    try {
      const res = await newsArticleApi.addMedia(id, {
        sourceUrl: url,
        mediaType: addMediaType,
      });
      if (res.data?.data) {
        setMedia((prev) => [...prev, res.data.data]);
        setAddMediaUrl('');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '미디어 추가 실패');
    } finally {
      setAddMediaLoading(false);
    }
  };

  const handleSaveExposure = async () => {
    const selectedItems = media.filter((m) => m.selected);
    const scriptDuration = article?.estimatedDurationSeconds ?? 60;
    const getExposure = (m: NewsArticleMediaResponse) =>
      exposureEdits[m.id] ?? m.exposureDurationSeconds ?? (m.mediaType === 'VIDEO' ? m.durationSeconds : 4) ?? 4;
    const items = selectedItems
      .map((m) => ({ mediaId: m.id, exposureDurationSeconds: getExposure(m) }))
      .filter((i) => i.exposureDurationSeconds > 0 && i.exposureDurationSeconds <= scriptDuration);
    if (items.length === 0) {
      setError(`노출 시간은 대본 길이(${Math.round(scriptDuration)}초) 이하여야 합니다.`);
      return;
    }
    setExposureSaving(true);
    setError(null);
    try {
      await newsArticleApi.updateMediaExposure(id, items);
      setMedia((prev) =>
        prev.map((m) =>
          exposureEdits[m.id] != null
            ? { ...m, exposureDurationSeconds: exposureEdits[m.id] }
            : m
        )
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : '노출 시간 저장 실패');
    } finally {
      setExposureSaving(false);
    }
  };

  const handleRequestRender = async () => {
    if (!article || article.status !== 'MULTIMEDIA_READY') return;
    setRenderLoading(true);
    setError(null);
    try {
      const res = await newsArticleApi.requestRender(id);
      const job = res.data.data;
      if (job && job.projectId) {
        setArticle((a) => (a ? { ...a, status: 'RENDER_REQUESTED' as const } : a));
        const poll = async () => {
          try {
            const s = await projectApi.getRenderStatus(job.id);
            const j = s.data.data;
            if (j?.status === 'COMPLETED' && j.outputFilePath) {
              fetchArticle();
              setRenderLoading(false);
              projectApi.getOutputUrl(j.projectId)
                .then((r) => { if (r.data.data) setOutputUrl(r.data.data); })
                .catch(() => setOutputUrl(j.outputFilePath));
              return;
            }
            if (j?.status === 'FAILED') {
              setError(j.errorMessage ?? '렌더 실패');
              setRenderLoading(false);
              return;
            }
            setTimeout(poll, 3000);
          } catch {
            setTimeout(poll, 3000);
          }
        };
        poll();
      } else {
        setRenderLoading(false);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '렌더 요청 실패');
      setRenderLoading(false);
    }
  };

  const canEdit = article?.status === 'MULTIMEDIA_READY' || article?.status === 'RENDERED';
  const statusCfg = article ? STATUS_CONFIG[article.status] : STATUS_CONFIG.FETCHED;
  const imageKeywords = parseKeywords(article?.imageSearchKeywords ?? null);
  const videoKeywords = parseKeywords(article?.videoSearchKeywords ?? null);

  if (loading) {
    return (
      <>
        <Navbar />
        <main className="min-h-screen pt-24 flex items-center justify-center">
          <Loader2 size={32} className="animate-spin text-purple-400" />
        </main>
      </>
    );
  }

  if (!article) {
    return (
      <>
        <Navbar />
        <main className="min-h-screen pt-24 px-4">
          <div className="max-w-2xl mx-auto flex flex-col items-center gap-4 py-16">
            <AlertCircle size={48} className="text-red-400" />
            <p className="text-gray-400">{error || '기사를 찾을 수 없습니다.'}</p>
            <button
              onClick={() => router.push('/news-articles')}
              className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium text-white"
              style={{ background: 'rgba(124,58,237,0.3)' }}
            >
              <ArrowLeft size={16} />
              목록으로
            </button>
          </div>
        </main>
      </>
    );
  }

  return (
    <>
      <Navbar />
      <main className="min-h-screen pt-24 pb-16 px-4">
        <div className="max-w-4xl mx-auto">
          {/* 뒤로가기 */}
          <button
            onClick={() => router.push('/news-articles')}
            className="flex items-center gap-2 text-gray-400 hover:text-white text-sm mb-8 transition-colors"
          >
            <ArrowLeft size={16} />
            목록으로
          </button>

          {/* 메타데이터 카드 */}
          <div className="rounded-2xl overflow-hidden mb-8"
            style={{
              background: 'rgba(255,255,255,0.03)',
              border: '1px solid rgba(255,255,255,0.08)',
            }}
          >
            {/* 썸네일 + 제목 */}
            <div className="relative h-48 bg-gradient-to-br from-purple-900/30 to-blue-900/20 flex items-end p-6">
              {article.urlToImage && (
                <img
                  src={article.urlToImage}
                  alt=""
                  className="absolute inset-0 w-full h-full object-cover opacity-40"
                />
              )}
              <div className="relative z-10">
                <span
                  className="inline-flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-full mb-3"
                  style={{ color: statusCfg.color, background: statusCfg.bg }}
                >
                  {statusCfg.label}
                </span>
                <h1 className="text-2xl font-bold text-white">
                  {article.translatedTitle || article.title}
                </h1>
                <p className="text-sm text-gray-400 mt-1">
                  {article.sourceName} · {article.author?.split(',')[0] ?? ''}
                </p>
              </div>
            </div>

            <div className="p-6 space-y-6">
              {/* 번역 본문 */}
              {article.translatedContent && (
                <div>
                  <h3 className="flex items-center gap-2 text-sm font-semibold text-gray-400 mb-2">
                    <FileText size={14} />
                    본문 요약
                  </h3>
                  <p className="text-gray-300 leading-relaxed">{article.translatedContent}</p>
                </div>
              )}

              {/* 대본 (수정 가능/읽기 전용) */}
              <div>
                <h3 className="flex items-center gap-2 text-sm font-semibold text-gray-400 mb-2">
                  <Edit3 size={14} />
                  숏폼 대본
                  {canEdit && (
                    <span className="text-xs text-emerald-400">(편집 가능)</span>
                  )}
                </h3>
                <textarea
                  value={scriptEdit}
                  onChange={(e) => setScriptEdit(e.target.value)}
                  disabled={!canEdit}
                  rows={6}
                  className="w-full px-4 py-3 rounded-xl text-gray-300 leading-relaxed resize-none"
                  style={{
                    background: canEdit ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.2)',
                    border: '1px solid rgba(255,255,255,0.08)',
                  }}
                  placeholder="대본이 없습니다."
                />
              </div>

              {/* 검색 키워드 (OpenAI 추출 + 사용자 추가, 활성화/비활성화) */}
              {(article.status === 'METADATA_READY' || article.status === 'MULTIMEDIA_READY' || article.status === 'RENDERED') && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <h3 className="flex items-center gap-2 text-sm font-semibold text-gray-400 mb-2">
                      <Image size={14} />
                      이미지 검색어
                      <span className="text-[10px] text-amber-400/90">(AI 추출 검색어: 삭제 불가, ⊙/○로 활성화·비활성화 / 사용자 추가: 20자)</span>
                    </h3>
                    {canEdit ? (
                      <div className="space-y-2">
                        <div className="flex flex-wrap gap-2 items-center">
                          {imageKeywordsEdit.map((k, i) => (
                            <span
                              key={i}
                              className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-medium ${!k.enabled ? 'opacity-50 line-through' : ''}`}
                              style={{ background: k.source === 'openai' ? 'rgba(96,165,250,0.2)' : 'rgba(34,197,94,0.15)', color: k.source === 'openai' ? '#93C5FD' : '#86EFAC' }}
                            >
                              {k.keyword}
                              {k.source === 'openai' && (
                                <span className="text-[9px] px-1 rounded bg-amber-500/30 text-amber-200">AI</span>
                              )}
                              {k.source === 'openai' ? (
                                <button
                                  type="button"
                                  onClick={() => handleToggleKeywordEnabled('image', i)}
                                  className="hover:opacity-80"
                                  title={k.enabled ? '비활성화 (검색 제외)' : '활성화'}
                                >
                                  {k.enabled ? '⊙' : '○'}
                                </button>
                              ) : (
                                <button type="button" onClick={() => handleRemoveUserKeyword('image', i)} className="hover:opacity-80">
                                  <X size={12} />
                                </button>
                              )}
                            </span>
                          ))}
                          <input
                            type="text"
                            placeholder="+ 추가 (20자)"
                            maxLength={20}
                            className="px-2 py-1 rounded-lg text-xs w-28 bg-white/5 border border-white/10 text-gray-300 placeholder-gray-500 focus:outline-none focus:border-purple-500"
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') {
                                const el = e.target as HTMLInputElement;
                                handleAddUserKeyword('image', el.value);
                                el.value = '';
                              }
                            }}
                          />
                        </div>
                        <button type="button" onClick={handleSaveKeywords} disabled={keywordsSaving}
                          className="text-xs px-3 py-1 rounded-lg bg-blue-600/30 text-blue-300 hover:bg-blue-600/50 disabled:opacity-50">
                          {keywordsSaving ? '저장 중...' : '저장'}
                        </button>
                      </div>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        {imageKeywords.filter((k) => k.enabled).length > 0
                          ? imageKeywords.filter((k) => k.enabled).map((k, i) => (
                              <span key={i} className="px-2.5 py-1 rounded-lg text-xs font-medium" style={{ background: 'rgba(96,165,250,0.15)', color: '#93C5FD' }}>{k.keyword}</span>
                            ))
                          : <span className="text-gray-400 text-sm">-</span>}
                      </div>
                    )}
                  </div>
                  <div>
                    <h3 className="flex items-center gap-2 text-sm font-semibold text-gray-400 mb-2">
                      <Video size={14} />
                      영상 검색어
                      <span className="text-[10px] text-amber-400/90">(AI 추출 검색어: 삭제 불가, ⊙/○로 활성화·비활성화 / 사용자 추가: 20자)</span>
                    </h3>
                    {canEdit ? (
                      <div className="space-y-2">
                        <div className="flex flex-wrap gap-2 items-center">
                          {videoKeywordsEdit.map((k, i) => (
                            <span
                              key={i}
                              className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-medium ${!k.enabled ? 'opacity-50 line-through' : ''}`}
                              style={{ background: k.source === 'openai' ? 'rgba(167,139,250,0.2)' : 'rgba(34,197,94,0.15)', color: k.source === 'openai' ? '#C4B5FD' : '#86EFAC' }}
                            >
                              {k.keyword}
                              {k.source === 'openai' && (
                                <span className="text-[9px] px-1 rounded bg-amber-500/30 text-amber-200">AI</span>
                              )}
                              {k.source === 'openai' ? (
                                <button type="button" onClick={() => handleToggleKeywordEnabled('video', i)} className="hover:opacity-80" title={k.enabled ? '비활성화' : '활성화'}>
                                  {k.enabled ? '⊙' : '○'}
                                </button>
                              ) : (
                                <button type="button" onClick={() => handleRemoveUserKeyword('video', i)} className="hover:opacity-80"><X size={12} /></button>
                              )}
                            </span>
                          ))}
                          <input
                            type="text"
                            placeholder="+ 추가 (20자)"
                            maxLength={20}
                            className="px-2 py-1 rounded-lg text-xs w-28 bg-white/5 border border-white/10 text-gray-300 placeholder-gray-500 focus:outline-none focus:border-purple-500"
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') {
                                const el = e.target as HTMLInputElement;
                                handleAddUserKeyword('video', el.value);
                                el.value = '';
                              }
                            }}
                          />
                        </div>
                        <button type="button" onClick={handleSaveKeywords} disabled={keywordsSaving}
                          className="text-xs px-3 py-1 rounded-lg bg-purple-600/30 text-purple-300 hover:bg-purple-600/50 disabled:opacity-50">
                          {keywordsSaving ? '저장 중...' : '저장'}
                        </button>
                      </div>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        {videoKeywords.filter((k) => k.enabled).length > 0
                          ? videoKeywords.filter((k) => k.enabled).map((k, i) => (
                              <span key={i} className="px-2.5 py-1 rounded-lg text-xs font-medium" style={{ background: 'rgba(167,139,250,0.15)', color: '#C4B5FD' }}>{k.keyword}</span>
                            ))
                          : <span className="text-gray-400 text-sm">-</span>}
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* 예상 길이 */}
              {article.estimatedDurationSeconds && (
                <p className="text-sm text-gray-500">
                  예상 영상 길이: 약 {Math.round(article.estimatedDurationSeconds)}초
                </p>
              )}

              {/* Confirm 버튼 + 에러 표시 */}
              {(article.status === 'METADATA_READY' || article.status === 'MULTIMEDIA_READY') && (
                <div className="pt-4">
                  {error && (
                    <div className="mb-4 flex items-center gap-2 px-4 py-3 rounded-xl text-sm text-red-300"
                      style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)' }}
                    >
                      <AlertCircle size={18} />
                      {error}
                    </div>
                  )}
                  <button
                    onClick={handleConfirm}
                    disabled={confirmLoading}
                    className="flex items-center gap-2 px-6 py-3 rounded-xl font-semibold text-white transition-all disabled:opacity-60"
                    style={{
                      background: 'linear-gradient(135deg, #7C3AED, #2563EB)',
                      boxShadow: '0 4px 20px rgba(124,58,237,0.4)',
                    }}
                  >
                    {confirmLoading ? (
                      <Loader2 size={18} className="animate-spin" />
                    ) : (
                      <Search size={18} />
                    )}
                    {article.status === 'MULTIMEDIA_READY' ? '다시 검색' : '멀티미디어 검색'}
                  </button>
                  <p className="text-xs text-gray-500 mt-2">
                    {article.status === 'MULTIMEDIA_READY'
                      ? '키워드 수정 후 다시 검색하면 결과가 갱신됩니다.'
                      : '관련 영상·이미지를 검색합니다. 완료 후 자막·미디어 편집이 가능합니다.'}
                  </p>
                </div>
              )}

              {/* 멀티미디어 검색 중 */}
              {article.status === 'MULTIMEDIA_FETCHING' && (
                <div className="flex items-center gap-3 px-4 py-3 rounded-xl"
                  style={{ background: 'rgba(167,139,250,0.1)', border: '1px solid rgba(167,139,250,0.2)' }}
                >
                  <Loader2 size={20} className="animate-spin text-purple-400" />
                  <span className="text-purple-300">멀티미디어 검색 중...</span>
                </div>
              )}

              {/* 편집 가능 안내 */}
              {canEdit && (
                <div className="flex items-center gap-3 px-4 py-3 rounded-xl"
                  style={{ background: 'rgba(52,211,153,0.1)', border: '1px solid rgba(52,211,153,0.2)' }}
                >
                  <CheckCircle size={20} className="text-emerald-400" />
                  <span className="text-emerald-300">자막·미디어 편집이 가능합니다.</span>
                </div>
              )}

              {/* 미디어 검색 결과 (편집 가능 시 표시) */}
              {canEdit && (
                <div>
                  <h3 className="flex items-center gap-2 text-sm font-semibold text-gray-400 mb-3">
                    <Video size={14} />
                    멀티미디어
                    <span className="text-xs font-normal text-gray-500">
                      (클릭하여 선택/해제, 최소 2개 이상 선택)
                    </span>
                  </h3>

                  {/* 수동 추가: URL 또는 파일 */}
                  <div className="mb-4 space-y-3">
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => { setAddMediaMode('url'); setAddMediaFile(null); }}
                        className={`px-3 py-1.5 rounded-lg text-sm ${addMediaMode === 'url' ? 'bg-purple-600/60 text-white' : 'bg-white/5 text-gray-400 hover:text-gray-300'}`}
                      >
                        URL
                      </button>
                      <button
                        type="button"
                        onClick={() => { setAddMediaMode('file'); setAddMediaUrl(''); }}
                        className={`px-3 py-1.5 rounded-lg text-sm ${addMediaMode === 'file' ? 'bg-purple-600/60 text-white' : 'bg-white/5 text-gray-400 hover:text-gray-300'}`}
                      >
                        파일
                      </button>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      {addMediaMode === 'url' ? (
                        <input
                          type="url"
                          value={addMediaUrl}
                          onChange={(e) => setAddMediaUrl(e.target.value)}
                          placeholder="이미지 또는 영상 URL"
                          className="flex-1 min-w-[200px] px-3 py-2 rounded-lg text-sm bg-white/5 border border-white/10 text-gray-300 placeholder-gray-500 focus:outline-none focus:border-purple-500"
                        />
                      ) : (
                        <label className="flex-1 min-w-[200px] px-3 py-2 rounded-lg text-sm bg-white/5 border border-white/10 text-gray-300 cursor-pointer hover:border-purple-500/50">
                          <input
                            type="file"
                            accept="image/*,video/*"
                            className="sr-only"
                            onChange={(e) => setAddMediaFile(e.target.files?.[0] ?? null)}
                          />
                          <span className="text-gray-500">{addMediaFile ? addMediaFile.name : '파일 선택'}</span>
                        </label>
                      )}
                      <select
                        value={addMediaType}
                        onChange={(e) => setAddMediaType(e.target.value as 'VIDEO' | 'IMAGE')}
                        className="px-3 py-2 rounded-lg text-sm bg-white/5 border border-white/10 text-gray-300 focus:outline-none focus:border-purple-500"
                      >
                        <option value="IMAGE">이미지</option>
                        <option value="VIDEO">영상</option>
                      </select>
                      <button
                        type="button"
                        onClick={handleAddMedia}
                        disabled={(addMediaMode === 'url' ? !addMediaUrl.trim() : !addMediaFile) || addMediaLoading}
                        className="flex items-center gap-1 px-4 py-2 rounded-lg text-sm font-medium bg-purple-600/40 text-purple-200 hover:bg-purple-600/60 disabled:opacity-50"
                      >
                        {addMediaLoading ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
                        {addMediaMode === 'url' ? 'URL로 추가' : '파일 업로드'}
                      </button>
                    </div>
                  </div>

                  {mediaLoading ? (
                    <div className="flex items-center justify-center py-12">
                      <Loader2 size={24} className="animate-spin text-purple-400" />
                    </div>
                  ) : media.length > 0 ? (
                    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                      {media.map((m) => (
                        <div key={m.id} className="space-y-1">
                          <button
                            type="button"
                            onClick={() => handleToggleMedia(m.id)}
                            className="relative aspect-video rounded-xl overflow-hidden group focus:outline-none focus:ring-2 focus:ring-purple-500 w-full"
                            style={{
                              border: m.selected ? '2px solid #A78BFA' : '1px solid rgba(255,255,255,0.1)',
                              background: 'rgba(255,255,255,0.02)',
                            }}
                          >
                            {m.mediaType === 'VIDEO' ? (
                              <video
                                src={m.sourceUrl}
                                poster={m.thumbnailUrl ?? undefined}
                                className="w-full h-full object-cover"
                                muted
                                playsInline
                                onMouseEnter={(e) => e.currentTarget.play()}
                                onMouseLeave={(e) => { e.currentTarget.pause(); e.currentTarget.currentTime = 0; }}
                              />
                            ) : (
                              <img
                                src={m.sourceUrl}
                                alt=""
                                className="w-full h-full object-cover"
                              />
                            )}
                            <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                              {m.selected ? <CheckCircle size={28} className="text-emerald-400" /> : null}
                            </div>
                            {m.selected && (
                              <div className="absolute top-2 left-2">
                                <CheckCircle size={18} className="text-emerald-400 drop-shadow" />
                              </div>
                            )}
                            <span className="absolute bottom-1 left-1 right-1 text-[10px] text-white/80 truncate bg-black/50 px-1 rounded flex justify-between">
                              <span>{m.mediaType}</span>
                              {m.mediaType === 'VIDEO' && m.durationSeconds != null && (
                                <span>{Math.round(m.durationSeconds)}초</span>
                              )}
                            </span>
                          </button>
                          {m.selected && (
                            <div className="flex items-center gap-1">
                              <input
                                type="number"
                                min={0.5}
                                max={article?.estimatedDurationSeconds ?? 60}
                                step={0.5}
                                value={exposureEdits[m.id] ?? m.exposureDurationSeconds ?? (m.mediaType === 'VIDEO' ? m.durationSeconds : 4) ?? 4}
                                onChange={(e) => setExposureEdits((p) => ({ ...p, [m.id]: parseFloat(e.target.value) || 0 }))}
                                className="w-16 px-1.5 py-0.5 rounded text-xs bg-white/5 border border-white/10 text-gray-300"
                              />
                              <span className="text-[10px] text-gray-500">초 노출</span>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div
                      className="rounded-xl p-8 text-center"
                      style={{
                        background: 'rgba(255,255,255,0.02)',
                        border: '1px dashed rgba(255,255,255,0.1)',
                      }}
                    >
                      <p className="text-gray-500 text-sm">검색된 미디어가 없습니다. URL로 추가하거나 멀티미디어 검색을 실행하세요.</p>
                    </div>
                  )}

                  {/* 노출 시간 저장 버튼 */}
                  {media.some((m) => m.selected) && Object.keys(exposureEdits).length > 0 && (
                    <div className="mt-3">
                      <button
                        type="button"
                        onClick={handleSaveExposure}
                        disabled={exposureSaving}
                        className="text-xs px-3 py-1.5 rounded-lg bg-amber-600/30 text-amber-300 hover:bg-amber-600/50 disabled:opacity-50"
                      >
                        {exposureSaving ? '저장 중...' : '노출 시간 저장'}
                      </button>
                      <span className="text-xs text-gray-500 ml-2">
                        (대본 길이 {Math.round(article?.estimatedDurationSeconds ?? 60)}초 이내)
                      </span>
                    </div>
                  )}

                  {/* 숏폼 제작 버튼 */}
                  {canEdit && media.some((m) => m.selected) && (
                    <div className="mt-6 pt-4 border-t border-white/5">
                      {error && (
                        <div className="mb-4 flex items-center gap-2 px-4 py-3 rounded-xl text-sm text-red-300"
                          style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)' }}
                        >
                          <AlertCircle size={18} />
                          {error}
                        </div>
                      )}
                      <button
                        onClick={handleRequestRender}
                        disabled={
                          renderLoading ||
                          article.status === 'RENDER_REQUESTED' ||
                          media.filter((m) => m.selected).length < 2
                        }
                        className="flex items-center gap-2 px-6 py-3 rounded-xl font-semibold text-white transition-all disabled:opacity-60 disabled:cursor-not-allowed"
                        style={{
                          background: 'linear-gradient(135deg, #059669, #0D9488)',
                          boxShadow: '0 4px 20px rgba(5,150,105,0.4)',
                        }}
                      >
                        {renderLoading || article.status === 'RENDER_REQUESTED' ? (
                          <Loader2 size={18} className="animate-spin" />
                        ) : (
                          <Film size={18} />
                        )}
                        숏폼 제작
                      </button>
                      {media.filter((m) => m.selected).length < 2 && (
                        <p className="text-xs text-amber-400 mt-1">멀티미디어를 최소 2개 이상 선택해 주세요.</p>
                      )}
                      <p className="text-xs text-gray-500 mt-2">
                        선택한 미디어와 대본으로 TTS 내레이션 영상을 생성합니다.
                      </p>
                    </div>
                  )}
                </div>
              )}

              {/* 완료된 영상 재생 */}
              {(article.status === 'RENDERED' || outputUrl) && outputUrl && (
                <div className="mt-6">
                  <h3 className="flex items-center gap-2 text-sm font-semibold text-gray-400 mb-3">
                    <Play size={14} />
                    숏폼 제작물
                  </h3>
                  <div className="rounded-xl overflow-hidden" style={{ background: '#000' }}>
                    <video
                      src={outputUrl}
                      controls
                      className="w-full aspect-[9/16] max-h-[70vh] object-contain"
                      playsInline
                    />
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* 원본 링크 */}
          <a
            href={article.url}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-purple-400 transition-colors"
          >
            <ExternalLink size={14} />
            원본 기사 보기
          </a>
        </div>
      </main>
    </>
  );
}
