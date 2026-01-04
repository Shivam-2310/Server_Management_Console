import { useEffect } from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { ToastNotifications } from '../ToastNotifications';
import { useAuthStore, useUIStore } from '@/lib/store';
import wsService from '@/lib/websocket';
import { cn } from '@/lib/utils';

export function Layout() {
  const { isAuthenticated } = useAuthStore();
  const { sidebarCollapsed } = useUIStore();

  useEffect(() => {
    if (isAuthenticated) {
      wsService.connect();
    }

    return () => {
      wsService.disconnect();
    };
  }, [isAuthenticated]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="min-h-screen bg-obsidian-950">
      <Sidebar />

      <motion.div
        className={cn('min-h-screen transition-all duration-300')}
        initial={false}
        animate={{ marginLeft: sidebarCollapsed ? 72 : 240 }}
      >
        <Header />
        <ToastNotifications />

        <main className="p-6">
          <div>
            <Outlet />
          </div>
        </main>
      </motion.div>
    </div>
  );
}
