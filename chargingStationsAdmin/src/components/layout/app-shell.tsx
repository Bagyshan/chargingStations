import { useState } from 'react';
import { Outlet } from '@tanstack/react-router';
import { Sidebar } from './sidebar';
import { Topbar } from './topbar';
import { Toaster } from '@/components/ui/toaster';
import { useRealtime } from '@/realtime/use-realtime';

export function AppShell() {
  const [mobileOpen, setMobileOpen] = useState(false);
  useRealtime();
  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar onOpenSidebar={() => setMobileOpen(true)} />
        <main className="flex-1 px-4 py-6 lg:px-8">
          <div className="mx-auto w-full max-w-7xl animate-in-up">
            <Outlet />
          </div>
        </main>
      </div>
      <Toaster />
    </div>
  );
}
