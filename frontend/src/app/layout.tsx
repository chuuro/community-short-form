import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'ShortForm Studio — 커뮤니티 숏폼 메이커',
  description: '커뮤니티 게시글을 AI가 자동으로 숏폼 영상으로 만들어드립니다.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <body className="bg-[#07070F] text-white min-h-screen">
        {children}
      </body>
    </html>
  );
}
