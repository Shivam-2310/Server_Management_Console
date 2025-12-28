import { NavLink, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  LayoutDashboard,
  Server,
  AlertTriangle,
  FileText,
  Activity,
  Shield,
  Settings,
  ChevronLeft,
  Zap,
  Radio,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useUIStore, useDashboardStore } from '@/lib/store';

const navItems = [
  { path: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { path: '/services', icon: Server, label: 'Services' },
  { path: '/incidents', icon: AlertTriangle, label: 'Incidents' },
  { path: '/logs', icon: FileText, label: 'Logs' },
  { path: '/audit', icon: Shield, label: 'Audit Trail' },
  { path: '/diagnostics', icon: Activity, label: 'Diagnostics' },
];

export function Sidebar() {
  const { pathname } = useLocation();
  const { sidebarCollapsed, toggleSidebar } = useUIStore();
  const { isConnected, dashboard } = useDashboardStore();

  return (
    <motion.aside
      className={cn(
        'fixed left-0 top-0 h-screen bg-obsidian-950 border-r border-obsidian-800 z-40',
        'flex flex-col'
      )}
      initial={false}
      animate={{ width: sidebarCollapsed ? 72 : 240 }}
      transition={{ duration: 0.3, ease: 'easeInOut' }}
    >
      {/* Logo */}
      <div className="h-16 flex items-center px-4 border-b border-obsidian-800">
        <div className="flex items-center gap-3">
          <motion.div
            className="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center"
            whileHover={{ scale: 1.1, rotate: 180 }}
            whileTap={{ scale: 0.95 }}
            transition={{ type: 'spring', stiffness: 300, damping: 20 }}
          >
            <Zap className="w-5 h-5 text-white" style={{ strokeWidth: 2.5, fill: 'currentColor' }} />
          </motion.div>
          <AnimatePresence>
            {!sidebarCollapsed && (
              <div>
                <h1 className="font-bold text-obsidian-100 whitespace-nowrap">Server Console</h1>
                <p className="text-xs text-obsidian-400">Management Hub</p>
              </div>
            )}
          </AnimatePresence>
        </div>
      </div>

      {/* Connection Status */}
      <div className="px-4 py-3 border-b border-obsidian-800">
        <div
          className={cn(
            'flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-medium',
            isConnected
              ? 'bg-emerald-600 text-white border border-emerald-500'
              : 'bg-rose-600 text-white border border-rose-500'
          )}
        >
          <motion.div
            animate={isConnected ? { scale: [1, 1.2, 1] } : {}}
            transition={{ duration: 2, repeat: Infinity }}
          >
            <Radio className="w-3.5 h-3.5" style={{ strokeWidth: 2.5 }} />
          </motion.div>
          <AnimatePresence>
            {!sidebarCollapsed && (
              <span>
                {isConnected ? 'Live Connected' : 'Disconnected'}
              </span>
            )}
          </AnimatePresence>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-4 px-3 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const isActive = pathname === item.path || 
            (item.path !== '/' && pathname.startsWith(item.path));
          const Icon = item.icon;

          // Get badge count for incidents
          const badgeCount = item.path === '/incidents' 
            ? dashboard?.activeIncidents || 0 
            : null;

          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive: linkActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-200 group relative',
                  linkActive || isActive
                    ? 'bg-emerald-600 text-white border border-emerald-500'
                    : 'text-obsidian-300 hover:text-white hover:bg-obsidian-800 border border-transparent hover:border-obsidian-700'
                )
              }
            >
              {isActive && (
                <motion.div
                  layoutId="activeNav"
                  className="absolute left-0 w-1 h-6 bg-emerald-400 rounded-r-full"
                  transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                />
              )}
              <motion.div
                whileHover={{ scale: 1.1, rotate: 5 }}
                whileTap={{ scale: 0.95 }}
                transition={{ type: 'spring', stiffness: 400, damping: 17 }}
              >
                <Icon className="w-5 h-5 flex-shrink-0" style={{ strokeWidth: 2.5 }} />
              </motion.div>
              <AnimatePresence>
                {!sidebarCollapsed && (
                  <span className="whitespace-nowrap">
                    {item.label}
                  </span>
                )}
              </AnimatePresence>
              {badgeCount !== null && badgeCount > 0 && (
                <AnimatePresence>
                  {!sidebarCollapsed && (
                    <motion.span
                      initial={{ scale: 0 }}
                      animate={{ scale: 1 }}
                      exit={{ scale: 0 }}
                      className="ml-auto px-2 py-0.5 text-xs font-medium bg-rose-500 text-white rounded-full"
                    >
                      {badgeCount}
                    </motion.span>
                  )}
                </AnimatePresence>
              )}
            </NavLink>
          );
        })}
      </nav>

      {/* Settings & Collapse */}
      <div className="p-3 border-t border-obsidian-800 space-y-1">
        <NavLink
          to="/settings"
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-200',
              isActive
                ? 'bg-emerald-600 text-white border border-emerald-500'
                : 'text-obsidian-300 hover:text-white hover:bg-obsidian-800 border border-transparent hover:border-obsidian-700'
            )
          }
        >
          <motion.div
            whileHover={{ scale: 1.1, rotate: 15 }}
            whileTap={{ scale: 0.95 }}
            transition={{ type: 'spring', stiffness: 400, damping: 17 }}
          >
            <Settings className="w-5 h-5" style={{ strokeWidth: 2.5 }} />
          </motion.div>
          <AnimatePresence>
            {!sidebarCollapsed && (
              <span>
                Settings
              </span>
            )}
          </AnimatePresence>
        </NavLink>

        <button
          onClick={toggleSidebar}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-obsidian-300 hover:text-white hover:bg-obsidian-800 border border-transparent hover:border-obsidian-700 transition-all duration-200"
        >
          <motion.div
            animate={{ rotate: sidebarCollapsed ? 180 : 0 }}
            transition={{ duration: 0.3 }}
          >
            <motion.div
              whileHover={{ scale: 1.1 }}
              whileTap={{ scale: 0.9 }}
              transition={{ type: 'spring', stiffness: 400, damping: 17 }}
            >
              <ChevronLeft className="w-5 h-5" />
            </motion.div>
          </motion.div>
          <AnimatePresence>
            {!sidebarCollapsed && (
              <span>
                Collapse
              </span>
            )}
          </AnimatePresence>
        </button>
      </div>
    </motion.aside>
  );
}
