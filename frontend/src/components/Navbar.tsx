'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Video, Layers, Newspaper, Settings } from 'lucide-react';

export default function Navbar() {
  const pathname = usePathname();
  const isHome = pathname === '/';

  return (
    <nav className="fixed top-0 left-0 right-0 z-40 h-16 flex items-center px-6"
      style={{
        background: 'linear-gradient(180deg, rgba(7,7,15,0.95) 0%, rgba(7,7,15,0) 100%)',
        backdropFilter: isHome ? 'none' : 'blur(20px)',
        borderBottom: isHome ? 'none' : '1px solid rgba(255,255,255,0.06)',
      }}
    >
      <div className="max-w-7xl w-full mx-auto flex items-center justify-between">
        {/* 로고 */}
        <Link href="/" className="flex items-center gap-2.5 group">
          <div className="w-8 h-8 rounded-lg flex items-center justify-center"
            style={{ background: 'linear-gradient(135deg, #7C3AED, #2563EB)' }}
          >
            <Video size={16} className="text-white" />
          </div>
          <span className="font-bold text-lg tracking-tight">
            Short<span className="text-purple-400">Form</span>
          </span>
        </Link>

        {/* 네비게이션 */}
        <div className="flex items-center gap-1">
          <Link
            href="/"
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all ${
              pathname === '/'
                ? 'bg-white/10 text-white'
                : 'text-gray-400 hover:text-white hover:bg-white/5'
            }`}
          >
            <Layers size={15} />
            프로젝트
          </Link>
          <Link
            href="/news-articles"
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all ${
              pathname?.startsWith('/news-articles')
                ? 'bg-white/10 text-white'
                : 'text-gray-400 hover:text-white hover:bg-white/5'
            }`}
          >
            <Newspaper size={15} />
            뉴스
          </Link>
          <button className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium text-gray-400 hover:text-white hover:bg-white/5 transition-all">
            <Settings size={15} />
            설정
          </button>
        </div>
      </div>
    </nav>
  );
}
