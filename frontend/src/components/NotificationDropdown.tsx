import { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Bell, X, AlertCircle, CheckCircle, AlertTriangle, Info } from 'lucide-react';
import { useUIStore, useDashboardStore } from '@/lib/store';
import { cn } from '@/lib/utils';
import type { Incident } from '@/types';

export function NotificationDropdown({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  const { notifications, removeNotification } = useUIStore();
  const { dashboard } = useDashboardStore();
  const dropdownRef = useRef<HTMLDivElement>(null);
  const [dismissedIncidents, setDismissedIncidents] = useState<Set<string>>(() => {
    // Load dismissed incidents from localStorage
    const stored = localStorage.getItem('dismissedIncidents');
    return stored ? new Set(JSON.parse(stored)) : new Set();
  });

  // Save dismissed incidents to localStorage whenever it changes
  useEffect(() => {
    localStorage.setItem('dismissedIncidents', JSON.stringify(Array.from(dismissedIncidents)));
  }, [dismissedIncidents]);

  // Handle clicks outside the dropdown
  useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (dropdownRef.current && !dropdownRef.current.contains(target)) {
        onClose();
      }
    };

    // Use capture phase to catch events early
    document.addEventListener('mousedown', handleClickOutside, true);

    return () => {
      document.removeEventListener('mousedown', handleClickOutside, true);
    };
  }, [isOpen, onClose]);

  const handleDismissIncident = (incidentId: string) => {
    setDismissedIncidents((prev) => new Set([...prev, incidentId]));
  };

  const handleClose = () => {
    onClose();
  };

  const handleDismissNotification = (notificationId: string, isIncident: boolean) => {
    if (isIncident) {
      handleDismissIncident(notificationId);
    } else {
      removeNotification(notificationId);
    }
  };

  const getNotificationIcon = (type: string) => {
    switch (type) {
      case 'error':
        return <AlertCircle className="w-4 h-4 text-rose-400" />;
      case 'warning':
        return <AlertTriangle className="w-4 h-4 text-amber-400" />;
      case 'success':
        return <CheckCircle className="w-4 h-4 text-emerald-400" />;
      default:
        return <Info className="w-4 h-4 text-cyan-400" />;
    }
  };

  const getNotificationColor = (type: string) => {
    switch (type) {
      case 'error':
        return 'border-rose-500/20 bg-rose-500/10';
      case 'warning':
        return 'border-amber-500/20 bg-amber-500/10';
      case 'success':
        return 'border-emerald-500/20 bg-emerald-500/10';
      default:
        return 'border-cyan-500/20 bg-cyan-500/10';
    }
  };

  // Combine UI notifications with active incidents from dashboard
  const activeIncidents = dashboard?.activeIncidentsList || [];
  
  // Filter out dismissed incidents
  const visibleIncidents = activeIncidents.filter(
    (incident: Incident) => !dismissedIncidents.has(`incident-${incident.id}`)
  );
  
  const allNotifications = [
    ...visibleIncidents.map((incident: Incident) => ({
      id: `incident-${incident.id}`,
      type: incident.severity === 'CRITICAL' ? 'error' : incident.severity === 'HIGH' ? 'warning' : 'info',
      title: incident.title,
      message: incident.description || `Service: ${incident.serviceName || 'Unknown'}`,
      timestamp: incident.createdAt,
      isIncident: true,
    })),
    ...notifications.map((n) => ({ ...n, isIncident: false })),
  ].sort((a, b) => {
    const timeA = a.timestamp ? new Date(a.timestamp).getTime() : 0;
    const timeB = b.timestamp ? new Date(b.timestamp).getTime() : 0;
    return timeB - timeA;
  });

  if (!isOpen) return null;

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-40"
        onClick={handleClose}
        aria-hidden="true"
      />
      
      {/* Dropdown */}
      <AnimatePresence>
        {isOpen && (
          <motion.div
            ref={dropdownRef}
            initial={{ opacity: 0, y: -10, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -10, scale: 0.95 }}
            transition={{ type: 'spring', stiffness: 300, damping: 30 }}
            className="absolute right-0 mt-2 w-96 bg-obsidian-900 border border-obsidian-800 rounded-xl z-50 shadow-2xl max-h-[600px] flex flex-col"
          >
            {/* Header */}
            <div className="p-4 border-b border-obsidian-800 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Bell className="w-5 h-5 text-obsidian-300" />
                <h3 className="font-semibold text-obsidian-200">Notifications</h3>
                {allNotifications.length > 0 && (
                  <span className="px-2 py-0.5 bg-rose-500 text-white text-xs font-bold rounded-full">
                    {allNotifications.length}
                  </span>
                )}
              </div>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  handleClose();
                }}
                className="p-1.5 hover:bg-obsidian-800 rounded-lg transition-colors cursor-pointer"
                type="button"
                aria-label="Close notifications"
              >
                <X className="w-4 h-4 text-obsidian-400 hover:text-obsidian-200" />
              </button>
            </div>

            {/* Notifications List */}
            <div className="flex-1 overflow-y-auto">
              {allNotifications.length === 0 ? (
                <div className="p-8 text-center">
                  <Bell className="w-12 h-12 text-obsidian-600 mx-auto mb-3" />
                  <p className="text-sm text-obsidian-400">No notifications</p>
                </div>
              ) : (
                <div className="p-2">
                  {allNotifications.map((notification) => (
                    <div
                      key={notification.id}
                      className={cn(
                        'p-3 mb-2 rounded-lg border relative',
                        getNotificationColor(notification.type)
                      )}
                    >
                      <div className="flex items-start gap-3">
                        <div className="mt-0.5 flex-shrink-0">
                          {getNotificationIcon(notification.type)}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-obsidian-200">
                            {notification.title}
                          </p>
                          {notification.message && (
                            <p className="text-xs text-obsidian-400 mt-1 line-clamp-2">
                              {notification.message}
                            </p>
                          )}
                          {notification.timestamp && (
                            <p className="text-xs text-obsidian-500 mt-1">
                              {new Date(notification.timestamp).toLocaleString()}
                            </p>
                          )}
                        </div>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDismissNotification(notification.id, notification.isIncident);
                          }}
                          className="p-1.5 hover:bg-obsidian-800 active:bg-obsidian-700 rounded transition-colors cursor-pointer flex-shrink-0"
                          type="button"
                          aria-label="Dismiss notification"
                        >
                          <X className="w-3.5 h-3.5 text-obsidian-400 hover:text-obsidian-200" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
