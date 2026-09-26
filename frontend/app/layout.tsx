import type { Metadata } from 'next';
import localFont from 'next/font/local';
import './globals.css';
import { Header } from '@/components/layout/Header';
import { Sidebar } from '@/components/layout/Sidebar';

const geistSans = localFont({
  src: './fonts/GeistVF.woff',
  variable: '--font-geist-sans',
  weight: '100 900',
});

const geistMono = localFont({
  src: './fonts/GeistMonoVF.woff',
  variable: '--font-geist-mono',
  weight: '100 900',
});

export const metadata: Metadata = {
  title: 'TaskFlow Pro | Deterministic DAG Scheduling Platform',
  description:
    'Dependency-aware workflow and DAG scheduling platform with cycle detection, readiness calculation, and schedule propagation.',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="dark">
      <body className={`${geistSans.variable} ${geistMono.variable} antialiased bg-zinc-950 text-zinc-100 min-h-screen flex flex-col font-sans`}>
        <Header />
        <div className="flex-1 flex">
          <Sidebar />
          <main className="flex-1 p-4 sm:p-6 md:p-8 overflow-auto">{children}</main>
        </div>
      </body>
    </html>
  );
}
