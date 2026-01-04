import { motion, AnimatePresence } from 'framer-motion';
import { X, AlertCircle, CheckCircle, AlertTriangle, Info } from 'lucide-react';
import { useUIStore } from '@/lib/store';
import { useEffect } from 'react';
import { cn } from '@/lib/utils';

export function ToastNotifications() {
  const { notifications, removeNotification } = useUIStore();

  useEffect(() => {
    const timers: NodeJS.Timeout[] = [];
    
    notifications.forEach((notification) => {
      if (notification.duration && notification.duration > 0) {
        const timer = setTimeout(() => {
          removeNotification(notification.id);
        }, notification.duration);
        timers.push(timer);
      }
    });

    return () => {
      timers.forEach((timer) => clearTimeout(timer));
    };
  }, [notifications, removeNotification]);

  const getNotificationIcon = (type: string) => {
    switch (type) {
      case 'error':
        return <AlertCircle className="w-5 h-5 text-rose-400" />;
      case 'warning':
        return <AlertTriangle className="w-5 h-5 text-amber-400" />;
      case 'success':
        return <CheckCircle className="w-5 h-5 text-emerald-400" />;
      default:
        return <Info className="w-5 h-5 text-cyan-400" />;
    }
  };

  const getNotificationStyles = (type: string) => {
    switch (type) {
      case 'error':
        return 'bg-obsidian-900 border-rose-500/50 shadow-rose-500/20';
      case 'warning':
        return 'bg-obsidian-900 border-amber-500/50 shadow-amber-500/20';
      case 'success':
        return 'bg-obsidian-900 border-emerald-500/50 shadow-emerald-500/20';
      default:
        return 'bg-obsidian-900 border-cyan-500/50 shadow-cyan-500/20';
    }
  };

  return (
    <div className="fixed top-20 right-6 z-50 flex flex-col gap-2 pointer-events-none">
      <AnimatePresence>
        {notifications.map((notification) => (
          <motion.div
            key={notification.id}
            initial={{ opacity: 0, x: 100, scale: 0.9 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: 100, scale: 0.9 }}
            transition={{ type: 'spring', stiffness: 300, damping: 30 }}
            className={cn(
              'pointer-events-auto w-96 p-4 rounded-lg border shadow-lg backdrop-blur-sm',
              getNotificationStyles(notification.type)
            )}
          >
            <div className="flex items-start gap-3">
              <div className="mt-0.5 flex-shrink-0">
                {getNotificationIcon(notification.type)}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-obsidian-200">
                  {notification.title}
                </p>
                {notification.message && (
                  <p className="text-xs text-obsidian-400 mt-1">
                    {notification.message}
                  </p>
                )}
              </div>
              <button
                onClick={() => removeNotification(notification.id)}
                className="flex-shrink-0 p-1 hover:bg-obsidian-800 rounded transition-colors"
              >
                <X className="w-4 h-4 text-obsidian-400" />
              </button>
            </div>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}

